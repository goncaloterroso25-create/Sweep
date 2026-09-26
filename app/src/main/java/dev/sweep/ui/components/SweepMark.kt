package dev.sweep.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepType

/**
 * The mark, at the launcher icon's 14 degree tilt, in the signal colour.
 *
 * Still. The brand moment is the launch, handled by the splash screen, and happens once per cold
 * start. A logo that moves every time Home appears is a logo people learn to resent.
 */
@Composable
fun SweepMark(modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Icon(
        imageVector = SweepIcons.Mark,
        contentDescription = null,
        tint = Sweep.colors.signalInk,
        modifier = modifier
            .size(size)
            .graphicsLayer { rotationZ = -14f },
    )
}

@Composable
fun SweepWordmark(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "Sweep"
            heading()
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SweepMark()
        Spacer(Modifier.width(8.dp))
        Text("Sweep", style = SweepType.headline, color = Sweep.colors.text)
    }
}
