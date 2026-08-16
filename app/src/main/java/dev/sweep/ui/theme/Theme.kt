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

/** Corner radii climb with surface importance. Only the primary action is a full pill. */
val SweepShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(11.dp),
    medium = RoundedCornerShape(15.dp),
    large = RoundedCornerShape(21.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

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

    // Standard still yields to Android: if the user has switched animations off system-wide,
    // Sweep is not the app that argues with that. That is behaviour, not a third option.
    val reducedMotion = motionPreference == MotionPreference.REDUCED || systemAnimationsOff

    // Material components still need a scheme; it is derived from the Sweep palette so a stray
    // M3 default can never introduce a colour that is not part of the design.
    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.info,
            background = colors.base,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textMute,
            outline = colors.line,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.info,
            background = colors.base,
            onBackground = colors.text,
            surface = colors.surface,
            onSurface = colors.text,
            surfaceVariant = colors.surfaceHigh,
            onSurfaceVariant = colors.textMute,
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
            shapes = SweepShapes,
            content = content,
        )
    }
}
