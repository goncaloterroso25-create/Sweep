package dev.sweep.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepShape
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.fold
import dev.sweep.ui.theme.unfold

/**
 * The way through Android's Restricted Settings.
 *
 * Android blocks sensitive toggles such as Usage Access for apps whose installer did not use the
 * session-based install API, which covers most manual installs. Store installs and `adb install`
 * are unaffected, which is why the same APK is restricted on one phone and not another. Nothing
 * about the app changes that, and Sweep does not try to work around a security control.
 *
 * It stays a quiet link, and opens by itself only for someone who has already been to the
 * settings screen and come back without the permission, so people for whom the normal flow works
 * never see a wall of troubleshooting.
 */
@Composable
fun RestrictedSettingsHelp(
    /** True once the user has been to the settings screen and returned without granting it. */
    autoExpand: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    val reduced = LocalReducedMotion.current
    var expanded by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(autoExpand) { if (autoExpand) expanded = true }

    Column(modifier) {
        TextAction(
            text = if (expanded) "Hide help" else "Usage Access switch greyed out?",
            onClick = { expanded = !expanded },
            color = colors.textMute,
        )
        AnimatedVisibility(visible = expanded, enter = unfold(reduced), exit = fold(reduced)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(SweepShape.control)
                    .background(colors.raised)
                    .padding(16.dp),
            ) {
                Text(
                    text = if (autoExpand) "Looks like Android blocked it." else "If Android blocks the switch",
                    style = SweepType.rowTitle,
                    color = colors.text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Android restricts some settings for apps installed from a file rather " +
                        "than a store. To allow it:",
                    style = SweepType.meta,
                    color = colors.textMute,
                )
                Spacer(Modifier.height(8.dp))
                Step(1, "Open Sweep's App info.")
                Step(2, "Open the menu at the top right.")
                Step(3, "Choose Allow restricted settings.")
                Step(4, "Come back and turn Usage Access on.")
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "The wording varies between manufacturers, and some phones never ask. " +
                        "That depends on how the file was installed, not on Sweep.",
                    style = SweepType.meta,
                    color = colors.textMute,
                )
                Spacer(Modifier.height(12.dp))
                SweepButton(
                    text = "Open App info",
                    onClick = {
                        SystemFlows.launchFirstAvailable(
                            context,
                            listOf(SweepPermissions.appDetailsIntent(context.packageName)),
                        )
                    },
                    tone = ButtonTone.Secondary,
                    compact = true,
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val colors = Sweep.colors
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$number", style = SweepType.meta, color = colors.signalInk, modifier = Modifier.width(18.dp))
        Text(text, style = SweepType.meta, color = colors.text)
    }
}
