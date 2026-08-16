package dev.sweep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import dev.sweep.ui.theme.springSnappy

/**
 * Touch feedback that feels physical: the surface compresses under the finger and springs back.
 *
 * The ripple is deliberately suppressed — on the large dark surfaces Sweep uses, a ripple reads
 * as a smudge, whereas a scale change reads as the thing being pushed.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
    onClick: () -> Unit,
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = springSnappy(),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = onClick,
        )
}

/** Same feel, but for rows where the whole surface is a selection toggle. */
@Composable
fun Modifier.pressableRow(
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = pressable(
    enabled = enabled,
    pressedScale = 0.985f,
    role = Role.Checkbox,
    onClick = onClick,
)
