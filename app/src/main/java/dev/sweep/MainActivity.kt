package dev.sweep

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import dev.sweep.ui.SweepAppRoot
import dev.sweep.ui.SweepViewModel

/**
 * Sweep is a single-activity app. Both of the permissions it uses are granted from Android's own
 * Settings screens, so the environment is re-read on every resume rather than assumed.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: SweepViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { SweepAppRoot(viewModel) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshEnvironment()
    }
}
