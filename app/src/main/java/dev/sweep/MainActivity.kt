package dev.sweep

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.animation.AccelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.sweep.core.android.SweepNotifications
import dev.sweep.ui.SweepAppRoot
import dev.sweep.ui.SweepViewModel

/**
 * Sweep is a single-activity app. Both of its special permissions are granted from Android's own
 * Settings screens, so the environment is re-read on every resume rather than assumed.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SweepViewModel by viewModels()

    /** Set when a reminder was tapped, so the app opens on the screen the reminder was about. */
    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installLaunch()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingDestination = intent?.destination()

        setContent {
            SweepAppRoot(
                viewModel = viewModel,
                openDestination = pendingDestination,
                onDestinationHandled = { pendingDestination = null },
            )
        }
    }

    /**
     * Android's splash screen is the whole launch sequence.
     *
     * It is held only until settings and the first storage reading exist, which is what Home needs
     * to draw its real first frame, and never longer than [MAX_HOLD_MS] whatever happens. Then the
     * mark is swept off to the right as the splash fades, a quarter of a second, the same gesture
     * as everything else in the app. With reduced motion, or animations off in Android, the splash
     * simply goes. A warm or hot start that shows no splash has nothing to replay.
     */
    private fun installLaunch() {
        val splash = installSplashScreen()
        val started = SystemClock.uptimeMillis()
        splash.setKeepOnScreenCondition {
            val state = viewModel.state.value
            val ready = state.settingsLoaded && state.environmentLoaded
            !ready && SystemClock.uptimeMillis() - started < MAX_HOLD_MS
        }
        splash.setOnExitAnimationListener { provider ->
            val reduced = viewModel.state.value.reducedMotion || systemAnimationsOff()
            if (reduced) {
                provider.remove()
                return@setOnExitAnimationListener
            }
            val travel = 28f * resources.displayMetrics.density
            runCatching {
                provider.iconView.animate()
                    .translationX(travel)
                    .alpha(0f)
                    .setDuration(220L)
                    .setInterpolator(AccelerateInterpolator(1.4f))
                    .start()
            }
            provider.view.animate()
                .alpha(0f)
                .setStartDelay(70L)
                .setDuration(200L)
                .withEndAction { provider.remove() }
                .start()
        }
    }

    private fun systemAnimationsOff(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)

    /** The activity is single-top, so a tapped reminder arrives here rather than in onCreate. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingDestination = intent.destination()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironment()
    }

    private fun Intent.destination(): String? = getStringExtra(SweepNotifications.EXTRA_DESTINATION)

    private companion object {
        const val MAX_HOLD_MS = 700L
    }
}
