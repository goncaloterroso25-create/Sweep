package dev.sweep.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import dev.sweep.core.model.AgeFormat
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.Reason

/**
 * All of the human-facing wording for a category lives here so the copy can be judged as a whole
 * rather than scattered across screens. Icons are one consistent outlined family.
 */
val CleanupCategory.title: String
    get() = when (this) {
        CleanupCategory.DUPLICATES -> "Duplicate files"
        CleanupCategory.INSTALLERS -> "Installers"
        CleanupCategory.ARCHIVES -> "Archives"
        CleanupCategory.SCREENSHOTS -> "Old screenshots"
        CleanupCategory.DOWNLOADS -> "Old downloads"
        CleanupCategory.LARGE_FILES -> "Large files"
        CleanupCategory.EMPTY_FOLDERS -> "Empty folders"
    }

val CleanupCategory.blurb: String
    get() = when (this) {
        CleanupCategory.DUPLICATES -> "Byte-identical copies. Sweep always keeps one."
        CleanupCategory.INSTALLERS -> "APK files left behind after installing."
        CleanupCategory.ARCHIVES -> "ZIP, RAR and 7z files, flagged when already extracted."
        CleanupCategory.SCREENSHOTS -> "Screenshots you haven't touched in months."
        CleanupCategory.DOWNLOADS -> "Files sitting in Downloads past your age threshold."
        CleanupCategory.LARGE_FILES -> "Your biggest files. Shown for review, never pre-selected."
        CleanupCategory.EMPTY_FOLDERS -> "Folders with nothing inside them."
    }

/** Shown when a category has no findings — each one is written for its own case. */
val CleanupCategory.emptyLine: String
    get() = when (this) {
        CleanupCategory.DUPLICATES -> "No identical files. Nothing is stored twice."
        CleanupCategory.INSTALLERS -> "No leftover installers."
        CleanupCategory.ARCHIVES -> "No archives lying around."
        CleanupCategory.SCREENSHOTS -> "No screenshots old enough to bother you about."
        CleanupCategory.DOWNLOADS -> "Downloads is tidy."
        CleanupCategory.LARGE_FILES -> "Nothing unusually large."
        CleanupCategory.EMPTY_FOLDERS -> "No empty folders."
    }

val CleanupCategory.icon: ImageVector
    get() = when (this) {
        CleanupCategory.DUPLICATES -> Icons.Outlined.ContentCopy
        CleanupCategory.INSTALLERS -> Icons.Outlined.Android
        CleanupCategory.ARCHIVES -> Icons.Outlined.Archive
        CleanupCategory.SCREENSHOTS -> Icons.Outlined.PhotoLibrary
        CleanupCategory.DOWNLOADS -> Icons.Outlined.FileDownload
        CleanupCategory.LARGE_FILES -> Icons.Outlined.Storage
        CleanupCategory.EMPTY_FOLDERS -> Icons.Outlined.Folder
    }

/**
 * Turns a [Reason] into the short phrase shown on a row. The category is passed in because the
 * same fact reads differently depending on where it appears — an age is "Downloaded 4 months ago"
 * in Downloads and "Modified 4 months ago" everywhere else.
 */
fun reasonLabel(reason: Reason, category: CleanupCategory? = null): String = when (reason) {
    is Reason.Age -> when (category) {
        CleanupCategory.DOWNLOADS -> "Downloaded ${AgeFormat.describe(reason.days)}"
        CleanupCategory.SCREENSHOTS -> "Taken ${AgeFormat.describe(reason.days)}"
        else -> "Modified ${AgeFormat.describe(reason.days)}"
    }

    is Reason.DuplicateCopies -> "${reason.count} identical copies"
    is Reason.Bytes -> ByteFormat.short(reason.value)
    is Reason.CopySuffix -> "Named ${reason.suffix}"
    is Reason.LastOpened -> "Last opened ${AgeFormat.describe(reason.days)}"
    Reason.Installer -> "APK installer"
    Reason.Archive -> "Archive"
    Reason.ExtractedFolderPresent -> "Already extracted here"
    Reason.Screenshot -> "Screenshot"
    Reason.EmptyFolder -> "Empty folder"
}

/** The size chip is rendered separately in most rows, so it is filtered out of the chip list. */
fun visibleReasons(reasons: List<Reason>): List<Reason> = reasons.filterNot { it is Reason.Bytes }
