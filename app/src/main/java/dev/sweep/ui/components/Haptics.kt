package dev.sweep.ui.components

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Haptics are rationed on purpose: a buzz on every tap stops meaning anything. Sweep uses them
 * for exactly four moments — picking an item, crossing a threshold, confirming a deletion, and
 * finishing one.
 *
 * Device testing showed nothing at all, and the reason is worth stating because it is the same
 * reason most apps get this wrong. [View.performHapticFeedback] is a *request*: the platform
 * silently drops it when the view has haptics disabled, and — more commonly — when the user has
 * turned off touch feedback in Android's own sound settings. Sweep's own switch cannot override
 * that and does not pretend to.
 *
 * So two things changed here. [FLAG_IGNORE_VIEW_SETTING] removes the view-level veto, which is the
 * half Sweep legitimately controls, and every call now returns whether Android accepted it, which
 * is what lets "Test haptic" in Settings report the truth instead of a shrug.
 */
@Immutable
class SweepHaptics(private val view: View, private val enabled: Boolean) {

    fun select(): Boolean = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    fun tick(): Boolean = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun confirm(): Boolean = perform(CONFIRM_CONSTANT)

    fun reject(): Boolean = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    )

    /**
     * Fires the confirmation haptic regardless of Sweep's own switch and reports what happened,
     * so a physical device can be checked in one tap. Bypassing the switch is deliberate: the
     * point of the test is to find out whether the device can do this at all.
     */
    fun test(): HapticProbe = HapticProbe(
        accepted = view.performHapticFeedback(CONFIRM_CONSTANT, FLAG_IGNORE_VIEW_SETTING),
        systemFeedbackEnabled = systemFeedbackEnabled(view.context),
        appSettingEnabled = enabled,
    )

    private fun perform(constant: Int): Boolean {
        if (!enabled) return false
        return view.performHapticFeedback(constant, FLAG_IGNORE_VIEW_SETTING)
    }

    private companion object {
        /**
         * The view-level opt-out only. The system-wide setting is deliberately still honoured —
         * `FLAG_IGNORE_GLOBAL_SETTING` requires a privileged permission and is ignored on modern
         * Android anyway, and overriding a user's accessibility choice would not be Sweep's call.
         */
        val FLAG_IGNORE_VIEW_SETTING = HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING

        val CONFIRM_CONSTANT: Int
            get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                HapticFeedbackConstants.CONFIRM
            } else {
                HapticFeedbackConstants.LONG_PRESS
            }
    }
}

/** What actually happened when Sweep asked for a haptic. Used by Settings → Test haptic. */
data class HapticProbe(
    /** True when Android accepted the request. False means the platform declined it outright. */
    val accepted: Boolean,
    /** Android's own touch-feedback switch. Sweep cannot override this one. */
    val systemFeedbackEnabled: Boolean,
    /** Sweep's switch, which the test deliberately ignores. */
    val appSettingEnabled: Boolean,
)

/**
 * Reads Android's touch-feedback setting. Returns true when it cannot be read, so a device that
 * hides the setting is never wrongly blamed for a silent haptic.
 *
 * The key is marked deprecated and has no replacement an ordinary app can read — the alternatives
 * all require the `VIBRATE` permission, which Sweep does not want for a diagnostic. It still
 * reflects the switch users actually toggle, and a failed read falls back to "assume it's on".
 */
@Suppress("DEPRECATION")
private fun systemFeedbackEnabled(context: Context): Boolean = runCatching {
    Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
}.getOrDefault(true)

@Composable
fun rememberHaptics(enabled: Boolean): SweepHaptics {
    val view = LocalView.current
    return remember(view, enabled) { SweepHaptics(view, enabled) }
}
