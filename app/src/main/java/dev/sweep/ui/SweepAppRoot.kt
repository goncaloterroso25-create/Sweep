package dev.sweep.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dev.sweep.core.model.CleanupCategory
import dev.sweep.ui.components.ConfirmCleanSheet
import dev.sweep.ui.components.SelectionBar
import dev.sweep.ui.components.rememberHaptics
import dev.sweep.ui.screens.CacheScreen
import dev.sweep.ui.screens.CategoryScreen
import dev.sweep.ui.screens.CompletionScreen
import dev.sweep.ui.screens.HomeScreen
import dev.sweep.ui.screens.OnboardingScreen
import dev.sweep.ui.screens.SettingsScreen
import dev.sweep.ui.screens.UnusedAppsScreen
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepTheme

private object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CATEGORY = "category/{category}"
    const val APPS = "apps"
    const val CACHE = "cache"
    const val SETTINGS = "settings"

    fun category(category: CleanupCategory) = "category/${category.name}"
}

/**
 * The whole navigation graph — six destinations, no tab bar.
 *
 * The selection toolbar and the confirmation sheet live above the graph rather than inside any
 * one screen, so a selection survives moving between Home and a category, and there is exactly
 * one path to a deletion no matter where the user started.
 */
@Composable
fun SweepAppRoot(viewModel: SweepViewModel) {
    val state by viewModel.state.collectAsState()

    SweepTheme(motionPreference = state.settings.motion) {
        val colors = Sweep.colors

        if (!state.settingsLoaded) {
            Box(Modifier.fillMaxSize().background(colors.base))
            return@SweepTheme
        }

        val navController = rememberNavController()
        val haptics = rememberHaptics(state.settings.hapticsEnabled)
        var confirming by remember { mutableStateOf(false) }

        val startDestination = remember {
            if (state.settings.onboardingComplete) Routes.HOME else Routes.ONBOARDING
        }

        Box(Modifier.fillMaxSize().background(colors.base)) {
            SweepNavHost(
                navController = navController,
                startDestination = startDestination,
                state = state,
                viewModel = viewModel,
                haptics = haptics,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
            ) {
                SelectionBar(
                    visible = state.stage == Stage.RESULTS && state.selectedCount > 0,
                    count = state.selectedCount,
                    bytes = state.selectedBytes,
                    onClear = { viewModel.clearSelection() },
                    onClean = {
                        haptics.select()
                        confirming = true
                    },
                )
            }

            AnimatedVisibility(
                visible = state.stage == Stage.DONE && state.cleanup != null,
                enter = fadeIn(tween(220)) + scaleIn(initialScale = 1.03f, animationSpec = tween(320)),
                exit = fadeOut(tween(180)),
            ) {
                state.cleanup?.let { summary ->
                    CompletionScreen(
                        summary = summary,
                        storage = state.storage,
                        haptics = haptics,
                        onDone = {
                            viewModel.dismissCompletion()
                            navController.popBackStack(Routes.HOME, inclusive = false)
                        },
                    )
                }
            }
        }

        if (confirming) {
            ConfirmCleanSheet(
                items = state.selectedItems,
                onConfirm = {
                    confirming = false
                    haptics.confirm()
                    viewModel.runCleanup()
                },
                onDismiss = { confirming = false },
            )
        }
    }
}

@Composable
private fun SweepNavHost(
    navController: NavHostController,
    startDestination: String,
    state: SweepUiState,
    viewModel: SweepViewModel,
    haptics: dev.sweep.ui.components.SweepHaptics,
) {
    val reduced = LocalReducedMotion.current
    val shift: (Int) -> Int = { it / 6 }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            if (reduced) fadeIn(tween(1))
            else slideInHorizontally(tween(300), shift) + fadeIn(tween(190))
        },
        exitTransition = {
            if (reduced) fadeOut(tween(1))
            else scaleOut(tween(280), targetScale = 0.97f) + fadeOut(tween(160))
        },
        popEnterTransition = {
            if (reduced) fadeIn(tween(1))
            else scaleIn(tween(300), initialScale = 0.97f) + fadeIn(tween(190))
        },
        popExitTransition = {
            if (reduced) fadeOut(tween(1))
            else slideOutHorizontally(tween(280)) { shift(it) } + fadeOut(tween(170))
        },
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                permissions = state.permissions,
                onPermissionsChanged = { viewModel.refreshEnvironment() },
                onContinue = {
                    viewModel.completeOnboarding()
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                state = state,
                onScan = {
                    haptics.select()
                    viewModel.startScan()
                },
                onCancelScan = { viewModel.cancelScan() },
                onRescan = {
                    haptics.select()
                    viewModel.startScan()
                },
                onOpenCategory = { navController.navigate(Routes.category(it)) },
                onOpenApps = {
                    viewModel.loadApps()
                    navController.navigate(Routes.APPS)
                },
                onOpenCache = {
                    viewModel.loadApps()
                    navController.navigate(Routes.CACHE)
                },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(Routes.CATEGORY) { entry ->
            val category = entry.arguments
                ?.getString("category")
                ?.let { name -> CleanupCategory.entries.firstOrNull { it.name == name } }

            if (category == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                CategoryScreen(
                    category = category,
                    state = state,
                    haptics = haptics,
                    onBack = { navController.popBackStack() },
                    onToggle = viewModel::toggle,
                    onSelectSafe = { viewModel.selectSafe(category) },
                    onSelectAll = {
                        viewModel.setSelected(state.itemsIn(category).map { it.path }, true)
                    },
                    onClearSelection = { viewModel.clearSelection(category) },
                    onExclude = viewModel::excludeItem,
                )
            }
        }

        composable(Routes.APPS) {
            UnusedAppsScreen(
                state = state,
                loadIcon = viewModel::appIcon,
                onBack = { navController.popBackStack() },
                onThresholdChange = viewModel::setUnusedAppThreshold,
                onExcludeApp = viewModel::excludeApp,
                onUninstallReturned = { viewModel.verifyUninstall() },
            )
        }

        composable(Routes.CACHE) {
            CacheScreen(
                state = state,
                loadIcon = viewModel::appIcon,
                onBack = { navController.popBackStack() },
                onClearOwnCache = { viewModel.clearOwnCache() },
                onRefreshOwnCache = { viewModel.refreshOwnCache() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                state = state,
                onBack = { navController.popBackStack() },
                onOldFileThreshold = viewModel::setOldFileThreshold,
                onLargeFileThreshold = viewModel::setLargeFileThreshold,
                onScreenshotThreshold = viewModel::setScreenshotThreshold,
                onUnusedAppThreshold = viewModel::setUnusedAppThreshold,
                onHaptics = viewModel::setHaptics,
                onMotion = viewModel::setMotion,
                onClearExclusions = { viewModel.clearExclusions() },
            )
        }
    }
}
