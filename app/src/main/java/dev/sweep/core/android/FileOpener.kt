package dev.sweep.core.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import dev.sweep.core.scan.FileClassifier
import java.io.File

/**
 * Opens a file in whatever app the device already has for it.
 *
 * "Is this the photo I think it is?" is the question standing between a user and a delete, and no
 * amount of filename and size will answer it. Rather than build a media viewer, Sweep hands the
 * file to the system with a read-only [FileProvider] grant and lets the gallery, the PDF reader or
 * the package installer do their job — and says so plainly when the device has nothing that can.
 */
object FileOpener {

    enum class Result {
        OPENED,

        /** Nothing installed can handle this type. The UI says which type, not just "failed". */
        NO_VIEWER,

        /** The file disappeared between the scan and the tap. */
        MISSING,

        /** Android refused the request — an unreadable path, or a provider that declined it. */
        FAILED,
    }

    /**
     * Extension-driven, because that is all Sweep knows without reading the file. Unknown types
     * fall back to the wildcard type so the chooser can still offer something, rather than an
     * early no.
     */
    fun mimeTypeOf(name: String): String {
        val extension = FileClassifier.extensionOf(name)
        if (extension.isEmpty()) return WILDCARD
        EXTRA_TYPES[extension]?.let { return it }
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: WILDCARD
    }

    fun open(context: Context, path: String): Result {
        val file = File(path)
        if (!file.isFile) return Result.MISSING

        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        }.getOrNull() ?: return Result.FAILED

        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mimeTypeOf(file.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return try {
            context.startActivity(intent)
            Result.OPENED
        } catch (e: ActivityNotFoundException) {
            Result.NO_VIEWER
        } catch (e: SecurityException) {
            Result.FAILED
        }
    }

    private const val WILDCARD = "*/*"

    /** Types MimeTypeMap either does not know or gets wrong for Sweep's purposes. */
    private val EXTRA_TYPES = mapOf(
        "apk" to "application/vnd.android.package-archive",
        "apks" to "application/vnd.android.package-archive",
        "xapk" to "application/vnd.android.package-archive",
        "apkm" to "application/vnd.android.package-archive",
        "7z" to "application/x-7z-compressed",
        "rar" to "application/vnd.rar",
        "tgz" to "application/gzip",
        "heic" to "image/heic",
        "heif" to "image/heif",
        "avif" to "image/avif",
        "opus" to "audio/opus",
        "mkv" to "video/x-matroska",
    )
}
