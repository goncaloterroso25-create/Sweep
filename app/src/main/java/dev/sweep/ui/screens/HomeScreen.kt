package dev.sweep.ui.screens

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.sweep.ui.theme.sweepTween
import java.io.File
import java.util.Locale

/**
 * The whole product in one screen: understand, scan, see what was found, act.
 *
 * The hero figure has exactly one meaning and never changes it: this is how much free space the
 * device has right now. Scan figures live below the meter with their own heading, because a number
 * that means "free" in one state and "found" in another is a number nobody can trust.
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
                    .size(44.dp)
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

        Spacer(Modifier.height(28.dp))
        FreeSpaceHero(state)
        Spacer(Modifier.height(20.dp))

        StorageMeter(
            usedFraction = state.storage.usedFraction,
            reclaimFraction = meterReclaimFraction(state),
            scanning = state.stage == Stage.SCANNING,
            contentDescription = meterDescription(state),
        )
        Spacer(Modifier.height(12.dp))
        MeterLegend(state)

        Spacer(Modifier.height(20.dp))
        ScanPanel(
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
            Spacer(Modifier.height(28.dp))
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
                trailing = state.apps
                    ?.takeIf { it.hasUsageAccess && it.reclaimableBytes > 0 }
                    ?.let { ByteFormat.short(it.reclaimableBytes) },
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

/**
 * Free space, always. This figure is read at a glance and acted on, so it is never reused to
 * carry a scan total — the one change that makes the rest of the screen believable.
 */
@Composable
private fun FreeSpaceHero(state: SweepUiState) {
    val colors = Sweep.colors
    val storage = state.storage

    Column {
        SectionLabel("Storage")
        Spacer(Modifier.height(10.dp))
        AnimatedBytes(bytes = storage.freeBytes, suffix = "free")
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (storage.totalBytes > 0) {
                "of ${ByteFormat.short(storage.totalBytes)} · " +
                    "${ByteFormat.short(storage.usedBytes)} used"
            } else {
                state.scanRootLabel.ifBlank { "Storage size unavailable" }
            },
            // Deliberately heavier than body copy: this line is half of the storage picture.
            style = MaterialTheme.typography.titleSmall,
            color = colors.textMute,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Says what the block field is showing right now. Without it the accent blocks mean three
 * different things across the app's states and the user has to infer which one is in play.
 */
@Composable
private fun MeterLegend(state: SweepUiState) {
    val colors = Sweep.colors
    if (state.storage.totalBytes <= 0L) return

    val accentLabel = when (state.stage) {
        Stage.SCANNING -> "Found so far"
        Stage.RESULTS, Stage.CLEANING, Stage.DONE ->
            if (state.selectedCount > 0) "Selected to remove" else null
        else -> null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendKey(colors.textMute.copy(alpha = 0.38f), "Used", filled = true)
        LegendKey(colors.line, "Free", filled = false)
        if (accentLabel != null) LegendKey(colors.accent, accentLabel, filled = true)
    }
}

@Composable
private fun LegendKey(color: Color, label: String, filled: Boolean) {
    val colors = Sweep.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(2.5.dp))
                .then(
                    if (filled) Modifier.background(color)
                    else Modifier.border(1.dp, color, RoundedCornerShape(2.5.dp))
                )
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.textFaint)
    }
}

/**
 * Everything about the scan itself: the action that starts it, the live figures while it runs,
 * and what it found once it is done. Kept as one block so the state change reads as one thing
 * updating rather than the screen rearranging.
 */
@Composable
private fun ScanPanel(
    state: SweepUiState,
    onScan: () -> Unit,
    onCancelScan: () -> Unit,
    onRescan: () -> Unit,
) {
    val colors = Sweep.colors

    Box(Modifier.animateContentSize(animationSpec = sweepTween(260))) {
        when (state.stage) {
            Stage.SCANNING -> {
                val progress = state.progress
                val found = progress?.partial?.sumOf { it.totalBytes } ?: 0L
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Scanning your storage",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = buildString {
                                append("${progress?.filesSeen.orZero().formatted()} files checked")
                                scanLocation(state)?.let { append(" · in $it") }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMute,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "${ByteFormat.short(found)} found for review",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.accent,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    SweepButton(text = "Stop", onClick = onCancelScan, tone = ButtonTone.Neutral)
                }
            }

            Stage.RESULTS, Stage.CLEANING, Stage.DONE -> {
                val result = state.result
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // With no findings the empty state below says everything worth saying, so
                    // the panel gets out of its way and offers only the action.
                    if (result != null && result.items.isNotEmpty()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "${ByteFormat.short(result.totalFoundBytes)} found for review",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.text,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = buildString {
                                    append("across ${result.items.size.formatted()} files")
                                    append(" · ${result.filesScanned.formatted()} checked")
                                    if (state.scanWasCancelled) append(" · stopped early, partial")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMute,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    SweepTextButton(
                        text = "Scan again",
                        onClick = onRescan,
                        icon = Icons.Outlined.FolderOpen,
                    )
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
}

/**
 * Where the walk currently is, when that is something worth saying. During hashing there is no
 * meaningful location, so the phase is named instead of showing a stale folder.
 */
private fun scanLocation(state: SweepUiState): String? {
    val progress = state.progress ?: return null
    return when (progress.phase) {
        ScanPhase.HASHING -> "comparing for duplicates"
        ScanPhase.FINISHING -> "finishing up"
        ScanPhase.WALKING -> progress.currentDirectory
            ?.let { File(it).name }
            ?.takeIf { it.isNotBlank() }
    }
}

private fun meterReclaimFraction(state: SweepUiState): Float {
    val total = state.storage.totalBytes
    if (total <= 0L) return 0f
    val bytes = when (state.stage) {
        // While scanning the accent blocks are "found so far", which is what the panel above
        // reports. Afterwards they track the live selection, so choosing more files visibly
        // grows the space that is about to come back. The legend names whichever is in play.
        Stage.SCANNING -> state.progress?.partial?.sumOf { it.totalBytes } ?: 0L
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
        apps.unused.isEmpty() && apps.unknownUsage.isNotEmpty() ->
            "Nothing confirmed unused · ${apps.unknownUsage.size} with no usage history"
        apps.unused.isEmpty() -> "Nothing unused in the last ${apps.thresholdDays} days"
        apps.unused.size == 1 -> "1 app unused for ${apps.thresholdDays}+ days"
        else -> "${apps.unused.size} apps unused for ${apps.thresholdDays}+ days"
    }
}

private fun cacheSubtitle(state: SweepUiState): String = when {
    !state.permissions.hasUsageAccess -> "Needs Usage Access to measure cached data"
    state.apps == null -> "See what apps are caching"
    else -> "Sweep shows the sizes — Android does the clearing"
}

private fun Int?.orZero(): Int = this ?: 0

private fun Int.formatted(): String = String.format(Locale.getDefault(), "%,d", this)
