package com.riyadm.unsendvault.crypto.message

import java.util.Base64

/**
 * Encodes and decodes encrypted binary payloads into explicit, armored text blocks.
 * Format: [UV:enc:<Base64>]
 */
object ExplicitArmorEngine {
    private const val PREFIX = "[UV:enc:"
    private const val SUFFIX = "]"
    private val REGEX = Regex("""(?:🔒\s*)?\[UV:enc:([A-Za-z0-9+/=_\-]+)\]""")

    fun wrap(payload: ByteArray): String {
        val b64 = Base64.getEncoder().encodeToString(payload)
        return "$PREFIX$b64$SUFFIX"
    }

    fun isExplicitEncrypted(text: String): Boolean {
        return REGEX.containsMatchIn(text)
    }

    fun unwrap(text: String): ByteArray? {
        val match = REGEX.find(text) ?: return null
        val b64 = match.groupValues[1]
        return try {
            Base64.getDecoder().decode(b64)
        } catch (_: Exception) {
            try {
                Base64.getUrlDecoder().decode(b64)
            } catch (_: Exception) {
                null
            }
        }
    }
}
