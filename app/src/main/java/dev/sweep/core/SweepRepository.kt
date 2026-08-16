package dev.sweep.core

import android.content.Context
import dev.sweep.core.android.AppInventory
import dev.sweep.core.android.DeviceStorage
import dev.sweep.core.android.DeviceStorageInfo
import dev.sweep.core.android.ScanRoots
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.model.AppScanResult
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DeletionOutcome
import dev.sweep.core.model.ScanConfig
import dev.sweep.core.model.ScanUpdate
import dev.sweep.core.scan.FileDeleter
import dev.sweep.core.scan.StorageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * The single seam between the Compose layer and the device.
 *
 * Deliberately thin: it picks the scan roots, moves work off the main thread, and tells
 * MediaStore about deletions. All of the actual logic lives in the Android-free `scan` package
 * so it can be unit-tested.
 */
class SweepRepository(private val context: Context) {

    private val scanner = StorageScanner()
    val appInventory = AppInventory(context)

    fun permissions() = SweepPermissions.status(context)

    fun storage(): DeviceStorageInfo = DeviceStorage.read(context)

    fun scanRoots() = ScanRoots.forDevice(context)

    fun scanFiles(
        config: ScanConfig,
        exclusions: Set<String>,
        stopRequested: () -> Boolean = { false },
    ): Flow<ScanUpdate> =
        scanner.scan(scanRoots(), config, exclusions, stopRequested).flowOn(Dispatchers.IO)

    suspend fun loadApps(thresholdDays: Int, excludedPackages: Set<String>): AppScanResult =
        appInventory.load(thresholdDays, excludedPackages)

    /**
     * Deletes the selection and then tells MediaStore what disappeared, so the gallery does not
     * keep showing thumbnails for files that are gone.
     */
    suspend fun delete(
        items: List<CleanupItem>,
        onProgress: (done: Int, total: Int) -> Unit,
    ): DeletionOutcome = withContext(Dispatchers.IO) {
        val outcome = FileDeleter.delete(items, onProgress)
        SystemFlows.notifyDeleted(context, outcome.deletedPaths)
        outcome
    }

    /**
     * Sweep's own cache is the one cache a third-party app is allowed to clear. It is usually
     * tiny, and it is shown alongside the other apps precisely so the difference between "Sweep
     * did this" and "Android did this" stays visible.
     */
    suspend fun ownCacheBytes(): Long = withContext(Dispatchers.IO) {
        listOfNotNull(context.cacheDir, context.externalCacheDir).sumOf { it.sizeRecursive() }
    }

    suspend fun clearOwnCache(): Long = withContext(Dispatchers.IO) {
        val before = listOfNotNull(context.cacheDir, context.externalCacheDir)
        val freed = before.sumOf { it.sizeRecursive() }
        before.forEach { dir -> dir.listFiles()?.forEach { it.deleteRecursively() } }
        freed - before.sumOf { it.sizeRecursive() }
    }

    private fun java.io.File.sizeRecursive(): Long =
        runCatching { walkTopDown().filter { it.isFile }.sumOf { it.length() } }.getOrDefault(0L)
}
