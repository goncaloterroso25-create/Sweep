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
import dev.sweep.ui.theme.staggerDelay
import dev.sweep.ui.theme.sweepTween

/**
 * Fades and lifts content in the first time it appears, staggered by position.
 *
 * This is what makes categories look like they are being *discovered* during a scan rather than
 * dumped into a list. The stagger is capped in [staggerDelay], and collapses to nothing when
 * reduced motion is on.
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
