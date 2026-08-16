package dev.sweep.core.android

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * The directories Sweep is allowed to walk.
 *
 * Primary shared storage only, plus any removable volume Android is willing to name. App-private
 * areas are never included: `Android/data` and `Android/obb` are unreadable from Android 11
 * regardless of "All files access", and the scanner skips them by name as well.
 */
object ScanRoots {

    fun forDevice(context: Context): List<File> = buildList {
        primaryRoot()?.let(::add)
        addAll(secondaryRoots(context))
    }

    private fun primaryRoot(): File? {
        if (Environment.getExternalStorageState() != Environment.MEDIA_MOUNTED) return null
        @Suppress("DEPRECATION")
        val dir = Environment.getExternalStorageDirectory() ?: return null
        return dir.takeIf { it.isDirectory && it.canRead() }
    }

    private fun secondaryRoots(context: Context): List<File> =
        DeviceStorage.read(context).volumes
            .asSequence()
            .filterNot { it.isPrimary }
            .mapNotNull { it.path?.let(::File) }
            .filter { it.isDirectory && it.canRead() }
            .toList()

    /** Shown in the UI so the user knows what "scan storage" actually covered. */
    fun describe(roots: List<File>): String = when {
        roots.isEmpty() -> "No readable storage"
        roots.size == 1 -> "Internal storage"
        else -> "Internal storage + ${roots.size - 1} more"
    }
}
