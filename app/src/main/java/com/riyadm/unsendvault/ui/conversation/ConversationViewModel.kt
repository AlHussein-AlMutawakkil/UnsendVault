package com.riyadm.unsendvault.ui.conversation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.riyadm.unsendvault.core.model.ArchivedMessage
import com.riyadm.unsendvault.core.model.CompressionPolicy
import com.riyadm.unsendvault.core.model.Conversation
import com.riyadm.unsendvault.core.model.EncryptionMode
import com.riyadm.unsendvault.core.model.MessageQuery
import com.riyadm.unsendvault.core.model.VaultSettings
import com.riyadm.unsendvault.crypto.message.MessageCryptoEngine
import com.riyadm.unsendvault.crypto.message.SteganographyEngine
import com.riyadm.unsendvault.crypto.message.UserMessageKeyManager
import com.riyadm.unsendvault.data.SettingsRepository
import com.riyadm.unsendvault.data.VaultRepository
import com.riyadm.unsendvault.data.read.ConversationReadTracker
import com.riyadm.unsendvault.reply.ReplyAvailability
import com.riyadm.unsendvault.reply.ReplyGateway
import com.riyadm.unsendvault.reply.ReplyResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Immutable
data class DecryptedMessageInfo(
    val decryptedText: String,
    val mode: EncryptionMode,
    val coverText: String? = null,
    val isDecrypted: Boolean = true,
)

/** Timeline rows, newest first, for a reversed (bottom-anchored) chat list. */
@Immutable
sealed interface TimelineItem {
    val key: String

    data class Message(
        val message: ArchivedMessage,
        val cryptoInfo: DecryptedMessageInfo? = null,
    ) : TimelineItem {
        override val key: String get() = "m${message.id}"
    }

    /** Placed after the oldest message of its day, so it renders above that day in a reversed list. */
    data class DayHeader(val day: LocalDate, val millis: Long) : TimelineItem {
        override val key: String get() = "d$day"
    }
}

fun buildTimeline(
    newestFirst: List<ArchivedMessage>,
    zone: ZoneId,
    candidateKeys: List<String> = emptyList(),
): List<TimelineItem> {
    val out = ArrayList<TimelineItem>(newestFirst.size + 8)
    newestFirst.forEachIndexed { index, message ->
        val cryptoInfo = if (MessageCryptoEngine.isEncrypted(message.text)) {
            val res = MessageCryptoEngine.decrypt(message.text, candidateKeys)
            if (res != null) {
                DecryptedMessageInfo(
                    decryptedText = res.plaintext,
                    mode = res.mode,
                    coverText = res.coverText,
                    isDecrypted = true,
                )
            } else {
                val isSteg = SteganographyEngine.containsStegano(message.text)
                DecryptedMessageInfo(
                    decryptedText = "",
                    mode = if (isSteg) EncryptionMode.STEGANO else EncryptionMode.EXPLICIT,
                    coverText = if (isSteg) SteganographyEngine.extractCoverText(message.text) else null,
                    isDecrypted = false,
                )
            }
        } else {
            null
        }
        out += TimelineItem.Message(message, cryptoInfo)
        val day = Instant.ofEpochMilli(message.messageTimestamp).atZone(zone).toLocalDate()
        val nextOlder = newestFirst.getOrNull(index + 1)?.let { Instant.ofEpochMilli(it.messageTimestamp).atZone(zone).toLocalDate() }
        if (nextOlder != day) out += TimelineItem.DayHeader(day, message.messageTimestamp)
    }
    return out
}

@Immutable
data class ConversationUiState(
    val loading: Boolean = true,
    val conversation: Conversation? = null,
    /** Newest first (rendered bottom-up in a reversed list, like a chat). */
    val messages: List<ArchivedMessage> = emptyList(),
    val timeline: List<TimelineItem> = emptyList(),
    val selection: Set<Long> = emptySet(),
    /** Null while unknown, or when replying isn't offered at all. */
    val reply: ReplyAvailability? = null,
    val sendingReply: Boolean = false,
    val encryptionMode: EncryptionMode = EncryptionMode.NONE,
    val conversationEncryptionMode: EncryptionMode? = null,
    val customCoverText: String = "",
    val conversationKey: String? = null,
    val globalKey: String = "",
    val showEncryptedOnly: Boolean = false,
) {
    val selecting: Boolean get() = selection.isNotEmpty()
    val hasPrivateKey: Boolean get() = !conversationKey.isNullOrBlank()
    val activeKey: String? get() = if (hasPrivateKey) conversationKey else globalKey.takeIf { it.isNotBlank() }
    val isKeyConfigured: Boolean get() = activeKey != null
    val hasCustomEncryptionMode: Boolean get() = conversationEncryptionMode != null
}

sealed interface ConversationEvent {
    data class MessagesDeleted(val count: Int) : ConversationEvent
    data object ConversationDeleted : ConversationEvent
    data class Reply(val result: ReplyResult) : ConversationEvent
    data class LaunchMessengerFallback(val packageName: String, val text: String) : ConversationEvent
    data class KeyMissingWarning(val mode: EncryptionMode) : ConversationEvent
}

private data class KeyInfo(
    val privateKey: String?,
    val globalKey: String,
    val candidates: List<String>,
)

private data class CryptoUiInfo(
    val mode: EncryptionMode,
    val savedMode: EncryptionMode?,
    val cover: String,
    val keyInfo: KeyInfo,
    val showEncryptedOnly: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    private val conversationId: Long,
    private val vault: VaultRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val replyGateway: ReplyGateway? = null,
    private val readTracker: ConversationReadTracker? = null,
    private val userMessageKeyManager: UserMessageKeyManager? = null,
    private val settingsRepository: SettingsRepository? = null,
) : ViewModel() {
    init {
        readTracker?.setVisibleConversation(conversationId)
    }

    override fun onCleared() {
        super.onCleared()
        readTracker?.setVisibleConversation(null)
    }

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private val replyRefresh = MutableStateFlow(0)
    private val sending = MutableStateFlow(false)
    private val manualEncryptionMode = MutableStateFlow<EncryptionMode?>(null)
    private val customCoverText = MutableStateFlow("")

    private val conversation = vault.observeConversation(conversationId)
    private val _events = Channel<ConversationEvent>(Channel.BUFFERED)
    val events: Flow<ConversationEvent> = _events.receiveAsFlow()

    private val settingsFlow = settingsRepository?.settings ?: flowOf(VaultSettings())

    private val keyInfo: Flow<KeyInfo> = combine(conversation, settingsFlow) { c, _ ->
        val convKeyId = c?.let { "${it.packageName}:${it.conversationKey}" }
        val rawConvKey = c?.conversationKey
        val privKey = (convKeyId?.let { userMessageKeyManager?.getConversationKey(it) }
            ?: rawConvKey?.let { userMessageKeyManager?.getConversationKey(it) })
        val globKey = userMessageKeyManager?.getGlobalKey().orEmpty()
        val candidates = listOfNotNull(privKey, globKey.takeIf { it.isNotBlank() }).distinct()
        KeyInfo(privKey, globKey, candidates)
    }

    private val savedConversationMode: Flow<EncryptionMode?> = combine(conversation, settingsFlow) { c, _ ->
        val convKeyId = c?.let { "${it.packageName}:${it.conversationKey}" }
        val rawConvKey = c?.conversationKey
        convKeyId?.let { userMessageKeyManager?.getConversationEncryptionMode(it) }
            ?: rawConvKey?.let { userMessageKeyManager?.getConversationEncryptionMode(it) }
    }

    private val effectiveMode: Flow<EncryptionMode> = combine(
        manualEncryptionMode,
        savedConversationMode,
        settingsFlow,
    ) { manual, saved, s ->
        manual ?: saved ?: s.defaultEncryptionMode
    }

    private val cryptoUi: Flow<CryptoUiInfo> = combine(
        effectiveMode,
        savedConversationMode,
        customCoverText,
        keyInfo,
        settingsFlow,
    ) { mode, saved, cover, keys, s ->
        CryptoUiInfo(mode, saved, cover, keys, s.showEncryptedOnly)
    }

    private val messages = vault.observeMessages(MessageQuery(conversationId = conversationId, limit = MESSAGE_LIMIT))

    private val processedMessages = combine(messages, keyInfo, settingsFlow) { list, keys, s ->
        val filtered = if (s.showEncryptedOnly) {
            list.filter { MessageCryptoEngine.isEncrypted(it.text) }
        } else {
            list
        }
        filtered to buildTimeline(filtered, zone, keys.candidates)
    }

    /**
     * Re-checked when the chat changes (a new message usually means the app re-posted its notification), when the
     * screen resumes and after each send. Each check reads only this app's active notifications.
     */
    private val replyAvailability = if (replyGateway == null) {
        flowOf(null)
    } else {
        combine(conversation.map { c -> c?.let { Triple(it.packageName, it.conversationKey, it.lastMessageAt) } }.distinctUntilChanged(), replyRefresh) { c, _ -> c }
            .mapLatest { c ->
                c?.let { (pkg, key, _) ->
                    try {
                        replyGateway.availability(pkg, key)
                    } catch (e: Exception) {
                        null
                    }
                }
            }
    }

    private val replyState = combine(replyAvailability, sending) { a, s -> a to s }

    val state: StateFlow<ConversationUiState> = combine(
        conversation,
        processedMessages,
        selection,
        replyState,
        cryptoUi,
    ) { conv, (list, timeline), selected, (reply, isSending), crypto ->
        val ids = list.mapTo(HashSet()) { it.id }
        ConversationUiState(
            loading = false,
            conversation = conv,
            messages = list,
            timeline = timeline,
            selection = selected.filterTo(LinkedHashSet()) { it in ids },
            reply = reply,
            sendingReply = isSending,
            encryptionMode = crypto.mode,
            conversationEncryptionMode = crypto.savedMode,
            customCoverText = crypto.cover,
            conversationKey = crypto.keyInfo.privateKey,
            globalKey = crypto.keyInfo.globalKey,
            showEncryptedOnly = crypto.showEncryptedOnly,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ConversationUiState())

    fun refreshReply() = replyRefresh.update { it + 1 }

    fun setEncryptionMode(mode: EncryptionMode) {
        manualEncryptionMode.value = mode
    }

    fun setConversationEncryptionMode(mode: EncryptionMode?) {
        val c = state.value.conversation ?: return
        val convKeyId = "${c.packageName}:${c.conversationKey}"
        manualEncryptionMode.value = mode
        viewModelScope.launch {
            userMessageKeyManager?.setConversationEncryptionMode(convKeyId, mode)
        }
    }

    fun toggleEncryption() {
        val current = state.value.encryptionMode
        if (current == EncryptionMode.NONE) {
            val target = state.value.conversationEncryptionMode ?: EncryptionMode.EXPLICIT
            setEncryptionMode(if (target == EncryptionMode.NONE) EncryptionMode.EXPLICIT else target)
        } else {
            setEncryptionMode(EncryptionMode.NONE)
        }
    }

    fun cycleEncryptionMode() {
        val current = state.value.encryptionMode
        manualEncryptionMode.value = when (current) {
            EncryptionMode.NONE -> EncryptionMode.EXPLICIT
            EncryptionMode.EXPLICIT -> EncryptionMode.STEGANO
            EncryptionMode.STEGANO -> EncryptionMode.NONE
        }
    }

    fun setCustomCoverText(text: String) {
        customCoverText.value = text
    }

    fun setConversationKey(key: String?) {
        val c = state.value.conversation ?: return
        val convKeyId = "${c.packageName}:${c.conversationKey}"
        viewModelScope.launch {
            userMessageKeyManager?.setConversationKey(convKeyId, key)
        }
    }

    fun generateStrongKey(): String = userMessageKeyManager?.generateStrongKey() ?: "uv_${System.currentTimeMillis()}"

    fun sendReply(rawText: String) {
        val c = state.value.conversation ?: return
        if (rawText.isBlank() || sending.value) return
        val currentMode = state.value.encryptionMode
        val activeKey = state.value.activeKey

        if (currentMode != EncryptionMode.NONE && activeKey.isNullOrBlank()) {
            _events.trySend(ConversationEvent.KeyMissingWarning(currentMode))
            return
        }

        viewModelScope.launch {
            sending.value = true
            val compression = settingsRepository?.current?.messageCompressionPolicy ?: CompressionPolicy.AUTO
            val textToSend = when (currentMode) {
                EncryptionMode.NONE -> rawText
                EncryptionMode.EXPLICIT -> MessageCryptoEngine.encrypt(
                    plaintext = rawText,
                    passphrase = activeKey!!,
                    mode = EncryptionMode.EXPLICIT,
                    compressionPolicy = compression,
                )
                EncryptionMode.STEGANO -> {
                    val cover = customCoverText.value.takeIf { it.isNotBlank() }
                    MessageCryptoEngine.encrypt(
                        plaintext = rawText,
                        passphrase = activeKey!!,
                        mode = EncryptionMode.STEGANO,
                        customCover = cover,
                        compressionPolicy = compression,
                    )
                }
            }

            val gateway = replyGateway
            val availability = try {
                gateway?.availability(c.packageName, c.conversationKey)
            } catch (e: Exception) {
                null
            }

            if (gateway != null && availability == ReplyAvailability.AVAILABLE) {
                val result = try {
                    gateway.send(c.packageName, c.conversationKey, textToSend)
                } catch (e: Exception) {
                    ReplyResult.FAILED
                }
                sending.value = false
                if (result == ReplyResult.SENT) {
                    _events.send(ConversationEvent.Reply(result))
                    refreshReply()
                } else {
                    _events.send(ConversationEvent.LaunchMessengerFallback(c.packageName, textToSend))
                }
            } else {
                sending.value = false
                _events.send(ConversationEvent.LaunchMessengerFallback(c.packageName, textToSend))
            }
        }
    }

    fun toggleSelection(id: Long) = selection.update { if (id in it) it - id else it + id }

    fun selectAll() = selection.update { state.value.messages.mapTo(LinkedHashSet()) { it.id } }

    fun clearSelection() = selection.update { emptySet() }

    fun toggleFavorite(message: ArchivedMessage) {
        viewModelScope.launch { vault.setFavorite(message.id, !message.isFavorite) }
    }

    fun deleteSelected() {
        val ids = selection.value
        if (ids.isEmpty()) return
        viewModelScope.launch {
            vault.deleteMessages(ids)
            selection.value = emptySet()
            _events.send(ConversationEvent.MessagesDeleted(ids.size))
        }
    }

    fun deleteConversation() {
        viewModelScope.launch {
            vault.deleteConversation(conversationId)
            _events.send(ConversationEvent.ConversationDeleted)
        }
    }

    companion object {
        const val MESSAGE_LIMIT = 2_000
    }
}
