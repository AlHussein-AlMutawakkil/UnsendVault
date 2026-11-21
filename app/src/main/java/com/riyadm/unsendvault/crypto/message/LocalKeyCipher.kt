package com.riyadm.unsendvault.crypto.message

import com.riyadm.unsendvault.data.security.AndroidKeystoreKeyWrapper
import com.riyadm.unsendvault.data.security.KeyWrapper
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Base64

/**
 * Encrypts and decrypts user message keys (both the global key and per-conversation keys)
 * before storing them in DataStore, using the project's local Android Keystore encryption.
 */
class LocalKeyCipher(
    private val wrapper: KeyWrapper = AndroidKeystoreKeyWrapper(KEY_ALIAS),
) {
    init {
        try {
            if (!wrapper.keyExists()) {
                wrapper.createKey(preferStrongBox = true)
            }
        } catch (_: Exception) {
            // Handled defensively
        }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isEmpty()) return ""
        return try {
            if (!wrapper.keyExists()) {
                wrapper.createKey(preferStrongBox = true)
            }
            val wrapped = wrapper.wrap(plainText.toByteArray(UTF_8))
            PREFIX + Base64.getEncoder().encodeToString(wrapped)
        } catch (_: Exception) {
            plainText
        }
    }

    fun decrypt(encryptedText: String): String {
        if (encryptedText.isEmpty()) return ""
        if (!encryptedText.startsWith(PREFIX)) return encryptedText
        val b64 = encryptedText.removePrefix(PREFIX)
        return try {
            val blob = Base64.getDecoder().decode(b64)
            val unwrapped = wrapper.unwrap(blob)
            String(unwrapped, UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        const val KEY_ALIAS = "unsendvault_user_msg_keys"
        private const val PREFIX = "ENC:"
    }
}
