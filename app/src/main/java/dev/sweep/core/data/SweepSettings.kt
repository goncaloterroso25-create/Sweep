package dev.sweep.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.sweep.core.model.ScanConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Two options, because there were three and one of them was a lie.
 *
 * "System" and "Full" behaved identically on any device with animations left on, which is nearly
 * all of them, so the setting looked like it did nothing. [STANDARD] now covers both: Sweep's
 * normal motion, which still collapses automatically when Android's animator duration scale is
 * zero. Older stored values simply fall back to [STANDARD].
 */
enum class MotionPreference { STANDARD, REDUCED }

data class SweepSettings(
    val oldFileThresholdDays: Int = 60,
    val largeFileThresholdBytes: Long = 250L * 1000 * 1000,
    val oldScreenshotThresholdDays: Int = 180,
    val unusedAppThresholdDays: Int = 90,
    val hapticsEnabled: Boolean = true,
    val motion: MotionPreference = MotionPreference.STANDARD,
    val onboardingComplete: Boolean = false,
    val excludedPaths: Set<String> = emptySet(),
    val excludedPackages: Set<String> = emptySet(),
) {
    fun toScanConfig() = ScanConfig(
        oldFileThresholdDays = oldFileThresholdDays,
        largeFileThresholdBytes = largeFileThresholdBytes,
        oldScreenshotThresholdDays = oldScreenshotThresholdDays,
    )
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sweep")

/**
 * Local settings and the "don't suggest this again" list. Nothing here ever leaves the device;
 * there is no account, no sync and no network code anywhere in Sweep.
 */
class SweepSettingsStore(private val context: Context) {

    val settings: Flow<SweepSettings> = context.dataStore.data.map { prefs ->
        SweepSettings(
            oldFileThresholdDays = prefs[OLD_FILE_DAYS] ?: 60,
            largeFileThresholdBytes = prefs[LARGE_FILE_BYTES] ?: (250L * 1000 * 1000),
            oldScreenshotThresholdDays = prefs[SCREENSHOT_DAYS] ?: 180,
            unusedAppThresholdDays = prefs[UNUSED_APP_DAYS] ?: 90,
            hapticsEnabled = prefs[HAPTICS] ?: true,
            // A stored "SYSTEM" or "FULL" from the three-option version no longer parses, and
            // falling back to STANDARD is exactly what both of them used to do.
            motion = prefs[MOTION]
                ?.let { runCatching { MotionPreference.valueOf(it) }.getOrNull() }
                ?: MotionPreference.STANDARD,
            onboardingComplete = prefs[ONBOARDING] ?: false,
            excludedPaths = prefs[EXCLUDED_PATHS] ?: emptySet(),
            excludedPackages = prefs[EXCLUDED_PACKAGES] ?: emptySet(),
        )
    }

    suspend fun setOldFileThreshold(days: Int) = put { it[OLD_FILE_DAYS] = days }
    suspend fun setLargeFileThreshold(bytes: Long) = put { it[LARGE_FILE_BYTES] = bytes }
    suspend fun setScreenshotThreshold(days: Int) = put { it[SCREENSHOT_DAYS] = days }
    suspend fun setUnusedAppThreshold(days: Int) = put { it[UNUSED_APP_DAYS] = days }
    suspend fun setHaptics(enabled: Boolean) = put { it[HAPTICS] = enabled }
    suspend fun setMotion(preference: MotionPreference) = put { it[MOTION] = preference.name }
    suspend fun setOnboardingComplete() = put { it[ONBOARDING] = true }

    suspend fun excludePath(path: String) = put {
        it[EXCLUDED_PATHS] = (it[EXCLUDED_PATHS] ?: emptySet()) + path
    }

    suspend fun includePath(path: String) = put {
        it[EXCLUDED_PATHS] = (it[EXCLUDED_PATHS] ?: emptySet()) - path
    }

    suspend fun excludePackage(packageName: String) = put {
        it[EXCLUDED_PACKAGES] = (it[EXCLUDED_PACKAGES] ?: emptySet()) + packageName
    }

    suspend fun includePackage(packageName: String) = put {
        it[EXCLUDED_PACKAGES] = (it[EXCLUDED_PACKAGES] ?: emptySet()) - packageName
    }

    suspend fun clearExclusions() = put {
        it[EXCLUDED_PATHS] = emptySet()
        it[EXCLUDED_PACKAGES] = emptySet()
    }

    private suspend fun put(block: (MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    private companion object {
        val OLD_FILE_DAYS = intPreferencesKey("old_file_days")
        val LARGE_FILE_BYTES = longPreferencesKey("large_file_bytes")
        val SCREENSHOT_DAYS = intPreferencesKey("screenshot_days")
        val UNUSED_APP_DAYS = intPreferencesKey("unused_app_days")
        val HAPTICS = booleanPreferencesKey("haptics")
        val MOTION = stringPreferencesKey("motion")
        val ONBOARDING = booleanPreferencesKey("onboarding_complete")
        val EXCLUDED_PATHS = stringSetPreferencesKey("excluded_paths")
        val EXCLUDED_PACKAGES = stringSetPreferencesKey("excluded_packages")
    }
}
