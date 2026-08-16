package dev.sweep.core.scan

import dev.sweep.core.model.CategorySummary
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DuplicateInfo
import dev.sweep.core.model.Reason
import dev.sweep.core.model.ScanConfig
import dev.sweep.core.model.ScanPhase
import dev.sweep.core.model.ScanResult
import dev.sweep.core.model.ScanUpdate
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.isActive
import java.io.File
import java.nio.file.Files

/**
 * Walks the accessible parts of shared storage and turns them into cleanup candidates.
 *
 * Three passes, streamed so the UI can show categories filling up rather than a spinner:
 *  1. WALKING   — one iterative traversal; classifies everything it can decide on the spot.
 *  2. HASHING   — duplicate detection over the files collected in pass 1.
 *  3. FINISHING — duplicate copies are promoted to the Duplicates category, empty folders added.
 *
 * Only [java.io.File] is used, so this class runs unchanged under a JVM unit test.
 */
class StorageScanner(
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxDepth: Int = 24,
) {

    /**
     * @param stopRequested polled between directories and between hash buckets. Setting it makes
     * the scan finish early and still emit a [ScanUpdate.Complete] with everything found so far,
     * which is what lets "Stop" keep its results instead of throwing them away. Cancelling the
     * coroutine also works, but discards the partial result — that is the abandon path, not this.
     */
    fun scan(
        roots: List<File>,
        config: ScanConfig = ScanConfig(),
        exclusions: Set<String> = emptySet(),
        stopRequested: () -> Boolean = { false },
    ): Flow<ScanUpdate> = channelFlow {
        val startedAt = clock()
        val now = clock()

        val provisional = LinkedHashMap<String, CleanupItem>()
        val duplicateCandidates = ArrayList<File>()
        val visitedDirs = HashSet<String>()
        val dirsWithContent = HashSet<String>()
        val rootPaths = roots.map { it.absolutePath }.toSet()

        var filesSeen = 0
        var bytesSeen = 0L
        var unreadable = 0
        var lastEmit = 0L

        suspend fun emitProgress(phase: ScanPhase, dir: String?, force: Boolean = false) {
            val nowMs = clock()
            if (!force && nowMs - lastEmit < PROGRESS_INTERVAL_MS) return
            lastEmit = nowMs
            send(
                ScanUpdate.Progress(
                    phase = phase,
                    filesSeen = filesSeen,
                    bytesSeen = bytesSeen,
                    currentDirectory = dir,
                    partial = summarise(provisional.values),
                )
            )
        }

        // ---- Pass 1: walk ------------------------------------------------------------------
        val stack = ArrayDeque<Pair<File, Int>>()
        roots.filter { it.isDirectory }.forEach { stack.addLast(it to 0) }

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            if (stopRequested()) break
            val (dir, depth) = stack.removeLast()
            if (depth > maxDepth) continue
            if (Exclusions.isExcluded(dir.absolutePath, exclusions)) continue
            if (depth > 0 && FileClassifier.shouldSkipDirectory(dir)) continue

            val children = dir.listFiles()
            if (children == null) {
                unreadable++
                continue
            }
            visitedDirs += dir.absolutePath

            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (isSymlink(child)) continue
                if (Exclusions.isExcluded(child.absolutePath, exclusions)) continue

                if (child.isDirectory) {
                    stack.addLast(child to depth + 1)
                    continue
                }
                if (!child.isFile) continue

                val size = child.length()
                val lastModified = child.lastModified()
                filesSeen++
                bytesSeen += size
                markAncestorsAsUsed(child, dirsWithContent, rootPaths)

                if (size >= config.minDuplicateSizeBytes) duplicateCandidates += child

                SafetyPolicy.classify(child, size, lastModified, now, config, duplicate = null)
                    ?.let { provisional[it.path] = it }
            }
            emitProgress(ScanPhase.WALKING, dir.absolutePath)
        }
        emitProgress(ScanPhase.WALKING, null, force = true)

        // ---- Pass 2: duplicates ------------------------------------------------------------
        var hashProgressCounter = 0
        val groups = DuplicateFinder.find(
            files = duplicateCandidates,
            minSize = config.minDuplicateSizeBytes,
            onHashProgress = { hashedFiles, candidates ->
                // trySend is non-blocking; dropped ticks only cost a frame of progress detail.
                if (hashedFiles - hashProgressCounter >= HASH_PROGRESS_STEP) {
                    hashProgressCounter = hashedFiles
                    trySend(
                        ScanUpdate.Progress(
                            phase = ScanPhase.HASHING,
                            filesSeen = filesSeen,
                            bytesSeen = bytesSeen,
                            currentDirectory = null,
                            partial = summarise(provisional.values),
                        )
                    )
                }
            },
            isActive = { this@channelFlow.isActive && !stopRequested() },
        )
        currentCoroutineContext().ensureActive()

        // ---- Pass 3: finish ----------------------------------------------------------------
        for (group in groups) {
            val info = DuplicateInfo(
                groupId = group.hash,
                copiesInGroup = group.totalCopies,
                keeperPath = group.keeper.absolutePath,
                keeperName = group.keeper.name,
            )
            for (copy in group.copies) {
                if (Exclusions.isExcluded(copy.absolutePath, exclusions)) continue
                SafetyPolicy.classify(
                    file = copy,
                    size = group.size,
                    lastModified = copy.lastModified(),
                    now = now,
                    config = config,
                    duplicate = info,
                )?.let { provisional[it.path] = it }
            }

            // The copy Sweep keeps may itself qualify for another category — an old PDF in
            // Downloads, say. If it were left pre-selected there, "Select safe" would remove
            // every copy of the file and the guarantee that one survives would be worthless.
            // It stays visible and selectable by hand, but never pre-selected.
            provisional[info.keeperPath]?.let { keeper ->
                provisional[info.keeperPath] = keeper.copy(
                    isSafeSuggestion = false,
                    reasons = keeper.reasons + Reason.DuplicateCopies(info.copiesInGroup),
                )
            }
        }

        if (config.includeEmptyFolders) {
            emptyFolders(visitedDirs, dirsWithContent, rootPaths, exclusions)
                .forEach { provisional[it.path] = SafetyPolicy.emptyFolderItem(it) }
        }

        emitProgress(ScanPhase.FINISHING, null, force = true)

        val items = provisional.values.sortedWith(
            compareBy<CleanupItem> { it.category.ordinal }
                .thenByDescending { it.size }
                .thenBy { it.path }
        )

        send(
            ScanUpdate.Complete(
                ScanResult(
                    items = items,
                    filesScanned = filesSeen,
                    bytesScanned = bytesSeen,
                    unreadableDirectories = unreadable,
                    durationMillis = clock() - startedAt,
                    config = config,
                    stoppedEarly = stopRequested(),
                )
            )
        )
    }

    private fun isSymlink(file: File): Boolean = try {
        Files.isSymbolicLink(file.toPath())
    } catch (e: Exception) {
        false
    }

    /** Marks every ancestor of [file] as containing content, stopping as soon as one is known. */
    private fun markAncestorsAsUsed(file: File, marked: MutableSet<String>, roots: Set<String>) {
        var parent = file.parentFile
        while (parent != null) {
            val path = parent.absolutePath
            if (!marked.add(path)) return
            if (path in roots) return
            parent = parent.parentFile
        }
    }

    /**
     * Directories we listed successfully that contain no file anywhere beneath them.
     * Only the topmost of a nested empty chain is reported, so the user sees one row per tree.
     */
    private fun emptyFolders(
        visited: Set<String>,
        withContent: Set<String>,
        roots: Set<String>,
        exclusions: Set<String>,
    ): List<File> {
        val empty = visited.asSequence()
            .filter { it !in withContent && it !in roots }
            .filterNot { Exclusions.isExcluded(it, exclusions) }
            .toHashSet()
        return empty.asSequence()
            .map(::File)
            .filterNot { it.name.startsWith(".") }
            .filterNot { FileClassifier.isStandardSharedDirectory(it.name) }
            .filter { it.parentFile?.absolutePath !in empty }
            .sortedBy { it.absolutePath }
            .toList()
    }

    private fun summarise(items: Collection<CleanupItem>): List<CategorySummary> {
        val grouped = items.groupBy { it.category }
        return CleanupCategory.entries.map { category ->
            val list = grouped[category].orEmpty()
            val safe = list.filter { it.isSafeSuggestion }
            CategorySummary(
                category = category,
                itemCount = list.size,
                totalBytes = list.sumOf { it.size },
                suggestedCount = safe.size,
                suggestedBytes = safe.sumOf { it.size },
            )
        }
    }

    private companion object {
        const val PROGRESS_INTERVAL_MS = 90L
        const val HASH_PROGRESS_STEP = 25
    }
}
