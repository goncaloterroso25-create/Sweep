package dev.sweep.core.android

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import java.io.File

data class StorageVolumeInfo(
    val label: String,
    val isPrimary: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
    val path: String?,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
}

data class DeviceStorageInfo(
    val totalBytes: Long,
    val freeBytes: Long,
    val volumes: List<StorageVolumeInfo>,
) {
    val usedBytes: Long get() = (totalBytes - freeBytes).coerceAtLeast(0)
    val usedFraction: Float
        get() = if (totalBytes <= 0) 0f else (usedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
    val hasExtraVolumes: Boolean get() = volumes.size > 1

    companion object {
        val UNKNOWN = DeviceStorageInfo(0, 0, emptyList())
    }
}

/**
 * Reads real device storage figures.
 *
 * [StorageStatsManager] is preferred because it reports the same total the Settings app shows
 * (which includes space the filesystem reserves), whereas [StatFs] under-reports it. StatFs is
 * kept as the fallback for devices where the stats service refuses the call.
 */
object DeviceStorage {

    fun read(context: Context): DeviceStorageInfo {
        val primary = readPrimary(context)
        val volumes = buildList {
            add(primary)
            addAll(readSecondaryVolumes(context))
        }
        return DeviceStorageInfo(
            totalBytes = primary.totalBytes,
            freeBytes = primary.freeBytes,
            volumes = volumes,
        )
    }

    private fun readPrimary(context: Context): StorageVolumeInfo {
        val stats = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        val fromStats = stats?.runCatching {
            getTotalBytes(StorageManager.UUID_DEFAULT) to getFreeBytes(StorageManager.UUID_DEFAULT)
        }?.getOrNull()

        val (total, free) = fromStats ?: statFs(Environment.getDataDirectory())
        return StorageVolumeInfo(
            label = "Internal storage",
            isPrimary = true,
            totalBytes = total,
            freeBytes = free,
            path = Environment.getExternalStorageDirectory()?.absolutePath,
        )
    }

    /**
     * SD cards and USB volumes. Their directories are only exposed from Android 11, so on older
     * releases Sweep simply reports the primary volume rather than guessing at paths.
     */
    private fun readSecondaryVolumes(context: Context): List<StorageVolumeInfo> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return emptyList()
        val manager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            ?: return emptyList()
        return manager.storageVolumes
            .asSequence()
            .filterNot { it.isPrimary }
            .mapNotNull { volume ->
                val dir = volume.directory ?: return@mapNotNull null
                if (!dir.isDirectory) return@mapNotNull null
                val (total, free) = statFs(dir)
                if (total <= 0L) return@mapNotNull null
                StorageVolumeInfo(
                    label = volume.getDescription(context) ?: dir.name,
                    isPrimary = false,
                    totalBytes = total,
                    freeBytes = free,
                    path = dir.absolutePath,
                )
            }
            .toList()
    }

    private fun statFs(dir: File): Pair<Long, Long> = try {
        val stat = StatFs(dir.absolutePath)
        stat.totalBytes to stat.availableBytes
    } catch (e: IllegalArgumentException) {
        0L to 0L
    }
}
