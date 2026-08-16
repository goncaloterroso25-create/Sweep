package dev.sweep.core.scan

import dev.sweep.core.model.InstalledApp
import dev.sweep.core.model.Reason
import dev.sweep.core.model.UnknownUsageApp
import dev.sweep.core.model.UnusedApp

/**
 * Splits the app inventory into "you have genuinely stopped opening these" and "Android would not
 * tell me".
 *
 * The distinction is the whole point. An app only qualifies as unused when Android reported a real
 * last-used timestamp *and* that timestamp is older than the chosen threshold. Anything without a
 * timestamp is unknown: it is listed separately, excluded from the unused count and from the
 * reclaimable total, and never suggested for removal. Treating silence as evidence is how a
 * cleaner ends up telling someone to uninstall the app they used an hour ago.
 *
 * System apps are excluded throughout because they cannot be uninstalled through the normal flow.
 */
object UnusedAppPolicy {

    val THRESHOLD_CHOICES = listOf(30, 60, 90, 180)

    data class Result(
        val unused: List<UnusedApp> = emptyList(),
        val unknownUsage: List<UnknownUsageApp> = emptyList(),
    )

    fun find(
        apps: List<InstalledApp>,
        thresholdDays: Int,
        now: Long,
        excludedPackages: Set<String> = emptySet(),
    ): Result {
        val unused = mutableListOf<UnusedApp>()
        val unknown = mutableListOf<UnknownUsageApp>()

        apps.asSequence()
            .filterNot { it.isSystemApp }
            .filterNot { it.packageName in excludedPackages }
            .forEach { app ->
                val lastUsed = app.lastUsedAt
                if (lastUsed == null || lastUsed <= 0L) {
                    // Only surfaced once the app has been installed longer than the threshold —
                    // a fresh install has no history yet for the most ordinary of reasons.
                    val installedDays = FileClassifier.ageDays(now, app.installedAt)
                    if (installedDays >= thresholdDays) {
                        unknown += UnknownUsageApp(app = app, installedDays = installedDays)
                    }
                    return@forEach
                }

                val idleDays = FileClassifier.ageDays(now, lastUsed)
                if (idleDays < thresholdDays) return@forEach
                unused += UnusedApp(
                    app = app,
                    daysSinceUse = idleDays,
                    reasons = listOf(Reason.LastOpened(idleDays), Reason.Bytes(app.totalBytes)),
                )
            }

        return Result(
            unused = unused.sortedWith(
                compareByDescending<UnusedApp> { it.daysSinceUse }
                    .thenByDescending { it.app.totalBytes }
                    .thenBy { it.app.label }
            ),
            unknownUsage = unknown.sortedWith(
                compareByDescending<UnknownUsageApp> { it.app.totalBytes }
                    .thenBy { it.app.label }
            ),
        )
    }
}
