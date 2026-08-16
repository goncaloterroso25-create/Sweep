package dev.sweep.core.android

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.storage.StorageManager
import android.provider.Settings

/**
 * The Android flows Sweep hands the user into rather than faking.
 *
 * Three things a third-party app genuinely cannot do, and what Sweep does instead:
 *  - uninstall an app silently        -> launch the system uninstall dialog and verify afterwards
 *  - clear another app's cache        -> launch Android's own "clear cached data" flow (API 31+)
 *  - clear one app's cache            -> open that app's storage page in Settings
 */
object SystemFlows {

    /** The system uninstall confirmation. Result is verified with [isInstalled] on return. */
    fun uninstallIntent(packageName: String): Intent =
        Intent(Intent.ACTION_DELETE, Uri.parse("package:$packageName"))

    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    /**
     * Android 12+ exposes a system dialog that clears cached data across all apps. This is the
     * only supported way for Sweep to affect another app's cache, and the OS — not Sweep —
     * performs and confirms the work.
     */
    fun clearAllCachesIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(StorageManager.ACTION_CLEAR_APP_CACHE)
        } else {
            null
        }

    fun storageSettingsIntent(): Intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)

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
