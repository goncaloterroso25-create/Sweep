package dev.sweep.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.IntSize

/**
 * One physical idea, used everywhere: Sweep moves things along a single horizontal axis.
 *
 *  - Arrive: new content enters from the left, a short distance, and settles. A category a scan
 *    has just found, a changed figure, the next state of a panel. It has been swept into place.
 *  - Clear: content that goes away leaves to the right, the direction the sweep travels. A panel
 *    state being replaced, the mark as the splash lifts. In the tally, what a delete removed
 *    drops to a stub at the edge of free space.
 *  - Settle: every spatial change ends on the same spring, firm and nearly without overshoot.
 *    Selection is the only place a little overshoot is allowed, because it is a thing you did.
 *  - Compress: pressure reads as compression. A pressed control, the tally under the scan front.
 *
 * Calm at rest: nothing in Sweep moves unless something is happening. There is no ambient motion
 * and no infinite transition outside a running scan.
 *
 * Reduced motion keeps the continuity and removes the travel. Spatial movement snaps, while
 * changes of state still cross-fade briefly, so a screen never jumps between two unrelated
 * frames. Every helper here decides that itself, so no screen has to remember to.
 */
object SweepMotion {
    /** Decisive arrival: fast start, long soft landing. */
    val Arrive: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** Accelerating departure, for things being cleared away. */
    val Clear: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)

    val Standard: Easing = CubicBezierEasing(0.3f, 0f, 0.1f, 1f)

    const val QUICK = 140
    const val BASE = 260
    const val SLOW = 420

    /** Reduced motion's cross-fade: long enough to read as a change, too short to be motion. */
    const val REDUCED_FADE = 120

    /** How far something travels along the sweep axis when it arrives or is cleared, in dp. */
    const val SHIFT_DP = 14
}

val LocalReducedMotion = staticCompositionLocalOf { false }

private val reduced: Boolean
    @Composable @ReadOnlyComposable get() = LocalReducedMotion.current

/** The spring every spatial change ends on. */
@Composable
@ReadOnlyComposable
fun <T> settleSpec(): FiniteAnimationSpec<T> =
    if (reduced) snap() else spring(dampingRatio = 0.86f, stiffness = 480f)

/** The same spring, allowed a touch of overshoot. Only for things the user did: selecting. */
@Composable
@ReadOnlyComposable
fun <T> selectSpec(): FiniteAnimationSpec<T> =
    if (reduced) snap() else spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)

/** Presses: fast in, fast out, never wobbly. */
@Composable
@ReadOnlyComposable
fun <T> pressSpec(): FiniteAnimationSpec<T> =
    if (reduced) snap() else spring(dampingRatio = 1f, stiffness = 900f)

/**
 * Non-spatial change: colour, opacity. Kept in reduced motion, just shorter, because fading is
 * continuity rather than movement.
 */
@Composable
@ReadOnlyComposable
fun <T> effectSpec(durationMillis: Int = SweepMotion.BASE, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    if (reduced) tween(SweepMotion.REDUCED_FADE)
    else tween(durationMillis, delayMillis, SweepMotion.Standard)

/** Something opening in place: a disclosure, a notice. */
fun unfold(reducedMotion: Boolean): EnterTransition =
    if (reducedMotion) {
        fadeIn(tween(SweepMotion.REDUCED_FADE)) + expandVertically(snap())
    } else {
        fadeIn(tween(SweepMotion.BASE, 60, SweepMotion.Standard)) +
            expandVertically(spring(dampingRatio = 0.9f, stiffness = 520f, visibilityThreshold = IntSize(1, 1)))
    }

fun fold(reducedMotion: Boolean): ExitTransition =
    if (reducedMotion) {
        fadeOut(tween(SweepMotion.REDUCED_FADE)) + shrinkVertically(snap())
    } else {
        fadeOut(tween(SweepMotion.QUICK)) +
            shrinkVertically(spring(dampingRatio = 1f, stiffness = 620f, visibilityThreshold = IntSize(1, 1)))
    }

/**
 * One state of a panel replacing another in place: the old one is cleared to the right while the
 * new one arrives from the left. The same gesture as the scan, at the size of a sentence.
 */
fun sweepReplace(reducedMotion: Boolean, shiftPx: Int): ContentTransform =
    if (reducedMotion) {
        fadeIn(tween(SweepMotion.REDUCED_FADE)) togetherWith fadeOut(tween(SweepMotion.REDUCED_FADE))
    } else {
        (fadeIn(tween(SweepMotion.BASE, 70, SweepMotion.Standard)) +
            slideInHorizontally(tween(SweepMotion.SLOW, 40, SweepMotion.Arrive)) { -shiftPx }) togetherWith
            (fadeOut(tween(SweepMotion.QUICK, easing = SweepMotion.Clear)) +
                slideOutHorizontally(tween(SweepMotion.BASE, easing = SweepMotion.Clear)) { shiftPx })
    }
