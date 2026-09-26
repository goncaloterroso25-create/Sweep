package dev.sweep.ui.screens

import android.content.ActivityNotFoundException
import android.graphics.drawable.Drawable
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.model.AgeFormat
import dev.sweep.core.model.InstalledApp
import dev.sweep.core.scan.UnusedAppPolicy
import dev.sweep.ui.AppNotice
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.Hairline
import dev.sweep.ui.components.IconAction
import dev.sweep.ui.components.Notice
import dev.sweep.ui.components.RestrictedSettingsHelp
import dev.sweep.ui.components.ScreenBar
import dev.sweep.ui.components.ScreenTitle
import dev.sweep.ui.components.SectionHeader
import dev.sweep.ui.components.SegmentedChoice
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.TextAction
import dev.sweep.ui.components.bytes
import dev.sweep.ui.components.plural
import dev.sweep.ui.components.pressable
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.fold
import dev.sweep.ui.theme.settleSpec
import dev.sweep.ui.theme.unfold

enum class AppsTab(val label: String) { UNUSED("Unused"), CACHE("Cache") }

/**
 * Apps: the ones Android says you have stopped opening, and what each one is caching.
 *
 * Two jobs that used to be two screens. They belong together because both are per-app, both need
 * Usage Access, and in both Sweep's part ends where Android's begins: Sweep finds and measures,
 * Android uninstalls and clears. Every result Sweep reports afterwards is one it has checked.
 */
@Composable
fun AppsScreen(
    state: SweepUiState,
    initialTab: AppsTab,
    loadIcon: suspend (String) -> Drawable?,
    onBack: () -> Unit,
    onThresholdChange: (Int) -> Unit,
    onExcludeApp: (String) -> Unit,
    onUninstallReturned: (packageName: String, label: String, resultCode: Int) -> Unit,
    onUninstallUnavailable: (label: String) -> Unit,
    onAppStorageReturned: (packageName: String, label: String, cacheBefore: Long) -> Unit,
    onClearOwnCache: () -> Unit,
    onRefreshOwnCache: () -> Unit,
    onDismissNotice: () -> Unit,
    onUsageAccessRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var unknownOpen by rememberSaveable { mutableStateOf(false) }
    val apps = state.apps
    val threshold = state.settings.unusedAppThresholdDays

    // Which app a system screen is currently about, so its result is attributed correctly.
    var pendingUninstall by remember { mutableStateOf<InstalledApp?>(null) }
    var pendingStorage by remember { mutableStateOf<InstalledApp?>(null) }

    val uninstallLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        pendingUninstall?.let { onUninstallReturned(it.packageName, it.label, result.resultCode) }
        pendingUninstall = null
    }
    val storageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        pendingStorage?.let { onAppStorageReturned(it.packageName, it.label, it.cacheBytes) }
        pendingStorage = null
    }

    fun uninstall(app: InstalledApp) {
        pendingUninstall = app
        for (intent in SystemFlows.uninstallIntents(app.packageName)) {
            try {
                uninstallLauncher.launch(intent)
                return
            } catch (e: ActivityNotFoundException) {
                continue
            }
        }
        pendingUninstall = null
        onUninstallUnavailable(app.label)
    }

    fun openStorage(app: InstalledApp) {
        pendingStorage = app
        try {
            storageLauncher.launch(SystemFlows.appDetailsIntent(app.packageName))
        } catch (e: ActivityNotFoundException) {
            pendingStorage = null
        }
    }

    fun openAppInfo(app: InstalledApp) {
        SystemFlows.launchFirstAvailable(context, listOf(SystemFlows.appDetailsIntent(app.packageName)))
    }

    androidx.compose.runtime.LaunchedEffect(tab) { if (tab == AppsTab.CACHE) onRefreshOwnCache() }
    // Sorted once per measurement, not once per frame.
    val cached = remember(apps) {
        apps?.apps?.filter { it.cacheBytes > 0 }?.sortedByDescending { it.cacheBytes }.orEmpty()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "bar") { ScreenBar(onBack, Modifier.widthIn(max = MaxContentWidth + 12.dp)) }
        item(key = "title") {
            Column(Modifier.appsColumn()) {
                ScreenTitle("Apps", subtitle = null)
                Spacer(Modifier.height(4.dp))
                SegmentedChoice(
                    options = AppsTab.entries.toList(),
                    selected = tab,
                    label = { it.label },
                    onSelect = { tab = it },
                )
            }
        }

        item(key = "notice") { AppNoticeRow(state.appNotice, onDismissNotice, Modifier.appsColumn()) }

        if (!state.permissions.hasUsageAccess) {
            item(key = "gate") {
                UsageAccessGate(
                    tab = tab,
                    refused = state.usageAccessRefused,
                    onUsageAccessRequested = onUsageAccessRequested,
                    modifier = Modifier.appsColumn().padding(top = 18.dp),
                )
            }
            if (tab == AppsTab.CACHE) {
                item(key = "own-cache") { OwnCacheRow(state.ownCacheBytes, onClearOwnCache, Modifier.appsColumn().padding(top = 18.dp)) }
            }
            return@LazyColumn
        }

        when (tab) {
            AppsTab.UNUSED -> {
                item(key = "threshold") {
                    Column(Modifier.appsColumn().padding(top = 18.dp)) {
                        SectionHeader("Not opened for at least")
                        SegmentedChoice(
                            options = UnusedAppPolicy.THRESHOLD_CHOICES,
                            selected = threshold,
                            label = { "$it days" },
                            onSelect = onThresholdChange,
                        )
                    }
                }
                when {
                    apps == null -> item(key = "loading") { LoadingLine("Reading when each app was last opened", Modifier.appsColumn()) }
                    !apps.hasAnyUsageHistory -> item(key = "no-history") {
                        Notice(
                            title = "Android has not shared any usage history",
                            text = "Usage Access is on, but this device returned no last-opened date for any app. " +
                                "Sweep will not guess, so nothing is listed as unused.",
                            icon = SweepIcons.Unknown,
                            modifier = Modifier.appsColumn().padding(top = 18.dp),
                        )
                    }
                    else -> {
                        item(key = "unused-summary") {
                            Text(
                                text = if (apps.unused.isEmpty()) {
                                    "Every app with a usage record has been opened in the last $threshold days."
                                } else {
                                    "${plural(apps.unused.size, "app")} not opened in $threshold+ days, using ${apps.reclaimableBytes.bytes()} together."
                                },
                                style = SweepType.meta,
                                color = colors.textMute,
                                modifier = Modifier.appsColumn().padding(top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(apps.unused, key = { "unused:" + it.app.packageName }) { unused ->
                            AppRow(
                                app = unused.app,
                                detail = "Last opened ${AgeFormat.describe(unused.daysSinceUse)}",
                                figure = unused.app.totalBytes,
                                loadIcon = loadIcon,
                                primaryAction = "Uninstall",
                                onPrimary = { uninstall(unused.app) },
                                danger = true,
                                menu = listOf(
                                    "App info" to { openAppInfo(unused.app) },
                                    "Don't suggest again" to { onExcludeApp(unused.app.packageName) },
                                ),
                                modifier = Modifier.appsColumn().animateItem(),
                            )
                        }
                    }
                }

                if (apps != null && apps.unknownUsage.isNotEmpty()) {
                    item(key = "unknown-header") {
                        UnknownHeader(
                            count = apps.unknownUsage.size,
                            open = unknownOpen,
                            onToggle = { unknownOpen = !unknownOpen },
                            modifier = Modifier.appsColumn().padding(top = 26.dp),
                        )
                    }
                    if (unknownOpen) {
                        items(apps.unknownUsage, key = { "unknown:" + it.app.packageName }) { unknown ->
                            AppRow(
                                app = unknown.app,
                                detail = "No usage on record · installed ${AgeFormat.describe(unknown.installedDays)}",
                                figure = unknown.app.totalBytes,
                                loadIcon = loadIcon,
                                primaryAction = "App info",
                                onPrimary = { openAppInfo(unknown.app) },
                                danger = false,
                                muted = true,
                                menu = listOf("Don't suggest again" to { onExcludeApp(unknown.app.packageName) }),
                                modifier = Modifier.appsColumn().animateItem(),
                            )
                        }
                    }
                }

                item(key = "unused-foot") {
                    Text(
                        text = "Android shows its own confirmation and does the uninstalling. Sweep " +
                            "checks afterwards that the app is really gone before saying so.",
                        style = SweepType.meta,
                        color = colors.textMute,
                        modifier = Modifier.appsColumn().padding(top = 22.dp),
                    )
                }
            }

            AppsTab.CACHE -> {
                item(key = "cache-explain") {
                    Notice(
                        title = "Android does the clearing",
                        text = "No app is allowed to clear another app's cache. Sweep measures it, and " +
                            "Clear takes you to that app's page in Android's settings, where Storage " +
                            "has the button. Sweep checks the size again when you come back.",
                        icon = SweepIcons.Info,
                        modifier = Modifier.appsColumn().padding(top = 18.dp),
                    )
                }
                when {
                    apps == null -> item(key = "cache-loading") { LoadingLine("Measuring app caches", Modifier.appsColumn()) }
                    cached.isEmpty() -> item(key = "cache-empty") {
                        Text(
                            "No app is holding a measurable cache right now.",
                            style = SweepType.body,
                            color = colors.textMute,
                            modifier = Modifier.appsColumn().padding(top = 18.dp),
                        )
                    }
                    else -> {
                        item(key = "cache-header") {
                            SectionHeader(
                                "${apps.totalCacheBytes.bytes()} cached across ${plural(cached.size, "app")}",
                                Modifier.appsColumn().padding(top = 18.dp),
                            )
                        }
                        items(cached.take(40), key = { "cache:" + it.packageName }) { app ->
                            AppRow(
                                app = app,
                                detail = "${app.totalBytes.bytes()} in total",
                                figure = app.cacheBytes,
                                loadIcon = loadIcon,
                                primaryAction = "Clear",
                                onPrimary = { openStorage(app) },
                                danger = false,
                                menu = emptyList(),
                                modifier = Modifier.appsColumn().animateItem(),
                            )
                        }
                    }
                }
                item(key = "own-cache") { OwnCacheRow(state.ownCacheBytes, onClearOwnCache, Modifier.appsColumn().padding(top = 24.dp)) }
            }
        }

        item(key = "end") { Spacer(Modifier.navigationBarsPadding()) }
    }
}

private fun Modifier.appsColumn(): Modifier = this
    .widthIn(max = MaxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = 20.dp)

@Composable
private fun AppNoticeRow(notice: AppNotice?, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val reduced = LocalReducedMotion.current
    val colors = Sweep.colors
    // Kept through the exit animation, so the notice folds away with its text still in it.
    var last by remember { mutableStateOf(notice) }
    if (notice != null) last = notice
    AnimatedVisibility(visible = notice != null, enter = unfold(reduced), exit = fold(reduced), modifier = modifier) {
        val current = last ?: return@AnimatedVisibility
        Row(
            Modifier
                .padding(top = 14.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (current.positive) colors.signalWash else colors.dangerWash)
                .padding(start = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (current.positive) SweepIcons.Check else SweepIcons.Warning,
                contentDescription = null,
                tint = if (current.positive) colors.signalInk else colors.danger,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(current.text, style = SweepType.meta, color = colors.text, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            IconAction(SweepIcons.Close, "Dismiss", onDismiss, tint = colors.textMute, size = 18.dp)
        }
    }
}

@Composable
private fun UsageAccessGate(
    tab: AppsTab,
    refused: Boolean,
    onUsageAccessRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    Column(modifier) {
        Icon(SweepIcons.Clock, contentDescription = null, tint = colors.text, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (tab == AppsTab.UNUSED) "Find the apps you forgot about" else "See what apps are caching",
            style = SweepType.headline,
            color = colors.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Usage Access tells Sweep when each app was last opened and how much space it " +
                "uses. Android grants it from its own settings page. Without it, everything to do " +
                "with files still works.",
            style = SweepType.body,
            color = colors.textMute,
        )
        Spacer(Modifier.height(18.dp))
        SweepButton(
            text = "Allow Usage Access",
            onClick = {
                onUsageAccessRequested()
                SystemFlows.launchFirstAvailable(context, SweepPermissions.usageAccessIntents(context))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        RestrictedSettingsHelp(autoExpand = refused)
    }
}

@Composable
private fun LoadingLine(text: String, modifier: Modifier = Modifier) {
    Text(text, style = SweepType.meta, color = Sweep.colors.textMute, modifier = modifier.padding(top = 18.dp))
}

@Composable
private fun UnknownHeader(count: Int, open: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    val rotation by animateFloatAsState(if (open) 180f else 0f, settleSpec(), label = "chevron")
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .pressable(pressedScale = 0.99f, role = Role.Button, onClick = onToggle)
                .semantics {
                    heading()
                    stateDescription = if (open) "Expanded" else "Collapsed"
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SweepIcons.Unknown, contentDescription = null, tint = colors.textMute, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text("Usage unknown · ${plural(count, "app")}", style = SweepType.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
            Icon(
                SweepIcons.ChevronDown,
                contentDescription = null,
                tint = colors.textMute,
                modifier = Modifier
                    .size(20.dp)
                    .graphicsLayer { rotationZ = rotation },
            )
        }
        Text(
            text = "Android gave no usage history for these, so Sweep cannot tell whether you use " +
                "them. They are not counted as unused and nothing here is suggested for removal.",
            style = SweepType.meta,
            color = colors.textMute,
            modifier = Modifier.padding(start = 32.dp, bottom = 6.dp),
        )
    }
}

/**
 * One app and the two facts that decide whether it should go. The size sits on the name's line
 * and never truncates; the name gives way instead. The last-opened line may wrap, but it never
 * disappears, whatever the width or the font scale.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppRow(
    app: InstalledApp,
    detail: String,
    figure: Long,
    loadIcon: suspend (String) -> Drawable?,
    primaryAction: String,
    onPrimary: () -> Unit,
    danger: Boolean,
    menu: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
    muted: Boolean = false,
) {
    val colors = Sweep.colors
    var menuOpen by remember { mutableStateOf(false) }
    val icon = rememberAppIcon(app.packageName, loadIcon)

    Box(modifier) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (menu.isNotEmpty()) Modifier.pressable(pressedScale = 0.99f, onClickLabel = "More options for ${app.label}") { menuOpen = true }
                        else Modifier
                    )
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    Modifier
                        .padding(top = 2.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    icon?.let {
                        Image(it, contentDescription = null, modifier = Modifier.size(32.dp).graphicsLayer { alpha = if (muted) 0.55f else 1f })
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            app.label,
                            style = SweepType.rowTitle,
                            color = if (muted) colors.textMute else colors.text,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = if (figure > 0L) figure.bytes() else "Size unknown",
                            style = if (figure > 0L) SweepType.rowFigure else SweepType.meta,
                            color = if (figure > 0L) colors.text else colors.textMute,
                            maxLines = 1,
                        )
                    }
                    // Flows rather than squeezes: when the action will not fit beside the detail, it
                    // drops to its own line and the detail keeps the full width.
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            detail,
                            style = SweepType.meta,
                            color = colors.textMute,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .align(Alignment.CenterVertically),
                        )
                        TextAction(
                            text = primaryAction,
                            onClick = onPrimary,
                            color = if (danger) colors.danger else colors.text,
                        )
                    }
                }
            }
            Hairline(inset = 54.dp)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = colors.raised) {
            menu.forEach { (label, action) ->
                DropdownMenuItem(
                    text = { Text(label, style = SweepType.rowTitle, color = colors.text) },
                    onClick = {
                        menuOpen = false
                        action()
                    },
                )
            }
        }
    }
}

@Composable
private fun OwnCacheRow(bytes: Long, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    Row(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Sweep's own cache", style = SweepType.rowTitle, color = colors.text)
            Text(
                "${bytes.bytes()} · the one cache Sweep can clear by itself",
                style = SweepType.meta,
                color = colors.textMute,
            )
        }
        SweepButton("Clear", onClick = onClear, tone = ButtonTone.Secondary, compact = true, enabled = bytes > 0)
    }
}

/** App icons are decoded per visible row, at row size, never up front for the whole list. */
@Composable
private fun rememberAppIcon(packageName: String, loadIcon: suspend (String) -> Drawable?): ImageBitmap? {
    if (LocalInspectionMode.current) return null
    val icon by produceState(iconBitmaps.get(packageName), packageName) {
        if (value != null) return@produceState
        value = runCatching { loadIcon(packageName)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap() }
            .getOrNull()
            ?.also { iconBitmaps.put(packageName, it) }
    }
    return icon
}

private const val ICON_PX = 96

/**
 * Row-sized icon bitmaps, kept across scrolling. The drawables are already cached by
 * [dev.sweep.core.android.AppInventory]; this saves re-rasterising one each time a row returns.
 */
private val iconBitmaps = android.util.LruCache<String, ImageBitmap>(96)
