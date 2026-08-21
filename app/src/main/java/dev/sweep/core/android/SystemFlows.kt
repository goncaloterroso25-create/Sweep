package dev.sweep.core.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import androidx.core.net.toUri
import android.provider.Settings

/**
 * The Android flows Sweep hands the user into rather than faking.
 *
 * Three things a third-party app genuinely cannot do, and what Sweep does instead:
 *  - uninstall an app silently        -> launch the system uninstall dialog and verify afterwards
 *  - clear another app's cache        -> open that app's storage page in Settings
 *  - turn on system haptics           -> open Android's sound & vibration settings
 *
 * Android 12's `ACTION_CLEAR_APP_CACHE` dialog used to be offered here as a fourth. It is gone:
 * on the devices Sweep was tested on it either did nothing or opened an unrelated screen, and a
 * button whose behaviour depends on the OEM is worse than no button.
 */
object SystemFlows {

    /**
     * The system uninstall confirmation, in order of preference.
     *
     * Sweep used to send only `ACTION_DELETE`. That is the older, looser action: some Settings
     * implementations answer it, some ignore it, and on the phone where uninstall appeared to do
     * nothing that is exactly what happened. `ACTION_UNINSTALL_PACKAGE` is the documented one, and
     * it needs `REQUEST_DELETE_PACKAGES` in the manifest, which Sweep now declares. With
     * `EXTRA_RETURN_RESULT` it also reports back whether the removal succeeded, was cancelled or
     * failed, so Sweep can stop guessing.
     *
     * `ACTION_DELETE` stays as a fallback, and the app's own settings page as a last resort, since
     * every device can at least open that.
     */
    @Suppress("DEPRECATION")
    fun uninstallIntents(packageName: String): List<Intent> {
        val target = "package:$packageName".toUri()
        return listOf(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, target)
                .putExtra(Intent.EXTRA_RETURN_RESULT, true),
            Intent(Intent.ACTION_DELETE, target),
            appDetailsIntent(packageName),
        )
    }

    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())

    fun storageSettingsIntent(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    /**
     * Where touch feedback is turned on. Sweep's haptics setting cannot override the system one,
     * so when the system's is off this is the only useful thing to offer.
     */
    fun soundSettingsIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_SOUND_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    /** Sweep's own notification settings, for when Android is the one saying no. */
    fun notificationSettingsIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        appDetailsIntent(context.packageName),
    )

    fun isInstalled(context: Context, packageName: String): Boolean = try {
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: Exception) {
        false
    }

    /**
     * Tries each intent in turn. OEM Settings apps vary in which actions they implement, so a
     * fallback chain is the difference between "opens the right screen" and a dead button.
     */
    fun launchFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return true
            } catch (e: ActivityNotFoundException) {
                continue
            } catch (e: SecurityException) {
                continue
            }
        }
        return false
    }

    /**
     * Tells MediaStore the files are gone. Without this the gallery keeps showing entries for
     * pictures that no longer exist, which would make Sweep look broken even though it worked.
     */
    fun notifyDeleted(context: Context, paths: List<String>) {
        if (paths.isEmpty()) return
        runCatching {
            MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
        }
    }
}
