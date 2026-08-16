package dev.sweep.ui.screens

import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
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
import dev.sweep.ui.components.SectionLabel
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.SweepTopBar
import dev.sweep.ui.components.pressable
import dev.sweep.ui.theme.Sweep

/**
 * Apps the device says you have stopped opening — and, separately, the apps it says nothing about.
 *
 * The split matters more than anything else on this screen. Only an app with a real last-used date
 * older than the threshold is called unused; everything Android stayed quiet about is listed under
 * "Usage unknown", counted nowhere, and offered no uninstall shortcut.
 *
 * Sweep cannot uninstall anything itself — no third-party app can — so the action opens Android's
 * own uninstall dialog and then re-reads the package list to report what actually happened.
 */
@Composable
fun UnusedAppsScreen(
    state: SweepUiState,
    loadIcon: suspend (String) -> Drawable?,
    onBack: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onExcludeApp: (String) -> Unit,
    onUninstallReturned: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val apps = state.apps
    val threshold = state.settings.unusedAppThresholdDays

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onUninstallReturned() }

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
                else "${it.unused.size} unused · ${ByteFormat.short(it.reclaimableBytes)}"
            },
            onBack = onBack,
        )

        if (!state.permissions.hasUsageAccess) {
            Column(Modifier.padding(20.dp)) {
                NoticeCard(
                    title = "Find apps you forgot",
                    body = "Usage Access lets Sweep tell when your apps were last opened, and how " +
                        "much space each one uses. Sweep reads this on your device and never " +
                        "uploads it. You'll grant it in Android's settings — Sweep can only open " +
                        "that screen for you.",
                    icon = Icons.Outlined.Schedule,
                    tint = colors.info,
                    action = {
                        SweepButton(
                            text = "Open Usage Access",
                            onClick = {
                                SystemFlows.launchFirstAvailable(
                                    context,
                                    SweepPermissions.usageAccessIntents(context),
                                )
                            },
                        )
                    },
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Without it, this screen stays empty. Everything else in Sweep keeps working.",
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
                title = "All in use.",
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
                items(apps.unused, key = { "unused:" + it.app.packageName }) { unused ->
                    AppRow(
                        app = unused.app,
                        detail = "Last opened ${AgeFormat.describe(unused.daysSinceUse)}",
                        loadIcon = loadIcon,
                        actionText = "Uninstall",
                        actionColor = colors.danger,
                        onAction = {
                            uninstallLauncher.launch(
                                SystemFlows.uninstallIntent(unused.app.packageName)
                            )
                        },
                        onExclude = { onExcludeApp(unused.app.packageName) },
                        modifier = Modifier.animateItem(),
                    )
                }
            } else if (apps.hasAnyUsageHistory) {
                // Only true when Android actually reported some history. When it reported none,
                // the note below is the honest explanation and this line would be vacuous.
                item {
                    Text(
                        text = "Every app Android has a usage record for has been opened in the " +
                            "last $threshold days.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMute,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    )
                }
            }

            if (apps.unknownUsage.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(14.dp))
                    UnknownUsageNote(
                        count = apps.unknownUsage.size,
                        everythingUnknown = !apps.hasAnyUsageHistory,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                items(apps.unknownUsage, key = { "unknown:" + it.app.packageName }) { unknown ->
                    AppRow(
                        app = unknown.app,
                        detail = "No usage on record · installed " +
                            AgeFormat.describe(unknown.installedDays),
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
                Spacer(Modifier.height(18.dp))
                Text(
                    text = "Android runs the uninstall. Sweep opens the system dialog and checks " +
                        "afterwards whether the app is really gone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

/**
 * States the limit plainly: no history is not evidence of disuse, so nothing here is counted or
 * recommended. [everythingUnknown] covers the devices that return no usage data at all.
 */
@Composable
private fun UnknownUsageNote(count: Int, everythingUnknown: Boolean) {
    val colors = Sweep.colors
    Column {
        SectionLabel(
            text = "Usage unknown",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surface)
                .border(1.dp, colors.line, MaterialTheme.shapes.medium)
                .padding(14.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.HelpOutline,
                contentDescription = null,
                tint = colors.info,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = buildString {
                    append(
                        if (everythingUnknown) {
                            "Android returned no usage history at all on this device, so Sweep " +
                                "cannot tell when any app was last opened. "
                        } else {
                            "Android gave Sweep no last-opened date for " +
                                (if (count == 1) "this app. " else "these $count apps. ")
                        }
                    )
                    append(
                        "That is not the same as unused — they may be opened daily. They are not " +
                            "counted above and Sweep won't suggest removing them."
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMute,
            )
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
                    if (app.totalBytes > 0) append(" · ${ByteFormat.short(app.totalBytes)}")
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
