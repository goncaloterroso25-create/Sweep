package dev.sweep.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.model.AgeFormat
import dev.sweep.core.model.CategorySummary
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.ScanPhase
import dev.sweep.ui.CleanupSummary
import dev.sweep.ui.Stage
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.ActionTray
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.ByteFigure
import dev.sweep.ui.components.IconAction
import dev.sweep.ui.components.LedgerRow
import dev.sweep.ui.components.Notice
import dev.sweep.ui.components.NoticeTone
import dev.sweep.ui.components.SectionHeader
import dev.sweep.ui.components.StorageTally
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepWordmark
import dev.sweep.ui.components.TallyFigures
import dev.sweep.ui.components.TextAction
import dev.sweep.ui.components.bytes
import dev.sweep.ui.components.grouped
import dev.sweep.ui.components.isCompact
import dev.sweep.ui.components.plural
import dev.sweep.ui.icon
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepMotion
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.sweepReplace
import dev.sweep.ui.title
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Home: how much space there is, whether anything is worth reviewing, and the one thing to do next.
 *
 * The hierarchy is fixed. Free space is the headline and only ever means free space. The tally
 * under it is the same fact drawn, plus whatever a scan found. Then one panel that owns the
 * primary action and changes in place as the session moves on: allow access, scan, scanning,
 * results, deleting, receipt. What was found follows as a ledger, and apps come last, because
 * they are a different job.
 */
@Composable
fun HomeScreen(
    state: SweepUiState,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onPermissionsChanged: () -> Unit,
    onOpenCategory: (CleanupCategory) -> Unit,
    onOpenApps: (cache: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onReview: () -> Unit,
    onClearSelection: () -> Unit,
    onDismissReceipt: () -> Unit,
    onLoadApps: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val stage = state.stage

    LaunchedEffect(state.permissions.hasUsageAccess) {
        if (state.permissions.hasUsageAccess) onLoadApps()
    }
    // Each scan discovers its categories afresh, so each one gets its entrance again.
    LaunchedEffect(stage) { if (stage == Stage.SCANNING) seenThisProcess.clear() }

    val findings: List<CategorySummary> = remember(state.result, state.progress, stage) {
        val source = when (stage) {
            Stage.SCANNING -> state.progress?.partial.orEmpty()
            Stage.RESULTS, Stage.CLEANING, Stage.DONE -> state.result?.summaries().orEmpty()
            Stage.IDLE -> emptyList()
        }
        val present = source.filterNot { it.isEmpty }
        // While scanning, categories hold the order they were found in so rows do not jump under
        // a moving scan. Once it resolves they sort by size, which is the order worth reading in.
        if (stage == Stage.SCANNING) present else present.sortedByDescending { it.totalBytes }
    }
    val largest = findings.maxOfOrNull { it.totalBytes }?.coerceAtLeast(1L) ?: 1L

    Box(
        modifier
            .fillMaxSize()
            .background(colors.base)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 132.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                Column(Modifier.column()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SweepWordmark(Modifier.weight(1f))
                        IconAction(SweepIcons.Settings, "Settings", onOpenSettings, tint = colors.textMute)
                    }
                }
            }

            item(key = "storage") {
                Column(Modifier.column().padding(top = 26.dp)) {
                    StorageHeadline(state)
                    Spacer(Modifier.height(22.dp))
                    StorageTally(
                        figures = tallyFigures(state),
                        scanning = stage == Stage.SCANNING,
                        activity = state.scanPulse,
                        description = tallyDescription(state),
                    )
                    Spacer(Modifier.height(10.dp))
                    TallyLegend(state)
                }
            }

            item(key = "panel") {
                Box(Modifier.column().padding(top = 28.dp)) {
                    StatusPanel(
                        state = state,
                        onScan = onScan,
                        onStop = onStop,
                        onPermissionsChanged = onPermissionsChanged,
                        onDismissReceipt = onDismissReceipt,
                    )
                }
            }

            if (findings.isNotEmpty()) {
                item(key = "found-header") {
                    SectionHeader(
                        text = if (stage == Stage.SCANNING) "Found so far" else "Found",
                        modifier = Modifier
                            .column()
                            .padding(top = 30.dp)
                            .animateItem(),
                    )
                }
                items(findings, key = { "category:" + it.category.name }) { summary ->
                    Arriving(key = summary.category, modifier = Modifier.animateItem()) {
                        LedgerRow(
                            title = summary.category.title,
                            meta = categoryMeta(summary, state, stage),
                            figure = summary.totalBytes.let { if (summary.category == CleanupCategory.EMPTY_FOLDERS) "-" else it.bytes() },
                            icon = summary.category.icon,
                            magnitude = summary.totalBytes.toFloat() / largest,
                            selectedShare = if (summary.totalBytes > 0L) {
                                (state.selection.bytesByCategory[summary.category] ?: 0L).toFloat() / summary.totalBytes
                            } else {
                                0f
                            },
                            enabled = stage == Stage.RESULTS || stage == Stage.DONE,
                            onClick = { onOpenCategory(summary.category) },
                            modifier = Modifier.column(),
                        )
                    }
                }
            }

            item(key = "apps-header") {
                SectionHeader(
                    text = "Apps",
                    modifier = Modifier
                        .column()
                        .padding(top = 30.dp)
                        .animateItem(),
                )
            }
            item(key = "apps-unused") {
                LedgerRow(
                    title = "Unused apps",
                    meta = unusedAppsMeta(state),
                    figure = state.apps
                        ?.takeIf { it.hasUsageAccess && it.reclaimableBytes > 0 }
                        ?.reclaimableBytes?.bytes().orEmpty(),
                    icon = SweepIcons.Apps,
                    onClick = { onOpenApps(false) },
                    modifier = Modifier
                        .column()
                        .animateItem(),
                )
            }
            item(key = "apps-cache") {
                LedgerRow(
                    title = "App caches",
                    meta = cacheMeta(state),
                    figure = state.apps
                        ?.takeIf { it.hasUsageAccess && it.totalCacheBytes > 0 }
                        ?.totalCacheBytes?.bytes().orEmpty(),
                    icon = SweepIcons.Cache,
                    onClick = { onOpenApps(true) },
                    showDivider = false,
                    modifier = Modifier
                        .column()
                        .animateItem(),
                )
            }
        }

        ActionTray(
            visible = stage == Stage.RESULTS && state.selection.count > 0,
            count = state.selection.count,
            bytes = state.selection.bytes,
            onClear = onClearSelection,
            onReview = onReview,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** Every block on Home shares one column: 20dp gutters, and a reading width on large screens. */
private fun Modifier.column(): Modifier = this
    .widthIn(max = MaxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = 20.dp)

@Composable
private fun StorageHeadline(state: SweepUiState) {
    val colors = Sweep.colors
    val storage = state.storage
    Column {
        ByteFigure(bytes = storage.freeBytes, spokenSuffix = "free")
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (storage.totalBytes > 0L) {
                "free of ${storage.totalBytes.bytes()} · ${storage.usedBytes.bytes()} used"
            } else {
                "Android did not report this device's storage size"
            },
            style = SweepType.meta.copy(fontSize = SweepType.body.fontSize),
            color = colors.textMute,
        )
        if (storage.hasExtraVolumes) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Internal storage. Removable storage is scanned too.",
                style = SweepType.meta,
                color = colors.textMute,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TallyLegend(state: SweepUiState) {
    val colors = Sweep.colors
    if (state.storage.totalBytes <= 0L) return
    val figures = tallyFigures(state)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendKey(colors.tallyUsed, 1f, "Used")
        if (figures.foundFraction > 0f) LegendKey(colors.tallyFound, 1f, "For review")
        if (figures.selectedFraction > 0f) LegendKey(colors.tallySelected, 1f, "Selected")
        LegendKey(colors.tallyFree, 0.45f, "Free")
    }
}

@Composable
private fun LegendKey(color: Color, height: Float, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(width = 3.dp, height = 12.dp)) {
            val stroke = size.width
            drawLine(
                color = color,
                start = Offset(stroke / 2f, size.height - stroke / 2f),
                end = Offset(stroke / 2f, size.height - stroke / 2f - (size.height - stroke) * height),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(label, style = SweepType.micro, color = Sweep.colors.textMute)
    }
}

private enum class Panel { NO_ACCESS, IDLE, SCANNING, RESULTS, CLEANING, RECEIPT }

private fun SweepUiState.panel(): Panel = when {
    stage == Stage.SCANNING -> Panel.SCANNING
    stage == Stage.CLEANING -> Panel.CLEANING
    stage == Stage.DONE && cleanup != null -> Panel.RECEIPT
    stage == Stage.RESULTS -> Panel.RESULTS
    !permissions.canScanFiles -> Panel.NO_ACCESS
    else -> Panel.IDLE
}

/**
 * One panel, six states. Each state replaces the last in place along the sweep axis, so the
 * session reads as one thing progressing rather than blocks swapping.
 */
@Composable
private fun StatusPanel(
    state: SweepUiState,
    onScan: () -> Unit,
    onStop: () -> Unit,
    onPermissionsChanged: () -> Unit,
    onDismissReceipt: () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    val shift = with(LocalDensity.current) { SweepMotion.SHIFT_DP.dp.roundToPx() }
    AnimatedContent(
        targetState = state.panel(),
        transitionSpec = { sweepReplace(reduced, shift) using SizeTransform(clip = false) },
        label = "panel",
    ) { panel ->
        when (panel) {
            Panel.NO_ACCESS -> AccessPanel(onPermissionsChanged)
            Panel.IDLE -> IdlePanel(state, onScan)
            Panel.SCANNING -> ScanningPanel(state, onStop)
            Panel.RESULTS -> ResultsPanel(state, onScan)
            Panel.CLEANING -> CleaningPanel(state)
            Panel.RECEIPT -> state.cleanup?.let { ReceiptPanel(it, onDismissReceipt) } ?: Spacer(Modifier)
        }
    }
}

/**
 * Asked for at the moment its value is obvious, which is the moment someone wants to scan. There
 * is no onboarding wall: Sweep opens straight to Home, with this in place of the scan button.
 */
@Composable
private fun AccessPanel(onPermissionsChanged: () -> Unit) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val runtimePrompt = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionsChanged() }

    Column {
        Text("Let Sweep look through your storage", style = SweepType.headline, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Finding duplicates, old downloads and forgotten installers needs access to " +
                "your files. Sweep reads them on this phone only, and nothing is deleted until " +
                "you have reviewed it.",
            style = SweepType.body,
            color = colors.textMute,
        )
        Spacer(Modifier.height(18.dp))
        SweepButton(
            text = "Allow storage access",
            icon = SweepIcons.Lock,
            onClick = {
                if (SweepPermissions.usesRuntimeStoragePrompt) {
                    runtimePrompt.launch(SweepPermissions.runtimeStoragePermissions)
                } else {
                    SystemFlows.launchFirstAvailable(context, SweepPermissions.fileAccessIntents(context))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (SweepPermissions.usesRuntimeStoragePrompt) {
                "Android will ask you to confirm."
            } else {
                "Android opens its All files access page. Turn Sweep on there, then come back."
            },
            style = SweepType.meta,
            color = colors.textMute,
        )
    }
}

@Composable
private fun IdlePanel(state: SweepUiState, onScan: () -> Unit) {
    val colors = Sweep.colors
    Column {
        SweepButton(
            text = "Scan storage",
            icon = SweepIcons.Scan,
            onClick = onScan,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val last = state.lastScan
        Text(
            text = if (last != null) {
                "Your last scan found ${last.foundBytes.bytes()} worth reviewing, ${ago(last.at)}. " +
                    "Scan again to see what is there now."
            } else {
                "Looks for duplicates, old downloads, installers, archives and more. Nothing is " +
                    "deleted until you review it."
            },
            style = SweepType.meta,
            color = colors.textMute,
        )
    }
}

@Composable
private fun ScanningPanel(state: SweepUiState, onStop: () -> Unit) {
    val colors = Sweep.colors
    val progress = state.progress
    val found = progress?.partial?.sumOf { it.totalBytes } ?: 0L
    val readout: @Composable (Modifier) -> Unit = { readoutModifier ->
        Column(readoutModifier.semantics { liveRegion = LiveRegionMode.Polite }) {
            Text("Scanning", style = SweepType.headline, color = colors.text)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "${(progress?.filesSeen ?: 0).grouped()} files checked",
                style = SweepType.meta.copy(fontSize = SweepType.body.fontSize),
                color = colors.text,
            )
            Text(
                text = "${found.bytes()} found for review",
                style = SweepType.meta.copy(fontSize = SweepType.body.fontSize),
                color = colors.signalInk,
            )
            scanLocation(state)?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = SweepType.meta, color = colors.textMute, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    BoxWithConstraints {
        if (isCompact(maxWidth)) {
            Column {
                readout(Modifier)
                Spacer(Modifier.height(12.dp))
                SweepButton("Stop", onClick = onStop, tone = ButtonTone.Secondary, compact = true)
            }
        } else {
            Row(verticalAlignment = Alignment.Top) {
                readout(Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                SweepButton("Stop", onClick = onStop, tone = ButtonTone.Secondary, compact = true)
            }
        }
    }
}

@Composable
private fun ResultsPanel(state: SweepUiState, onScan: () -> Unit) {
    val colors = Sweep.colors
    val result = state.result ?: return
    Column {
        Text(
            text = if (result.items.isEmpty()) "Nothing worth reviewing"
            else "${result.totalFoundBytes.bytes()} worth reviewing",
            style = SweepType.headline,
            color = colors.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (result.items.isEmpty()) {
                "Sweep checked ${result.filesScanned.grouped()} files and found nothing it " +
                    "would suggest removing."
            } else {
                "${plural(result.items.size, "file")} across ${plural(result.byCategory.size, "category", "categories")}. " +
                    "${result.filesScanned.grouped()} checked."
            },
            style = SweepType.meta,
            color = colors.textMute,
        )
        if (result.stoppedEarly) {
            Text("Stopped early, so this is part of the picture.", style = SweepType.meta, color = colors.textMute)
        }
        if (result.unreadableDirectories > 0) {
            Text(
                text = "${plural(result.unreadableDirectories, "folder")} could not be read.",
                style = SweepType.meta,
                color = colors.textMute,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Aligned to the text above rather than to the text action's own padding.
        TextAction("Scan again", onClick = onScan, icon = SweepIcons.Scan, modifier = Modifier.offset(x = (-12).dp))
    }
}

@Composable
private fun CleaningPanel(state: SweepUiState) {
    val colors = Sweep.colors
    val cleaning = state.cleaning ?: return
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("Deleting", style = SweepType.headline, color = colors.text)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "${cleaning.done.grouped()} of ${plural(cleaning.total, "file")}",
            style = SweepType.meta.copy(fontSize = SweepType.body.fontSize),
            color = colors.text,
        )
        Text(
            text = "${cleaning.recoveredBytes.bytes()} confirmed gone so far",
            style = SweepType.meta.copy(fontSize = SweepType.body.fontSize),
            color = colors.signalInk,
        )
    }
}

/**
 * What actually happened. The large figure is bytes the deleter confirmed gone; the free-space
 * line is Android's own measurement taken afterwards. Failures are listed, never absorbed.
 */
@Composable
private fun ReceiptPanel(summary: CleanupSummary, onDone: () -> Unit) {
    val colors = Sweep.colors
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        when {
            summary.filesRemoved > 0 && summary.bytesRecovered > 0L -> {
                Text("Freed", style = SweepType.label, color = colors.textMute)
                Spacer(Modifier.height(2.dp))
                ByteFigure(
                    bytes = summary.bytesRecovered,
                    valueStyle = SweepType.figure,
                    unitStyle = SweepType.headline,
                    valueColor = colors.signalInk,
                    animate = false,
                )
            }
            summary.filesRemoved > 0 -> Text("Tidied up", style = SweepType.headline, color = colors.text)
            else -> Text("Nothing was deleted", style = SweepType.headline, color = colors.text)
        }
        Spacer(Modifier.height(8.dp))
        val lines = buildList {
            if (summary.filesRemoved > 0) {
                add(
                    "${plural(summary.filesRemoved, "item")} deleted from " +
                        summary.categories.joinToString(", ") { it.title.lowercase() } + "."
                )
            }
            if (summary.filesRemoved > 0 && summary.bytesRecovered == 0L) {
                add("They were empty folders, so there was no space to reclaim.")
            }
            if (summary.freeBytesAfter > 0L) add("Android now reports ${summary.freeBytesAfter.bytes()} free.")
            if (summary.alreadyGone == 1) {
                add("1 file had already gone before Sweep reached it, so it is not counted.")
            } else if (summary.alreadyGone > 1) {
                add("${summary.alreadyGone.grouped()} files had already gone before Sweep reached them, so they are not counted.")
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            lines.forEach { Text(it, style = SweepType.meta, color = colors.textMute) }
        }

        if (summary.failed.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            val reasons = summary.failed.map { it.reason }.distinct().take(2).joinToString(" ") { "$it." }
            Notice(
                title = "${plural(summary.failed.size, "item")} could not be deleted",
                text = "${summary.failed.sumOf { it.size }.bytes()} is still on the device and is not counted above. $reasons",
                icon = SweepIcons.Warning,
                tone = NoticeTone.Danger,
            )
        }
        Spacer(Modifier.height(14.dp))
        SweepButton("Done", onClick = onDone, tone = ButtonTone.Secondary, compact = true)
    }
}

/**
 * A category arriving mid-scan sweeps in from the left, once. A category that is already on
 * screen does not re-animate when its figure grows, and one scrolled back into view does not
 * replay its entrance.
 */
@Composable
private fun Arriving(key: CleanupCategory, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val shift = with(LocalDensity.current) { SweepMotion.SHIFT_DP.dp.toPx() }
    val progress = remember(key) { Animatable(if (reduced || key in seenThisProcess) 1f else 0f) }
    LaunchedEffect(key) {
        seenThisProcess += key
        if (progress.value < 1f) {
            progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
        }
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value.coerceIn(0f, 1f)
            translationX = (1f - progress.value) * -shift
        }
    ) { content() }
}

/** Categories that have already made their entrance, so a scrolled-away row returns quietly. */
private val seenThisProcess = HashSet<CleanupCategory>()

private fun tallyFigures(state: SweepUiState): TallyFigures {
    val total = state.storage.totalBytes.toFloat()
    if (total <= 0f) return TallyFigures(0f)
    val found = when (state.stage) {
        Stage.SCANNING -> state.progress?.partial?.sumOf { it.totalBytes } ?: 0L
        Stage.IDLE -> 0L
        else -> state.result?.totalFoundBytes ?: 0L
    }
    val selected = if (state.stage == Stage.RESULTS || state.stage == Stage.CLEANING) state.selection.bytes else 0L
    val cleared = state.cleaning?.recoveredBytes ?: 0L
    return TallyFigures(
        usedFraction = state.storage.usedFraction,
        foundFraction = found / total,
        selectedFraction = selected / total,
        clearedFraction = cleared / total,
    )
}

private fun tallyDescription(state: SweepUiState): String {
    val storage = state.storage
    if (storage.totalBytes <= 0L) return "Storage size unavailable"
    val figures = tallyFigures(state)
    return buildString {
        append("${storage.usedBytes.bytes()} used and ${storage.freeBytes.bytes()} free of ${storage.totalBytes.bytes()}.")
        if (figures.foundFraction > 0f) append(" ${state.result?.totalFoundBytes?.bytes() ?: ""} found for review.")
        if (state.selection.bytes > 0L && state.stage == Stage.RESULTS) append(" ${state.selection.bytes.bytes()} selected.")
    }
}

private fun categoryMeta(summary: CategorySummary, state: SweepUiState, stage: Stage): String {
    val noun = if (summary.category == CleanupCategory.EMPTY_FOLDERS) "folder" else "file"
    val items = plural(summary.itemCount, noun)
    if (stage == Stage.SCANNING) return items
    val selected = state.selection.countByCategory[summary.category] ?: 0
    return when {
        selected > 0 -> "$items · ${selected.grouped()} selected"
        summary.suggestedCount == 0 -> "$items · review one by one"
        else -> "$items · none selected"
    }
}

private fun scanLocation(state: SweepUiState): String? {
    val progress = state.progress ?: return null
    return when (progress.phase) {
        ScanPhase.HASHING -> "Comparing files to find exact duplicates"
        ScanPhase.FINISHING -> "Finishing up"
        ScanPhase.WALKING -> progress.currentDirectory
            ?.let { File(it).name }
            ?.takeIf { it.isNotBlank() && it != "0" }
            ?.let { "In $it" }
    }
}

private fun unusedAppsMeta(state: SweepUiState): String {
    val apps = state.apps
    return when {
        !state.permissions.hasUsageAccess -> "Needs Usage Access to see when apps were last opened"
        apps == null -> "Checking when your apps were last opened"
        apps.unused.isNotEmpty() -> "${plural(apps.unused.size, "app")} not opened in ${apps.thresholdDays}+ days"
        !apps.hasAnyUsageHistory -> "Android has not shared usage history on this device"
        else -> "Nothing unopened for ${apps.thresholdDays} days"
    }
}

private fun cacheMeta(state: SweepUiState): String = when {
    !state.permissions.hasUsageAccess -> "Needs Usage Access to measure cached data"
    state.apps == null -> "Measuring what apps have cached"
    else -> "Measured by Sweep, cleared by Android"
}

private fun ago(at: Long): String {
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - at).toInt()
    return AgeFormat.describe(days)
}
