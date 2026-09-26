package dev.sweep.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sweep.core.data.MotionPreference

val LocalSweepColors = staticCompositionLocalOf { DarkColors }

/**
 * Geometry. Sweep is mostly rows on a page, so there are few surfaces to round at all, and the
 * ones that exist use two radii: 12 for controls and groups, 20 for sheets. The primary action
 * is the only pill, which is what keeps it findable.
 */
object SweepShape {
    val control = RoundedCornerShape(12.dp)
    val group = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)
    val thumb = RoundedCornerShape(9.dp)
}

/** The widest a column of reading content is allowed to get on a tablet or unfolded phone. */
val MaxContentWidth = 620.dp

object Sweep {
    val colors: SweepColors
        @Composable @ReadOnlyComposable get() = LocalSweepColors.current
}

@Composable
fun SweepTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    motionPreference: MotionPreference = MotionPreference.STANDARD,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val context = LocalContext.current

    val systemAnimationsOff = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) == 0f
        }.getOrDefault(false)
    }

    // Standard still yields to Android: if animations are off system-wide, Sweep follows.
    val reducedMotion = motionPreference == MotionPreference.REDUCED || systemAnimationsOff

    // Material components still read a scheme, so it is derived from Sweep's palette and a stray
    // Material default can never introduce a colour that is not part of the design.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.signal,
            onPrimary = colors.onSignal,
            background = colors.base,
            onBackground = colors.text,
            surface = colors.raised,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.textMute,
            surfaceContainer = colors.raised,
            outline = colors.line,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.signalInk,
            onPrimary = colors.raised,
            background = colors.base,
            onBackground = colors.text,
            surface = colors.raised,
            onSurface = colors.text,
            surfaceVariant = colors.surface,
            onSurfaceVariant = colors.textMute,
            surfaceContainer = colors.raised,
            outline = colors.line,
            error = colors.danger,
        )
    }

    CompositionLocalProvider(
        LocalSweepColors provides colors,
        LocalReducedMotion provides reducedMotion,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = SweepTypography,
            shapes = Shapes(
                extraSmall = SweepShape.control,
                small = SweepShape.control,
                medium = SweepShape.control,
                large = SweepShape.group,
                extraLarge = SweepShape.group,
            ),
            content = content,
        )
    }
}
