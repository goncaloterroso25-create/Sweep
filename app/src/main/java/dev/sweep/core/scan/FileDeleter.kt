package dev.sweep.core.scan

import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DeletionOutcome
import dev.sweep.core.model.FailedDeletion
import java.io.File

/**
 * Deletes selected items and reports exactly what happened.
 *
 * Bytes are measured immediately before the delete and only counted once the file is
 * confirmed gone, so the "recovered" figure can never be larger than what actually left
 * the device. Files that vanished on their own are reported separately, not as successes.
 */
object FileDeleter {

    fun delete(
        items: List<CleanupItem>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        isActive: () -> Boolean = { true },
    ): DeletionOutcome {
        val deleted = ArrayList<String>(items.size)
        val failed = ArrayList<FailedDeletion>()
        var recovered = 0L
        var alreadyGone = 0
        var done = 0

        // Deepest paths first so an empty parent folder can still go after its children.
        val ordered = items.sortedByDescending { it.path.count { c -> c == '/' || c == '\\' } }

        for (item in ordered) {
            if (!isActive()) break
            val file = File(item.path)

            if (!file.exists()) {
                alreadyGone++
                done++
                onProgress(done, items.size)
                continue
            }

            val sizeNow = if (file.isDirectory) 0L else file.length()
            val removed = try {
                if (file.isDirectory) deleteEmptyTree(file) else file.delete()
            } catch (e: SecurityException) {
                false
            }

            if (removed && !file.exists()) {
                deleted += item.path
                recovered += sizeNow
            } else {
                failed += FailedDeletion(
                    path = item.path,
                    name = item.name,
                    size = sizeNow,
                    reason = failureReason(file),
                )
            }
            done++
            onProgress(done, items.size)
        }

        return DeletionOutcome(
            deletedPaths = deleted,
            bytesRecovered = recovered,
            failed = failed,
            alreadyGone = alreadyGone,
        )
    }

    /**
     * Removes a folder that contains nothing but other empty folders.
     *
     * The scanner reports only the topmost folder of an empty chain, so a plain delete() would
     * fail on the very folders it offered — File.delete() refuses a directory with any child at
     * all, even an empty one. The tree is re-checked for files first, so a folder that gained
     * content between the scan and the delete is refused rather than wiped.
     */
    private fun deleteEmptyTree(root: File): Boolean {
        val containsFile = root.walkTopDown().any { it.isFile }
        if (containsFile) return false
        root.walkBottomUp().forEach { entry ->
            if (entry.isDirectory) entry.delete()
        }
        return !root.exists()
    }

    private fun failureReason(file: File): String = when {
        file.isDirectory && file.walkTopDown().any { it.isFile } ->
            "Something was added to this folder"

        !file.canWrite() -> "Android denied write access"
        else -> "Android refused the delete"
    }
}
