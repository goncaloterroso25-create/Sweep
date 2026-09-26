package dev.sweep.core.data

import java.util.concurrent.TimeUnit

/**
 * When a reminder is worth sending. Android-free, so every rule here is unit-tested.
 *
 * A utility earns a notification only by saying something the user does not already know, so the
 * rules are all about staying quiet:
 *  - never within a few days of the user having opened Sweep: they have just seen it all
 *  - never twice in the same week, whichever reminder it was
 *  - the cleanup figure must come from a real scan, recent enough to still mean something, and
 *    must not be the figure already sent
 *  - the unused-app count must have grown since last time. Fewer unused apps is not news, and
 *    apps under "Usage unknown" are never counted in the first place
 */
object ReminderPolicy {

    val QUIET_AFTER_OPEN_MS = TimeUnit.DAYS.toMillis(3)
    val COOLDOWN_MS = TimeUnit.DAYS.toMillis(6)

    /** Past this, a scan's figure is history rather than a description of the phone. */
    val MAX_SCAN_AGE_MS = TimeUnit.DAYS.toMillis(45)

    fun mayNotify(state: ReminderState, now: Long): Boolean =
        now - state.lastOpenedAt >= QUIET_AFTER_OPEN_MS &&
            now - state.lastNotifiedAt >= COOLDOWN_MS

    fun shouldRemindAboutCleanup(state: ReminderState, thresholdBytes: Long, now: Long): Boolean =
        state.lastScanAt > 0L &&
            now - state.lastScanAt <= MAX_SCAN_AGE_MS &&
            state.lastScanFoundBytes >= thresholdBytes &&
            state.lastScanFoundBytes != state.lastNotifiedBytes

    fun shouldRemindAboutUnusedApps(state: ReminderState, unusedCount: Int): Boolean =
        unusedCount > 0 && unusedCount > state.lastUnusedAppCount
}
