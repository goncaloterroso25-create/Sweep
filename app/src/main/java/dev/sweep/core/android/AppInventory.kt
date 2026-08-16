package dev.sweep.core.android

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.util.LruCache
import dev.sweep.core.model.AppScanResult
import dev.sweep.core.model.InstalledApp
import dev.sweep.core.scan.UnusedAppPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Reads the installed-app inventory and, when Usage Access is granted, how long each app has
 * been sitting idle and how much space it occupies.
 *
 * Two Android limits shape this and are surfaced honestly in the UI:
 *  - Package visibility (Android 11+) means the list is only complete because Sweep declares
 *    QUERY_ALL_PACKAGES. Without it Android would hide most of the device's apps.
 *  - Usage history is retained for roughly two years, so "no usage record" is reported as
 *    exactly that, never as "never opened".
 */
class AppInventory(private val context: Context) {

    private val packageManager: PackageManager get() = context.packageManager
    private val iconCache = LruCache<String, Drawable>(96)

    suspend fun load(
        thresholdDays: Int,
        excludedPackages: Set<String> = emptySet(),
        now: Long = System.currentTimeMillis(),
    ): AppScanResult = withContext(Dispatchers.IO) {
        val hasUsageAccess = SweepPermissions.hasUsageAccess(context)
        val usage = if (hasUsageAccess) readUsage(now) else emptyMap()
        val storageStats = if (hasUsageAccess) {
            context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
        } else {
            null
        }

        val apps = installedApplications()
            .asSequence()
            .filter { it.packageName != context.packageName }
            .map { info -> toInstalledApp(info, usage, storageStats) }
            .sortedByDescending { it.totalBytes }
            .toList()

        AppScanResult(
            apps = apps,
            unused = UnusedAppPolicy.find(apps, thresholdDays, now, excludedPackages),
            thresholdDays = thresholdDays,
            hasUsageAccess = hasUsageAccess,
        )
    }

    private fun toInstalledApp(
        info: ApplicationInfo,
        usage: Map<String, UsageStats>,
        stats: StorageStatsManager?,
    ): InstalledApp {
        val pkg = info.packageName
        val installedAt = runCatching {
            packageManager.getPackageInfo(pkg, 0).firstInstallTime
        }.getOrDefault(0L)

        val lastUsed = usage[pkg]?.let { entry ->
            val visible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                entry.lastTimeVisible
            } else {
                0L
            }
            maxOf(entry.lastTimeUsed, visible).takeIf { it > 0L }
        }

        val sizes = stats?.runCatching {
            queryStatsForPackage(info.storageUuid, pkg, Process.myUserHandle())
        }?.getOrNull()

        return InstalledApp(
            packageName = pkg,
            label = runCatching { packageManager.getApplicationLabel(info).toString() }
                .getOrDefault(pkg),
            isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            installedAt = installedAt,
            lastUsedAt = lastUsed,
            appBytes = sizes?.appBytes ?: 0L,
            // StorageStats.dataBytes already includes the cache, so subtract it to keep
            // totalBytes honest instead of counting those bytes twice.
            dataBytes = ((sizes?.dataBytes ?: 0L) - (sizes?.cacheBytes ?: 0L)).coerceAtLeast(0L),
            cacheBytes = sizes?.cacheBytes ?: 0L,
        )
    }

    private fun readUsage(now: Long): Map<String, UsageStats> {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return emptyMap()
        val begin = now - TimeUnit.DAYS.toMillis(USAGE_HISTORY_DAYS)
        return runCatching { manager.queryAndAggregateUsageStats(begin, now) }
            .getOrDefault(emptyMap())
    }

    @Suppress("DEPRECATION")
    private fun installedApplications(): List<ApplicationInfo> =
        runCatching { packageManager.getInstalledApplications(0) }.getOrDefault(emptyList())

    /**
     * Icons are loaded per visible row rather than up front — decoding a hundred adaptive icons
     * during a scan is exactly the kind of stall this app is supposed to avoid.
     */
    suspend fun icon(packageName: String): Drawable? {
        iconCache[packageName]?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { packageManager.getApplicationIcon(packageName) }
                .getOrNull()
                ?.also { iconCache.put(packageName, it) }
        }
    }

    private companion object {
        /** Android keeps roughly two years of aggregated usage. Asking for more gains nothing. */
        const val USAGE_HISTORY_DAYS = 730L
    }
}
