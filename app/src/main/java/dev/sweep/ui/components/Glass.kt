package dev.sweep.ui.components

import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import dev.sweep.ui.theme.Sweep

/**
 * Sweep's take on layered glass.
 *
 * Android has no backdrop blur for an ordinary in-tree composable — the platform only offers it
 * for windows, from Android 12. Rather than pull in a blur library and pay for a full-screen
 * render pass on every frame, ordinary floating surfaces get the *optics* of glass without the
 * cost: a translucent tint, a bright top edge where light would catch, and a hairline that
 * separates it from what is behind. Content beneath is faded out by [ScrimBehindGlass] so the
 * surface still reads as sitting on top.
 *
 * Modal sheets — which are real windows, and which are on screen briefly — get the genuine
 * platform blur through [ApplyDialogBlur].
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = Sweep.colors
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.glassFill, shape)
            .background(
                brush = Brush.verticalGradient(
                    0f to colors.glassHighlight,
                    0.45f to Color.Transparent,
                ),
                shape = shape,
            )
            .border(1.dp, colors.line.copy(alpha = 0.55f), shape),
        content = content,
    )
}

/**
 * A short fade from the page colour into transparency, placed under a floating bar so scrolling
 * content dissolves rather than colliding with it.
 */
@Composable
fun ScrimBehindGlass(height: Dp = 56.dp, modifier: Modifier = Modifier) {
    val base = Sweep.colors.base
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to base,
                )
            )
    )
}

/**
 * Real platform blur behind a modal sheet or dialog, on Android 12+.
 *
 * The system may refuse it — battery saver, low-end devices, or the user turning window blur off
 * — in which case nothing happens and the translucent surface stands on its own.
 */
@Composable
fun ApplyDialogBlur(radius: Int = 42) {
    val view = LocalView.current
    SideEffect {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@SideEffect
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        val manager = view.context.getSystemService(WindowManager::class.java)
        if (manager?.isCrossWindowBlurEnabled != true) return@SideEffect
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        window.attributes = window.attributes.also { it.blurBehindRadius = radius }
    }
}
