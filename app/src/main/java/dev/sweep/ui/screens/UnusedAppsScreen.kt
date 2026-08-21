package dev.sweep.ui.screens

import android.content.ActivityNotFoundException
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.model.AgeFormat
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.InstalledApp
import dev.sweep.core.scan.UnusedAppPolicy
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.EmptyState
import dev.sweep.ui.components.NoticeCard
import dev.sweep.ui.components.RestrictedSettingsHelp
import dev.sweep.ui.components.RevealIn
import dev.sweep.ui.components.SectionLabel
import dev.sweep.ui.components.SegmentedChoice
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.SweepTopBar
import dev.sweep.ui.components.pressable
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.springGentle
import dev.sweep.ui.theme.sweepTween

/**
 * Apps the device says you have stopped opening, and separately the apps it says nothing about.
 *
 * The split is the point. Only an app with a real last-used date older than the threshold is
 * called unused. Everything Android stayed quiet about sits under "Usage unknown", counted
 * nowhere and recommended for nothing.
 *
 * Sweep cannot uninstall anything itself. It opens Android's own confirmation and then re-reads
 * the package list, so a removal is only reported once Android agrees the package is gone.
 */
@Composable
fun UnusedAppsScreen(
    state: SweepUiState,
    loadIcon: suspend (String) -> Drawable?,
    onBack: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onExcludeApp: (String) -> Unit,
    onUninstallReturned: (packageName: String, label: String, resultCode: Int) -> Unit,
    onUninstallUnavailable: (label: String) -> Unit,
    onDismissNotice: () -> Unit,
    onUsageAccessRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val apps = state.apps
    val threshold = state.settings.unusedAppThresholdDays

    // Which app the system dialog is currently about, so its result can be attributed correctly.
    var pending by remember { mutableStateOf<InstalledApp?>(null) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        pending?.let { onUninstallReturned(it.packageName, it.label, result.resultCode) }
        pending = null
    }

    /** Tries each uninstall route in turn, since OEM builds answer different ones. */
    fun startUninstall(app: InstalledApp) {
        pending = app
        for (intent in SystemFlows.uninstallIntents(app.packageName)) {
            try {
                uninstallLauncher.launch(intent)
                return
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
        pending = null
        onUninstallUnavailable(app.label)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding()
    ) {
        SweepTopBar(
            title = "Unused apps",
            subtitle = apps?.takeIf { it.hasUsageAccess }?.let {
                if (it.unused.isEmpty()) "Nothing confirmed unused"
                else "${it.unused.size} unused, ${ByteFormat.short(it.reclaimableBytes)}"
            },
            onBack = onBack,
        )

        AppNotice(text = state.appNotice, onDismiss = onDismissNotice)

        if (!state.permissions.hasUsageAccess) {
            Column(Modifier.padding(20.dp)) {
                NoticeCard(
                    title = "Find apps you forgot",
                    body = "Usage Access tells Sweep when each app was last opened, and how much " +
                        "space it uses. Android grants it from its own settings screen.",
                    icon = SweepIcons.Clock,
                    tint = colors.info,
                    action = {
                        SweepButton(
                            text = "Open Usage Access",
                            onClick = {
                                onUsageAccessRequested()
                                SystemFlows.launchFirstAvailable(
                                    context,
                                    SweepPermissions.usageAccessIntents(context),
                                )
                            },
                        )
                    },
                )
                Spacer(Modifier.height(6.dp))
                RestrictedSettingsHelp(autoExpand = state.usageAccessRefused)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Only this screen and app cache sizes need it. Everything else works " +
                        "without it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                )
            }
            return@Column
        }

        ThresholdRow(selected = threshold, onSelect = onThresholdChange)

        if (state.appsLoading && apps == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            }
            return@Column
        }

        if (apps == null || (apps.unused.isEmpty() && apps.unknownUsage.isEmpty())) {
            EmptyState(
                title = "All in use",
                body = "Nothing has been sitting untouched for $threshold days or more.",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (apps.unused.isNotEmpty()) {
                item {
                    SectionLabel(
                        text = "Not opened in $threshold+ days",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                    )
                }
                itemsIndexed(apps.unused, key = { _, it -> "unused:" + it.app.packageName }) { index, unused ->
                    RevealIn(index = index) {
                        AppRow(
                            app = unused.app,
                            detail = "Last opened ${AgeFormat.describe(unused.daysSinceUse)}",
                            loadIcon = loadIcon,
                            actionText = "Uninstall",
                            actionColor = colors.danger,
                            onAction = { startUninstall(unused.app) },
                            onExclude = { onExcludeApp(unused.app.packageName) },
                        )
                    }
                }
            } else if (apps.hasAnyUsageHistory) {
                item {
                    Text(
                        text = "Every app with a usage record has been opened in the last " +
                            "$threshold days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMute,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                }
            }

            if (apps.unknownUsage.isNotEmpty()) {
                item {
                    Column(Modifier.padding(top = 14.dp)) {
                        SectionLabel(
                            text = "Usage unknown",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "Android didn't provide enough usage history for these apps, " +
                                "so Sweep can't tell whether you use them. They aren't counted " +
                                "above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMute,
                            modifier = Modifier.padding(start = 6.dp, end = 6.dp, bottom = 10.dp),
                        )
                    }
                }
                items(apps.unknownUsage, key = { "unknown:" + it.app.packageName }) { unknown ->
                    AppRow(
                        app = unknown.app,
                        detail = "No usage on record",
                        loadIcon = loadIcon,
                        actionText = "App info",
                        actionColor = colors.textMute,
                        onAction = {
                            SystemFlows.launchFirstAvailable(
                                context,
                                listOf(SystemFlows.appDetailsIntent(unknown.app.packageName)),
                            )
                        },
                        onExclude = { onExcludeApp(unknown.app.packageName) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item {
                Text(
                    text = "Android runs the uninstall. Sweep checks afterwards whether the app " +
                        "is really gone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp),
                )
            }
        }
    }
}

/** The result of the last uninstall attempt. Rises in, and only ever states a verified outcome. */
@Composable
private fun AppNotice(text: String?, onDismiss: () -> Unit) {
    val colors = Sweep.colors
    AnimatedVisibility(
        visible = text != null,
        enter = expandVertically(springGentle()) + fadeIn(sweepTween(180)),
        exit = shrinkVertically(springGentle()) + fadeOut(sweepTween(140)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surfaceHigh)
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SweepIcons.Close,
                    contentDescription = "Dismiss",
                    tint = colors.textMute,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun ThresholdRow(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        SectionLabel("Unused for at least")
        Spacer(Modifier.height(9.dp))
        SegmentedChoice(
            options = UnusedAppPolicy.THRESHOLD_CHOICES,
            selected = selected,
            label = { "$it days" },
            onSelect = onSelect,
        )
    }
}

/**
 * One app, with the two facts that decide whether it should go.
 *
 * How long ago it was last opened and how much space it takes are the entire point of this
 * screen, so neither is allowed to truncate. Previously both were crammed into one ellipsised
 * line and the size was the half that disappeared, which meant the row showed a reason to
 * uninstall without the payoff. Now the name ellipsises, the two values wrap onto a second line
 * when they have to, and the size is set in the text colour so it reads as a figure rather than
 * as a caption.
 *
 * On a narrow screen, or at a large font scale, the actions move to their own line instead of
 * squeezing the values into a column a few characters wide.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(
    app: InstalledApp,
    detail: String,
    loadIcon: suspend (String) -> Drawable?,
    actionText: String,
    actionColor: Color,
    onAction: () -> Unit,
    onExclude: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val shape = MaterialTheme.shapes.medium

    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        icon = runCatching {
            loadIcon(app.packageName)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
        }.getOrNull()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.8f), shape)
            .padding(12.dp),
    ) {
        val stacked = maxWidth < STACK_ACTIONS_BELOW

        val appIcon: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(colors.surfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                icon?.let {
                    Image(bitmap = it, contentDescription = null, modifier = Modifier.size(30.dp))
                }
            }
        }

        val details: @Composable (Modifier) -> Unit = { columnModifier ->
            Column(columnModifier) {
                Text(
                    text = app.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                // Wraps rather than truncates. Both values survive on any width.
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMute,
                    )
                    if (app.totalBytes > 0) {
                        Text(
                            text = ByteFormat.short(app.totalBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.text,
                        )
                    }
                }
            }
        }

        val actions: @Composable RowScope.() -> Unit = {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onExclude),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    SweepIcons.Exclude,
                    contentDescription = "Don't suggest ${app.label} again",
                    tint = colors.textFaint,
                    modifier = Modifier.size(18.dp),
                )
            }
            SweepTextButton(text = actionText, onClick = onAction, color = actionColor)
        }

        if (stacked) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    appIcon()
                    Spacer(Modifier.width(13.dp))
                    details(Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) { actions() }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                appIcon()
                Spacer(Modifier.width(13.dp))
                details(Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                actions()
            }
        }
    }
}

/** Below this the actions get their own line, so the two values keep their space. */
private val STACK_ACTIONS_BELOW = 300.dp

/** App icons are decoded at list-row size, not at their intrinsic adaptive-icon resolution. */
internal const val ICON_PX = 96
