package dev.sweep.core.android

import android.app.usage.UsageEvents
import android.app.usage.UsageStats
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import dev.sweep.BuildConfig
import dev.sweep.core.model.UsageRecord
import dev.sweep.core.scan.UsageMerge
import java.util.concurrent.TimeUnit

data class UsageHistoryReport(
    val records: Map<String, UsageRecord>,
    val packagesFromEvents: Int,
    val packagesFromIntervals: Int,
) {
    val isEmpty: Boolean get() = records.isEmpty()
}

/**
 * Answers one question as well as Android allows: when did the user last open this app?
 *
 * Sweep used to ask `queryAndAggregateUsageStats(now - 2 years, now)` and take whatever came back.
 * That call picks a single bucket size internally (`INTERVAL_BEST`), and for a two-year range it
 * picks the yearly one. Yearly buckets are the least reliably populated of the four: the system
 * keeps a limited number of interval files and rotates them, and on several devices the yearly
 * table is sparse or effectively empty. When it is, that one query returns almost nothing, which
 * is exactly the symptom seen on two of the three test phones, while the third had yearly data and
 * worked fine. The data was there on all of them. Sweep was asking for it in the one way most
 * likely to come back empty.
 *
 * So this reads every source Android offers and merges them:
 *
 *  1. Raw foreground events over the recent window. `ACTIVITY_RESUMED` means the user genuinely
 *     opened the app, which is the most defensible evidence there is. Event retention is short and
 *     varies by device, so this covers recent use precisely rather than completely.
 *  2. All four interval buckets, queried explicitly rather than leaving the choice to
 *     `INTERVAL_BEST`. Daily and weekly buckets are populated on every device tested, so an app
 *     missing from the yearly table is still found here.
 *
 * The result per package is the newest plausible timestamp from any source. A package that appears
 * in none of them has no record, and that stays unknown rather than being read as disuse.
 *
 * Worth stating because it is a common misreading: Samsung's Sleeping and Deep Sleeping states do
 * not erase this history. They restrict what a rarely used app may do in the background, which is a
 * consequence of the user not opening it, not a cause of missing usage data. The third test device
 * returned dates from well before Sweep was installed, which also rules out any notion that Sweep
 * only sees usage from its own install date onward.
 */
object UsageHistory {

    fun read(context: Context, now: Long = System.currentTimeMillis()): UsageHistoryReport {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return UsageHistoryReport(emptyMap(), 0, 0)

        val fromEvents = readForegroundEvents(manager, now)
        val fromIntervals = readIntervalStats(manager, now)

        val report = UsageHistoryReport(
            records = UsageMerge.merge(fromEvents, fromIntervals),
            packagesFromEvents = fromEvents.size,
            packagesFromIntervals = fromIntervals.size,
        )
        logSummary(report, now)
        return report
    }

    /**
     * Precise recent use. One pass over the event stream, reusing a single [UsageEvents.Event] as
     * the API intends, keeping only the newest foreground moment per package.
     */
    private fun readForegroundEvents(
        manager: UsageStatsManager,
        now: Long,
    ): Map<String, Long> {
        val begin = now - TimeUnit.DAYS.toMillis(EVENT_WINDOW_DAYS)
        val stream = runCatching { manager.queryEvents(begin, now) }.getOrNull() ?: return emptyMap()

        val latest = HashMap<String, Long>()
        val event = UsageEvents.Event()
        runCatching {
            while (stream.hasNextEvent()) {
                stream.getNextEvent(event)
                if (event.eventType != foregroundEventType) continue
                val pkg = event.packageName ?: continue
                val stamp = event.timeStamp
                if (!UsageMerge.isPlausible(stamp, now)) continue
                val existing = latest[pkg]
                if (existing == null || stamp > existing) latest[pkg] = stamp
            }
        }
        return latest
    }

    /**
     * Older use, from the aggregated buckets. Every interval is queried because they are populated
     * independently, and the widest one is the least dependable.
     */
    private fun readIntervalStats(manager: UsageStatsManager, now: Long): Map<String, Long> {
        val begin = now - TimeUnit.DAYS.toMillis(HISTORY_DAYS)
        val latest = HashMap<String, Long>()

        for (interval in INTERVALS) {
            val stats = runCatching { manager.queryUsageStats(interval, begin, now) }
                .getOrNull()
                .orEmpty()
            for (entry in stats) {
                val stamp = usableTimestamp(entry, now) ?: continue
                val pkg = entry.packageName ?: continue
                val existing = latest[pkg]
                if (existing == null || stamp > existing) latest[pkg] = stamp
            }
        }
        return latest
    }

    /**
     * `lastTimeVisible` catches apps that were on screen without registering foreground time, so
     * both fields are considered. Zero means "this bucket has nothing for that package", which is
     * absence of evidence, not a date in 1970.
     */
    private fun usableTimestamp(entry: UsageStats, now: Long): Long? {
        val visible = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) entry.lastTimeVisible else 0L
        val stamp = maxOf(entry.lastTimeUsed, visible)
        return stamp.takeIf { UsageMerge.isPlausible(it, now) }
    }

    private val foregroundEventType: Int
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UsageEvents.Event.ACTIVITY_RESUMED
        } else {
            @Suppress("DEPRECATION")
            UsageEvents.Event.MOVE_TO_FOREGROUND
        }

    /**
     * One line, debug builds only. Enough to tell on a real device whether a shortfall is Android
     * having no data or Sweep failing to ask for it, without shipping diagnostics to users.
     */
    private fun logSummary(report: UsageHistoryReport, now: Long) {
        if (!BuildConfig.DEBUG) return
        val oldest = report.records.values.minOfOrNull { it.lastUsedAt }
        val oldestDays = oldest?.let { TimeUnit.MILLISECONDS.toDays(now - it) } ?: -1
        Log.d(
            "SweepUsage",
            "packages=${report.records.size} events=${report.packagesFromEvents} " +
                "intervals=${report.packagesFromIntervals} oldestDays=$oldestDays",
        )
    }

    private val INTERVALS = intArrayOf(
        UsageStatsManager.INTERVAL_DAILY,
        UsageStatsManager.INTERVAL_WEEKLY,
        UsageStatsManager.INTERVAL_MONTHLY,
        UsageStatsManager.INTERVAL_YEARLY,
    )

    /** Android retains roughly two years of aggregated usage. Asking for more gains nothing. */
    private const val HISTORY_DAYS = 730L

    /**
     * Raw events are kept for a matter of days on most devices, so a wide window costs a longer
     * walk for nothing. Anything older is the interval buckets' job.
     */
    private const val EVENT_WINDOW_DAYS = 30L
}
