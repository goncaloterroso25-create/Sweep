package dev.sweep.ui

import dev.sweep.core.android.DeviceStorageInfo
import dev.sweep.core.android.FileAccess
import dev.sweep.core.android.PermissionStatus
import dev.sweep.core.android.StorageVolumeInfo
import dev.sweep.core.model.AppScanResult
import dev.sweep.core.model.CategorySummary
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DuplicateInfo
import dev.sweep.core.model.InstalledApp
import dev.sweep.core.model.Reason
import dev.sweep.core.model.ScanConfig
import dev.sweep.core.model.ScanPhase
import dev.sweep.core.model.ScanResult
import dev.sweep.core.model.ScanUpdate
import dev.sweep.core.model.UnknownUsageApp
import dev.sweep.core.model.UnusedApp

/** Realistic, deliberately awkward data for rendering screens: long names, big numbers. */
object Fixtures {

    private const val GB = 1_000_000_000L
    private const val MB = 1_000_000L
    private const val DAY = 86_400_000L
    private val now = 1_790_000_000_000L
    private const val ROOT = "/storage/emulated/0"

    val storage = DeviceStorageInfo(
        totalBytes = 128 * GB,
        freeBytes = 41_200 * MB,
        volumes = listOf(StorageVolumeInfo("Internal storage", true, 128 * GB, 41_200 * MB, ROOT)),
    )

    val allowed = PermissionStatus(FileAccess.FULL, hasUsageAccess = true)

    private fun item(
        name: String,
        folder: String,
        size: Long,
        category: CleanupCategory,
        ageDays: Int,
        safe: Boolean,
        reasons: List<Reason>,
        duplicate: DuplicateInfo? = null,
        directory: Boolean = false,
    ) = CleanupItem(
        path = "$ROOT/$folder/$name",
        name = name,
        size = size,
        lastModified = now - ageDays * DAY,
        category = category,
        reasons = reasons,
        isSafeSuggestion = safe,
        isDirectory = directory,
        duplicate = duplicate,
    )

    val items: List<CleanupItem> = buildList {
        val group = DuplicateInfo("g1", 3, "$ROOT/Documents/Tax return 2025 final signed.pdf", "Tax return 2025 final signed.pdf")
        add(item("Tax return 2025 final signed (1).pdf", "Download", 18 * MB, CleanupCategory.DUPLICATES, 40, true, listOf(Reason.DuplicateCopies(3), Reason.Bytes(18 * MB), Reason.CopySuffix("(1)")), group))
        add(item("Tax return 2025 final signed.pdf", "WhatsApp/Media/WhatsApp Documents", 18 * MB, CleanupCategory.DUPLICATES, 38, true, listOf(Reason.DuplicateCopies(3), Reason.Bytes(18 * MB)), group))
        val group2 = DuplicateInfo("g2", 2, "$ROOT/Movies/Holiday edit v3.mp4", "Holiday edit v3.mp4")
        add(item("Holiday edit v3.mp4", "Download/Telegram", 1_420 * MB, CleanupCategory.DUPLICATES, 120, true, listOf(Reason.DuplicateCopies(2), Reason.Bytes(1_420 * MB)), group2))
        add(item("com.example.someverylongpackagename.installer-release-build-v4.12.0.apk", "Download", 212 * MB, CleanupCategory.INSTALLERS, 90, true, listOf(Reason.Installer, Reason.Bytes(212 * MB), Reason.Age(90))))
        add(item("Maps offline.apk", "Download", 96 * MB, CleanupCategory.INSTALLERS, 3, false, listOf(Reason.Installer, Reason.Bytes(96 * MB), Reason.Age(3))))
        add(item("Photos backup 2023.zip", "Download", 2_310 * MB, CleanupCategory.ARCHIVES, 300, true, listOf(Reason.Archive, Reason.ExtractedFolderPresent, Reason.Bytes(2_310 * MB), Reason.Age(300))))
        add(item("fonts.zip", "Download", 12 * MB, CleanupCategory.ARCHIVES, 20, false, listOf(Reason.Archive, Reason.Bytes(12 * MB), Reason.Age(20))))
        add(item("Screenshot_20240312_101122_Chrome.png", "Pictures/Screenshots", 2 * MB, CleanupCategory.SCREENSHOTS, 420, false, listOf(Reason.Screenshot, Reason.Age(420), Reason.Bytes(2 * MB))))
        add(item("Screenshot_20240101_090000.png", "Pictures/Screenshots", 1 * MB, CleanupCategory.SCREENSHOTS, 480, false, listOf(Reason.Screenshot, Reason.Age(480), Reason.Bytes(1 * MB))))
        add(item("Boarding pass.pdf", "Download", 1 * MB, CleanupCategory.DOWNLOADS, 200, true, listOf(Reason.Age(200), Reason.Bytes(1 * MB))))
        add(item("Lecture 04 recording.m4a", "Download", 84 * MB, CleanupCategory.DOWNLOADS, 150, false, listOf(Reason.Age(150), Reason.Bytes(84 * MB))))
        add(item("Drone footage raw 4K.mov", "DCIM/Drone", 3_900 * MB, CleanupCategory.LARGE_FILES, 610, false, listOf(Reason.Bytes(3_900 * MB), Reason.Age(610))))
        add(item("Old project", "Documents", 0, CleanupCategory.EMPTY_FOLDERS, 800, true, listOf(Reason.EmptyFolder), directory = true))
    }

    val result = ScanResult(
        items = items,
        filesScanned = 38_112,
        bytesScanned = 86 * GB,
        unreadableDirectories = 0,
        durationMillis = 4_100,
        config = ScanConfig(),
    )

    val preselected: Set<String> = items.filter { it.isSafeSuggestion }.map { it.path }.toSet()

    fun selectionOf(selected: Set<String>): SelectionSummary {
        val chosen = items.filter { it.path in selected }
        return SelectionSummary(
            count = chosen.size,
            bytes = chosen.sumOf { it.size },
            bytesByCategory = chosen.groupBy { it.category }.mapValues { (_, list) -> list.sumOf { it.size } },
            countByCategory = chosen.groupingBy { it.category }.eachCount(),
        )
    }

    fun summaries(): List<CategorySummary> = result.summaries()

    val progress = ScanUpdate.Progress(
        phase = ScanPhase.WALKING,
        filesSeen = 12_408,
        bytesSeen = 30 * GB,
        currentDirectory = "$ROOT/Download/Telegram",
        partial = result.summaries().map { if (it.category == CleanupCategory.LARGE_FILES) it.copy(itemCount = 0, totalBytes = 0) else it },
    )

    private fun app(label: String, pkg: String, size: Long, lastUsedDaysAgo: Int?, cache: Long = 40 * MB) = InstalledApp(
        packageName = pkg,
        label = label,
        isSystemApp = false,
        installedAt = now - 700 * DAY,
        lastUsedAt = lastUsedDaysAgo?.let { now - it * DAY },
        appBytes = size - cache,
        dataBytes = 0,
        cacheBytes = cache,
    )

    val apps: AppScanResult = run {
        val unused = listOf(
            app("A Remarkably Long Application Name That Will Not Fit On Any Phone", "a.long", 1_240 * MB, 300, cache = 480 * MB),
            app("Conference 2024", "b.conf", 210 * MB, 190),
            app("Parking", "c.park", 58 * MB, 120),
        )
        val unknown = listOf(app("Some Bank Authenticator", "d.bank", 88 * MB, null))
        val all = unused + unknown + listOf(app("Chrome", "e.chrome", 900 * MB, 0, cache = 612 * MB))
        AppScanResult(
            apps = all,
            unused = unused.map { UnusedApp(it, daysSinceUse = ((now - it.lastUsedAt!!) / DAY).toInt(), reasons = emptyList()) },
            unknownUsage = unknown.map { UnknownUsageApp(it, installedDays = 700) },
            thresholdDays = 90,
            hasUsageAccess = true,
        )
    }

    fun state(stage: Stage, dark: Boolean = true): SweepUiState = when (stage) {
        Stage.IDLE -> SweepUiState(
            stage = Stage.IDLE,
            permissions = allowed,
            storage = storage,
            settingsLoaded = true,
            environmentLoaded = true,
            lastScan = dev.sweep.core.data.LastScan(3_200 * MB, System.currentTimeMillis() - 3 * DAY),
            apps = apps,
        )
        Stage.SCANNING -> SweepUiState(
            stage = Stage.SCANNING,
            permissions = allowed,
            storage = storage,
            settingsLoaded = true,
            environmentLoaded = true,
            progress = progress,
            scanPulse = 12,
            apps = apps,
        )
        Stage.RESULTS -> SweepUiState(
            stage = Stage.RESULTS,
            permissions = allowed,
            storage = storage,
            settingsLoaded = true,
            environmentLoaded = true,
            result = result,
            selectedPaths = preselected,
            selection = selectionOf(preselected),
            apps = apps,
        )
        Stage.CLEANING -> state(Stage.RESULTS).copy(
            stage = Stage.CLEANING,
            cleaning = CleaningProgress(done = 4, total = 8, recoveredBytes = 1_600 * MB),
        )
        Stage.DONE -> state(Stage.RESULTS).copy(
            stage = Stage.DONE,
            storage = storage.copy(freeBytes = 45_100 * MB),
            cleanup = CleanupSummary(
                bytesRecovered = 3_880 * MB,
                filesRemoved = 7,
                alreadyGone = 1,
                failed = listOf(dev.sweep.core.model.FailedDeletion("$ROOT/Download/x.zip", "x.zip", 12 * MB, "Android denied write access")),
                categories = listOf(CleanupCategory.DUPLICATES, CleanupCategory.INSTALLERS, CleanupCategory.ARCHIVES),
                freeBytesBefore = 41_200 * MB,
                freeBytesAfter = 45_100 * MB,
            ),
        )
    }
}
