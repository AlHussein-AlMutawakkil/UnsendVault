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
