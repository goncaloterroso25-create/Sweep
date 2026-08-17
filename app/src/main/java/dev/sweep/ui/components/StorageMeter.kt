package dev.sweep.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion
import dev.sweep.ui.theme.springGentle
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sweep's storage visualisation: a field of blocks, each one a slice of the device.
 *
 * It is the same object in every state, which is what makes the app feel like one continuous
 * thing rather than a set of screens:
 *  - at rest      the filled blocks are the space in use, the outlined ones are free
 *  - scanning     a front travels through the field, blocks respond as it passes, and a wake of
 *                 residual light follows it
 *  - discovering  blocks entering the reclaimable region pulse once as they are found
 *  - resolving    when the scan stops, one last pass runs out of the field and it settles
 *  - after a scan the accent blocks are exactly what a clean would give back
 *  - after a clean those blocks drain back into free space
 *
 * The proportions come from real figures. The motion does not: the front's position says nothing
 * about how far through the scan Sweep is, because the scanner cannot know that until it finishes.
 * It communicates activity and discovery, never a percentage.
 */
@Composable
fun StorageMeter(
    usedFraction: Float,
    reclaimFraction: Float,
    scanPhase: State<Float>?,
    modifier: Modifier = Modifier,
    rows: Int = 5,
    height: Dp = 96.dp,
    contentDescription: String? = null,
) {
    val colors = Sweep.colors
    val reduced = LocalReducedMotion.current
    val scanning = scanPhase != null

    val used by animateFloatAsState(
        targetValue = usedFraction.coerceIn(0f, 1f),
        animationSpec = springGentle(),
        label = "used",
    )
    val reclaim by animateFloatAsState(
        targetValue = reclaimFraction.coerceIn(0f, 1f),
        animationSpec = springGentle(),
        label = "reclaim",
    )

    // One-off reveal so the field assembles itself the first time it appears.
    var reveal by remember { mutableFloatStateOf(if (reduced) 1f else 0f) }
    val revealed by animateFloatAsState(
        targetValue = reveal,
        animationSpec = tween(720, easing = LinearEasing),
        label = "reveal",
    )
    LaunchedEffect(Unit) { reveal = 1f }

    // Fires when the reclaimable region grows noticeably, which during a scan means "something was
    // just found". Read inside the draw lambda, so a discovery costs a redraw and not a recompose.
    //
    // Quantised on purpose: the raw fraction changes many times a second while a scan is running,
    // and restarting the pulse on every one of those would leave the blocks permanently lit and
    // churn through effects for nothing. A step is worth about 2.5% of the device.
    val discoveryStep = (reclaimFraction * DISCOVERY_STEPS).toInt()
    val discovery = remember { Animatable(0f) }
    LaunchedEffect(discoveryStep, reduced) {
        if (reduced || discoveryStep <= 0) return@LaunchedEffect
        discovery.snapTo(1f)
        discovery.animateTo(0f, tween(520, easing = SweepMotion.Standard))
    }

    // The scan resolving: one bright pass leaving the field after the last block is counted.
    val resolve = rememberResolveProgress(trigger = scanning)

    val freeColor = colors.line.copy(alpha = 0.75f)
    val usedColor = colors.textMute.copy(alpha = 0.38f)
    val accent = colors.accent

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription?.let { this.contentDescription = it } }
    ) {
        val gap = 4.dp.toPx()
        val targetBlock = 13.dp.toPx()
        val columns = max(10, ((size.width + gap) / (targetBlock + gap)).roundToInt())
        val blockW = (size.width - gap * (columns - 1)) / columns
        val blockH = (size.height - gap * (rows - 1)) / rows
        val radius = CornerRadius(blockW.coerceAtMost(blockH) * 0.28f)
        val total = columns * rows

        val usedBlocks = (total * used).roundToInt().coerceIn(0, total)
        val reclaimBlocks = (total * reclaim).roundToInt().coerceIn(0, usedBlocks)
        // Reclaimable space sits at the trailing edge of the used region: the part about to go.
        val reclaimStart = usedBlocks - reclaimBlocks

        val revealCount = if (reduced) total else (total * revealed).roundToInt()

        // Reduced motion keeps the field readable and completely still: the blocks still say how
        // full the device is and what is about to go, they just do not react to anything.
        val front = if (reduced) null else scanPhase?.value
        val resolvePass = resolve.value
        // The resolving pass reuses the same geometry as the scan front, travelling out to the
        // right, so the end of a scan looks like the last sweep rather than a separate effect.
        val resolveFront = if (resolvePass < 1f) resolvePass * 1.3f else null

        for (index in 0 until total) {
            if (index >= revealCount) continue
            val column = index / rows
            val row = index % rows
            val x = column * (blockW + gap)
            val y = row * (blockH + gap)
            val centreX = x + blockW / 2f

            val isUsed = index < usedBlocks
            val isReclaim = index in reclaimStart until usedBlocks && reclaimBlocks > 0

            // How strongly this block is reacting right now: the leading edge is brightest, and
            // everything the front has already crossed keeps a little light that decays behind it.
            var response = 0f
            if (front != null) {
                val frontX = front * size.width
                val distance = centreX - frontX
                response = if (distance <= 0f) {
                    // Behind the front: the wake.
                    val wake = (1f + distance / (size.width * WAKE_LENGTH)).coerceIn(0f, 1f)
                    wake * wake * 0.55f
                } else {
                    // Just ahead of it: a short anticipation.
                    (1f - distance / (size.width * LEAD_LENGTH)).coerceIn(0f, 1f) * 0.8f
                }
            }
            if (resolveFront != null) {
                val pass = (1f - abs(centreX / size.width - resolveFront) / RESOLVE_WIDTH)
                    .coerceIn(0f, 1f)
                response = max(response, pass * (1f - resolvePass) * 0.9f)
            }

            // Blocks that have just been discovered as reclaimable get their own brief lift, so
            // finding something reads differently from the front merely passing over it.
            val found = if (isReclaim) discovery.value else 0f
            val lift = (response * 0.6f + found * 0.4f).coerceIn(0f, 1f)
            val inset = -lift * blockW * 0.06f

            when {
                isReclaim -> {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.18f + found * 0.22f),
                        topLeft = Offset(x - 1.5f - inset, y - 1.5f - inset),
                        size = Size(blockW + 3f + inset * 2, blockH + 3f + inset * 2),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(x, y),
                        size = Size(blockW, blockH),
                        cornerRadius = radius,
                    )
                }

                isUsed -> drawRoundRect(
                    color = lerpColor(usedColor, accent, lift * 0.5f),
                    topLeft = Offset(x - inset, y - inset),
                    size = Size(blockW + inset * 2, blockH + inset * 2),
                    cornerRadius = radius,
                )

                else -> {
                    val hollow = blockW * 0.22f
                    drawRoundRect(
                        color = lerpColor(freeColor, accent.copy(alpha = 0.4f), lift),
                        topLeft = Offset(x + hollow / 2f - inset, y + hollow / 2f - inset),
                        size = Size(blockW - hollow + inset * 2, blockH - hollow + inset * 2),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/** How far behind the front its wake stays visible, as a fraction of the field's width. */
private const val WAKE_LENGTH = 0.30f

/** The short brightening just ahead of the front. */
private const val LEAD_LENGTH = 0.06f

/** Width of the single resolving pass, as a fraction of the field. */
private const val RESOLVE_WIDTH = 0.22f

/** How finely growth in the reclaimable region is split into separate discoveries. */
private const val DISCOVERY_STEPS = 40

private fun lerpColor(from: Color, to: Color, t: Float): Color =
    if (t <= 0f) from else androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))
