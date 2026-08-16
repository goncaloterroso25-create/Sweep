package dev.sweep.core.scan

import dev.sweep.core.model.InstalledApp
import dev.sweep.core.model.Reason
import dev.sweep.core.model.UnusedApp

/**
 * Turns the raw app inventory into "you probably forgot about these".
 *
 * System apps are excluded because they cannot be uninstalled through the normal flow, and an
 * app with no usage record is only surfaced once it has been installed longer than the
 * threshold — otherwise a fresh install would appear the moment it was added.
 */
object UnusedAppPolicy {

    val THRESHOLD_CHOICES = listOf(30, 60, 90, 180)

    fun find(
        apps: List<InstalledApp>,
        thresholdDays: Int,
        now: Long,
        excludedPackages: Set<String> = emptySet(),
    ): List<UnusedApp> = apps.asSequence()
        .filterNot { it.isSystemApp }
        .filterNot { it.packageName in excludedPackages }
        .mapNotNull { app -> evaluate(app, thresholdDays, now) }
        .sortedWith(
            compareByDescending<UnusedApp> { it.daysSinceUse ?: Int.MAX_VALUE }
                .thenByDescending { it.app.totalBytes }
                .thenBy { it.app.label }
        )
        .toList()

    private fun evaluate(app: InstalledApp, thresholdDays: Int, now: Long): UnusedApp? {
        val lastUsed = app.lastUsedAt
        val installedDays = FileClassifier.ageDays(now, app.installedAt)

        if (lastUsed == null || lastUsed <= 0L) {
            if (installedDays < thresholdDays) return null
            return UnusedApp(
                app = app,
                daysSinceUse = null,
                reasons = listOf(Reason.NoUsageRecord, Reason.Bytes(app.totalBytes)),
            )
        }

        val idleDays = FileClassifier.ageDays(now, lastUsed)
        if (idleDays < thresholdDays) return null
        return UnusedApp(
            app = app,
            daysSinceUse = idleDays,
            reasons = listOf(Reason.LastOpened(idleDays), Reason.Bytes(app.totalBytes)),
        )
    }
}
