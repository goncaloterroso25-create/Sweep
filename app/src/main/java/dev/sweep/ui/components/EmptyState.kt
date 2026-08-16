package dev.sweep.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion

/**
 * "Nothing to clean" should feel like an outcome, not a missing list — so it gets the same
 * attention as a result screen: a mark that draws itself, then the copy.
 */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    accent: Boolean = true,
) {
    val colors = Sweep.colors
    val reduced = LocalReducedMotion.current
    val tint = if (accent) colors.accent else colors.textMute

    val draw = remember { Animatable(if (reduced) 1f else 0f) }
    val ring = remember { Animatable(if (reduced) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        draw.animateTo(1f, tween(620, easing = SweepMotion.Emphasized))
        ring.animateTo(1f, tween(720, easing = SweepMotion.Standard))
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Canvas(Modifier.size(72.dp)) {
            val centre = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2f - 6.dp.toPx()
            val t = draw.value

            // A single ring pulses outward once, then settles. No confetti.
            if (ring.value > 0f && ring.value < 1f) {
                drawCircle(
                    color = tint.copy(alpha = (1f - ring.value) * 0.28f),
                    radius = radius + ring.value * 16.dp.toPx(),
                    center = centre,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            drawCircle(
                color = tint.copy(alpha = 0.16f),
                radius = radius * (0.6f + 0.4f * t),
                center = centre,
            )
            drawCircle(
                color = tint.copy(alpha = 0.55f),
                radius = radius,
                center = centre,
                style = Stroke(width = 1.4.dp.toPx()),
            )

            val w = size.width
            val h = size.height
            val p0 = Offset(w * 0.33f, h * 0.51f)
            val p1 = Offset(w * 0.45f, h * 0.64f)
            val p2 = Offset(w * 0.68f, h * 0.37f)
            val stroke = 3.dp.toPx()

            val first = (t / 0.4f).coerceIn(0f, 1f)
            if (first > 0f) {
                drawLine(
                    color = tint,
                    start = p0,
                    end = Offset(p0.x + (p1.x - p0.x) * first, p0.y + (p1.y - p0.y) * first),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
            val second = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
            if (second > 0f) {
                drawLine(
                    color = tint,
                    start = p1,
                    end = Offset(p1.x + (p2.x - p1.x) * second, p1.y + (p2.y - p1.y) * second),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.textMute,
            textAlign = TextAlign.Center,
        )
    }
}

/** Neutral variant for states that are not a success, like a cancelled scan. */
@Composable
fun QuietState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Sweep.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = colors.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMute,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
