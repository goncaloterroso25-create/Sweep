package dev.sweep.core.scan

import dev.sweep.core.model.UsageEvidence
import dev.sweep.core.model.UsageRecord
import java.util.concurrent.TimeUnit

/**
 * Combines the two things Android will tell you about when an app was last opened.
 *
 * Kept free of Android types so the rule that decides what counts as evidence can be tested,
 * which is the same reason the scanner and the safety policy live out here. The platform side is
 * [dev.sweep.core.android.UsageHistory]; it does the querying and nothing else.
 */
object UsageMerge {

    /**
     * Newest wins, and a foreground event wins ties.
     *
     * Ties are not hypothetical: an app opened in the last few days usually appears in both
     * sources with the same timestamp, and attributing that to the event is the more accurate
     * description of where the number came from.
     */
    fun merge(
        fromEvents: Map<String, Long>,
        fromIntervals: Map<String, Long>,
    ): Map<String, UsageRecord> {
        val merged = HashMap<String, UsageRecord>(fromEvents.size + fromIntervals.size)
        fromIntervals.forEach { (pkg, stamp) ->
            merged[pkg] = UsageRecord(stamp, UsageEvidence.INTERVAL_STATS)
        }
        fromEvents.forEach { (pkg, stamp) ->
            val existing = merged[pkg]
            if (existing == null || stamp >= existing.lastUsedAt) {
                merged[pkg] = UsageRecord(stamp, UsageEvidence.FOREGROUND_EVENT)
            }
        }
        return merged
    }

    /**
     * Rejects the two kinds of nonsense these APIs produce: zero or near-zero stamps from empty
     * buckets, and timestamps in the future after a clock change or a restore from backup.
     */
    fun isPlausible(stamp: Long, now: Long): Boolean =
        stamp >= EARLIEST_PLAUSIBLE && stamp <= now + CLOCK_TOLERANCE_MS

    /** 2010-01-01. Any usage stamp older than this is a placeholder, not a date. */
    private const val EARLIEST_PLAUSIBLE = 1_262_304_000_000L

    private val CLOCK_TOLERANCE_MS = TimeUnit.HOURS.toMillis(12)
}
