package dev.sweep.core.model

/**
 * The categories Sweep can surface. Every [CleanupItem] belongs to exactly one of these, so
 * the totals shown on Home never double-count a file that matches several rules.
 * Declaration order is the assignment priority used by the classifier.
 */
enum class CleanupCategory {
    DUPLICATES,
    INSTALLERS,
    ARCHIVES,
    SCREENSHOTS,
    DOWNLOADS,
    LARGE_FILES,
    EMPTY_FOLDERS,
}

/** Why Sweep surfaced an item. Rendered as short chips in the UI. */
sealed interface Reason {
    data class Age(val days: Int) : Reason
    data class DuplicateCopies(val count: Int) : Reason
    data class Bytes(val value: Long) : Reason
    data object Installer : Reason
    data object Archive : Reason
    data object ExtractedFolderPresent : Reason
    data object Screenshot : Reason
    data object EmptyFolder : Reason
    data class CopySuffix(val suffix: String) : Reason
    data class LastOpened(val days: Int) : Reason
}

/** Extra context for an item that is a redundant copy of another file. */
data class DuplicateInfo(
    val groupId: String,
    /** Total number of identical files found, including the copy Sweep keeps. */
    val copiesInGroup: Int,
    val keeperPath: String,
    val keeperName: String,
)

/**
 * A single deletion candidate. Immutable and free of any Android type so the whole
 * scanning pipeline is unit-testable on the JVM.
 */
data class CleanupItem(
    val path: String,
    val name: String,
    val size: Long,
    val lastModified: Long,
    val category: CleanupCategory,
    val reasons: List<Reason>,
    /**
     * True only when Sweep is confident that removing this costs the user nothing.
     * Drives "Select safe" — it never covers personal media. See SafetyPolicy.
     */
    val isSafeSuggestion: Boolean,
    val isDirectory: Boolean = false,
    val duplicate: DuplicateInfo? = null,
)

data class CategorySummary(
    val category: CleanupCategory,
    val itemCount: Int,
    val totalBytes: Long,
    val suggestedCount: Int,
    val suggestedBytes: Long,
) {
    val isEmpty: Boolean get() = itemCount == 0
}

/** Thresholds the user can change in Settings. */
data class ScanConfig(
    val oldFileThresholdDays: Int = 60,
    val largeFileThresholdBytes: Long = 250L * 1000 * 1000,
    val oldScreenshotThresholdDays: Int = 180,
    /** Files below this are never hashed — three copies of a 2 KB file is not worth a row. */
    val minDuplicateSizeBytes: Long = 4L * 1024,
    val includeEmptyFolders: Boolean = true,
) {
    companion object {
        val AGE_CHOICES = listOf(30, 60, 90, 180)
        val LARGE_FILE_CHOICES = listOf(
            100L * 1000 * 1000,
            250L * 1000 * 1000,
            500L * 1000 * 1000,
            1000L * 1000 * 1000,
        )
    }
}

enum class ScanPhase { WALKING, HASHING, FINISHING }

/** Streamed out of the scanner so the UI can fill in categories as they are discovered. */
sealed interface ScanUpdate {
    data class Progress(
        val phase: ScanPhase,
        val filesSeen: Int,
        val bytesSeen: Long,
        val currentDirectory: String?,
        val partial: List<CategorySummary>,
    ) : ScanUpdate

    data class Complete(val result: ScanResult) : ScanUpdate
}

data class ScanResult(
    val items: List<CleanupItem>,
    val filesScanned: Int,
    val bytesScanned: Long,
    val unreadableDirectories: Int,
    val durationMillis: Long,
    val config: ScanConfig,
    /** True when the user pressed Stop: these results are real, but incomplete. */
    val stoppedEarly: Boolean = false,
) {
    val byCategory: Map<CleanupCategory, List<CleanupItem>> by lazy { items.groupBy { it.category } }

    /** Conservative headline figure: only what "Select safe" would pick. */
    val suggestedBytes: Long by lazy { items.filter { it.isSafeSuggestion }.sumOf { it.size } }

    val totalFoundBytes: Long by lazy { items.sumOf { it.size } }

    fun summary(category: CleanupCategory): CategorySummary {
        val list = byCategory[category].orEmpty()
        val safe = list.filter { it.isSafeSuggestion }
        return CategorySummary(
            category = category,
            itemCount = list.size,
            totalBytes = list.sumOf { it.size },
            suggestedCount = safe.size,
            suggestedBytes = safe.sumOf { it.size },
        )
    }

    fun summaries(): List<CategorySummary> = CleanupCategory.entries.map { summary(it) }

    companion object {
        fun empty(config: ScanConfig = ScanConfig()) =
            ScanResult(emptyList(), 0, 0, 0, 0, config)
    }
}

/** What actually happened when Sweep tried to delete. Never optimistic. */
data class DeletionOutcome(
    val deletedPaths: List<String>,
    val bytesRecovered: Long,
    val failed: List<FailedDeletion>,
    /** Files that were already gone by the time we got to them: no error, but no bytes either. */
    val alreadyGone: Int,
) {
    val deletedCount: Int get() = deletedPaths.size
    val hasFailures: Boolean get() = failed.isNotEmpty()

    companion object {
        val EMPTY = DeletionOutcome(emptyList(), 0, emptyList(), 0)
    }
}

data class FailedDeletion(val path: String, val name: String, val size: Long, val reason: String)
