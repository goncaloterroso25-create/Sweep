package dev.sweep.core

import dev.sweep.core.data.ReminderPolicy
import dev.sweep.core.data.ReminderState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReminderPolicyTest {

    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L
    private val gb = 1_000_000_000L

    private val quiet = ReminderState(lastOpenedAt = now - 10 * day, lastNotifiedAt = now - 10 * day)

    @Test
    fun `stays quiet for a few days after the app was opened`() {
        assertFalse(ReminderPolicy.mayNotify(quiet.copy(lastOpenedAt = now - 2 * day), now))
        assertTrue(ReminderPolicy.mayNotify(quiet.copy(lastOpenedAt = now - 4 * day), now))
    }

    @Test
    fun `never two reminders in the same week`() {
        assertFalse(ReminderPolicy.mayNotify(quiet.copy(lastNotifiedAt = now - 5 * day), now))
        assertTrue(ReminderPolicy.mayNotify(quiet.copy(lastNotifiedAt = now - 7 * day), now))
    }

    @Test
    fun `a first ever run is allowed to speak`() {
        assertTrue(ReminderPolicy.mayNotify(ReminderState(), now))
    }

    @Test
    fun `cleanup reminder needs a real scan above the threshold`() {
        val scanned = quiet.copy(lastScanAt = now - 3 * day, lastScanFoundBytes = 4 * gb)
        assertTrue(ReminderPolicy.shouldRemindAboutCleanup(scanned, 3 * gb, now))
        assertFalse(ReminderPolicy.shouldRemindAboutCleanup(scanned, 5 * gb, now))
        // Never scanned: there is no figure to quote, and Sweep will not invent one.
        assertFalse(ReminderPolicy.shouldRemindAboutCleanup(quiet.copy(lastScanFoundBytes = 4 * gb), 1 * gb, now))
    }

    @Test
    fun `cleanup reminder does not repeat a figure it already sent`() {
        val scanned = quiet.copy(lastScanAt = now - 3 * day, lastScanFoundBytes = 4 * gb, lastNotifiedBytes = 4 * gb)
        assertFalse(ReminderPolicy.shouldRemindAboutCleanup(scanned, 1 * gb, now))
    }

    @Test
    fun `a stale scan is history, not a description of the phone`() {
        val old = quiet.copy(lastScanAt = now - 60 * day, lastScanFoundBytes = 9 * gb)
        assertFalse(ReminderPolicy.shouldRemindAboutCleanup(old, 1 * gb, now))
    }

    @Test
    fun `unused app reminder only when the count has grown`() {
        assertTrue(ReminderPolicy.shouldRemindAboutUnusedApps(ReminderState(lastUnusedAppCount = -1), 2))
        assertTrue(ReminderPolicy.shouldRemindAboutUnusedApps(ReminderState(lastUnusedAppCount = 2), 3))
        assertFalse(ReminderPolicy.shouldRemindAboutUnusedApps(ReminderState(lastUnusedAppCount = 3), 3))
        assertFalse(ReminderPolicy.shouldRemindAboutUnusedApps(ReminderState(lastUnusedAppCount = 5), 3))
        assertFalse(ReminderPolicy.shouldRemindAboutUnusedApps(ReminderState(lastUnusedAppCount = -1), 0))
    }
}
