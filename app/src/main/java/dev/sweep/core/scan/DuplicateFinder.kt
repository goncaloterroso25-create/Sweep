package dev.sweep.core.scan

import java.io.File

/** One set of byte-identical files. [keeper] is the copy Sweep protects; [copies] are removable. */
data class DuplicateGroup(
    val hash: String,
    val size: Long,
    val keeper: File,
    val copies: List<File>,
) {
    val totalCopies: Int get() = copies.size + 1
    val reclaimableBytes: Long get() = size * copies.size
}

/**
 * Finds byte-identical files without hashing the whole device.
 *
 * 1. Group by exact size — files of different sizes cannot be identical, and this pass is free.
 * 2. Prefix-hash the survivors — cheap, and settles almost every remaining group.
 * 3. Full-hash only what still collides.
 *
 * A group is never returned with fewer than two members, and one member is always kept.
 */
object DuplicateFinder {

    fun find(
        files: List<File>,
        minSize: Long,
        onHashProgress: (hashedFiles: Int, candidateFiles: Int) -> Unit = { _, _ -> },
        isActive: () -> Boolean = { true },
    ): List<DuplicateGroup> {
        // Stage 1: size buckets.
        val bySize = HashMap<Long, MutableList<File>>()
        for (file in files) {
            val size = file.length()
            if (size < minSize) continue
            bySize.getOrPut(size) { ArrayList(2) }.add(file)
        }
        val sizeCandidates = bySize.values.filter { it.size > 1 }
        val candidateCount = sizeCandidates.sumOf { it.size }
        if (candidateCount == 0) return emptyList()

        var hashed = 0
        val groups = ArrayList<DuplicateGroup>()

        for (bucket in sizeCandidates) {
            if (!isActive()) return groups
            val size = bucket.first().length()

            // Stage 2: prefix hash.
            val byPrefix = HashMap<String, MutableList<File>>()
            for (file in bucket) {
                if (!isActive()) return groups
                val prefix = Hashing.prefixHash(file) ?: continue
                byPrefix.getOrPut(prefix) { ArrayList(2) }.add(file)
                hashed++
                onHashProgress(hashed, candidateCount)
            }

            for ((prefix, sameHead) in byPrefix) {
                if (sameHead.size < 2) continue
                if (!isActive()) return groups

                // A file no bigger than the prefix window is already fully hashed.
                val identical: Map<String, List<File>> = if (size <= Hashing.PREFIX_BYTES) {
                    mapOf(prefix to sameHead)
                } else {
                    // Stage 3: full hash.
                    val byFull = HashMap<String, MutableList<File>>()
                    for (file in sameHead) {
                        if (!isActive()) return groups
                        val full = Hashing.fullHash(file) ?: continue
                        byFull.getOrPut(full) { ArrayList(2) }.add(file)
                    }
                    byFull
                }

                for ((hash, matches) in identical) {
                    if (matches.size < 2) continue
                    val ordered = matches.sortedWith(KEEPER_ORDER)
                    groups += DuplicateGroup(
                        hash = hash,
                        size = size,
                        keeper = ordered.first(),
                        copies = ordered.drop(1),
                    )
                }
            }
        }
        return groups.sortedByDescending { it.reclaimableBytes }
    }

    /**
     * Decides which copy survives. First entry after sorting is the keeper.
     * Preference: no "(1)" suffix, not sitting in Downloads, oldest, shortest path.
     * The final path comparison keeps the choice deterministic.
     */
    internal val KEEPER_ORDER: Comparator<File> =
        compareBy<File> { if (FileClassifier.copySuffixOf(it.name) != null) 1 else 0 }
            .thenBy { if (FileClassifier.isInDownloads(it.absolutePath)) 1 else 0 }
            .thenBy { it.lastModified().let { m -> if (m <= 0L) Long.MAX_VALUE else m } }
            .thenBy { it.absolutePath.length }
            .thenBy { it.absolutePath }
}
