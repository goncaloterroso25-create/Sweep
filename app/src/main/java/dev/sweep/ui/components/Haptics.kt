package dev.sweep.ui.components

import android.os.Build
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
 */
@Immutable
class SweepHaptics(private val view: View, private val enabled: Boolean) {

    fun select() = perform(HapticFeedbackConstants.VIRTUAL_KEY)

    fun tick() = perform(HapticFeedbackConstants.CLOCK_TICK)

    fun confirm() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.LONG_PRESS
    )

    fun reject() = perform(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
    )

    private fun perform(constant: Int) {
        if (!enabled) return
        view.performHapticFeedback(constant)
    }
}

@Composable
fun rememberHaptics(enabled: Boolean): SweepHaptics {
    val view = LocalView.current
    return remember(view, enabled) { SweepHaptics(view, enabled) }
}
