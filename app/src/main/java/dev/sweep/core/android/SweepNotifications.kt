package dev.sweep.core.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.sweep.MainActivity
import dev.sweep.R
import dev.sweep.core.model.ByteFormat

/**
 * The two reminders Sweep is willing to send, and nothing else.
 *
 * Both are off until the user turns them on, both go to their own channel so either can be
 * silenced independently in Android's settings, and neither ever says the phone is full or that
 * something needs boosting. A cleaner that nags is a cleaner people uninstall.
 */
object SweepNotifications {

    const val CHANNEL_CLEANUP = "cleanup_reminders"
    const val CHANNEL_UNUSED_APPS = "unused_app_reminders"

    private const val ID_CLEANUP = 1001
    private const val ID_UNUSED_APPS = 1002

    /** Read by [MainActivity] so tapping a reminder lands on the screen it is about. */
    const val EXTRA_DESTINATION = "dev.sweep.destination"
    const val DESTINATION_UNUSED_APPS = "unused_apps"

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CLEANUP,
                "Cleanup reminders",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Occasional note when there is a meaningful amount worth reviewing."
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UNUSED_APPS,
                "Unused app reminders",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Occasional note about apps you have not opened in a long time."
                setShowBadge(false)
            }
        )
    }

    /** False when the user declined the runtime permission, or turned Sweep's notifications off. */
    fun canNotify(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun showCleanupReminder(context: Context, bytes: Long) {
        notify(
            context = context,
            id = ID_CLEANUP,
            channel = CHANNEL_CLEANUP,
            title = "${ByteFormat.short(bytes)} worth reviewing",
            body = "Sweep's last scan found files you may no longer need.",
            destination = null,
        )
    }

    fun showUnusedAppsReminder(context: Context, appCount: Int, thresholdDays: Int) {
        notify(
            context = context,
            id = ID_UNUSED_APPS,
            channel = CHANNEL_UNUSED_APPS,
            title = "Haven't used these in a while",
            body = if (appCount == 1) {
                "1 app has been inactive for $thresholdDays+ days."
            } else {
                "$appCount apps have been inactive for $thresholdDays+ days."
            },
            destination = DESTINATION_UNUSED_APPS,
        )
    }

    fun cancelAll(context: Context) {
        NotificationManagerCompat.from(context).apply {
            cancel(ID_CLEANUP)
            cancel(ID_UNUSED_APPS)
        }
    }

    /**
     * App names are deliberately absent from the text. Which apps someone has stopped using is
     * their business, and a lock screen is not a private place.
     */
    private fun notify(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        body: String,
        destination: String?,
    ) {
        // Checked again here rather than only in the caller, because this is the line that
        // actually needs it and a guard three frames up is a guard nobody can see.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannels(context)

        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { destination?.let { putExtra(EXTRA_DESTINATION, it) } }

        val pending = android.app.PendingIntent.getActivity(
            context,
            id,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                android.app.PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()

        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}
