package dev.sweep.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.sweep.core.model.CleanupCategory

/**
 * Sweep's palette.
 *
 * The base is a cool near-black in dark mode and warm paper in light mode — deliberately not
 * white-on-grey, which is what every other utility app looks like. One acid-lime signal colour
 * carries "space you get back"; it is used as a fill rather than as text so it never has to fight
 * for contrast, and large figures stay in the plain text colour in both themes.
 */
@Immutable
data class SweepColors(
    val base: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val line: Color,
    val text: Color,
    val textMute: Color,
    val textFaint: Color,
    val accent: Color,
    val accentPressed: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
    val info: Color,
    val glassFill: Color,
    val glassHighlight: Color,
    val isDark: Boolean,
) {
    /**
     * A restrained family used only as small marks: category icons and the ticks in the scan
     * field. Never as a background fill, so the app never turns into a dashboard.
     */
    fun categoryTint(category: CleanupCategory): Color = when (category) {
        CleanupCategory.DUPLICATES -> accent
        CleanupCategory.INSTALLERS -> if (isDark) Color(0xFF7FA9FF) else Color(0xFF3663C8)
        CleanupCategory.ARCHIVES -> if (isDark) Color(0xFFC49BFF) else Color(0xFF7C4CD1)
        CleanupCategory.SCREENSHOTS -> if (isDark) Color(0xFF6FD9C4) else Color(0xFF177F6B)
        CleanupCategory.DOWNLOADS -> if (isDark) Color(0xFFFFC24D) else Color(0xFFA9720B)
        CleanupCategory.LARGE_FILES -> if (isDark) Color(0xFFFF8A65) else Color(0xFFC0552F)
        CleanupCategory.EMPTY_FOLDERS -> textMute
    }
}

val DarkColors = SweepColors(
    base = Color(0xFF0B0E11),
    surface = Color(0xFF151A1F),
    surfaceHigh = Color(0xFF1E252C),
    line = Color(0xFF2B343D),
    text = Color(0xFFECEFF1),
    textMute = Color(0xFF97A2AE),
    textFaint = Color(0xFF5D6873),
    accent = Color(0xFFC8F04B),
    accentPressed = Color(0xFFAFD634),
    onAccent = Color(0xFF10160A),
    accentSoft = Color(0x1FC8F04B),
    danger = Color(0xFFFF7A52),
    dangerSoft = Color(0x1FFF7A52),
    info = Color(0xFF8FB4FF),
    glassFill = Color(0xCC161C22),
    glassHighlight = Color(0x14FFFFFF),
    isDark = true,
)

val LightColors = SweepColors(
    base = Color(0xFFEFEDE6),
    surface = Color(0xFFFAF9F5),
    surfaceHigh = Color(0xFFFFFFFF),
    line = Color(0xFFDCD8CD),
    text = Color(0xFF13181D),
    textMute = Color(0xFF616B76),
    textFaint = Color(0xFF9AA2AB),
    accent = Color(0xFFB4DE2E),
    accentPressed = Color(0xFF9AC21C),
    onAccent = Color(0xFF10160A),
    accentSoft = Color(0x33B4DE2E),
    danger = Color(0xFFCF4620),
    dangerSoft = Color(0x1FCF4620),
    info = Color(0xFF2F5FD0),
    glassFill = Color(0xE0FBFAF6),
    glassHighlight = Color(0x66FFFFFF),
    isDark = false,
)
