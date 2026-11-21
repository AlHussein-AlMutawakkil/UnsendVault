package com.riyadm.unsendvault.ui

import app.cash.turbine.test
import com.riyadm.unsendvault.core.model.EncryptionMode
import com.riyadm.unsendvault.crypto.message.LocalKeyCipher
import com.riyadm.unsendvault.crypto.message.MessageCryptoEngine
import com.riyadm.unsendvault.crypto.message.SteganographyEngine
import com.riyadm.unsendvault.crypto.message.UserMessageKeyManager
import com.riyadm.unsendvault.data.fake.InMemorySettingsRepository
import com.riyadm.unsendvault.data.fake.InMemoryVaultRepository
import com.riyadm.unsendvault.data.security.KeyWrapper
import com.riyadm.unsendvault.reply.ReplyAvailability
import com.riyadm.unsendvault.reply.ReplyGateway
import com.riyadm.unsendvault.reply.ReplyResult
import com.riyadm.unsendvault.ui.conversation.ConversationEvent
import com.riyadm.unsendvault.ui.conversation.ConversationViewModel
import com.riyadm.unsendvault.ui.conversation.TimelineItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.ZoneOffset

class MessageEncryptionViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private class FakeKeyWrapper : KeyWrapper {
        override fun keyExists(): Boolean = true
        override fun createKey(preferStrongBox: Boolean) {}
        override fun wrap(plain: ByteArray): ByteArray = plain
        override fun unwrap(blob: ByteArray): ByteArray = blob
        override fun deleteKey() {}
    }

    private class RecordingReplyGateway(
        var availability: ReplyAvailability = ReplyAvailability.AVAILABLE,
    ) : ReplyGateway {
        var lastSentPkg: String? = null
        var lastSentKey: String? = null
        var lastSentText: String? = null

        override suspend fun availability(packageName: String, conversationKey: String): ReplyAvailability = availability

        override suspend fun send(packageName: String, conversationKey: String, text: String): ReplyResult {
            lastSentPkg = packageName
            lastSentKey = conversationKey
            lastSentText = text
            return if (availability == ReplyAvailability.AVAILABLE) ReplyResult.SENT else ReplyResult.UNAVAILABLE
        }
    }

    @Test
    fun conversationViewModelEncryptsExplicitReply() = runTest {
        val vault = InMemoryVaultRepository(Fixtures.conversations, Fixtures.messages)
        val settings = InMemorySettingsRepository()
        val keyManager = UserMessageKeyManager(settings, LocalKeyCipher(FakeKeyWrapper()))
        keyManager.setGlobalKey("global_secret_key_123")

        val gateway = RecordingReplyGateway()
        val vm = ConversationViewModel(
            conversationId = Fixtures.alex.id,
            vault = vault,
            zone = ZoneOffset.UTC,
            replyGateway = gateway,
            userMessageKeyManager = keyManager,
            settingsRepository = settings,
        )

        vm.state.testState {
            awaitMatching { !it.loading }
            vm.setEncryptionMode(EncryptionMode.EXPLICIT)

            vm.events.test {
                vm.sendReply("Secret password is 42")
                val event = awaitItem()
                assertTrue(event is ConversationEvent.Reply)
                assertEquals(ReplyResult.SENT, (event as ConversationEvent.Reply).result)
            }

            val sent = gateway.lastSentText
            assertNotNull(sent)
            assertTrue(MessageCryptoEngine.isEncrypted(sent!!))
            assertTrue(sent.startsWith("[UV:enc:"))
            assertFalse(sent.contains("🔒"))

            val decrypted = MessageCryptoEngine.decrypt(sent, listOf("global_secret_key_123"))
            assertNotNull(decrypted)
            assertEquals("Secret password is 42", decrypted!!.plaintext)
            assertEquals(EncryptionMode.EXPLICIT, decrypted.mode)
        }
    }

    @Test
    fun conversationViewModelEncryptsSteganoReplyWithCoverText() = runTest {
        val vault = InMemoryVaultRepository(Fixtures.conversations, Fixtures.messages)
        val settings = InMemorySettingsRepository()
        val keyManager = UserMessageKeyManager(settings, LocalKeyCipher(FakeKeyWrapper()))
        keyManager.setConversationKey("${Fixtures.alex.packageName}:${Fixtures.alex.conversationKey}", "alex_private_key")

        val gateway = RecordingReplyGateway()
        val vm = ConversationViewModel(
            conversationId = Fixtures.alex.id,
            vault = vault,
            zone = ZoneOffset.UTC,
            replyGateway = gateway,
            userMessageKeyManager = keyManager,
            settingsRepository = settings,
        )

        vm.state.testState {
            awaitMatching { !it.loading }
            vm.setEncryptionMode(EncryptionMode.STEGANO)
            vm.setCustomCoverText("أهلاً بك، هل تود اللقاء غداً؟")

            vm.events.test {
                vm.sendReply("Top secret meeting at 9 PM")
                val event = awaitItem()
                assertTrue(event is ConversationEvent.Reply)
            }

            val sent = gateway.lastSentText
            assertNotNull(sent)
            assertTrue(MessageCryptoEngine.isEncrypted(sent!!))
            assertTrue(SteganographyEngine.containsStegano(sent))

            val cover = SteganographyEngine.extractCoverText(sent)
            assertEquals("أهلاً بك، هل تود اللقاء غداً؟", cover)

            val decrypted = MessageCryptoEngine.decrypt(sent, listOf("alex_private_key"))
            assertNotNull(decrypted)
            assertEquals("Top secret meeting at 9 PM", decrypted!!.plaintext)
            assertEquals(EncryptionMode.STEGANO, decrypted.mode)
        }
    }

    @Test
    fun fallbackTriggeredWhenNotificationUnavailable() = runTest {
        val vault = InMemoryVaultRepository(Fixtures.conversations, Fixtures.messages)
        val settings = InMemorySettingsRepository()
        val keyManager = UserMessageKeyManager(settings, LocalKeyCipher(FakeKeyWrapper()))
        keyManager.setGlobalKey("shared_key")

        // Gateway indicates notification is gone (e.g. after reboot or dismissed)
        val gateway = RecordingReplyGateway(availability = ReplyAvailability.NO_NOTIFICATION)
        val vm = ConversationViewModel(
            conversationId = Fixtures.alex.id,
            vault = vault,
            zone = ZoneOffset.UTC,
            replyGateway = gateway,
            userMessageKeyManager = keyManager,
            settingsRepository = settings,
        )

        vm.state.testState {
            awaitMatching { !it.loading }
            vm.setEncryptionMode(EncryptionMode.EXPLICIT)

            vm.events.test {
                vm.sendReply("Urgent message")
                val event = awaitItem()
                assertTrue("Expected fallback event", event is ConversationEvent.LaunchMessengerFallback)
                val fallback = event as ConversationEvent.LaunchMessengerFallback
                assertEquals(Fixtures.alex.packageName, fallback.packageName)
                assertTrue(MessageCryptoEngine.isEncrypted(fallback.text))
            }
        }
    }

    @Test
    fun timelineDecryptsIncomingEncryptedMessages() = runTest {
        val key = "alex_private_key"
        val encText = MessageCryptoEngine.encrypt(
            plaintext = "Decrypted message text",
            passphrase = key,
            mode = EncryptionMode.EXPLICIT,
        )
        val msg = Fixtures.message(100, Fixtures.alex, encText, System.currentTimeMillis())

        val vault = InMemoryVaultRepository(Fixtures.conversations, listOf(msg))
        val settings = InMemorySettingsRepository()
        val keyManager = UserMessageKeyManager(settings, LocalKeyCipher(FakeKeyWrapper()))
        keyManager.setConversationKey("${Fixtures.alex.packageName}:${Fixtures.alex.conversationKey}", key)

        val vm = ConversationViewModel(
            conversationId = Fixtures.alex.id,
            vault = vault,
            zone = ZoneOffset.UTC,
            userMessageKeyManager = keyManager,
            settingsRepository = settings,
        )

        vm.state.testState {
            val s = awaitMatching { it.timeline.any { item -> item is TimelineItem.Message && item.cryptoInfo != null } }
            val item = s.timeline.filterIsInstance<TimelineItem.Message>().first()
            assertNotNull(item.cryptoInfo)
            assertTrue(item.cryptoInfo!!.isDecrypted)
            assertEquals("Decrypted message text", item.cryptoInfo!!.decryptedText)
            assertEquals(EncryptionMode.EXPLICIT, item.cryptoInfo!!.mode)
        }
    }

    @Test
    fun showEncryptedOnlyFiltersOutUnencryptedMessages() = runTest {
        val encText = MessageCryptoEngine.encrypt("Secret", "key", EncryptionMode.EXPLICIT)
        val msg1 = Fixtures.message(1, Fixtures.alex, "Plain message", 1000L)
        val msg2 = Fixtures.message(2, Fixtures.alex, encText, 2000L)

        val vault = InMemoryVaultRepository(Fixtures.conversations, listOf(msg1, msg2))
        val settings = InMemorySettingsRepository()
        settings.update { it.copy(showEncryptedOnly = true) }

        val vm = ConversationViewModel(
            conversationId = Fixtures.alex.id,
            vault = vault,
            zone = ZoneOffset.UTC,
            settingsRepository = settings,
        )

        vm.state.testState {
            val s = awaitMatching { !it.loading }
            assertEquals(1, s.messages.size)
            assertEquals(2L, s.messages.single().id)
        }
    }

    @Test
    fun perConversationEncryptionModeOverridesGlobalDefault() = runTest {
        val vault = InMemoryVaultRepository(Fixtures.conversations, Fixtures.messages)
        val settings = InMemorySettingsRepository()
        settings.update { it.copy(defaultEncryptionMode = EncryptionMode.NONE) }
        val keyManager = UserMessageKeyManager(settings, LocalKeyCipher(FakeKeyWrapper()))
        keyManager.setConversationEncryptionMode("${Fixtures.alex.packageName}:${Fixtures.alex.conversationKey}", EncryptionMode.STEGANO)

        val vm = ConversationViewModel(
            conversationId = Fixtures.alex.id,
            vault = vault,
            zone = ZoneOffset.UTC,
            userMessageKeyManager = keyManager,
            settingsRepository = settings,
        )

        vm.state.testState {
            val s = awaitMatching { !it.loading && it.conversationEncryptionMode != null }
            assertEquals(EncryptionMode.STEGANO, s.encryptionMode)
            assertEquals(EncryptionMode.STEGANO, s.conversationEncryptionMode)
        }
    }
}
