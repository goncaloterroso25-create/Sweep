package dev.sweep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.SweepMotion
import dev.sweep.ui.theme.staggerDelay
import dev.sweep.ui.theme.sweepTween

/**
 * Fades and lifts content in the first time it appears, staggered by position.
 *
 * Used for full-screen content that should feel like it is arriving from below: onboarding blocks,
 * the completion summary.
 */
@Composable
fun AppearIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = sweepTween(360, delayMillis = staggerDelay(index)),
        label = "appear",
    )

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 16.dp.toPx()
        }
    ) {
        content()
    }
}

/**
 * Grows content into place from slightly smaller, once.
 *
 * Used for the preview thumbnail, where the effect stands in for a shared-element transition: the
 * image reads as expanding out of the row that was tapped, without any of the coordination a real
 * shared element would need between two screens.
 */
@Composable
fun ExpandIn(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = sweepTween(300),
        label = "expand",
    )

    if (reduced) {
        Box(modifier) { content() }
        return
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            val scale = 0.94f + 0.06f * progress
            scaleX = scale
            scaleY = scale
        }
    ) {
        content()
    }
}

/**
 * The sweep-direction entrance: content slides in along the same axis the scan front travels,
 * with a fraction of a percent of scale so it reads as settling rather than sliding.
 *
 * This is what makes a category being discovered mid-scan look like part of the same gesture as
 * the field being swept, instead of a card that happened to appear. It runs once per item, so a
 * category that updates its count later does not re-animate.
 */
@Composable
fun RevealIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val reduced = LocalReducedMotion.current
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = sweepTween(340, delayMillis = staggerDelay(index)),
        label = "reveal",
    )

    if (reduced) {
        Box(modifier) { content() }
        return
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationX = (1f - progress) * -SweepMotion.ENTER_SHIFT_DP.dp.toPx()
            val settle = 0.985f + 0.015f * progress
            scaleX = settle
            scaleY = settle
        }
    ) {
        content()
    }
}
