package dev.sweep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.ScanPhase
import dev.sweep.ui.Stage
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.AnimatedBytes
import dev.sweep.ui.components.AppearIn
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.CategoryCard
import dev.sweep.ui.components.EmptyState
import dev.sweep.ui.components.NoticeCard
import dev.sweep.ui.components.SectionLabel
import dev.sweep.ui.components.StorageMeter
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.UtilityCard
import dev.sweep.ui.components.pressable
import dev.sweep.ui.theme.Grotesk
import dev.sweep.ui.theme.Sweep
import java.io.File
import java.util.Locale

/**
 * The whole product in one screen: understand, scan, see what was found, act.
 *
 * The hero figure changes meaning with the stage — free space before a scan, reclaimable space
 * after one — and the block field underneath is the same object throughout, so the transition
 * from "how full am I" to "what can go" never costs a screen change.
 */
@Composable
fun HomeScreen(
    state: SweepUiState,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onRescan: () -> Unit,
    onOpenCategory: (CleanupCategory) -> Unit,
    onOpenApps: () -> Unit,
    onOpenCache: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val scroll = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .verticalScroll(scroll)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Sweep",
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Grotesk),
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = colors.textMute,
                    modifier = Modifier.size(21.dp),
                )
            }
        }

        Spacer(Modifier.height(30.dp))
        Hero(state)
        Spacer(Modifier.height(22.dp))

        StorageMeter(
            usedFraction = state.storage.usedFraction,
            reclaimFraction = meterReclaimFraction(state),
            scanning = state.stage == Stage.SCANNING,
            contentDescription = meterDescription(state),
        )

        Spacer(Modifier.height(20.dp))
        Actions(
            state = state,
            onScan = onScan,
            onCancelScan = onCancelScan,
            onRescan = onRescan,
        )

        if (!state.permissions.canScanFiles) {
            Spacer(Modifier.height(22.dp))
            NoticeCard(
                title = "See what can go",
                body = "Sweep needs file access to find old downloads, duplicates and installers. " +
                    "Everything stays on your device — there is no account and no network.",
                icon = Icons.Outlined.Lock,
                tint = colors.accent,
                action = {
                    SweepButton(
                        text = "Grant file access",
                        onClick = {
                            SystemFlows.launchFirstAvailable(
                                context,
                                SweepPermissions.fileAccessIntents(context),
                            )
                        },
                    )
                },
            )
        }

        val summaries = state.result?.summaries()?.filterNot { it.isEmpty }.orEmpty()
        val showEmptyState = state.stage == Stage.RESULTS && summaries.isEmpty()

        if (summaries.isNotEmpty() || state.stage == Stage.SCANNING) {
            Spacer(Modifier.height(30.dp))
            SectionLabel(if (state.stage == Stage.SCANNING) "Found so far" else "What Sweep found")
            Spacer(Modifier.height(12.dp))

            val live = if (state.stage == Stage.SCANNING) {
                state.progress?.partial?.filterNot { it.isEmpty }.orEmpty()
            } else {
                summaries
            }

            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                live.forEachIndexed { index, summary ->
                    // Keyed so a category keeps its identity as the scan reshuffles the list.
                    androidx.compose.runtime.key(summary.category) {
                        AppearIn(index = index) {
                            CategoryCard(
                                summary = summary,
                                onClick = { onOpenCategory(summary.category) },
                            )
                        }
                    }
                }
            }
        }

        if (showEmptyState) {
            Spacer(Modifier.height(16.dp))
            EmptyState(
                title = "You're clear.",
                body = "Nothing obvious is wasting space. Sweep looked through " +
                    "${state.result?.filesScanned.orZero().formatted()} files.",
            )
        }

        Spacer(Modifier.height(28.dp))
        SectionLabel("Apps")
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            UtilityCard(
                title = "Unused apps",
                subtitle = appsSubtitle(state),
                trailing = state.apps?.reclaimableBytes
                    ?.takeIf { it > 0 && state.apps?.hasUsageAccess == true }
                    ?.let { ByteFormat.short(it) },
                icon = Icons.Outlined.Apps,
                tint = colors.info,
                onClick = onOpenApps,
            )
            UtilityCard(
                title = "App caches",
                subtitle = cacheSubtitle(state),
                trailing = state.apps?.totalCacheBytes
                    ?.takeIf { it > 0 }
                    ?.let { ByteFormat.short(it) },
                icon = Icons.Outlined.Cached,
                tint = colors.categoryTint(CleanupCategory.SCREENSHOTS),
                onClick = onOpenCache,
            )
        }

        Spacer(Modifier.height(120.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun Hero(state: SweepUiState) {
    val colors = Sweep.colors
    val storage = state.storage
    val result = state.result

    val eyebrow: String
    val value: Long
    val support: String

    when {
        state.stage == Stage.SCANNING -> {
            eyebrow = "Scanning"
            value = state.progress?.partial?.sumOf { it.suggestedBytes } ?: 0L
            support = "${state.progress?.filesSeen.orZero().formatted()} files checked"
        }

        state.stage == Stage.RESULTS && result != null -> {
            val suggested = result.suggestedBytes
            if (suggested > 0L) {
                eyebrow = "Ready to clean"
                value = suggested
                support = "${ByteFormat.short(storage.freeBytes)} free of " +
                    ByteFormat.short(storage.totalBytes)
            } else if (state.hasFindings) {
                eyebrow = "Found for review"
                value = result.totalFoundBytes
                support = "Nothing pre-selected — Sweep isn't confident enough to choose for you"
            } else {
                eyebrow = "Free space"
                value = storage.freeBytes
                support = "of ${ByteFormat.short(storage.totalBytes)}"
            }
        }

        else -> {
            eyebrow = "Free space"
            value = storage.freeBytes
            support = if (storage.totalBytes > 0) {
                "of ${ByteFormat.short(storage.totalBytes)} · " +
                    "${ByteFormat.short(storage.usedBytes)} used"
            } else {
                state.scanRootLabel
            }
        }
    }

    Column {
        SectionLabel(eyebrow)
        Spacer(Modifier.height(10.dp))
        AnimatedBytes(bytes = value)
        Spacer(Modifier.height(8.dp))
        Text(
            text = support,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMute,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Actions(
    state: SweepUiState,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onRescan: () -> Unit,
) {
    val colors = Sweep.colors
    when (state.stage) {
        Stage.SCANNING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = when (state.progress?.phase) {
                            ScanPhase.HASHING -> "Comparing files for duplicates"
                            ScanPhase.FINISHING -> "Finishing up"
                            else -> "Looking through storage"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.text,
                    )
                    Text(
                        text = state.progress?.currentDirectory?.let { File(it).name }
                            ?: state.scanRootLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(12.dp))
                SweepButton(text = "Stop", onClick = onCancelScan, tone = ButtonTone.Neutral)
            }
        }

        Stage.RESULTS, Stage.CLEANING, Stage.DONE -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SweepTextButton(
                    text = "Scan again",
                    onClick = onRescan,
                    icon = Icons.Outlined.FolderOpen,
                    modifier = Modifier.padding(start = 0.dp),
                )
                if (state.scanWasCancelled) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Stopped early — results are partial",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint,
                    )
                }
            }
        }

        else -> {
            SweepButton(
                text = "Scan storage",
                onClick = onScan,
                enabled = state.permissions.canScanFiles,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun meterReclaimFraction(state: SweepUiState): Float {
    val total = state.storage.totalBytes
    if (total <= 0L) return 0f
    val bytes = when (state.stage) {
        Stage.SCANNING -> state.progress?.partial?.sumOf { it.suggestedBytes } ?: 0L
        // Once there are results the meter tracks the live selection, so choosing more files
        // visibly grows the space that is about to come back.
        Stage.RESULTS, Stage.CLEANING, Stage.DONE -> state.selectedBytes
        else -> 0L
    }
    return (bytes.toFloat() / total).coerceIn(0f, 1f)
}

private fun meterDescription(state: SweepUiState): String {
    val storage = state.storage
    if (storage.totalBytes <= 0L) return "Storage usage unavailable"
    return "${ByteFormat.short(storage.usedBytes)} used of " +
        "${ByteFormat.short(storage.totalBytes)}, " +
        "${ByteFormat.short(storage.freeBytes)} free"
}

private fun appsSubtitle(state: SweepUiState): String {
    val apps = state.apps
    return when {
        !state.permissions.hasUsageAccess -> "Needs Usage Access to see when apps were last opened"
        apps == null -> "Check which apps you've stopped opening"
        apps.unused.isEmpty() -> "Nothing unused in the last ${apps.thresholdDays} days"
        apps.unused.size == 1 -> "1 app unused for ${apps.thresholdDays}+ days"
        else -> "${apps.unused.size} apps unused for ${apps.thresholdDays}+ days"
    }
}

private fun cacheSubtitle(state: SweepUiState): String = when {
    !state.permissions.hasUsageAccess -> "Needs Usage Access to measure cached data"
    state.apps == null -> "See what apps are caching"
    else -> "Android clears these — Sweep shows you where the space is"
}

private fun Int?.orZero(): Int = this ?: 0

private fun Int.formatted(): String = String.format(Locale.getDefault(), "%,d", this)
