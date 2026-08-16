package dev.sweep.core.model

/** A user-installed app plus whatever Android is willing to tell us about it. */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val isSystemApp: Boolean,
    val installedAt: Long,
    /** Epoch millis of last foreground use, or null when Android has no record. */
    val lastUsedAt: Long?,
    /** APK + native libraries. Zero when Usage Access has not been granted. */
    val appBytes: Long = 0L,
    val dataBytes: Long = 0L,
    val cacheBytes: Long = 0L,
) {
    val totalBytes: Long get() = appBytes + dataBytes + cacheBytes
}

data class UnusedApp(
    val app: InstalledApp,
    /** Null when Android has no usage record for this package. */
    val daysSinceUse: Int?,
    val reasons: List<Reason>,
)

data class AppScanResult(
    val apps: List<InstalledApp>,
    val unused: List<UnusedApp>,
    val thresholdDays: Int,
    /** False when Usage Access is missing: sizes and last-used dates are unavailable. */
    val hasUsageAccess: Boolean,
) {
    val reclaimableBytes: Long get() = unused.sumOf { it.app.totalBytes }
    val totalCacheBytes: Long get() = apps.sumOf { it.cacheBytes }

    companion object {
        fun empty(thresholdDays: Int = 90) =
            AppScanResult(emptyList(), emptyList(), thresholdDays, hasUsageAccess = false)
    }
}
