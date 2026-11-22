package com.riyadm.unsendvault.crypto.message

import com.riyadm.unsendvault.core.model.CompressionPolicy
import com.riyadm.unsendvault.core.model.EncryptionMode
import com.riyadm.unsendvault.data.security.KeyWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MessageCryptoTest {

    @Test
    fun compression_roundTrip() {
        val original = "هذا نص تجريبي طويل جداً لتجربة الضغط والتأكد من استعادته بشكل سليم 1234567890".repeat(10).toByteArray()
        val compressed = MessageCompression.compress(original)
        assertTrue(compressed.size < original.size)
        val decompressed = MessageCompression.decompress(compressed)
        assertEquals(String(original), String(decompressed))
    }

    @Test
    fun steganography_roundTrip() {
        val secretBytes = "SecretPayload123".toByteArray()
        val cover = "السلام عليكم ورحمة الله"
        val encoded = SteganographyEngine.encode(secretBytes, cover)

        assertTrue(SteganographyEngine.containsStegano(encoded))
        assertEquals(cover, SteganographyEngine.extractCoverText(encoded))

        val decoded = SteganographyEngine.decode(encoded)
        assertNotNull(decoded)
        assertEquals(String(secretBytes), String(decoded!!))
    }

    @Test
    fun explicitArmor_roundTrip() {
        val secretBytes = "MyExplicitSecret".toByteArray()
        val wrapped = ExplicitArmorEngine.wrap(secretBytes)

        assertTrue(ExplicitArmorEngine.isExplicitEncrypted(wrapped))
        val unwrapped = ExplicitArmorEngine.unwrap(wrapped)
        assertNotNull(unwrapped)
        assertEquals(String(secretBytes), String(unwrapped!!))

        // Multiline / wrapped text with spaces and newlines
        val multilineWrapped = "🔒 [UV:enc:\n" + wrapped.removePrefix("[UV:enc:").removeSuffix("]") + "\n]"
        assertTrue(ExplicitArmorEngine.isExplicitEncrypted(multilineWrapped))
        val unwrappedMulti = ExplicitArmorEngine.unwrap(multilineWrapped)
        assertNotNull(unwrappedMulti)
        assertEquals(String(secretBytes), String(unwrappedMulti!!))
    }

    @Test
    fun steganography_legacyBOMFallback() {
        // Legacy BOM format with \uFEFF
        val legacyPrefix = charArrayOf('\u200B', '\u200D', '\u200C', '\uFEFF').concatToString()
        val payload = "LegacyHello".toByteArray()
        val cover = "مرحبا بكم"
        val sb = StringBuilder()
        sb.append(cover)
        sb.append(legacyPrefix)
        for (b in payload) {
            val v = b.toInt() and 0xFF
            fun toC(bits: Int) = when (bits) {
                0 -> '\u200B'
                1 -> '\u200C'
                2 -> '\u200D'
                else -> '\uFEFF'
            }
            sb.append(toC((v ushr 6) and 0x03))
            sb.append(toC((v ushr 4) and 0x03))
            sb.append(toC((v ushr 2) and 0x03))
            sb.append(toC(v and 0x03))
        }
        val encodedLegacy = sb.toString()
        assertTrue(SteganographyEngine.containsStegano(encodedLegacy))
        val decoded = SteganographyEngine.decode(encodedLegacy)
        assertNotNull(decoded)
        assertEquals("LegacyHello", String(decoded!!))
        assertEquals(cover, SteganographyEngine.extractCoverText(encodedLegacy))
    }

    @Test
    fun cryptoEngine_steganoEncryptionAndDecryption() {
        val secret = "رسالة سرية للغاية بين الطرفين"
        val key = "MySecretPassphrase123"
        val customCover = "صباح الخير"

        val encrypted = MessageCryptoEngine.encrypt(
            plaintext = secret,
            passphrase = key,
            mode = EncryptionMode.STEGANO,
            compressionPolicy = CompressionPolicy.AUTO,
            customCover = customCover,
        )

        assertTrue(MessageCryptoEngine.isEncrypted(encrypted))
        assertTrue(encrypted.startsWith("صباح الخير"))

        // Decryption with correct key
        val result = MessageCryptoEngine.decrypt(encrypted, listOf(key))
        assertNotNull(result)
        assertEquals(secret, result!!.plaintext)
        assertEquals(EncryptionMode.STEGANO, result.mode)
        assertEquals(customCover, result.coverText)

        // Decryption with wrong key
        val wrongResult = MessageCryptoEngine.decrypt(encrypted, listOf("WrongKey"))
        assertNull(wrongResult)
    }

    @Test
    fun cryptoEngine_explicitEncryptionAndDecryption() {
        val secret = "Explicit Top Secret Data"
        val key = "AnotherKey!@#"

        val encrypted = MessageCryptoEngine.encrypt(
            plaintext = secret,
            passphrase = key,
            mode = EncryptionMode.EXPLICIT,
            compressionPolicy = CompressionPolicy.ALWAYS,
        )

        assertTrue(MessageCryptoEngine.isEncrypted(encrypted))
        assertTrue(encrypted.contains("[UV:enc:"))

        val result = MessageCryptoEngine.decrypt(encrypted, listOf(key))
        assertNotNull(result)
        assertEquals(secret, result!!.plaintext)
        assertEquals(EncryptionMode.EXPLICIT, result.mode)
    }

    @Test
    fun localKeyCipher_roundTrip() {
        val testWrapper = object : KeyWrapper {
            private val key = SecretKeySpec(ByteArray(32) { 1 }, "AES")
            override fun keyExists(): Boolean = true
            override fun createKey(preferStrongBox: Boolean) {}
            override fun deleteKey() {}
            override fun wrap(plain: ByteArray): ByteArray {
                val iv = ByteArray(12) { 2 }
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
                val ct = c.doFinal(plain)
                return byteArrayOf(12) + iv + ct
            }
            override fun unwrap(blob: ByteArray): ByteArray {
                val ivLen = blob[0].toInt()
                val iv = blob.copyOfRange(1, 1 + ivLen)
                val ct = blob.copyOfRange(1 + ivLen, blob.size)
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                return c.doFinal(ct)
            }
        }

        val cipher = LocalKeyCipher(testWrapper)
        val originalKey = "UserPassphrase#99"
        val encrypted = cipher.encrypt(originalKey)
        assertTrue(encrypted.startsWith("ENC:"))
        val decrypted = cipher.decrypt(encrypted)
        assertEquals(originalKey, decrypted)
    }
}
