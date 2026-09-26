package dev.sweep.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.sweep.core.android.SweepNotifications
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.scan.SafetyPolicy
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.components.rememberHaptics
import dev.sweep.ui.screens.AppsScreen
import dev.sweep.ui.screens.AppsTab
import dev.sweep.ui.screens.DeleteSheet
import dev.sweep.ui.screens.HomeScreen
import dev.sweep.ui.screens.ReviewScreen
import dev.sweep.ui.screens.SettingsScreen
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion
import dev.sweep.ui.theme.SweepTheme

private object Routes {
    const val HOME = "home"
    const val REVIEW = "review/{category}"
    const val APPS = "apps/{tab}"
    const val SETTINGS = "settings"

    fun review(category: CleanupCategory) = "review/${category.name}"
    fun apps(tab: AppsTab) = "apps/${tab.name}"
}

/**
 * Four destinations and one sheet.
 *
 * Home is where a session starts and ends. A category opens for review, Apps and Settings are
 * their own places, and the delete sheet lives above all of them, so there is exactly one path to
 * a deletion however the user got there. After a delete the app returns to Home, because that is
 * where the storage picture is, and the point of deleting was to change it.
 */
@Composable
fun SweepAppRoot(
    viewModel: SweepViewModel,
    /** Set when a reminder was tapped. Consumed once, then cleared. */
    openDestination: String? = null,
    onDestinationHandled: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()

    SweepTheme(motionPreference = state.settings.motion) {
        val colors = Sweep.colors
        // The splash screen stays up until settings have loaded, so nothing is drawn with the
        // wrong motion preference and there is never a blank frame to cover here.
        if (!state.settingsLoaded) {
            Box(Modifier.fillMaxSize().background(colors.base))
            return@SweepTheme
        }

        val navController = rememberNavController()
        val haptics = rememberHaptics(state.settings.hapticsEnabled)
        var confirming by rememberSaveable { mutableStateOf(false) }

        StageHaptics(state, haptics)

        LaunchedEffect(openDestination) {
            if (openDestination == SweepNotifications.DESTINATION_UNUSED_APPS) {
                viewModel.loadApps()
                navController.navigate(Routes.apps(AppsTab.UNUSED)) { launchSingleTop = true }
                onDestinationHandled()
            }
        }

        Box(Modifier.fillMaxSize().background(colors.base)) {
            SweepNavHost(
                navController = navController,
                state = state,
                viewModel = viewModel,
                haptics = haptics,
                onReview = { confirming = true },
            )
        }

        if (confirming && state.selection.count > 0) {
            DeleteSheet(
                items = state.selectedItems,
                lastCopies = remember(state.items, state.selectedPaths) {
                    SafetyPolicy.groupsLeftWithoutACopy(state.items, state.selectedPaths)
                },
                onConfirm = {
                    confirming = false
                    haptics.confirm()
                    navController.popBackStack(Routes.HOME, inclusive = false)
                    viewModel.runCleanup()
                },
                onDismiss = { confirming = false },
            )
        }
    }
}

/**
 * Haptics are rationed so the few that remain mean something: one tick when a scan resolves, and
 * one confirmation when a delete has finished and something was actually removed. Nothing while a
 * scan runs; a buzz per discovery would turn a long scan into a pocket full of noise.
 */
@Composable
private fun StageHaptics(state: SweepUiState, haptics: SweepHaptics) {
    var previous by remember { mutableStateOf(state.stage) }
    LaunchedEffect(state.stage) {
        when {
            previous == Stage.SCANNING && state.stage == Stage.RESULTS -> haptics.tick()
            previous == Stage.CLEANING && state.stage == Stage.DONE -> {
                if ((state.cleanup?.filesRemoved ?: 0) > 0) haptics.confirm() else haptics.reject()
            }
        }
        previous = state.stage
    }
}

@Composable
private fun SweepNavHost(
    navController: NavHostController,
    state: SweepUiState,
    viewModel: SweepViewModel,
    haptics: SweepHaptics,
    onReview: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val distance = with(LocalDensity.current) { 36.dp.roundToPx() }

    // Forward travels in from the right, as Android does everywhere; back returns the way it came.
    // Reduced motion keeps the cross-fade and drops the travel.
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) fadeIn(tween(SweepMotion.REDUCED_FADE))
        else slideInHorizontally(spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = IntOffset(1, 1))) { distance } +
            fadeIn(tween(SweepMotion.BASE, 40, SweepMotion.Standard))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced) fadeOut(tween(SweepMotion.REDUCED_FADE))
        else slideOutHorizontally(tween(SweepMotion.BASE, easing = SweepMotion.Standard)) { -distance / 3 } +
            fadeOut(tween(SweepMotion.QUICK))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        if (reduced) fadeIn(tween(SweepMotion.REDUCED_FADE))
        else slideInHorizontally(spring(dampingRatio = 1f, stiffness = 520f, visibilityThreshold = IntOffset(1, 1))) { -distance / 3 } +
            fadeIn(tween(SweepMotion.BASE, 40, SweepMotion.Standard))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        if (reduced) fadeOut(tween(SweepMotion.REDUCED_FADE))
        else slideOutHorizontally(tween(SweepMotion.BASE, easing = SweepMotion.Clear)) { distance } +
            fadeOut(tween(SweepMotion.QUICK))
    }

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = enter,
        exitTransition = exit,
        popEnterTransition = popEnter,
        popExitTransition = popExit,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onScan = viewModel::startScan,
                onStop = viewModel::stopScan,
                onPermissionsChanged = viewModel::refreshEnvironment,
                onOpenCategory = { navController.navigate(Routes.review(it)) },
                onOpenApps = { cache ->
                    viewModel.loadApps()
                    navController.navigate(Routes.apps(if (cache) AppsTab.CACHE else AppsTab.UNUSED))
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onReview = onReview,
                onClearSelection = { viewModel.clearSelection() },
                onDismissReceipt = viewModel::dismissReceipt,
                onLoadApps = { viewModel.loadApps() },
            )
        }

        composable(Routes.REVIEW) { entry ->
            val category = entry.arguments?.getString("category")
                ?.let { name -> CleanupCategory.entries.firstOrNull { it.name == name } }
            if (category == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                ReviewScreen(
                    category = category,
                    state = state,
                    haptics = haptics,
                    onBack = { navController.popBackStack() },
                    onToggle = viewModel::toggle,
                    onSelectSuggested = { viewModel.selectSuggested(category) },
                    onSelectAll = { viewModel.setSelected(state.itemsIn(category).map { it.path }, true) },
                    onClearSelection = { viewModel.clearSelection(category) },
                    onExclude = viewModel::excludeItem,
                    onReview = onReview,
                )
            }
        }

        composable(Routes.APPS) { entry ->
            val tab = entry.arguments?.getString("tab")
                ?.let { name -> AppsTab.entries.firstOrNull { it.name == name } }
                ?: AppsTab.UNUSED
            AppsScreen(
                state = state,
                initialTab = tab,
                loadIcon = viewModel::appIcon,
                onBack = { navController.popBackStack() },
                onThresholdChange = { viewModel.setUnusedAppThreshold(it) },
                onExcludeApp = viewModel::excludeApp,
                onUninstallReturned = viewModel::onUninstallReturned,
                onUninstallUnavailable = viewModel::reportUninstallUnavailable,
                onAppStorageReturned = viewModel::onAppStorageReturned,
                onClearOwnCache = { viewModel.clearOwnCache() },
                onRefreshOwnCache = { viewModel.refreshOwnCache() },
                onDismissNotice = viewModel::dismissAppNotice,
                onUsageAccessRequested = viewModel::noteUsageAccessRequested,
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = state,
                haptics = haptics,
                onBack = { navController.popBackStack() },
                onOldFileThreshold = { viewModel.setOldFileThreshold(it) },
                onLargeFileThreshold = { viewModel.setLargeFileThreshold(it) },
                onScreenshotThreshold = { viewModel.setScreenshotThreshold(it) },
                onUnusedAppThreshold = { viewModel.setUnusedAppThreshold(it) },
                onHaptics = { viewModel.setHaptics(it) },
                onMotion = { viewModel.setMotion(it) },
                onClearExclusions = { viewModel.clearExclusions() },
                onUsageAccessRequested = viewModel::noteUsageAccessRequested,
                onCleanupReminders = { viewModel.setCleanupReminders(it) },
                onUnusedAppReminders = { viewModel.setUnusedAppReminders(it) },
                onReminderThreshold = { viewModel.setReminderThreshold(it) },
            )
        }
    }
}
