package dev.sweep.core.scan

import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Pure filename/path heuristics. Deliberately boring: every rule here has to be one a
 * user would agree with when they read the "why" chip on the row.
 */
object FileClassifier {

    val INSTALLER_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm", "obb")

    val ARCHIVE_EXTENSIONS =
        setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "tbz", "xz", "iso", "cab", "arj")

    val IMAGE_EXTENSIONS =
        setOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw", "avif")

    val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "m4v", "ts", "flv")

    val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr")

    /** Directories Sweep must never walk into. */
    private val SKIPPED_DIRECTORY_NAMES = setOf(
        "Android",      // app-private data; inaccessible on Android 11+ and never ours to touch
        ".android_secure",
        "LOST.DIR",
        ".trashed",
        ".thumbnails",
    )

    /**
     * The directories Android itself creates in shared storage. They are frequently empty on a
     * fresh device, but offering to delete DCIM or Ringtones is noise at best — the system and
     * other apps expect them to exist, and Android recreates them anyway.
     */
    private val STANDARD_SHARED_DIRECTORIES = setOf(
        "Alarms", "Audiobooks", "DCIM", "Documents", "Download", "Downloads", "Movies",
        "Music", "Notifications", "Pictures", "Podcasts", "Recordings", "Ringtones",
        "Screenshots",
    )

    fun isStandardSharedDirectory(name: String) = name in STANDARD_SHARED_DIRECTORIES

    fun extensionOf(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).lowercase(Locale.ROOT)
    }

    fun baseNameOf(name: String): String {
        val dot = name.lastIndexOf('.')
        return if (dot <= 0) name else name.substring(0, dot)
    }

    fun isInstaller(name: String) = extensionOf(name) in INSTALLER_EXTENSIONS

    fun isArchive(name: String) = extensionOf(name) in ARCHIVE_EXTENSIONS

    fun isMedia(name: String): Boolean {
        val ext = extensionOf(name)
        return ext in IMAGE_EXTENSIONS || ext in VIDEO_EXTENSIONS || ext in AUDIO_EXTENSIONS
    }

    fun isScreenshot(path: String, name: String): Boolean {
        if (extensionOf(name) !in IMAGE_EXTENSIONS) return false
        val normalised = path.replace('\\', '/')
        if (normalised.contains("/Screenshots/", ignoreCase = true)) return true
        val lower = name.lowercase(Locale.ROOT)
        return lower.startsWith("screenshot") || lower.startsWith("screen_shot")
    }

    fun isScreenRecording(path: String, name: String): Boolean {
        if (extensionOf(name) !in VIDEO_EXTENSIONS) return false
        val normalised = path.replace('\\', '/')
        if (normalised.contains("/Screen recordings/", ignoreCase = true)) return true
        val lower = name.lowercase(Locale.ROOT)
        return lower.startsWith("screenrecord") || lower.startsWith("screen_record") ||
            lower.startsWith("screen recording")
    }

    fun isInDownloads(path: String): Boolean {
        val normalised = path.replace('\\', '/')
        return normalised.contains("/Download/", ignoreCase = true) ||
            normalised.contains("/Downloads/", ignoreCase = true)
    }

    fun shouldSkipDirectory(dir: File): Boolean {
        if (dir.name in SKIPPED_DIRECTORY_NAMES) return true
        // A .nomedia folder is usually an app's working directory. Leave it alone.
        return File(dir, ".nomedia").exists()
    }

    /**
     * Detects browser-style duplicate downloads: `report(1).pdf`, `photo (2).jpg`, `song-1.mp3`.
     * Returns the matched suffix so the UI can quote it, or null.
     */
    fun copySuffixOf(name: String): String? {
        val base = baseNameOf(name).trimEnd()
        val match = COPY_SUFFIX.find(base) ?: return null
        return match.value.trim()
    }

    private val COPY_SUFFIX = Regex("""\((\d{1,3})\)$""")

    fun ageDays(nowMillis: Long, lastModified: Long): Int {
        if (lastModified <= 0L) return 0
        val delta = nowMillis - lastModified
        if (delta <= 0L) return 0
        return TimeUnit.MILLISECONDS.toDays(delta).toInt()
    }

    /**
     * True when a folder sits beside the archive with the archive's own name, which almost
     * always means the archive was already extracted and the copy is redundant.
     */
    fun hasExtractedSibling(archive: File): Boolean {
        val parent = archive.parentFile ?: return false
        val base = baseNameOf(archive.name)
        if (base.isBlank()) return false
        val sibling = File(parent, base)
        return sibling.isDirectory
    }
}
