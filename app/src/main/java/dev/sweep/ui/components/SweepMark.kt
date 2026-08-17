package dev.sweep.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.Grotesk
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion
import androidx.compose.animation.core.tween

/**
 * The Sweep mark: three bars shortening as they descend, with one fragment left behind.
 *
 * Same geometry as the launcher icon, redrawn here rather than loaded as a vector so it can react
 * to what the app is doing. It reacts rarely and briefly: the bars draw themselves in when Home
 * first appears, and extend once when a scan starts. It is otherwise completely still, because a
 * logo that animates while you read is a logo you end up resenting.
 */
@Composable
fun SweepMark(
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    /** Bumped by the caller when something worth acknowledging happens, such as a scan starting. */
    pulseKey: Int = 0,
) {
    val colors = Sweep.colors
    val reduced = LocalReducedMotion.current

    // How much of each bar is drawn. Runs once on entrance, and again on a pulse.
    val extend = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(reduced) {
        if (reduced) extend.snapTo(1f) else extend.animateTo(1f, tween(560, easing = SweepMotion.Emphasized))
    }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0 || reduced) return@LaunchedEffect
        extend.snapTo(0.55f)
        extend.animateTo(1f, tween(520, easing = SweepMotion.Emphasized))
    }

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Sweep" }
    ) {
        val w = this.size.width
        val h = this.size.height
        val stroke = w * 0.115f
        val right = w * 0.84f
        // The bars sit on a slight incline, as in the icon, so the mark reads as motion rather
        // than as a list.
        val tilt = h * 0.055f

        // Longest bar on top, each one shorter, matching the icon's proportions.
        val lengths = floatArrayOf(0.62f, 0.40f, 0.20f)
        val progress = extend.value

        lengths.forEachIndexed { index, length ->
            val y = h * (0.30f + index * 0.20f) + (index - 1) * tilt
            // Bars extend from the right edge back to the left, in the direction of the sweep.
            val drawn = length * progress
            drawLine(
                color = colors.accent,
                start = androidx.compose.ui.geometry.Offset(right - w * drawn, y),
                end = androidx.compose.ui.geometry.Offset(right, y),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }

        // The fragment that has not been swept yet.
        val fragmentY = h * 0.70f + tilt
        drawLine(
            color = colors.accent.copy(alpha = 0.45f * progress),
            start = androidx.compose.ui.geometry.Offset(w * 0.15f, fragmentY),
            end = androidx.compose.ui.geometry.Offset(w * 0.17f, fragmentY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** The mark and the wordmark, locked together as the app's header identity. */
@Composable
fun SweepWordmark(modifier: Modifier = Modifier, pulseKey: Int = 0) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        SweepMark(pulseKey = pulseKey)
        Spacer(Modifier.width(9.dp))
        Text(
            text = "Sweep",
            style = MaterialTheme.typography.headlineMedium.copy(fontFamily = Grotesk),
            color = Sweep.colors.text,
        )
    }
}
