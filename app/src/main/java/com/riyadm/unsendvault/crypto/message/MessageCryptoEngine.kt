package com.riyadm.unsendvault.crypto.message

import com.riyadm.unsendvault.core.model.CompressionPolicy
import com.riyadm.unsendvault.core.model.EncryptionMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets.UTF_8
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * High-level encryption and decryption engine for end-to-end messages.
 * Uses AES-256-GCM with PBKDF2-HMAC-SHA256 key derivation.
 */
object MessageCryptoEngine {
    private const val VERSION: Byte = 1
    private const val FLAG_COMPRESSED: Byte = 0x01
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 10_000

    private val random = SecureRandom()

    data class DecryptionResult(
        val plaintext: String,
        val mode: EncryptionMode,
        val coverText: String?,
        val wasCompressed: Boolean,
    )

    /**
     * Encrypts plaintext and returns the formatted output string according to [mode].
     */
    fun encrypt(
        plaintext: String,
        passphrase: String,
        mode: EncryptionMode,
        compressionPolicy: CompressionPolicy = CompressionPolicy.AUTO,
        customCover: String? = null,
    ): String {
        if (mode == EncryptionMode.NONE || passphrase.isBlank()) return plaintext

        val rawBytes = plaintext.toByteArray(UTF_8)
        val shouldCompress = when (compressionPolicy) {
            CompressionPolicy.ALWAYS -> true
            CompressionPolicy.DISABLED -> false
            CompressionPolicy.AUTO -> {
                val compressed = try { MessageCompression.compress(rawBytes) } catch (_: Exception) { null }
                compressed != null && compressed.size < rawBytes.size
            }
        }

        val dataToEncrypt = if (shouldCompress) {
            try { MessageCompression.compress(rawBytes) } catch (_: Exception) { rawBytes }
        } else {
            rawBytes
        }
        val isCompressed = shouldCompress && dataToEncrypt !== rawBytes

        val salt = ByteArray(SALT_BYTES).also { random.nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val secretKey = deriveKey(passphrase, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
        val ciphertext = cipher.doFinal(dataToEncrypt)

        val flags: Byte = if (isCompressed) FLAG_COMPRESSED else 0

        val out = ByteArrayOutputStream()
        out.write(VERSION.toInt())
        out.write(flags.toInt())
        out.write(salt)
        out.write(iv)
        out.write(ciphertext)
        val encryptedPayload = out.toByteArray()

        return when (mode) {
            EncryptionMode.STEGANO -> {
                val cover = if (!customCover.isNullOrBlank()) customCover else SteganographyEngine.randomCoverText()
                SteganographyEngine.encode(encryptedPayload, cover)
            }
            EncryptionMode.EXPLICIT -> {
                ExplicitArmorEngine.wrap(encryptedPayload)
            }
            EncryptionMode.NONE -> plaintext
        }
    }

    /**
     * Checks whether the incoming text contains any encrypted message (explicit or steganographic).
     */
    fun isEncrypted(text: String): Boolean {
        return SteganographyEngine.containsStegano(text) || ExplicitArmorEngine.isExplicitEncrypted(text)
    }

    /**
     * Attempts to decrypt an incoming message with a list of candidate passphrases (e.g. private key first, then global key).
     */
    fun decrypt(text: String, candidatePassphrases: List<String>): DecryptionResult? {
        val (payload, mode, coverText) = when {
            SteganographyEngine.containsStegano(text) -> {
                val bytes = SteganographyEngine.decode(text) ?: return null
                val cover = SteganographyEngine.extractCoverText(text)
                Triple(bytes, EncryptionMode.STEGANO, cover)
            }
            ExplicitArmorEngine.isExplicitEncrypted(text) -> {
                val bytes = ExplicitArmorEngine.unwrap(text) ?: return null
                Triple(bytes, EncryptionMode.EXPLICIT, null)
            }
            else -> return null
        }

        for (passphrase in candidatePassphrases) {
            if (passphrase.isBlank()) continue
            val result = tryDecryptPayload(payload, passphrase)
            if (result != null) {
                return DecryptionResult(
                    plaintext = result.first,
                    mode = mode,
                    coverText = coverText,
                    wasCompressed = result.second,
                )
            }
        }
        return null
    }

    private fun tryDecryptPayload(payload: ByteArray, passphrase: String): Pair<String, Boolean>? {
        if (payload.size < 1 + 1 + SALT_BYTES + IV_BYTES + 16) return null
        val inStream = ByteArrayInputStream(payload)
        val version = inStream.read()
        if (version != VERSION.toInt()) return null

        val flags = inStream.read()
        val isCompressed = (flags and FLAG_COMPRESSED.toInt()) != 0

        val salt = ByteArray(SALT_BYTES)
        if (inStream.read(salt) != SALT_BYTES) return null

        val iv = ByteArray(IV_BYTES)
        if (inStream.read(iv) != IV_BYTES) return null

        val ciphertext = inStream.readBytes()
        return try {
            val secretKey = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_BITS, iv))
            val decryptedBytes = cipher.doFinal(ciphertext)

            val finalBytes = if (isCompressed) {
                MessageCompression.decompress(decryptedBytes)
            } else {
                decryptedBytes
            }
            Pair(String(finalBytes, UTF_8), isCompressed)
        } catch (_: Exception) {
            null
        }
    }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, 256)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }
}
