package dev.sweep.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.FormattedBytes
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.sweepReplace
import java.util.Locale

/**
 * A byte figure set as number and unit on one baseline: "41.2" large, "GB" quiet.
 *
 * Two behaviours matter more than the typesetting:
 *
 *  - It fits. The size shrinks, never the content, so the hero figure survives a 320dp phone at the
 *    largest font scale without wrapping or clipping a digit. Large figures are already large, so
 *    capping their growth is what Android's own non-linear font scaling does too.
 *  - It does not count. When the value changes, the new figure is swept in from the left while the
 *    old one is cleared to the right. Counting up from an old number to a new one would show a
 *    string of intermediate values the device never had.
 */
@Composable
fun ByteFigure(
    bytes: Long,
    modifier: Modifier = Modifier,
    valueStyle: TextStyle = SweepType.hero,
    unitStyle: TextStyle = SweepType.heroUnit,
    valueColor: Color = Sweep.colors.text,
    unitColor: Color = Sweep.colors.textMute,
    /** Read after the figure by screen readers, e.g. "free". */
    spokenSuffix: String? = null,
    animate: Boolean = true,
) {
    val formatted = ByteFormat.format(bytes)
    val spoken = formatted.toString() + (spokenSuffix?.let { " $it" } ?: "")
    val reduced = LocalReducedMotion.current
    val density = LocalDensity.current
    val shift = with(density) { 18.dp.roundToPx() }

    BoxWithConstraints(modifier.clearAndSetSemantics { contentDescription = spoken }) {
        val scale = rememberFitScale(formatted, valueStyle, unitStyle, maxWidth.value)
        val value = valueStyle.scaled(scale)
        val unit = unitStyle.scaled(scale)

        if (!animate) {
            FigureRow(formatted, value, unit, valueColor, unitColor)
        } else {
            AnimatedContent(
                targetState = formatted,
                transitionSpec = { sweepReplace(reduced, shift) using SizeTransform(clip = false) },
                label = "figure",
            ) { shown ->
                FigureRow(shown, value, unit, valueColor, unitColor)
            }
        }
    }
}

@Composable
private fun FigureRow(
    formatted: FormattedBytes,
    value: TextStyle,
    unit: TextStyle,
    valueColor: Color,
    unitColor: Color,
) {
    Row {
        Text(formatted.value, style = value, color = valueColor, maxLines = 1, modifier = Modifier.alignByBaseline())
        Spacer(Modifier.width((value.fontSize.value * 0.09f).dp))
        Text(formatted.unit, style = unit, color = unitColor, maxLines = 1, modifier = Modifier.alignByBaseline())
    }
}

/**
 * How far the figure has to shrink to fit [maxWidthDp]. Measured against a slightly wider
 * placeholder than the current value so the size does not jump as digits change.
 */
@Composable
private fun rememberFitScale(
    formatted: FormattedBytes,
    value: TextStyle,
    unit: TextStyle,
    maxWidthDp: Float,
): Float {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val template = formatted.value.map { if (it.isDigit()) '8' else it }.joinToString("")
    return remember(template, formatted.unit, value, unit, maxWidthDp, density) {
        val valueWidth = measurer.measure(AnnotatedString(template), value, maxLines = 1).size.width
        val unitWidth = measurer.measure(AnnotatedString(formatted.unit), unit, maxLines = 1).size.width
        val total = with(density) { (valueWidth + unitWidth).toDp().value } + value.fontSize.value * 0.09f + 2f
        if (total <= maxWidthDp || total <= 0f) 1f else (maxWidthDp / total).coerceAtLeast(0.35f)
    }
}

private fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale >= 1f) this
    else copy(
        fontSize = (fontSize.value * scale).sp,
        lineHeight = if (lineHeight.isSp) (lineHeight.value * scale).sp else lineHeight,
        letterSpacing = if (letterSpacing.isSp) (letterSpacing.value * scale).sp else letterSpacing,
    )

/**
 * True when a row this wide cannot hold its usual side-by-side layout at the current font scale.
 *
 * Measured in dp per unit of font scale, so a 411dp phone at 200% text and a 205dp strip at 100%
 * are treated alike: both have room for about 205dp of normal-sized text. Rows that cross this
 * line restack rather than squeeze, so no name is ever crushed into a column two letters wide.
 */
@Composable
fun isCompact(maxWidth: androidx.compose.ui.unit.Dp): Boolean =
    maxWidth.value / LocalDensity.current.fontScale < COMPACT_BELOW

private const val COMPACT_BELOW = 260f

/** "12,408", grouped for the reader's locale. */
fun Int.grouped(): String = String.format(Locale.getDefault(), "%,d", this)

fun Long.bytes(): String = ByteFormat.short(this)

fun plural(count: Int, one: String, many: String = one + "s"): String =
    "${count.grouped()} ${if (count == 1) one else many}"
