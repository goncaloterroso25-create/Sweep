package dev.sweep.core

import java.io.File
import java.util.concurrent.TimeUnit

/** Fixed "now" so age assertions never depend on the wall clock. */
const val NOW = 1_700_000_000_000L

fun daysAgo(days: Int): Long = NOW - TimeUnit.DAYS.toMillis(days.toLong())

/**
 * Writes a real file of [size] bytes filled from [seed], with a real modification time.
 * The scanner and duplicate finder are exercised against actual bytes on disk, not fakes.
 */
fun File.writeFile(relative: String, size: Int, seed: Int, ageDays: Int): File {
    val file = File(this, relative)
    file.parentFile?.mkdirs()
    val bytes = ByteArray(size) { i -> ((i * 31 + seed) % 251).toByte() }
    file.writeBytes(bytes)
    check(file.setLastModified(daysAgo(ageDays))) { "could not set mtime on $relative" }
    return file
}

fun File.makeDir(relative: String): File =
    File(this, relative).apply { check(mkdirs() || isDirectory) { "could not create $relative" } }
