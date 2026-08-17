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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
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
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.SweepTopBar
import dev.sweep.ui.components.pressable
import dev.sweep.ui.theme.Sweep
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
                    icon = Icons.Outlined.Schedule,
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
                    Icons.Outlined.Close,
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
    val colors = Sweep.colors
    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
        SectionLabel("Unused for at least")
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            UnusedAppPolicy.THRESHOLD_CHOICES.forEach { days ->
                val active = days == selected
                Text(
                    text = "${days}d",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (active) colors.onAccent else colors.textMute,
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) colors.accent else colors.surface)
                        .border(
                            1.dp,
                            if (active) colors.accent else colors.line,
                            RoundedCornerShape(9.dp),
                        )
                        .pressable(onClick = { onSelect(days) })
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                )
            }
        }
    }
}

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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.8f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

        Spacer(Modifier.width(13.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(detail)
                    if (app.totalBytes > 0) append(", ${ByteFormat.short(app.totalBytes)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .pressable(onClick = onExclude),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Block,
                contentDescription = "Don't suggest ${app.label} again",
                tint = colors.textFaint,
                modifier = Modifier.size(17.dp),
            )
        }

        SweepTextButton(text = actionText, onClick = onAction, color = actionColor)
    }
}

/** App icons are decoded at list-row size, not at their intrinsic adaptive-icon resolution. */
internal const val ICON_PX = 96
