package dev.sweep.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Sweep's palette: ink, paper, and one signal.
 *
 * Colour has exactly two jobs in the app. Lime means "space you could get back": what a scan
 * found, what is selected, the action that reviews it. Red means "this is permanent". Everything
 * else, including every category, is drawn in the text colours, so a single lime mark on a screen
 * always answers the same question.
 *
 * The two themes are designed separately rather than inverted. Lime is a fill in both, but on
 * paper it is too pale to carry text or thin strokes, so light mode reads it through [signalInk],
 * a deep olive from the same hue. Dark mode can use the lime itself.
 */
@Immutable
data class SweepColors(
    /** The page. */
    val base: Color,
    /** Grouped content that sits on the page: settings groups, the action tray. */
    val surface: Color,
    /** Sheets and anything that floats above a surface. */
    val raised: Color,
    /** Hairlines and resting outlines. */
    val line: Color,
    /** Outlines that need to read as a control rather than a divider. */
    val lineStrong: Color,
    val text: Color,
    /** Metadata. Passes 4.5:1 on [base] and [surface] in both themes. */
    val textMute: Color,
    /** Non-essential text only: footnotes, disabled labels. */
    val textFaint: Color,
    /** The lime, as a fill. Always carries [onSignal] content. */
    val signal: Color,
    val onSignal: Color,
    /** The lime as text or a thin stroke on [base]. Olive on paper, lime on ink. */
    val signalInk: Color,
    /** A barely-there wash behind selected rows. */
    val signalWash: Color,
    val danger: Color,
    val onDanger: Color,
    val dangerWash: Color,
    /** Storage in use, as the tally draws it. */
    val tallyUsed: Color,
    /** Free storage, as the tally draws it: short, quiet stubs. */
    val tallyFree: Color,
    /** Found by a scan but not selected. */
    val tallyFound: Color,
    /** Selected, and confirmed-deleted stubs waiting for Android to re-measure. */
    val tallySelected: Color,
    val scrim: Color,
    val isDark: Boolean,
)

val DarkColors = SweepColors(
    base = Color(0xFF0B0E11),
    surface = Color(0xFF13171B),
    raised = Color(0xFF1A1F24),
    line = Color(0xFF252C33),
    lineStrong = Color(0xFF3A434C),
    text = Color(0xFFEDEFF1),
    textMute = Color(0xFF9AA3AD),
    textFaint = Color(0xFF737D87),
    signal = Color(0xFFC8F04B),
    onSignal = Color(0xFF0E1405),
    signalInk = Color(0xFFC8F04B),
    signalWash = Color(0x12C8F04B),
    danger = Color(0xFFFF6A4D),
    onDanger = Color(0xFF1A0803),
    dangerWash = Color(0x1AFF6A4D),
    tallyUsed = Color(0xFFB9C0C7),
    tallyFree = Color(0xFF2E363E),
    tallyFound = Color(0xFF5F7428),
    tallySelected = Color(0xFFC8F04B),
    scrim = Color(0xB3050708),
    isDark = true,
)

val LightColors = SweepColors(
    base = Color(0xFFF1EFE9),
    surface = Color(0xFFFAF9F6),
    raised = Color(0xFFFFFFFF),
    line = Color(0xFFDEDAD0),
    lineStrong = Color(0xFFBDB8AC),
    text = Color(0xFF111518),
    textMute = Color(0xFF56606A),
    textFaint = Color(0xFF737C85),
    signal = Color(0xFFC8F04B),
    onSignal = Color(0xFF0E1405),
    signalInk = Color(0xFF4A6400),
    signalWash = Color(0x144A6400),
    danger = Color(0xFFC23B1C),
    onDanger = Color(0xFFFFFFFF),
    dangerWash = Color(0x14C23B1C),
    tallyUsed = Color(0xFF2A3036),
    tallyFree = Color(0xFFD3CEC3),
    tallyFound = Color(0xFF9DB05E),
    tallySelected = Color(0xFF4A6400),
    scrim = Color(0x80111518),
    isDark = false,
)
