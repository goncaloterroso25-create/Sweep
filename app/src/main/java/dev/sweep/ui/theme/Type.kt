package dev.sweep.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import dev.sweep.R

/**
 * Two families with separate jobs, kept from earlier versions because nothing beat them.
 *
 * Space Grotesk sets figures and screen titles. Its numerals are wide and slightly engineered,
 * which is what makes a storage readout feel measured rather than printed. Inter does all the
 * reading and gets out of the way.
 *
 * Figures that change while you watch them (file counts, sizes, the free-space hero) use tabular
 * numerals, so "9.8 GB" becoming "10.2 GB" does not make the line jitter sideways.
 */
val Grotesk = FontFamily(
    Font(R.font.grotesk_medium, FontWeight.Medium),
    Font(R.font.grotesk_bold, FontWeight.Bold),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

private const val TABULAR = "tnum"

private val Trim = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.Both,
)

@Immutable
object SweepType {

    /** Free space on Home. Medium rather than Bold: large enough already, and calmer for it. */
    val hero = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 64.sp,
        lineHeight = 64.sp,
        letterSpacing = (-2.6).sp,
        fontFeatureSettings = TABULAR,
        lineHeightStyle = Trim,
    )

    /** The unit beside the hero figure. */
    val heroUnit = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 26.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.4).sp,
        lineHeightStyle = Trim,
    )

    /** A total that is the point of its surface: the delete sheet, the receipt. */
    val figure = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-1.0).sp,
        fontFeatureSettings = TABULAR,
    )

    /** Sizes in lists. Grotesk, so every figure in the app shares one texture. */
    val rowFigure = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = (-0.1).sp,
        fontFeatureSettings = TABULAR,
    )

    /** Screen titles below Home. */
    val title = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.7).sp,
    )

    /** Status headlines: "Scanning", "3.2 GB worth reviewing". */
    val headline = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp,
        fontFeatureSettings = TABULAR,
    )

    /** Names of things: categories, files, apps, settings. */
    val rowTitle = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp,
    )

    val body = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    /** Metadata under a name. Always [SweepColors.textMute], never fainter. */
    val meta = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = TABULAR,
    )

    /** Group headings. Sentence case, no tracking: a label, not a banner. */
    val label = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val button = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    )

    /** Small print: legends, footnotes. */
    val micro = TextStyle(
        fontFamily = Inter,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontFeatureSettings = TABULAR,
    )
}

/**
 * Material components (menus, sheets) still read the Material slots, so they are mapped onto the
 * same styles and never introduce a size that is not part of the scale above.
 */
val SweepTypography = Typography(
    headlineMedium = SweepType.title,
    headlineSmall = SweepType.headline,
    titleMedium = SweepType.rowTitle,
    titleSmall = SweepType.label,
    bodyLarge = SweepType.body,
    bodyMedium = SweepType.body.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = SweepType.meta,
    labelLarge = SweepType.button,
    labelMedium = SweepType.micro,
    labelSmall = SweepType.micro,
)
