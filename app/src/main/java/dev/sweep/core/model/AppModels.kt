package dev.sweep.core.model

/** Which Android source produced a package's last-used timestamp. */
enum class UsageEvidence {
    /** The user actually brought the app to the foreground. The strongest signal available. */
    FOREGROUND_EVENT,

    /** An aggregated usage bucket. Coarser, but it reaches much further back. */
    INTERVAL_STATS,
}

data class UsageRecord(val lastUsedAt: Long, val evidence: UsageEvidence)

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

/**
 * An app Sweep can say something definite about: Android recorded when it was last opened, and
 * that date is past the user's threshold.
 */
data class UnusedApp(
    val app: InstalledApp,
    /** Days since the last use Android actually recorded. Never inferred. */
    val daysSinceUse: Int,
    val reasons: List<Reason>,
)

/**
 * An app Android returned no usage history for.
 *
 * Unknown is not the same as unused — the app may be opened daily — so these are kept out of the
 * unused count, out of the reclaimable total, and are never presented as safe to uninstall.
 */
data class UnknownUsageApp(
    val app: InstalledApp,
    /** How long it has been installed: the only fact Sweep actually has about it. */
    val installedDays: Int,
)

data class AppScanResult(
    val apps: List<InstalledApp>,
    val unused: List<UnusedApp>,
    val unknownUsage: List<UnknownUsageApp>,
    val thresholdDays: Int,
    /** False when Usage Access is missing: sizes and last-used dates are unavailable. */
    val hasUsageAccess: Boolean,
) {
    /** Only apps with a real last-used date count. Apps of unknown usage contribute nothing. */
    val reclaimableBytes: Long get() = unused.sumOf { it.app.totalBytes }

    val unknownBytes: Long get() = unknownUsage.sumOf { it.app.totalBytes }

    val totalCacheBytes: Long get() = apps.sumOf { it.cacheBytes }

    /**
     * False when Android returned no last-used date for a single app. That happens on some
     * devices even with Usage Access granted, and the screen has to say so rather than present
     * an entire phone's worth of apps as candidates for removal.
     */
    val hasAnyUsageHistory: Boolean get() = apps.any { it.lastUsedAt != null }

    companion object {
        fun empty(thresholdDays: Int = 90) = AppScanResult(
            apps = emptyList(),
            unused = emptyList(),
            unknownUsage = emptyList(),
            thresholdDays = thresholdDays,
            hasUsageAccess = false,
        )
    }
}
