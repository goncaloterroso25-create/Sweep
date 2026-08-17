package dev.sweep.ui.screens

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sweep.BuildConfig
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.data.MotionPreference
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.ScanConfig
import dev.sweep.core.scan.UnusedAppPolicy
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.HairLine
import dev.sweep.ui.components.HapticProbe
import dev.sweep.ui.components.RestrictedSettingsHelp
import dev.sweep.ui.components.SectionLabel
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.SweepTopBar
import dev.sweep.ui.components.pressable
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springSnappy

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
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val settings = state.settings

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding()
    ) {
        SweepTopBar(title = "Settings", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            SectionLabel("Scanning")
            Spacer(Modifier.height(12.dp))

            ChoiceRow(
                title = "Downloads older than",
                caption = "How long a file sits in Downloads before Sweep suggests it.",
                options = ScanConfig.AGE_CHOICES,
                selected = settings.oldFileThresholdDays,
                label = { "$it days" },
                onSelect = onOldFileThreshold,
            )
            ChoiceRow(
                title = "Large file threshold",
                caption = "Files at or above this size are listed for review.",
                options = ScanConfig.LARGE_FILE_CHOICES,
                selected = settings.largeFileThresholdBytes,
                label = { ByteFormat.short(it) },
                onSelect = onLargeFileThreshold,
            )
            ChoiceRow(
                title = "Screenshots older than",
                caption = "Old screenshots are shown but never pre-selected.",
                options = listOf(90, 180, 365),
                selected = settings.oldScreenshotThresholdDays,
                label = { if (it >= 365) "1 year" else "$it days" },
                onSelect = onScreenshotThreshold,
            )
            ChoiceRow(
                title = "Apps unused for",
                caption = "Needs Usage Access to mean anything.",
                options = UnusedAppPolicy.THRESHOLD_CHOICES,
                selected = settings.unusedAppThresholdDays,
                label = { "$it days" },
                onSelect = onUnusedAppThreshold,
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("Feel")
            Spacer(Modifier.height(12.dp))

            ToggleRow(
                title = "Haptics",
                caption = "A short tick when you select, confirm or finish. Android's own touch " +
                    "feedback setting still has the final say.",
                checked = settings.hapticsEnabled,
                onChange = onHaptics,
            )
            HapticTestRow(haptics = haptics)

            Spacer(Modifier.height(4.dp))
            ChoiceRow(
                title = "Motion",
                caption = "Reduced removes springs, staggers and moving decoration. If you've " +
                    "turned animations off in Android, Sweep follows that either way.",
                options = MotionPreference.entries.toList(),
                selected = settings.motion,
                label = {
                    when (it) {
                        MotionPreference.STANDARD -> "Standard"
                        MotionPreference.REDUCED -> "Reduced"
                    }
                },
                onSelect = onMotion,
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel("Exclusions")
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surface)
                    .border(1.dp, colors.line, MaterialTheme.shapes.medium)
                    .padding(15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Don't suggest again",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.text,
                    )
                    Text(
                        text = "${settings.excludedPaths.size} files or folders · " +
                            "${settings.excludedPackages.size} apps",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMute,
                    )
                }
                if (settings.excludedPaths.isNotEmpty() || settings.excludedPackages.isNotEmpty()) {
                    SweepTextButton(text = "Clear all", onClick = onClearExclusions)
                }
            }

            Spacer(Modifier.height(22.dp))
            SectionLabel("Permissions")
            Spacer(Modifier.height(12.dp))

            PermissionRow(
                title = "Storage access",
                granted = state.permissions.canScanFiles,
                grantedText = "Granted. Sweep can scan shared storage.",
                deniedText = "Not granted. File scanning is unavailable.",
                onOpen = {
                    SystemFlows.launchFirstAvailable(
                        context,
                        SweepPermissions.fileAccessIntents(context),
                    )
                },
            )
            Spacer(Modifier.height(9.dp))
            PermissionRow(
                title = "Usage Access",
                granted = state.permissions.hasUsageAccess,
                grantedText = "Granted. Sweep can see last-opened dates and app sizes.",
                deniedText = "Not granted. Unused apps and cache sizes are unavailable.",
                onOpen = {
                    onUsageAccessRequested()
                    SystemFlows.launchFirstAvailable(
                        context,
                        SweepPermissions.usageAccessIntents(context),
                    )
                },
            )
            if (!state.permissions.hasUsageAccess) {
                RestrictedSettingsHelp(autoExpand = state.usageAccessRefused)
            }

            Spacer(Modifier.height(26.dp))
            HairLine()
            Spacer(Modifier.height(18.dp))
            SectionLabel("About")
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Sweep ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                // Which build a tester is running, without having to guess from the icon.
                text = "${BuildConfig.BUILD_TYPE} build, version code ${BuildConfig.VERSION_CODE}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Everything happens on this device. There is no account, no server and " +
                    "no network permission, so your file list cannot leave the phone.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMute,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "A personal project, not a commercial cleaner.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
            )
            Spacer(Modifier.height(40.dp))
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

/**
 * One tap, one honest answer.
 *
 * Haptics are the one part of the app that cannot be verified by looking at it, and Sweep's switch
 * is not the only switch involved. So the test fires the real confirmation haptic, then says which
 * of the three things that could be wrong actually is — and offers the system screen when the
 * answer is Android's setting rather than Sweep's.
 */
@Composable
private fun HapticTestRow(haptics: SweepHaptics) {
    val colors = Sweep.colors
    val context = LocalContext.current
    var probe by remember { mutableStateOf<HapticProbe?>(null) }

    Column(Modifier.padding(bottom = 18.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(colors.surface)
                .border(1.dp, colors.line, MaterialTheme.shapes.medium)
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Test haptic",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                )
                Text(
                    text = "Fires the confirmation tick, even if Sweep's switch is off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMute,
                )
            }
            Spacer(Modifier.width(10.dp))
            SweepButton(
                text = "Test",
                onClick = { probe = haptics.test() },
                tone = ButtonTone.Neutral,
                dense = true,
            )
        }

        probe?.let { result ->
            Spacer(Modifier.height(9.dp))
            Text(
                text = when {
                    !result.systemFeedbackEnabled ->
                        "Android's touch feedback is off, so nothing will be felt. Sweep's " +
                            "setting cannot override that."
                    !result.accepted ->
                        "Android declined the request. This device doesn't provide that haptic."
                    !result.appSettingEnabled ->
                        "Android accepted it. Sweep's own haptics are off, so this was a one-off."
                    else ->
                        "Android accepted it. If you felt nothing, check vibration intensity in " +
                            "your sound settings."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (result.systemFeedbackEnabled && result.accepted) {
                    colors.textMute
                } else {
                    colors.danger
                },
            )
            if (!result.systemFeedbackEnabled || !result.accepted) {
                Spacer(Modifier.height(8.dp))
                SweepTextButton(
                    text = "Open sound settings",
                    onClick = {
                        SystemFlows.launchFirstAvailable(context, SystemFlows.soundSettingsIntents())
                    },
                    color = colors.accent,
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    caption: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val colors = Sweep.colors
    Column(Modifier.padding(bottom = 18.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.textMute)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                val active = option == selected
                Text(
                    text = label(option),
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
                        .pressable(onClick = { onSelect(option) })
                        .padding(horizontal = 13.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    caption: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = Sweep.colors
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = springSnappy(),
        label = "toggle",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .pressable(onClick = { onChange(!checked) })
            .padding(vertical = 8.dp)
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = colors.textMute)
        }
        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .size(width = 46.dp, height = 28.dp)
                .clip(CircleShape)
                .background(lerp(colors.surfaceHigh, colors.accent, progress))
                .border(1.dp, lerp(colors.line, colors.accent, progress), CircleShape),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    // Lambda overload: the offset is animation-backed, so this keeps the knob
                    // in the layout phase instead of recomposing every frame.
                    .offset { IntOffset(x = ((2 + progress * 18) * density).toInt(), y = 0) }
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (checked) colors.onAccent else colors.textFaint)
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    grantedText: String,
    deniedText: String,
    onOpen: () -> Unit,
) {
    val colors = Sweep.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .border(1.dp, colors.line, MaterialTheme.shapes.medium)
            .padding(15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (granted) colors.accent else colors.textFaint)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(
                text = if (granted) grantedText else deniedText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMute,
            )
        }
        if (!granted) {
            Spacer(Modifier.width(10.dp))
            SweepButton(text = "Open", onClick = onOpen, tone = ButtonTone.Neutral, dense = true)
        }
    }
}
