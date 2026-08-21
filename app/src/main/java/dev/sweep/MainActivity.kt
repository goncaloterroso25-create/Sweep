package dev.sweep

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.sweep.core.android.SweepNotifications
import dev.sweep.ui.SweepAppRoot
import dev.sweep.ui.SweepViewModel

/**
 * Sweep is a single-activity app. Both of the permissions it uses are granted from Android's own
 * Settings screens, so the environment is re-read on every resume rather than assumed.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SweepViewModel by viewModels()

    /** Set when a reminder was tapped, so the app opens on the screen the reminder was about. */
    private var pendingDestination by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Android's own splash window, which is the whole launch sequence. Sweep's brand moment
        // happens in the first frame of content instead, where it costs nothing.
        installSplashScreenIfAvailable()
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

    private fun Intent.destination(): String? =
        getStringExtra(SweepNotifications.EXTRA_DESTINATION)

    /**
     * The splash screen library is not a dependency, so this is deliberately a no-op hook: the
     * platform's own launch theme already shows the icon on a matching background, which is the
     * fastest possible cold start. It exists as the single place to change that decision.
     */
    private fun installSplashScreenIfAvailable() = Unit
}
