package dev.sweep

import android.app.Application
import dev.sweep.core.SweepRepository
import dev.sweep.core.data.SweepSettingsStore

/**
 * Sweep has exactly two long-lived objects, so they live here instead of behind a DI framework.
 * If this app ever grows a third, that is the moment to reconsider — not before.
 */
class SweepApplication : Application() {

    val repository: SweepRepository by lazy { SweepRepository(this) }
    val settings: SweepSettingsStore by lazy { SweepSettingsStore(this) }
}
