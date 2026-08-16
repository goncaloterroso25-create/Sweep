package dev.sweep.core.android

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Process
import android.provider.Settings

/** How much of the filesystem Android is currently letting Sweep see. */
enum class FileAccess {
    /** Android 11+ "All files access", or legacy read/write on Android 8-10. */
    FULL,

    /** Nothing granted. File scanning is unavailable and the UI says so. */
    NONE,
}

data class PermissionStatus(
    val fileAccess: FileAccess,
    val hasUsageAccess: Boolean,
) {
    val canScanFiles: Boolean get() = fileAccess == FileAccess.FULL
    val allGranted: Boolean get() = canScanFiles && hasUsageAccess
}

/**
 * Everything Sweep needs to know about its own permissions.
 *
 * Neither of the two permissions Sweep uses is a normal runtime permission on a modern device:
 * both are granted from a Settings screen the app can only open, never satisfy itself. The UI
 * is built around that — Sweep explains the value, opens the right screen, and re-checks on
 * resume rather than pretending it can prompt.
 */
object SweepPermissions {

    fun status(context: Context) = PermissionStatus(
        fileAccess = if (hasFileAccess(context)) FileAccess.FULL else FileAccess.NONE,
        hasUsageAccess = hasUsageAccess(context),
    )

    fun hasFileAccess(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            granted(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                granted(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

    /** True on Android 10 and below, where a normal runtime prompt is still the right flow. */
    val usesRuntimeStoragePrompt: Boolean
        get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R

    val runtimeStoragePermissions = arrayOf(
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
    )

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName,
                )
            }
        } catch (e: SecurityException) {
            return false
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_DEFAULT ->
                granted(context, Manifest.permission.PACKAGE_USAGE_STATS)
            else -> false
        }
    }

    /**
     * Opens the "All files access" toggle for Sweep specifically, falling back to the full list
     * on devices whose Settings app does not honour the per-package variant.
     */
    fun fileAccessIntents(context: Context): List<Intent> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                appDetailsIntent(context.packageName),
            )
        } else {
            listOf(appDetailsIntent(context.packageName))
        }

    fun usageAccessIntents(context: Context): List<Intent> = listOf(
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            .setData(Uri.parse("package:${context.packageName}")),
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS),
    )

    fun appDetailsIntent(packageName: String): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))

    private fun granted(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
