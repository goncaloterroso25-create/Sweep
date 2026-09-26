package dev.sweep.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * What the tally is currently showing, as fractions of the whole device.
 *
 * Every figure here comes from a measurement. [clearedFraction] in particular is only ever bytes
 * the deleter has confirmed gone; free space itself is never moved until Android has been asked
 * again, which is what [usedFraction] reflects.
 */
data class TallyFigures(
    val usedFraction: Float,
    /** Found by a scan, whether or not selected. Always inside the used region. */
    val foundFraction: Float = 0f,
    /** Selected for deletion. Always inside the found region. */
    val selectedFraction: Float = 0f,
    /** Confirmed deleted, waiting for Android's next measurement to become free space. */
    val clearedFraction: Float = 0f,
)

/**
 * Sweep's picture of the device: a tally of round-capped strokes, each one an equal share.
 *
 * Tall strokes are storage in use, short stubs are free, and whatever a scan finds collects in lime
 * at the edge of the used region, where it would become free if it went. It replaced a field of
 * blocks that said the same thing less directly and suggested a precision it did not have: a tally
 * is visibly counted in steps, which is exactly how precise it is.
 *
 * It is the same object in every state, and it moves only when something happened:
 *  - scanning: a front travels through the strokes, which lean in its wake and settle back. How
 *    fast it travels follows how busy the scanner actually is, so it slows when the scanner is
 *    grinding through large files and never loops at a fixed rate. The first pass reads the
 *    strokes from dim to full; later passes are calmer. None of it is a percentage.
 *  - discovering: strokes join the lime region as the found total grows
 *  - deleting: selected strokes drop to stubs as each file is confirmed gone
 *  - after deleting: the whole tally re-forms around Android's fresh measurement
 *
 * Reduced motion keeps every state and removes every movement: no front, no lean, no springing.
 */
@Composable
fun StorageTally(
    figures: TallyFigures,
    scanning: Boolean,
    /** Bumped on every progress update from the scanner. Drives the front's speed. */
    activity: Int,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    description: String,
) {
    val colors = Sweep.colors
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val sim = remember { TallySimulation() }

    SideEffect {
        sim.reduced = reduced
        sim.update(figures, scanning)
    }
    LaunchedEffect(activity) { sim.pulse() }

    // The frame loop exists only while something is unsettled: a running scan, a front finishing
    // its last pass, or strokes springing to new heights. An idle tally asks for no frames at all.
    LaunchedEffect(sim) {
        while (true) {
            snapshotFlow { sim.wantsFrames }.first { it }
            var last = withFrameNanos { it }
            while (sim.wantsFrames) {
                withFrameNanos { now ->
                    sim.step(((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f))
                    last = now
                }
            }
        }
    }

    val pitch = with(density) { PITCH.toPx() }
    val stroke = with(density) { STROKE.toPx() }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .onSizeChanged { size ->
                val count = ((size.width + pitch - stroke) / pitch).toInt().coerceIn(MIN_TICKS, MAX_TICKS)
                sim.resize(count)
            }
            .semantics { contentDescription = description },
    ) {
        // Reading the frame counter here, and only here, keeps every frame of motion in the draw
        // phase: the tally never recomposes or relayouts while it animates.
        @Suppress("UNUSED_VARIABLE") val frame = sim.frame
        val count = sim.count
        if (count == 0) return@Canvas

        val gap = (size.width - stroke) / max(1, count - 1)
        val bottom = size.height - stroke / 2f
        val fullHeight = size.height - stroke
        val passStrength = sim.amplitude

        for (i in 0 until count) {
            val x = stroke / 2f + i * gap
            val position = if (count == 1) 0f else i.toFloat() / (count - 1)

            var lean = 0f
            var press = 0f
            if (passStrength > 0f) {
                val behind = (sim.front - position) / WAKE
                if (behind >= 0f) {
                    // A damped swing: pushed over as the front passes, one soft rebound, then still.
                    lean = exp(-behind * 1.7f) * cos(behind * 2.6f)
                    press = exp(-behind * 5f)
                } else if (behind > -0.35f) {
                    press = (1f + behind / 0.35f) * 0.5f
                }
                lean *= passStrength
                press *= passStrength
            }

            val h = fullHeight * sim.heights[i] * (1f - press * 0.14f)
            val angle = lean * MAX_LEAN
            val topX = x + sin(angle) * h
            val topY = bottom - cos(angle) * h

            val base = colorFor(sim.classes[i], colors.tallyUsed, colors.tallyFree, colors.tallyFound, colors.tallySelected)
            val from = colorFor(sim.previous[i], colors.tallyUsed, colors.tallyFree, colors.tallyFound, colors.tallySelected)
            var color = if (sim.mix[i] >= 1f) base else lerp(from, base, sim.mix[i])
            color = color.copy(alpha = color.alpha * sim.read[i])

            drawLine(
                color = color,
                start = Offset(x, bottom),
                end = Offset(topX, topY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

private fun colorFor(kind: Int, used: Color, free: Color, found: Color, selected: Color): Color = when (kind) {
    USED -> used
    FOUND -> found
    SELECTED, CLEARED -> selected
    else -> free
}

private const val USED = 0
private const val FOUND = 1
private const val SELECTED = 2
private const val CLEARED = 3
private const val FREE = 4

private val PITCH = 5.6.dp
private val STROKE = 2.6.dp
private const val MIN_TICKS = 24
private const val MAX_TICKS = 120

/** The furthest a stroke leans at the crest of the front: the mark's own tilt, 14 degrees. */
private const val MAX_LEAN = (14.0 * PI / 180.0).toFloat()

/** How far behind the front the wake reaches, as a fraction of the width. */
private const val WAKE = 0.16f

private const val FREE_HEIGHT = 0.26f

/** Unread strokes during the first pass of a scan. */
private const val UNREAD = 0.36f

/**
 * The physics behind the tally: a front, per-stroke springs for height, and a read state.
 *
 * Plain arrays and a frame counter rather than Compose animations, because a hundred and twenty
 * springs as Animatables would be a hundred and twenty coroutines for something one loop does.
 */
@Stable
private class TallySimulation {
    var reduced = false

    var count by mutableIntStateOf(0)
        private set
    var frame by mutableIntStateOf(0)
        private set
    var wantsFrames by mutableStateOf(false)
        private set

    var heights = FloatArray(0); private set
    private var velocities = FloatArray(0)
    var classes = IntArray(0); private set
    var previous = IntArray(0); private set
    var mix = FloatArray(0); private set
    var read = FloatArray(0); private set
    private var readTarget = FloatArray(0)

    /** Position of the front, 0 at the left edge and 1 at the right. Travels past both ends. */
    var front = FRONT_START; private set
    /** How strongly the front bends the strokes. Rises as a scan starts, falls as it resolves. */
    var amplitude = 0f; private set

    private var figures = TallyFigures(0f)
    private var scanning = false
    private var resolving = false
    private var passes = 0
    private var energy = 1f

    fun resize(newCount: Int) {
        if (newCount == count) return
        count = newCount
        heights = FloatArray(newCount)
        velocities = FloatArray(newCount)
        classes = IntArray(newCount)
        previous = IntArray(newCount)
        mix = FloatArray(newCount) { 1f }
        read = FloatArray(newCount) { 1f }
        readTarget = FloatArray(newCount) { 1f }
        applyTargets(snap = true)
        frame++
    }

    fun update(next: TallyFigures, nowScanning: Boolean) {
        val startedScan = nowScanning && !scanning
        val endedScan = !nowScanning && scanning
        val changed = next != figures
        figures = next
        scanning = nowScanning

        if (startedScan && !reduced) {
            front = FRONT_START
            passes = 0
            energy = 1f
            resolving = false
            // Everything in use starts unread, and the first pass of the front reads it.
            for (i in 0 until count) if (classes[i] != FREE) readTarget[i] = UNREAD
        }
        if (endedScan) {
            resolving = !reduced
            for (i in 0 until count) readTarget[i] = 1f
        }
        if (changed || startedScan || endedScan) applyTargets(snap = reduced)
        if (reduced) {
            for (i in 0 until count) {
                read[i] = 1f
                readTarget[i] = 1f
            }
            amplitude = 0f
            frame++
            return
        }
        if (changed || startedScan || endedScan || scanning || resolving) wantsFrames = true
    }

    fun pulse() {
        energy = 1f
    }

    private fun applyTargets(snap: Boolean) {
        val n = count
        if (n == 0) return
        val used = (figures.usedFraction * n).roundToInt().coerceIn(0, n)
        val found = atLeastOne(figures.foundFraction, n).coerceAtMost(used)
        val selected = atLeastOne(figures.selectedFraction, n).coerceAtMost(found)
        val cleared = (figures.clearedFraction * n).roundToInt().coerceAtMost(selected)

        for (i in 0 until n) {
            // From the right-hand end of the used region inward: cleared, then selected, then
            // found. What is closest to becoming free sits closest to the free space.
            val fromEdge = used - 1 - i
            val kind = when {
                i >= used -> FREE
                fromEdge < cleared -> CLEARED
                fromEdge < selected -> SELECTED
                fromEdge < found -> FOUND
                else -> USED
            }
            if (kind != classes[i]) {
                previous[i] = if (snap) kind else classes[i]
                classes[i] = kind
                mix[i] = if (snap) 1f else 0f
            }
            if (snap) {
                heights[i] = targetHeight(kind)
                velocities[i] = 0f
            }
        }
    }

    private fun atLeastOne(fraction: Float, n: Int): Int =
        if (fraction <= 0f) 0 else max(1, (fraction * n).roundToInt())

    private fun targetHeight(kind: Int) = if (kind == FREE || kind == CLEARED) FREE_HEIGHT else 1f

    fun step(dt: Float) {
        var unsettled = false

        // ---- the front ----
        if (scanning || resolving) {
            val speed = if (resolving) RESOLVE_SPEED else BASE_SPEED * (0.32f + 0.78f * energy)
            energy *= exp(-dt / ENERGY_DECAY)
            front += speed * dt

            if (scanning) {
                // Later passes are calmer. It never gets louder over time, and never stops while
                // there is real work happening.
                val target = max(0.42f, 1f - passes * 0.18f)
                amplitude += (target - amplitude) * min(1f, dt * 4f)
                if (front > FRONT_END) {
                    front = FRONT_START
                    passes++
                }
            } else {
                // Resolving: the front runs out of the field once, then the strokes settle.
                amplitude += (0f - amplitude) * min(1f, dt * 2.2f)
                if (front > FRONT_END || amplitude < 0.01f) {
                    resolving = false
                    amplitude = 0f
                }
            }

            // The front reads what it passes. After the first full pass, everything is read.
            for (i in 0 until count) {
                val position = if (count == 1) 0f else i.toFloat() / (count - 1)
                if (passes > 0 || position < front) readTarget[i] = 1f
            }
            unsettled = true
        }

        // ---- per-stroke springs ----
        for (i in 0 until count) {
            val target = targetHeight(classes[i])
            val displacement = heights[i] - target
            val acceleration = -STIFFNESS * displacement - DAMPING * velocities[i]
            velocities[i] += acceleration * dt
            heights[i] += velocities[i] * dt
            if (kotlin.math.abs(displacement) > 0.002f || kotlin.math.abs(velocities[i]) > 0.01f) {
                unsettled = true
            } else {
                heights[i] = target
                velocities[i] = 0f
            }

            if (mix[i] < 1f) {
                mix[i] = min(1f, mix[i] + dt / COLOR_SECONDS)
                unsettled = true
            }

            val readDelta = readTarget[i] - read[i]
            if (kotlin.math.abs(readDelta) > 0.005f) {
                read[i] += readDelta * min(1f, dt * if (readDelta > 0f) 7f else 10f)
                unsettled = true
            } else {
                read[i] = readTarget[i]
            }
        }

        frame++
        if (!unsettled) wantsFrames = false
    }

    private companion object {
        const val FRONT_START = -0.18f
        const val FRONT_END = 1.3f

        /** Widths per second when the scanner is fully busy: a pass takes about a second and a half. */
        const val BASE_SPEED = 0.95f
        const val RESOLVE_SPEED = 2.4f

        /** How quickly the front slows once the scanner stops reporting. */
        const val ENERGY_DECAY = 0.7f

        const val STIFFNESS = 260f
        const val DAMPING = 26f
        const val COLOR_SECONDS = 0.28f
    }
}
