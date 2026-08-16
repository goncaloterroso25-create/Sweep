package dev.sweep.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springGentle
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Sweep's storage visualisation: a field of blocks, each one a slice of the device.
 *
 * It is the same object in every state, which is what makes the app feel like one continuous
 * thing rather than a set of screens:
 *  - at rest      the filled blocks are the space in use, the outlined ones are free
 *  - scanning     a light travels across the field and blocks turn accent as candidates are found
 *  - after a scan the accent blocks are exactly what a clean would give back
 *  - after a clean those blocks drain back into free space
 *
 * The proportions come straight from real figures. Nothing here is decorative filler.
 */
@Composable
fun StorageMeter(
    usedFraction: Float,
    reclaimFraction: Float,
    scanning: Boolean,
    modifier: Modifier = Modifier,
    rows: Int = 5,
    height: Dp = 96.dp,
    contentDescription: String? = null,
) {
    val colors = Sweep.colors
    val reduced = LocalReducedMotion.current

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

    // Created only while a scan is running. An InfiniteTransition keeps asking for frames for as
    // long as it is composed, so leaving one alive on an idle Home screen would burn battery for
    // an animation nobody is looking at. Kept as a State and read inside the draw lambda, so the
    // travelling light invalidates drawing only — it never recomposes this composable.
    val sweepPhase: State<Float>? = if (scanning && !reduced) {
        rememberInfiniteTransition(label = "sweepLight").animateFloat(
            initialValue = -0.25f,
            targetValue = 1.25f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "phase",
        )
    } else {
        null
    }

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

        for (index in 0 until total) {
            if (index >= revealCount) continue
            val column = index / rows
            val row = index % rows
            val x = column * (blockW + gap)
            val y = row * (blockH + gap)

            val isUsed = index < usedBlocks
            val isReclaim = index in reclaimStart until usedBlocks && reclaimBlocks > 0

            // The scan light brightens whatever it is passing over.
            val lightBoost = if (sweepPhase != null) {
                val centre = sweepPhase.value * size.width
                val distance = kotlin.math.abs(x + blockW / 2f - centre)
                val falloff = (1f - distance / (size.width * 0.18f)).coerceIn(0f, 1f)
                falloff * falloff
            } else {
                0f
            }

            when {
                isReclaim -> {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.18f),
                        topLeft = Offset(x - 1.5f, y - 1.5f),
                        size = Size(blockW + 3f, blockH + 3f),
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
                    color = lerpColor(usedColor, accent, lightBoost * 0.5f),
                    topLeft = Offset(x, y),
                    size = Size(blockW, blockH),
                    cornerRadius = radius,
                )

                else -> {
                    val inset = blockW * 0.22f
                    drawRoundRect(
                        color = lerpColor(freeColor, accent.copy(alpha = 0.4f), lightBoost),
                        topLeft = Offset(x + inset / 2f, y + inset / 2f),
                        size = Size(blockW - inset, blockH - inset),
                        cornerRadius = radius,
                    )
                }
            }
        }

        if (sweepPhase != null) {
            val bandWidth = size.width * 0.14f
            val centre = sweepPhase.value * size.width
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, accent.copy(alpha = 0.10f), Color.Transparent),
                    startX = centre - bandWidth,
                    endX = centre + bandWidth,
                ),
                topLeft = Offset(centre - bandWidth, 0f),
                size = Size(bandWidth * 2, size.height),
            )
        }
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color =
    if (t <= 0f) from else androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))
