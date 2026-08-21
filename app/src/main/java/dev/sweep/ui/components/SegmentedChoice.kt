package dev.sweep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springSnappy

/**
 * The one control Sweep uses for "pick one of these thresholds".
 *
 * It exists because the old version was a plain Row of intrinsically sized chips, which is fine
 * until the labels stop fitting. On a narrower phone the last option in a four-way group was
 * squeezed to a sliver and "180 days" wrapped down four lines, leaving one control twice the
 * height of every other control on the screen.
 *
 * So this measures the widest label at the current density and font scale, works out how many
 * equal columns actually fit, and drops to a 2x2 grid, then to a single column, rather than
 * letting anything wrap. Every option is the same width and height regardless of how long its
 * text is, which also stops "1.0 GB" looking more important than "100 MB".
 */
@Composable
fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (options.isEmpty()) return

    val style = MaterialTheme.typography.labelMedium
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Width of the longest label as it will actually be drawn, so the decision follows the
    // user's font scale instead of an assumption about English at 1.0x.
    val widestLabel: Dp = remember(options, label, style, density, measurer) {
        val widest = options.maxOf { option ->
            measurer.measure(
                text = AnnotatedString(label(option)),
                style = style,
                maxLines = 1,
                softWrap = false,
            ).size.width
        }
        with(density) { widest.toDp() }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val available = maxWidth
        val cellWidth = widestLabel + HORIZONTAL_PADDING * 2

        fun fits(columns: Int): Boolean =
            cellWidth * columns + GAP * (columns - 1) <= available

        // Preferred shapes, widest first. Four options fall back to 2x2 rather than 3+1, which
        // would leave an orphan and read as a mistake.
        val columns = when (options.size) {
            4 -> listOf(4, 2, 1)
            3 -> listOf(3, 1)
            else -> listOf(options.size, 1)
        }.firstOrNull(::fits) ?: 1

        Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
            options.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                    row.forEach { option ->
                        Option(
                            text = label(option),
                            active = option == selected,
                            onClick = { onSelect(option) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    // Keeps a short final row aligned with the one above it instead of
                    // stretching its last item across the gap.
                    repeat(columns - row.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun Option(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val shape = RoundedCornerShape(10.dp)
    val fill by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = springSnappy(),
        label = "segment",
    )

    Box(
        modifier = modifier
            .heightIn(min = MIN_TOUCH_TARGET)
            .clip(shape)
            .background(lerp(colors.surface, colors.accent, fill))
            .border(1.dp, lerp(colors.line, colors.accent, fill), shape)
            .pressable(onClick = onClick)
            .padding(horizontal = HORIZONTAL_PADDING, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = lerp(colors.textMute, colors.onAccent, fill),
            maxLines = 1,
            softWrap = false,
            textAlign = TextAlign.Center,
        )
    }
}

private val HORIZONTAL_PADDING = 10.dp
private val GAP = 8.dp
private val MIN_TOUCH_TARGET = 44.dp
