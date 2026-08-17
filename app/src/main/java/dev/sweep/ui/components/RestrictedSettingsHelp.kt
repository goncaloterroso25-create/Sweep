package dev.sweep.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springGentle
import dev.sweep.ui.theme.sweepTween

/**
 * The way out of Android's Restricted Settings.
 *
 * Android blocks sensitive toggles like Usage Access for apps that were installed manually rather
 * than from a store, and the switch simply refuses with "App was denied access". That is Android
 * protecting the user from sideloaded apps, which is reasonable, and Sweep does not try to work
 * around it.
 *
 * What it can do is explain the way through. This stays a quiet link most of the time, and opens
 * itself only for someone who has already tried and come back without the permission, so a user
 * for whom the normal flow works never sees a wall of troubleshooting.
 */
@Composable
fun RestrictedSettingsHelp(
    /** True once the user has been to the settings screen and returned without granting it. */
    autoExpand: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }

    LaunchedEffect(autoExpand) { if (autoExpand) expanded = true }

    Column(modifier) {
        SweepTextButton(
            text = if (expanded) "Hide" else "Can't enable Usage Access?",
            onClick = { expanded = !expanded },
            color = colors.info,
        )

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(springGentle()) + fadeIn(sweepTween(180)),
            exit = shrinkVertically(springGentle()) + fadeOut(sweepTween(140)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.surfaceHigh)
                    .padding(16.dp),
            ) {
                Text(
                    text = "Android restricts some settings for apps installed manually instead " +
                        "of from a store. If the Usage Access switch is greyed out or says the " +
                        "app was denied access:",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMute,
                )
                Spacer(Modifier.height(10.dp))
                Step(1, "Open Sweep's App info below.")
                Step(2, "Open the menu in the top right, if there is one.")
                Step(3, "Choose Allow restricted settings.")
                Step(4, "Come back and turn Usage Access on again.")
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Manufacturers word this differently, so the menu may not match exactly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                )
                Spacer(Modifier.height(14.dp))
                SweepButton(
                    text = "Open App info",
                    onClick = {
                        SystemFlows.launchFirstAvailable(
                            context,
                            listOf(SweepPermissions.appDetailsIntent(context.packageName)),
                        )
                    },
                    tone = ButtonTone.Neutral,
                    dense = true,
                )
            }
        }
    }
}

@Composable
private fun Step(number: Int, text: String) {
    val colors = Sweep.colors
    Text(
        text = "$number. $text",
        style = MaterialTheme.typography.bodySmall,
        color = colors.text,
        modifier = Modifier.padding(vertical = 3.dp),
    )
}
