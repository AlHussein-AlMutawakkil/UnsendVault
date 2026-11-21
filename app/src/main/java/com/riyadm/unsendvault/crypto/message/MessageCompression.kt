package com.riyadm.unsendvault.crypto.message

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Handles message payload compression.
 */
object MessageCompression {
    /**
     * Compresses bytes using GZIP.
     */
    fun compress(input: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(input) }
        return out.toByteArray()
    }

    /**
     * Decompresses GZIP-compressed bytes.
     */
    fun decompress(compressed: ByteArray): ByteArray {
        val inStream = ByteArrayInputStream(compressed)
        val out = ByteArrayOutputStream()
        GZIPInputStream(inStream).use { gz ->
            val buf = ByteArray(1024)
            var n: Int
            while (gz.read(buf).also { n = it } > 0) {
                out.write(buf, 0, n)
            }
        }
        return out.toByteArray()
    }
}
