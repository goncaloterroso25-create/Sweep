package dev.sweep.core

import dev.sweep.core.model.UsageEvidence
import dev.sweep.core.scan.UsageMerge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * The rule that decides what counts as evidence of an app being opened.
 *
 * This is the logic the cross-device problem came down to: one source was consulted, it was the
 * least reliably populated one, and anything it missed was read as "no history".
 */
class UsageMergeTest {

    @Test
    fun `an app seen only in the interval buckets still gets a date`() {
        val merged = UsageMerge.merge(
            fromEvents = emptyMap(),
            fromIntervals = mapOf("com.old" to daysAgo(200)),
        )
        val record = merged.getValue("com.old")
        assertEquals(daysAgo(200), record.lastUsedAt)
        assertEquals(UsageEvidence.INTERVAL_STATS, record.evidence)
    }

    @Test
    fun `an app seen only in the event stream still gets a date`() {
        val merged = UsageMerge.merge(
            fromEvents = mapOf("com.recent" to daysAgo(2)),
            fromIntervals = emptyMap(),
        )
        val record = merged.getValue("com.recent")
        assertEquals(daysAgo(2), record.lastUsedAt)
        assertEquals(UsageEvidence.FOREGROUND_EVENT, record.evidence)
    }

    @Test
    fun `the newer source wins when both have the package`() {
        val merged = UsageMerge.merge(
            fromEvents = mapOf("com.app" to daysAgo(3)),
            fromIntervals = mapOf("com.app" to daysAgo(120)),
        )
        assertEquals(daysAgo(3), merged.getValue("com.app").lastUsedAt)
        assertEquals(UsageEvidence.FOREGROUND_EVENT, merged.getValue("com.app").evidence)
    }

    @Test
    fun `a stale event never overrides a newer bucket`() {
        // Contrived, but the merge must not assume events are always fresher than buckets.
        val merged = UsageMerge.merge(
            fromEvents = mapOf("com.app" to daysAgo(40)),
            fromIntervals = mapOf("com.app" to daysAgo(1)),
        )
        assertEquals(daysAgo(1), merged.getValue("com.app").lastUsedAt)
        assertEquals(UsageEvidence.INTERVAL_STATS, merged.getValue("com.app").evidence)
    }

    @Test
    fun `an identical timestamp is attributed to the foreground event`() {
        val stamp = daysAgo(5)
        val merged = UsageMerge.merge(
            fromEvents = mapOf("com.app" to stamp),
            fromIntervals = mapOf("com.app" to stamp),
        )
        assertEquals(UsageEvidence.FOREGROUND_EVENT, merged.getValue("com.app").evidence)
    }

    @Test
    fun `packages in neither source stay absent rather than becoming zero`() {
        val merged = UsageMerge.merge(
            fromEvents = mapOf("com.a" to daysAgo(1)),
            fromIntervals = mapOf("com.b" to daysAgo(1)),
        )
        assertEquals(setOf("com.a", "com.b"), merged.keys)
        assertNull(merged["com.missing"])
    }

    @Test
    fun `empty-bucket placeholder timestamps are rejected`() {
        assertFalse("zero is an empty bucket, not 1970", UsageMerge.isPlausible(0L, NOW))
        assertFalse(UsageMerge.isPlausible(-1L, NOW))
        assertFalse("a 2001 stamp is a placeholder", UsageMerge.isPlausible(1_000_000_000_000L, NOW))
    }

    @Test
    fun `future timestamps are rejected beyond a tolerance for clock skew`() {
        assertTrue(UsageMerge.isPlausible(NOW + TimeUnit.HOURS.toMillis(2), NOW))
        assertFalse(UsageMerge.isPlausible(NOW + TimeUnit.DAYS.toMillis(3), NOW))
    }

    @Test
    fun `ordinary recent and old timestamps are accepted`() {
        assertTrue(UsageMerge.isPlausible(daysAgo(1), NOW))
        assertTrue(UsageMerge.isPlausible(daysAgo(700), NOW))
    }
}
