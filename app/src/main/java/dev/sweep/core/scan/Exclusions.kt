package dev.sweep.core.scan

/**
 * "Don't suggest this again." Exclusions are stored as absolute paths; excluding a folder
 * also excludes everything under it.
 */
object Exclusions {

    fun normalise(path: String): String =
        path.replace('\\', '/').trimEnd('/').ifEmpty { "/" }

    fun isExcluded(path: String, excluded: Set<String>): Boolean {
        if (excluded.isEmpty()) return false
        val target = normalise(path)
        for (raw in excluded) {
            val prefix = normalise(raw)
            if (target == prefix) return true
            if (target.startsWith("$prefix/")) return true
        }
        return false
    }
}
