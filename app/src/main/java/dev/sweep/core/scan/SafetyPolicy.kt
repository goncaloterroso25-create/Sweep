package dev.sweep.core.scan

import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DuplicateInfo
import dev.sweep.core.model.Reason
import dev.sweep.core.model.ScanConfig
import java.io.File

/**
 * Decides which single category a file belongs to and whether Sweep is willing to pre-select it.
 *
 * The safety rules are deliberately narrow. "Safe" means Sweep is confident the bytes are
 * redundant, not merely that they are old:
 *
 *  - duplicate copies      safe (an identical file is kept)
 *  - installers            safe after 7 days (the app is installed or the file was abandoned)
 *  - archives              safe only when an extracted folder of the same name sits beside it
 *  - old downloads         safe only for non-media files past the user's age threshold
 *  - empty folders         safe (no data)
 *  - screenshots           never pre-selected — these are the user's pictures
 *  - large files           never pre-selected — big does not mean unwanted
 */
object SafetyPolicy {

    /** An installer this recent might still be mid-install. */
    const val INSTALLER_GRACE_DAYS = 7

    /** Even an obviously extracted archive gets a short grace period. */
    const val ARCHIVE_GRACE_DAYS = 7

    fun classify(
        file: File,
        size: Long,
        lastModified: Long,
        now: Long,
        config: ScanConfig,
        duplicate: DuplicateInfo?,
    ): CleanupItem? {
        val name = file.name
        val path = file.absolutePath
        val age = FileClassifier.ageDays(now, lastModified)
        val sizeReason = Reason.Bytes(size)

        if (duplicate != null) {
            return CleanupItem(
                path = path,
                name = name,
                size = size,
                lastModified = lastModified,
                category = CleanupCategory.DUPLICATES,
                reasons = buildList {
                    add(Reason.DuplicateCopies(duplicate.copiesInGroup))
                    add(sizeReason)
                    FileClassifier.copySuffixOf(name)?.let { add(Reason.CopySuffix(it)) }
                },
                isSafeSuggestion = true,
                duplicate = duplicate,
            )
        }

        if (FileClassifier.isInstaller(name)) {
            return CleanupItem(
                path = path,
                name = name,
                size = size,
                lastModified = lastModified,
                category = CleanupCategory.INSTALLERS,
                reasons = listOf(Reason.Installer, sizeReason, Reason.Age(age)),
                isSafeSuggestion = age >= INSTALLER_GRACE_DAYS,
            )
        }

        if (FileClassifier.isArchive(name)) {
            val extracted = FileClassifier.hasExtractedSibling(file)
            return CleanupItem(
                path = path,
                name = name,
                size = size,
                lastModified = lastModified,
                category = CleanupCategory.ARCHIVES,
                reasons = buildList {
                    add(Reason.Archive)
                    if (extracted) add(Reason.ExtractedFolderPresent)
                    add(sizeReason)
                    add(Reason.Age(age))
                },
                isSafeSuggestion = extracted && age >= ARCHIVE_GRACE_DAYS,
            )
        }

        if (FileClassifier.isScreenshot(path, name) && age >= config.oldScreenshotThresholdDays) {
            return CleanupItem(
                path = path,
                name = name,
                size = size,
                lastModified = lastModified,
                category = CleanupCategory.SCREENSHOTS,
                reasons = listOf(Reason.Screenshot, Reason.Age(age), sizeReason),
                isSafeSuggestion = false,
            )
        }

        if (FileClassifier.isInDownloads(path) && age >= config.oldFileThresholdDays) {
            val media = FileClassifier.isMedia(name)
            return CleanupItem(
                path = path,
                name = name,
                size = size,
                lastModified = lastModified,
                category = CleanupCategory.DOWNLOADS,
                reasons = buildList {
                    add(Reason.Age(age))
                    add(sizeReason)
                    FileClassifier.copySuffixOf(name)?.let { add(Reason.CopySuffix(it)) }
                },
                isSafeSuggestion = !media,
            )
        }

        if (size >= config.largeFileThresholdBytes) {
            return CleanupItem(
                path = path,
                name = name,
                size = size,
                lastModified = lastModified,
                category = CleanupCategory.LARGE_FILES,
                reasons = listOf(sizeReason, Reason.Age(age)),
                isSafeSuggestion = false,
            )
        }

        return null
    }

    fun emptyFolderItem(dir: File): CleanupItem = CleanupItem(
        path = dir.absolutePath,
        name = dir.name,
        size = 0L,
        lastModified = dir.lastModified(),
        category = CleanupCategory.EMPTY_FOLDERS,
        reasons = listOf(Reason.EmptyFolder),
        isSafeSuggestion = true,
        isDirectory = true,
    )
}
