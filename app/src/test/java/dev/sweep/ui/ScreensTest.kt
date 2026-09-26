package dev.sweep.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import dev.sweep.core.data.MotionPreference
import dev.sweep.core.model.CleanupCategory
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.screens.AppsScreen
import dev.sweep.ui.screens.AppsTab
import dev.sweep.ui.screens.HomeScreen
import dev.sweep.ui.screens.ReviewScreen
import dev.sweep.ui.screens.SettingsScreen
import dev.sweep.ui.theme.SweepTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the real screens with realistic data at the sizes that break layouts: a 320dp phone,
 * a common phone, a tablet column, the largest font scale, both themes, and reduced motion.
 *
 * The assertions are about facts that must never disappear, not about pixels: the free-space
 * figure, both halves of an unused app's row, the size of a file under review. Run with
 * `-Psweep.shots` to also write every screen to app/build/shots/ for a person to look at.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w900dp-h1800dp-xhdpi")
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val record = System.getProperty("sweep.shots") == "true"

    private fun render(
        width: Dp,
        height: Dp,
        dark: Boolean,
        fontScale: Float = 1f,
        reduced: Boolean = false,
        content: @Composable (SweepHaptics) -> Unit,
    ) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                SweepTheme(
                    darkTheme = dark,
                    motionPreference = if (reduced) MotionPreference.REDUCED else MotionPreference.STANDARD,
                ) {
                    val view = androidx.compose.ui.platform.LocalView.current
                    Box(Modifier.size(width, height).testTag(FRAME)) {
                        content(SweepHaptics(view, enabled = false))
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
    }

    private fun shot(name: String) {
        if (record) compose.onNodeWithTag(FRAME).captureRoboImage("build/shots/$name.png")
    }

    // ---- Home --------------------------------------------------------------------------------

    private fun home(stage: Stage, width: Dp, dark: Boolean, fontScale: Float = 1f, reduced: Boolean = false) =
        render(width, 860.dp, dark, fontScale, reduced) {
            HomeScreen(
                state = Fixtures.state(stage),
                onScan = {}, onStop = {}, onPermissionsChanged = {}, onOpenCategory = {},
                onOpenApps = {}, onOpenSettings = {}, onReview = {}, onClearSelection = {},
                onDismissReceipt = {}, onLoadApps = {},
            )
        }

    @Test fun home_idle_dark() { home(Stage.IDLE, 360.dp, dark = true); compose.onNodeWithText("Scan storage").assertIsDisplayed(); shot("home_idle_dark") }
    @Test fun home_idle_light() { home(Stage.IDLE, 360.dp, dark = false); shot("home_idle_light") }
    @Test fun home_results_dark() { home(Stage.RESULTS, 360.dp, dark = true); compose.onNodeWithText("Review").assertIsDisplayed(); shot("home_results_dark") }
    @Test fun home_results_light() { home(Stage.RESULTS, 360.dp, dark = false); shot("home_results_light") }
    @Test fun home_cleaning_dark() { home(Stage.CLEANING, 360.dp, dark = true); shot("home_cleaning_dark") }
    @Test fun home_receipt_light() { home(Stage.DONE, 360.dp, dark = false); compose.onNodeWithText("Freed").assertIsDisplayed(); shot("home_receipt_light") }
    @Test fun home_reduced_results() { home(Stage.RESULTS, 360.dp, dark = true, reduced = true); shot("home_results_reduced") }
    @Test fun home_tablet() { home(Stage.RESULTS, 840.dp, dark = false); shot("home_tablet") }

    @Test
    fun home_scanning_dark() {
        compose.mainClock.autoAdvance = false
        home(Stage.SCANNING, 360.dp, dark = true)
        compose.mainClock.advanceTimeBy(420)
        compose.onNodeWithText("Scanning").assertExists()
        shot("home_scanning_dark")
    }

    /** The hero figure must fit, whole, on the narrowest phone at the largest font scale. */
    @Test
    fun home_narrow_huge_font() {
        home(Stage.RESULTS, 320.dp, dark = true, fontScale = 2f)
        compose.onNodeWithText("41.2", useUnmergedTree = true).assertIsDisplayed()
        shot("home_results_320_font2")
    }

    // ---- Review ------------------------------------------------------------------------------

    private fun review(category: CleanupCategory, width: Dp, dark: Boolean, fontScale: Float = 1f) =
        render(width, 860.dp, dark, fontScale) { haptics ->
            ReviewScreen(
                category = category,
                state = Fixtures.state(Stage.RESULTS),
                haptics = haptics,
                onBack = {}, onToggle = {}, onSelectSuggested = {}, onSelectAll = {},
                onClearSelection = {}, onExclude = {}, onReview = {},
            )
        }

    @Test fun review_duplicates_dark() { review(CleanupCategory.DUPLICATES, 360.dp, dark = true); compose.onAllNodesWithText("Holiday edit v3.mp4")[0].assertIsDisplayed(); shot("review_duplicates_dark") }
    @Test fun review_downloads_light() { review(CleanupCategory.INSTALLERS, 360.dp, dark = false); shot("review_installers_light") }

    @Test
    fun review_narrow_huge_font_keeps_sizes() {
        review(CleanupCategory.INSTALLERS, 320.dp, dark = true, fontScale = 2f)
        compose.onNodeWithText("212 MB").assertIsDisplayed()
        shot("review_installers_320_font2")
    }

    @Test
    fun preview_pane() {
        render(360.dp, 780.dp, dark = true) {
            dev.sweep.ui.screens.PreviewPane(
                item = Fixtures.items.first(),
                selected = true,
                media = { it },
                onClose = {}, onToggleSelected = {}, onExclude = {},
            )
        }
        compose.onNodeWithText("Kept copy").assertIsDisplayed()
        shot("preview_duplicate_dark")
    }

    // ---- Apps --------------------------------------------------------------------------------

    private fun apps(tab: AppsTab, width: Dp, dark: Boolean, fontScale: Float = 1f) =
        render(width, 900.dp, dark, fontScale) {
            AppsScreen(
                state = Fixtures.state(Stage.IDLE),
                initialTab = tab,
                loadIcon = { null },
                onBack = {}, onThresholdChange = {}, onExcludeApp = {}, onUninstallReturned = { _, _, _ -> },
                onUninstallUnavailable = {}, onAppStorageReturned = { _, _, _ -> }, onClearOwnCache = {},
                onRefreshOwnCache = {}, onDismissNotice = {}, onUsageAccessRequested = {},
            )
        }

    @Test fun apps_unused_dark() { apps(AppsTab.UNUSED, 360.dp, dark = true); shot("apps_unused_dark") }
    @Test fun apps_cache_light() { apps(AppsTab.CACHE, 360.dp, dark = false); shot("apps_cache_light") }

    /** Last opened and size are the two facts the screen exists for. Neither may drop out. */
    @Test
    fun apps_narrow_huge_font_keeps_both_facts() {
        apps(AppsTab.UNUSED, 320.dp, dark = false, fontScale = 2f)
        compose.onNodeWithText("1.2 GB").assertIsDisplayed()
        compose.onNodeWithText("Last opened 10 months ago").assertIsDisplayed()
        shot("apps_unused_320_font2")
    }

    // ---- Settings ----------------------------------------------------------------------------

    private fun settings(width: Dp, dark: Boolean, fontScale: Float = 1f) =
        render(width, 1_700.dp, dark, fontScale) { haptics ->
            SettingsScreen(
                state = Fixtures.state(Stage.IDLE).copy(
                    settings = Fixtures.state(Stage.IDLE).settings.copy(cleanupReminders = true)
                ),
                haptics = haptics,
                onBack = {}, onOldFileThreshold = {}, onLargeFileThreshold = {}, onScreenshotThreshold = {},
                onUnusedAppThreshold = {}, onHaptics = {}, onMotion = {}, onClearExclusions = {},
                onUsageAccessRequested = {}, onCleanupReminders = {}, onUnusedAppReminders = {}, onReminderThreshold = {},
            )
        }

    @Test fun settings_dark() { settings(360.dp, dark = true); shot("settings_dark") }
    @Test fun settings_narrow_huge_font() { settings(320.dp, dark = false, fontScale = 2f); shot("settings_320_font2") }

    private companion object {
        const val FRAME = "frame"
    }
}
