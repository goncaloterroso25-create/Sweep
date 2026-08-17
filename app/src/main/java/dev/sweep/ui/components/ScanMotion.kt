package dev.sweep.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion

/**
 * The position of the scan front, 0 to 1 across the screen, shared by everything that reacts to it.
 *
 * Hoisted rather than created per component for one practical reason: the block field and the
 * light behind it have to agree on where the front is, and two independent infinite transitions
 * drift apart within seconds. It also means there is exactly one animation clock to stop.
 *
 * The transition only exists while [active] is true, so an idle Home screen asks for no frames.
 */
@Composable
fun rememberSweepPhase(active: Boolean): State<Float> {
    val reduced = LocalReducedMotion.current
    val idle = remember { mutableFloatStateOf(0f) }

    if (!active || reduced) return idle

    return rememberInfiniteTransition(label = "sweepFront").animateFloat(
        initialValue = -0.15f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(SweepMotion.SWEEP_PERIOD_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )
}

/**
 * Runs 0 to 1 once, each time [trigger] flips from true to false.
 *
 * This is the "the scan resolved" moment: one pass of light leaving the field, after which
 * everything is at rest. Held at 1 afterwards so nothing keeps drawing.
 */
@Composable
fun rememberResolveProgress(trigger: Boolean): State<Float> {
    val reduced = LocalReducedMotion.current
    val progress = remember { Animatable(1f) }
    val wasActive = remember { mutableStateOf(false) }

    LaunchedEffect(trigger, reduced) {
        if (trigger) {
            wasActive.value = true
            progress.snapTo(0f)
        } else if (wasActive.value) {
            wasActive.value = false
            if (reduced) {
                progress.snapTo(1f)
            } else {
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(SweepMotion.RESOLVE_MS, easing = SweepMotion.Emphasized),
                )
            }
        }
    }
    return progress.asState()
}

/**
 * A very faint pool of accent light that travels with the scan front, behind the storage field.
 *
 * This is the whole background treatment: no gradients, no particles, no permanent glow. It exists
 * only while a scan is running, it is drawn in [drawBehind] so it never recomposes anything, and
 * at its brightest it is a few percent of the accent colour. The intent is that the background
 * feels warmer where Sweep is currently looking, without the user consciously noticing why.
 */
@Composable
fun Modifier.sweepGlow(phase: State<Float>, active: Boolean): Modifier {
    val accent = Sweep.colors.accent
    val reduced = LocalReducedMotion.current
    if (!active || reduced) return this

    return this.drawBehind {
        val centre = phase.value * size.width
        val radius = size.height.coerceAtLeast(size.width * 0.22f)
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(accent.copy(alpha = 0.07f), Color.Transparent),
                center = Offset(centre, size.height * 0.55f),
                radius = radius,
            ),
            topLeft = Offset(centre - radius, 0f),
            size = Size(radius * 2, size.height),
        )
    }
}
