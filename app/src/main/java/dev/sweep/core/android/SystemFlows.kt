package dev.sweep.core.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
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

    /** The system uninstall confirmation. Result is verified with [isInstalled] on return. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    fun storageSettingsIntent(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

    /**
     * Where touch feedback is turned on. Sweep's haptics setting cannot override the system one,
     * so when the system's is off this is the only useful thing to offer.
     */
    fun soundSettingsIntents(): List<Intent> = listOf(
        Intent(Settings.ACTION_SOUND_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
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
