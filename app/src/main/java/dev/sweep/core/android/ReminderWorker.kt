package dev.sweep.core.android

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.sweep.SweepApplication
import dev.sweep.core.data.ReminderPolicy
import dev.sweep.core.data.ReminderState
import java.util.concurrent.TimeUnit

/**
 * The only thing Sweep does when it is not open.
 *
 * It runs about once a week, does no scanning of its own, and sends at most one reminder. There
 * is a deliberate limit on what it is allowed to claim: a background job cannot hash a phone's
 * worth of files without costing real battery, so the cleanup reminder reports the amount the
 * last real scan actually measured rather than inventing a fresh figure. If the user has never
 * scanned, it says nothing at all.
 *
 * The unused-app reminder is cheap and honest by comparison, because [UsageHistory] is a couple of
 * binder calls and the answer is a fact rather than an estimate.
 */
class ReminderWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? SweepApplication ?: return Result.success()
        val settings = app.settings.currentSettings()
        val state = app.settings.currentReminderState()
        val now = System.currentTimeMillis()

        if (!settings.anyReminderEnabled) {
            // The toggle went off while the job was queued. Stop rescheduling ourselves.
            cancel(applicationContext)
            return Result.success()
        }
        if (!SweepNotifications.canNotify(applicationContext)) return Result.success()
        if (!ReminderPolicy.mayNotify(state, now)) return Result.success()

        if (settings.unusedAppReminders && remindAboutUnusedApps(settings, state, now)) {
            return Result.success()
        }
        if (settings.cleanupReminders && ReminderPolicy.shouldRemindAboutCleanup(state, settings.reminderThresholdBytes, now)) {
            SweepNotifications.showCleanupReminder(applicationContext, state.lastScanFoundBytes, state.lastScanAt)
            app.settings.recordReminderSent(now, notifiedBytes = state.lastScanFoundBytes)
        }
        return Result.success()
    }

    /**
     * Only apps Android gave a real last-used date for. Anything under "Usage unknown" is
     * excluded here for the same reason it is excluded everywhere else: Sweep has no evidence.
     */
    private suspend fun remindAboutUnusedApps(
        settings: dev.sweep.core.data.SweepSettings,
        state: ReminderState,
        now: Long,
    ): Boolean {
        val app = applicationContext as SweepApplication
        if (!SweepPermissions.hasUsageAccess(applicationContext)) return false

        val result = runCatching {
            app.repository.loadApps(settings.unusedAppThresholdDays, settings.excludedPackages)
        }.getOrNull() ?: return false

        val count = result.unused.size
        if (!ReminderPolicy.shouldRemindAboutUnusedApps(state, count)) {
            // Remember a fall too, so the next rise is measured from here rather than from a peak.
            if (count != state.lastUnusedAppCount) app.settings.recordUnusedAppCount(count)
            return false
        }

        SweepNotifications.showUnusedAppsReminder(
            context = applicationContext,
            appCount = count,
            thresholdDays = settings.unusedAppThresholdDays,
            totalBytes = result.reclaimableBytes,
        )
        app.settings.recordReminderSent(now, unusedAppCount = count)
        return true
    }

    companion object {
        private const val WORK_NAME = "sweep_reminders"

        /**
         * Weekly, inexact, and only when the battery is not already low. WorkManager decides the
         * exact moment, which is the whole point: nothing here is urgent enough to wake a phone.
         */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(7, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build()
                )
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            SweepNotifications.cancelAll(context)
        }

        /** Called whenever a reminder setting changes, so the schedule matches the switches. */
        fun sync(context: Context, anyEnabled: Boolean) {
            if (anyEnabled) schedule(context) else cancel(context)
        }
    }
}
