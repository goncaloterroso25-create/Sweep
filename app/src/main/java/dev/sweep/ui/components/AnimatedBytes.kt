package dev.sweep.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.ByteFormat
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion
import androidx.compose.animation.core.tween

/**
 * A byte figure that counts to its new value instead of jumping.
 *
 * Number and unit are separate text nodes sharing a baseline, so the figure can be set large and
 * tight while "GB" stays quiet — the detail that makes a storage readout feel designed rather
 * than printed. Screen readers get the whole thing as one string.
 */
@Composable
fun AnimatedBytes(
    bytes: Long,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = MaterialTheme.typography.displayLarge,
    unitStyle: TextStyle = MaterialTheme.typography.headlineSmall,
    valueColor: Color = Sweep.colors.text,
    unitColor: Color = Sweep.colors.textMute,
    animate: Boolean = true,
) {
    val reduced = LocalReducedMotion.current
    val animated = remember { Animatable(bytes.toFloat()) }

    LaunchedEffect(bytes, animate, reduced) {
        if (!animate || reduced) {
            animated.snapTo(bytes.toFloat())
        } else {
            animated.animateTo(
                targetValue = bytes.toFloat(),
                animationSpec = tween(durationMillis = 620, easing = SweepMotion.Emphasized),
            )
        }
    }

    val shown = ByteFormat.format(animated.value.toLong().coerceAtLeast(0L))
    val spoken = ByteFormat.short(bytes)

    Row(modifier = modifier.semantics { contentDescription = spoken }) {
        Text(
            text = shown.value,
            style = valueStyle,
            color = valueColor,
            modifier = Modifier.alignByBaseline(),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = shown.unit,
            style = unitStyle,
            color = unitColor,
            modifier = Modifier.alignByBaseline(),
        )
    }
}
