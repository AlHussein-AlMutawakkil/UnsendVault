package com.riyadm.unsendvault.crypto.message

import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Encodes and decodes encrypted binary payloads into natural text using invisible Unicode Zero-Width characters.
 *
 * Anyone viewing the message in WhatsApp, Telegram, etc. sees only the innocent cover text.
 * The invisible characters are:
 * - \u200B (Zero-width space)        -> 00
 * - \u200C (Zero-width non-joiner)   -> 01
 * - \u200D (Zero-width joiner)       -> 10
 * - \uFEFF (Zero-width no-break)     -> 11
 */
object SteganographyEngine {
    private const val CHAR_00 = '\u200B'
    private const val CHAR_01 = '\u200C'
    private const val CHAR_10 = '\u200D'
    private const val CHAR_11 = '\uFEFF'

    // Magic signature to unambiguously identify our steganographic payloads
    private val MAGIC_PREFIX = charArrayOf(CHAR_00, CHAR_10, CHAR_01, CHAR_11).concatToString()

    val DEFAULT_COVER_TEXTS = listOf(
        "تمام إن شاء الله",
        "السلام عليكم ورحمة الله وبركاته",
        "أهلاً وسهلاً، كيف حالك؟",
        "حياك الله، كل التوفيق",
        "يسعد مساك، نتواصل لاحقاً إن شاء الله",
        "جزاك الله خيراً وبارك فيك",
        "ألف شكر، يعطيك العافية",
        "صباح الخير والسرور",
    )

    fun randomCoverText(): String = DEFAULT_COVER_TEXTS[Random.nextInt(DEFAULT_COVER_TEXTS.size)]

    /**
     * Injects the binary payload as invisible zero-width characters into the cover text.
     */
    fun encode(payload: ByteArray, coverText: String): String {
        val cleanCover = if (coverText.isBlank()) randomCoverText() else coverText.trim()
        val sb = StringBuilder()
        sb.append(cleanCover)
        sb.append(MAGIC_PREFIX)

        for (b in payload) {
            val v = b.toInt() and 0xFF
            sb.append(toChar((v ushr 6) and 0x03))
            sb.append(toChar((v ushr 4) and 0x03))
            sb.append(toChar((v ushr 2) and 0x03))
            sb.append(toChar(v and 0x03))
        }
        return sb.toString()
    }

    /**
     * Checks if the given text contains our zero-width steganographic payload.
     */
    fun containsStegano(text: String): Boolean {
        return text.contains(MAGIC_PREFIX)
    }

    /**
     * Extracts the hidden payload from text, or null if no valid payload is found.
     */
    fun decode(text: String): ByteArray? {
        val idx = text.indexOf(MAGIC_PREFIX)
        if (idx == -1) return null

        val payloadChars = text.substring(idx + MAGIC_PREFIX.length)
        val validChars = payloadChars.filter { isSteganoChar(it) }
        if (validChars.isEmpty() || validChars.length % 4 != 0) return null

        val out = ByteArrayOutputStream(validChars.length / 4)
        for (i in validChars.indices step 4) {
            val b0 = toBits(validChars[i]) ?: return null
            val b1 = toBits(validChars[i + 1]) ?: return null
            val b2 = toBits(validChars[i + 2]) ?: return null
            val b3 = toBits(validChars[i + 3]) ?: return null
            val byteVal = (b0 shl 6) or (b1 shl 4) or (b2 shl 2) or b3
            out.write(byteVal)
        }
        return out.toByteArray()
    }

    /**
     * Extracts the visible cover text, stripping the hidden payload.
     */
    fun extractCoverText(text: String): String {
        val idx = text.indexOf(MAGIC_PREFIX)
        val visiblePart = if (idx != -1) text.substring(0, idx) else text
        return visiblePart.filterNot { isSteganoChar(it) }.trim()
    }

    private fun isSteganoChar(c: Char): Boolean =
        c == CHAR_00 || c == CHAR_01 || c == CHAR_10 || c == CHAR_11

    private fun toChar(bits: Int): Char = when (bits) {
        0 -> CHAR_00
        1 -> CHAR_01
        2 -> CHAR_10
        3 -> CHAR_11
        else -> CHAR_00
    }

    private fun toBits(c: Char): Int? = when (c) {
        CHAR_00 -> 0
        CHAR_01 -> 1
        CHAR_10 -> 2
        CHAR_11 -> 3
        else -> null
    }
}
