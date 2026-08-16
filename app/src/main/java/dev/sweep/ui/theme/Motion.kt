package dev.sweep.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Motion is meant to explain what happened, never to make the user wait.
 *
 * Every animation in Sweep goes through one of these helpers, which collapse to [snap] when
 * reduced motion is on — so honouring the accessibility setting is the default rather than
 * something each screen has to remember.
 */
object SweepMotion {
    /** Slow in, decisive out. Used for surfaces entering and leaving. */
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val Standard: Easing = CubicBezierEasing(0.3f, 0f, 0.2f, 1f)

    /** Sequential reveals: cards, rows, chips. */
    const val STAGGER_MS = 26
    const val MAX_STAGGERED_ITEMS = 12
}

val LocalReducedMotion = staticCompositionLocalOf { false }

/** Firm and fast — presses, selection, size changes. */
@Composable
fun <T> springSnappy(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(dampingRatio = 0.9f, stiffness = 520f)

/** Softer settle — layout shifts and the storage meter. */
@Composable
fun <T> springGentle(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(dampingRatio = 0.86f, stiffness = 220f)

/** A little overshoot. Reserved for moments that should feel good: selection, completion. */
@Composable
fun <T> springBouncy(): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else spring(dampingRatio = 0.52f, stiffness = 320f)

@Composable
fun <T> sweepTween(
    durationMillis: Int = 320,
    delayMillis: Int = 0,
    easing: Easing = SweepMotion.Emphasized,
): FiniteAnimationSpec<T> = if (LocalReducedMotion.current) {
    snap()
} else {
    tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
}

/** Stagger delay for the nth item, capped so a long list never feels slow. */
@Composable
fun staggerDelay(index: Int): Int =
    if (LocalReducedMotion.current) 0
    else index.coerceAtMost(SweepMotion.MAX_STAGGERED_ITEMS) * SweepMotion.STAGGER_MS
