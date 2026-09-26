package dev.sweep.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.sweep.BuildConfig
import dev.sweep.core.android.SweepNotifications
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.data.MotionPreference
import dev.sweep.core.data.SweepSettings
import dev.sweep.core.model.ScanConfig
import dev.sweep.core.scan.UnusedAppPolicy
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.Group
import dev.sweep.ui.components.HapticProbe
import dev.sweep.ui.components.Hairline
import dev.sweep.ui.components.RestrictedSettingsHelp
import dev.sweep.ui.components.ScreenBar
import dev.sweep.ui.components.ScreenTitle
import dev.sweep.ui.components.SectionHeader
import dev.sweep.ui.components.SegmentedChoice
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.components.SweepSwitch
import dev.sweep.ui.components.TextAction
import dev.sweep.ui.components.bytes
import dev.sweep.ui.components.plural
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.fold
import dev.sweep.ui.theme.unfold

/**
 * Settings, grouped by what they are about rather than by what kind of control they use.
 *
 * Each group is one surface with inset dividers, so related settings read as related. Every
 * choice uses the same adaptive selector, which re-flows to a grid rather than letting a long
 * label push one option into a tall column.
 */
@Composable
fun SettingsScreen(
    state: SweepUiState,
    haptics: SweepHaptics,
    onBack: () -> Unit,
    onOldFileThreshold: (Int) -> Unit,
    onLargeFileThreshold: (Long) -> Unit,
    onScreenshotThreshold: (Int) -> Unit,
    onUnusedAppThreshold: (Int) -> Unit,
    onHaptics: (Boolean) -> Unit,
    onMotion: (MotionPreference) -> Unit,
    onClearExclusions: () -> Unit,
    onUsageAccessRequested: () -> Unit,
    onCleanupReminders: (Boolean) -> Unit,
    onUnusedAppReminders: (Boolean) -> Unit,
    onReminderThreshold: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val settings = state.settings

    Box(
        modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth + 12.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            ScreenBar(onBack)
            Column(Modifier.padding(horizontal = 20.dp)) {
                ScreenTitle("Settings", subtitle = null)

                Section("What Sweep looks for") {
                    ChoiceSetting(
                        title = "Old downloads",
                        caption = "How long a file sits in Downloads before Sweep suggests it.",
                        options = ScanConfig.AGE_CHOICES,
                        selected = settings.oldFileThresholdDays,
                        label = { "$it days" },
                        onSelect = onOldFileThreshold,
                    )
                    Divider()
                    ChoiceSetting(
                        title = "Large files",
                        caption = "Files this size or bigger are listed for review. Never pre-selected.",
                        options = ScanConfig.LARGE_FILE_CHOICES,
                        selected = settings.largeFileThresholdBytes,
                        label = { it.bytes() },
                        onSelect = onLargeFileThreshold,
                    )
                    Divider()
                    ChoiceSetting(
                        title = "Old screenshots",
                        caption = "Listed once they are older than this. Never pre-selected.",
                        options = listOf(90, 180, 365),
                        selected = settings.oldScreenshotThresholdDays,
                        label = { if (it >= 365) "1 year" else "$it days" },
                        onSelect = onScreenshotThreshold,
                    )
                    Divider()
                    ChoiceSetting(
                        title = "Unused apps",
                        caption = "How long since an app was opened before it counts as unused.",
                        options = UnusedAppPolicy.THRESHOLD_CHOICES,
                        selected = settings.unusedAppThresholdDays,
                        label = { "$it days" },
                        onSelect = onUnusedAppThreshold,
                    )
                }

                Section("Reminders") {
                    RemindersGroup(
                        settings = settings,
                        usageAccessGranted = state.permissions.hasUsageAccess,
                        onCleanupReminders = onCleanupReminders,
                        onUnusedAppReminders = onUnusedAppReminders,
                        onReminderThreshold = onReminderThreshold,
                    )
                }

                Section("Motion and touch") {
                    ChoiceSetting(
                        title = "Motion",
                        caption = "Reduced keeps every change visible but removes movement: no scan " +
                            "front, no sliding, no springs. If animations are off in Android, Sweep " +
                            "follows that either way.",
                        options = MotionPreference.entries.toList(),
                        selected = settings.motion,
                        label = { if (it == MotionPreference.STANDARD) "Standard" else "Reduced" },
                        onSelect = onMotion,
                    )
                    Divider()
                    ToggleSetting(
                        title = "Haptics",
                        caption = "A short tick when you select, delete or finish. Android's own touch " +
                            "feedback setting still has the final say.",
                        checked = settings.hapticsEnabled,
                        onChange = onHaptics,
                    )
                    Divider()
                    HapticTest(haptics)
                }

                Section("Access") {
                    PermissionSetting(
                        title = "Storage",
                        granted = state.permissions.canScanFiles,
                        grantedText = "Sweep can scan shared storage.",
                        deniedText = "Not allowed. Scanning is unavailable.",
                        onOpen = {
                            SystemFlows.launchFirstAvailable(context, SweepPermissions.fileAccessIntents(context))
                        },
                    )
                    Divider()
                    PermissionSetting(
                        title = "Usage Access",
                        granted = state.permissions.hasUsageAccess,
                        grantedText = "Sweep can see when apps were last opened, and their sizes.",
                        deniedText = "Not allowed. Unused apps and cache sizes are unavailable.",
                        onOpen = {
                            onUsageAccessRequested()
                            SystemFlows.launchFirstAvailable(context, SweepPermissions.usageAccessIntents(context))
                        },
                    )
                    if (!state.permissions.hasUsageAccess) {
                        Box(Modifier.padding(start = 4.dp, end = 16.dp, bottom = 8.dp)) {
                            RestrictedSettingsHelp(autoExpand = state.usageAccessRefused)
                        }
                    }
                }

                val excludedCount = settings.excludedPaths.size + settings.excludedPackages.size
                Section("Not suggested") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 64.dp)
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Things you asked Sweep to skip", style = SweepType.rowTitle, color = colors.text)
                            Text(
                                text = if (excludedCount == 0) "Nothing yet. Long-press a file or app to add it."
                                else "${plural(settings.excludedPaths.size, "file or folder", "files or folders")}, " +
                                    plural(settings.excludedPackages.size, "app"),
                                style = SweepType.meta,
                                color = colors.textMute,
                            )
                        }
                        if (excludedCount > 0) TextAction("Clear all", onClearExclusions, color = colors.danger)
                    }
                }

                Spacer(Modifier.height(30.dp))
                Text(
                    text = "Sweep ${BuildConfig.VERSION_NAME}",
                    style = SweepType.rowTitle,
                    color = colors.text,
                )
                Text(
                    // Which build a tester is running, without guessing from the icon.
                    text = "${BuildConfig.BUILD_TYPE.replaceFirstChar { it.uppercase() }} build, version code ${BuildConfig.VERSION_CODE}",
                    style = SweepType.meta,
                    color = colors.textMute,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Everything happens on this phone. Sweep has no account, no server and no " +
                        "network permission, so your files and apps cannot leave the device.",
                    style = SweepType.meta,
                    color = colors.textMute,
                )
                Spacer(Modifier.height(40.dp))
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(22.dp))
    SectionHeader(title, Modifier.padding(start = 4.dp))
    Spacer(Modifier.height(4.dp))
    Group(content = content)
}

@Composable
private fun Divider() = Hairline(inset = 16.dp)

@Composable
private fun <T> ChoiceSetting(
    title: String,
    caption: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = Sweep.colors
    Column(Modifier.padding(16.dp)) {
        Text(title, style = SweepType.rowTitle, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = SweepType.meta, color = colors.textMute)
        Spacer(Modifier.height(12.dp))
        SegmentedChoice(options = options, selected = selected, label = label, onSelect = onSelect)
    }
}

@Composable
private fun ToggleSetting(title: String, caption: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Sweep.colors
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = SweepType.rowTitle, color = colors.text)
            Spacer(Modifier.height(2.dp))
            Text(caption, style = SweepType.meta, color = colors.textMute)
        }
        Spacer(Modifier.width(16.dp))
        SweepSwitch(checked)
    }
}

@Composable
private fun PermissionSetting(
    title: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    onOpen: () -> Unit,
) {
    val colors = Sweep.colors
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) colors.signalInk else colors.lineStrong)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = SweepType.rowTitle, color = colors.text)
            Text(if (granted) grantedText else deniedText, style = SweepType.meta, color = colors.textMute)
        }
        if (!granted) {
            Spacer(Modifier.width(8.dp))
            SweepButton("Allow", onClick = onOpen, tone = ButtonTone.Secondary, compact = true)
        }
    }
}

/**
 * Two reminders, both off until asked for. Android's notification permission is requested at
 * the moment a switch is turned on, never at launch, so nobody who does not want reminders is
 * ever asked. A refusal leaves the switch off and says so.
 */
@Composable
private fun RemindersGroup(
    settings: SweepSettings,
    usageAccessGranted: Boolean,
    onCleanupReminders: (Boolean) -> Unit,
    onUnusedAppReminders: (Boolean) -> Unit,
    onReminderThreshold: (Long) -> Unit,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    var denied by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<((Boolean) -> Unit)?>(null) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) pending?.invoke(true)
        pending = null
    }

    fun enable(setter: (Boolean) -> Unit) {
        if (SweepNotifications.canNotify(context)) {
            denied = false
            setter(true)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pending = setter
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            // Before Android 13 there is no runtime prompt, so this is the app-level switch.
            denied = true
        }
    }

    ToggleSetting(
        title = "Storage worth reviewing",
        caption = "A note when your last scan found more than the amount below. Sweep quotes what " +
            "that scan measured, and never scans in the background.",
        checked = settings.cleanupReminders,
        onChange = { if (it) enable(onCleanupReminders) else onCleanupReminders(false) },
    )
    AnimatedVisibility(settings.cleanupReminders, enter = unfold(reduced), exit = fold(reduced)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            SegmentedChoice(
                options = REMINDER_THRESHOLDS,
                selected = settings.reminderThresholdBytes,
                label = { it.bytes() },
                onSelect = onReminderThreshold,
            )
        }
    }
    Divider()
    ToggleSetting(
        title = "Unused apps",
        caption = if (usageAccessGranted) "A note when more apps pass your unused threshold. Apps " +
            "with unknown usage are never counted."
        else "Needs Usage Access, which is not allowed yet.",
        checked = settings.unusedAppReminders,
        onChange = { if (it) enable(onUnusedAppReminders) else onUnusedAppReminders(false) },
    )

    if (denied) {
        Divider()
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)) {
            Text(
                "Android is blocking notifications from Sweep, so reminders stay off.",
                style = SweepType.meta,
                color = colors.danger,
            )
            TextAction(
                "Open notification settings",
                onClick = { SystemFlows.launchFirstAvailable(context, SystemFlows.notificationSettingsIntents(context)) },
                modifier = Modifier.padding(start = 0.dp),
            )
        }
    } else if (settings.anyReminderEnabled) {
        Divider()
        Text(
            text = "Checked about once a week when the battery is not low, and never within a few " +
                "days of you opening Sweep.",
            style = SweepType.meta,
            color = colors.textMute,
            modifier = Modifier.padding(16.dp),
        )
    }
}

private val REMINDER_THRESHOLDS = listOf(
    1L * 1000 * 1000 * 1000,
    3L * 1000 * 1000 * 1000,
    5L * 1000 * 1000 * 1000,
    10L * 1000 * 1000 * 1000,
)

/**
 * Haptics are the one part of the app that cannot be checked by looking at it, and Sweep's switch
 * is not the only one involved. The test fires the real haptic and says which setting, if any, is
 * in the way.
 */
@Composable
private fun HapticTest(haptics: SweepHaptics) {
    val colors = Sweep.colors
    val context = LocalContext.current
    var probe by remember { mutableStateOf<HapticProbe?>(null) }
    Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Test haptic", style = SweepType.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
            TextAction("Try it", onClick = { probe = haptics.test() }, color = colors.signalInk)
        }
        probe?.let { result ->
            Text(
                text = when {
                    !result.systemFeedbackEnabled ->
                        "Android's touch feedback is off, so nothing will be felt. Sweep cannot override it."
                    !result.accepted -> "Android declined. This device does not offer that haptic."
                    !result.appSettingEnabled -> "Android accepted it. Sweep's haptics are off, so this was a one-off."
                    else -> "Android accepted it. If you felt nothing, check vibration strength in sound settings."
                },
                style = SweepType.meta,
                color = if (result.systemFeedbackEnabled && result.accepted) colors.textMute else colors.danger,
                modifier = Modifier.padding(end = 8.dp, bottom = 4.dp),
            )
            if (!result.systemFeedbackEnabled || !result.accepted) {
                TextAction(
                    "Open sound settings",
                    onClick = { SystemFlows.launchFirstAvailable(context, SystemFlows.soundSettingsIntents()) },
                )
            }
        }
    }
}
