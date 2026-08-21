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

    // How thoroughly the field has been read, which grows while scanning and saturates.
    //
    // This is what stops a long scan looking like a two-second loop: successive passes leave the
    // field progressively more settled rather than identical. It saturates well before most scans
    // finish, precisely so it never reads as a progress bar. The scanner cannot know how far
    // through it is, and nothing here pretends otherwise.
    val readDepth = remember { Animatable(0f) }
    LaunchedEffect(scanning, reduced) {
        if (scanning && !reduced) {
            readDepth.animateTo(1f, tween(READ_DEPTH_MS, easing = LinearEasing))
        } else {
            readDepth.snapTo(0f)
        }
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

        // Reduced motion keeps the field readable and completely still: the blocks still say how
        // full the device is and what is about to go, they just do not react to anything.
        val front = if (reduced) null else scanPhase?.value
        val resolvePass = resolve.value
        // The resolving pass reuses the same geometry as the scan front, travelling out to the
        // right, so the end of a scan looks like the last sweep rather than a separate effect.
        val resolveFront = if (resolvePass < 1f) resolvePass * 1.3f else null

        val depth = readDepth.value

        for (index in 0 until total) {
            if (index >= revealCount) continue
            val column = index / rows
            val row = index % rows
            val x = column * (blockW + gap)
            val y = row * (blockH + gap)
            val centreX = x + blockW / 2f

            val isUsed = index < usedBlocks
            val isReclaim = index in reclaimStart until usedBlocks && reclaimBlocks > 0

            // The wave this block is currently sitting in. Positive lifts it, negative presses it
            // down, which is what makes the front read as something moving through the field
            // rather than a light shining on it.
            var swell = 0f
            var glow = 0f
            var read = 0f

            if (front != null) {
                // Each row is fractionally behind the one above, so the front arrives as a soft
                // diagonal. A perfectly vertical edge looks like a wipe; this looks like a sweep.
                val frontX = (front - row * ROW_LAG) * size.width
                val distance = (centreX - frontX) / size.width

                when {
                    // Ahead of the front, briefly anticipating it.
                    distance > 0f -> {
                        val near = (1f - distance / LEAD_LENGTH).coerceIn(0f, 1f)
                        swell = near * 0.35f
                        glow = near * 0.55f
                    }
                    // The crest, where the front is passing through this block right now.
                    distance > -CREST_LENGTH -> {
                        val t = (1f + distance / CREST_LENGTH).coerceIn(0f, 1f)
                        swell = t
                        glow = t
                    }
                    // Immediately behind: compressed by what just went past, springing back.
                    distance > -(CREST_LENGTH + TROUGH_LENGTH) -> {
                        val t = ((-distance - CREST_LENGTH) / TROUGH_LENGTH).coerceIn(0f, 1f)
                        swell = -(1f - t) * 0.55f
                        glow = (1f - t) * 0.3f
                        read = t
                    }
                    // Settled, and now counted as read.
                    else -> {
                        read = 1f
                        glow = ((1f + (distance + CREST_LENGTH) / WAKE_LENGTH) * 0.18f)
                            .coerceIn(0f, 0.18f)
                    }
                }
            }

            if (resolveFront != null) {
                val pass = (1f - abs(centreX / size.width - resolveFront) / RESOLVE_WIDTH)
                    .coerceIn(0f, 1f)
                val strength = pass * (1f - resolvePass)
                swell = max(swell, strength * 0.8f)
                glow = max(glow, strength * 0.9f)
            }

            // Blocks that have just been discovered as reclaimable get their own brief lift, so
            // finding something reads differently from the front merely passing over it.
            val found = if (isReclaim) discovery.value else 0f
            val lift = (glow * 0.6f + found * 0.4f).coerceIn(0f, 1f)
            // Read regions sit fractionally brighter for the rest of the scan. It saturates, so a
            // long scan looks progressively more understood without ever implying a percentage.
            val settled = read * depth

            val grow = (swell * blockW * 0.07f) + (found * blockW * 0.05f)

            when {
                isReclaim -> {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.18f + found * 0.22f),
                        topLeft = Offset(x - 1.5f - grow, y - 1.5f - grow),
                        size = Size(blockW + 3f + grow * 2, blockH + 3f + grow * 2),
                        cornerRadius = radius,
                    )
                    drawRoundRect(
                        color = accent,
                        topLeft = Offset(x, y),
                        size = Size(blockW, blockH),
                        cornerRadius = radius,
                    )
                }

                isUsed -> {
                    drawRoundRect(
                        color = lerpColor(
                            lerpColor(usedColor, usedColor.copy(alpha = 0.52f), settled),
                            accent,
                            lift * 0.5f,
                        ),
                        topLeft = Offset(x - grow, y - grow),
                        size = Size(blockW + grow * 2, blockH + grow * 2),
                        cornerRadius = radius,
                    )
                    // A thin accent edge on the leading side of whatever the crest is touching.
                    if (glow > 0.55f) {
                        drawRoundRect(
                            color = accent.copy(alpha = (glow - 0.55f) * 1.6f),
                            topLeft = Offset(x + blockW - EDGE_WIDTH, y - grow),
                            size = Size(EDGE_WIDTH, blockH + grow * 2),
                            cornerRadius = radius,
                        )
                    }
                }

                else -> {
                    val hollow = blockW * 0.22f
                    drawRoundRect(
                        color = lerpColor(freeColor, accent.copy(alpha = 0.4f), lift),
                        topLeft = Offset(x + hollow / 2f - grow, y + hollow / 2f - grow),
                        size = Size(blockW - hollow + grow * 2, blockH - hollow + grow * 2),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }
}

/**
 * The shape of the wave, as fractions of the field's width.
 *
 * Together these are the whole feel of the scan: a short anticipation, a crest where the front is
 * currently working, a trough immediately behind it where blocks are still recovering, and a long
 * quiet wake. Widening the crest makes the sweep feel heavier; widening the trough makes it feel
 * springier.
 */
private const val LEAD_LENGTH = 0.05f
private const val CREST_LENGTH = 0.07f
private const val TROUGH_LENGTH = 0.10f
private const val WAKE_LENGTH = 0.32f

/** Each row trails the one above by this fraction, turning the front into a soft diagonal. */
private const val ROW_LAG = 0.022f

/** Width of the single resolving pass, as a fraction of the field. */
private const val RESOLVE_WIDTH = 0.22f

/** The accent edge revealed on the leading side of a block at the crest, in pixels. */
private const val EDGE_WIDTH = 2f

/** How finely growth in the reclaimable region is split into separate discoveries. */
private const val DISCOVERY_STEPS = 40

/** Roughly three passes. Long enough to feel like it is building, short enough to settle. */
private const val READ_DEPTH_MS = 5200

private fun lerpColor(from: Color, to: Color, t: Float): Color =
    if (t <= 0f) from else androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))
