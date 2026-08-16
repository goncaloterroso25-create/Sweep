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
import dev.sweep.core.model.UnusedApp
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
 * Apps the device says you have stopped opening.
 *
 * Sweep cannot uninstall anything itself — no third-party app can — so the action here opens
 * Android's own uninstall dialog and then re-reads the package list to report what actually
 * happened. Nothing is counted as removed unless the package is genuinely gone.
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
                "${it.unused.size} unused · ${ByteFormat.short(it.reclaimableBytes)}"
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

        ThresholdRow(
            selected = state.settings.unusedAppThresholdDays,
            onSelect = onThresholdChange,
        )

        if (state.appsLoading && apps == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            }
            return@Column
        }

        if (apps == null || apps.unused.isEmpty()) {
            EmptyState(
                title = "All in use.",
                body = "Nothing has been sitting untouched for " +
                    "${state.settings.unusedAppThresholdDays} days or more.",
            )
            return@Column
        }

        LazyColumn(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp, end = 14.dp, top = 8.dp, bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            items(apps.unused, key = { it.app.packageName }) { unused ->
                AppRow(
                    unused = unused,
                    loadIcon = loadIcon,
                    onUninstall = {
                        uninstallLauncher.launch(
                            SystemFlows.uninstallIntent(unused.app.packageName)
                        )
                    },
                    onExclude = { onExcludeApp(unused.app.packageName) },
                    modifier = Modifier.animateItem(),
                )
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
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    unused: UnusedApp,
    loadIcon: suspend (String) -> Drawable?,
    onUninstall: () -> Unit,
    onExclude: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val app = unused.app
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
                    append(
                        unused.daysSinceUse
                            ?.let { "Last opened ${AgeFormat.describe(it)}" }
                            ?: "No usage on record"
                    )
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
                .size(36.dp)
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

        SweepTextButton(text = "Uninstall", onClick = onUninstall, color = colors.danger)
    }
}

/** App icons are decoded at list-row size, not at their intrinsic adaptive-icon resolution. */
internal const val ICON_PX = 96
