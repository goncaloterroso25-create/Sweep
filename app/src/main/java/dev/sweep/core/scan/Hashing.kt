package dev.sweep.core.scan

import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Content hashing for duplicate detection.
 *
 * Two stages on purpose: a cheap prefix hash removes almost every same-size-but-different
 * pair after reading 64 KB, so the expensive whole-file read only happens for files that
 * really are likely identical.
 */
object Hashing {

    const val PREFIX_BYTES = 64 * 1024
    private const val BUFFER = 64 * 1024

    /** SHA-256 of the first [PREFIX_BYTES] of the file, or null if it could not be read. */
    fun prefixHash(file: File, limit: Int = PREFIX_BYTES): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(minOf(BUFFER, limit))
            var remaining = limit
            while (remaining > 0) {
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) break
                digest.update(buffer, 0, read)
                remaining -= read
            }
        }
        digest.digest().toHex()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** SHA-256 of the whole file, or null if it could not be read. */
    fun fullHash(file: File): String? = try {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    private fun ByteArray.toHex(): String {
        val out = StringBuilder(size * 2)
        for (b in this) {
            val v = b.toInt() and 0xFF
            out.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
        }
        return out.toString()
    }

    private const val HEX = "0123456789abcdef"
}
