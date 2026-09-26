package dev.sweep.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepShape
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.effectSpec
import dev.sweep.ui.theme.pressSpec
import dev.sweep.ui.theme.selectSpec
import dev.sweep.ui.theme.settleSpec

/**
 * Touch feedback as compression: the control gives slightly under the finger and springs back.
 * The ripple is suppressed because on Sweep's flat surfaces it reads as a smudge, while a change
 * of scale reads as the thing being pushed.
 */
@Composable
fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = pressSpec(),
        label = "press",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
        )
}

enum class ButtonTone { Primary, Secondary, Danger }

/**
 * The button. Primary is the only pill in the app and appears at most once per screen, which is
 * what makes "what do I do next" answerable at a glance. Secondary is a quiet outlined control.
 * Danger is reserved for the single button that permanently deletes something.
 */
@Composable
fun SweepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    compact: Boolean = false,
) {
    val colors = Sweep.colors
    val shape = if (tone == ButtonTone.Secondary) SweepShape.control else CircleShape
    val fill = when (tone) {
        ButtonTone.Primary -> colors.signal
        ButtonTone.Secondary -> Color.Transparent
        ButtonTone.Danger -> colors.danger
    }
    val content = when (tone) {
        ButtonTone.Primary -> colors.onSignal
        ButtonTone.Secondary -> colors.text
        ButtonTone.Danger -> colors.onDanger
    }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, effectSpec(), label = "enabled")

    Row(
        modifier = modifier
            .alpha(alpha)
            .heightIn(min = if (compact) 44.dp else 54.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (tone == ButtonTone.Secondary) Modifier.border(1.dp, colors.lineStrong, shape)
                else Modifier
            )
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(
            text = text,
            style = SweepType.button,
            color = content,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/** Text-only action. Still 44dp tall, still compresses. */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Sweep.colors.text,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .heightIn(min = 44.dp)
            .clip(SweepShape.control)
            .pressable(enabled = enabled, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = SweepType.label.copy(fontSize = SweepType.button.fontSize), color = color)
    }
}

/** A 44dp target around a 22dp glyph. */
@Composable
fun IconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Sweep.colors.text,
    size: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick, onClickLabel = contentDescription),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size))
    }
}

/**
 * The selection mark. A square, because a circle reads as a radio button, and drawn rather than
 * composed so the tick can stroke itself on in two movements.
 *
 * Unselected it is an outline in the strong line colour; selected it fills with the signal and the
 * tick draws. That is the whole selected state of a row: the row itself stays the page colour, so
 * a long list with many things selected never turns into a lime wall.
 */
@Composable
fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    val t by animateFloatAsState(if (selected) 1f else 0f, selectSpec(), label = "select")
    Canvas(modifier.size(22.dp)) {
        val corner = CornerRadius(6.dp.toPx())
        val fill = t.coerceIn(0f, 1f)
        if (fill > 0f) drawRoundRect(color = colors.signal.copy(alpha = fill), cornerRadius = corner)
        drawRoundRect(
            color = lerp(colors.lineStrong, colors.signal, fill),
            cornerRadius = corner,
            style = Stroke(width = 1.7.dp.toPx()),
        )
        if (t > 0.02f) {
            val w = size.width
            val h = size.height
            val p0 = Offset(w * 0.27f, h * 0.52f)
            val p1 = Offset(w * 0.44f, h * 0.69f)
            val p2 = Offset(w * 0.75f, h * 0.33f)
            val stroke = 2.1.dp.toPx()
            val first = (t / 0.42f).coerceIn(0f, 1f)
            drawLine(colors.onSignal, p0, p0 + (p1 - p0) * first, stroke, StrokeCap.Round)
            val second = ((t - 0.42f) / 0.58f).coerceIn(0f, 1f)
            if (second > 0f) drawLine(colors.onSignal, p1, p1 + (p2 - p1) * second, stroke, StrokeCap.Round)
        }
    }
}

@Composable
fun SweepSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    val t by animateFloatAsState(if (checked) 1f else 0f, settleSpec(), label = "switch")
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 28.dp)
            .clip(CircleShape)
            .background(lerp(Color.Transparent, colors.signal, t))
            .border(1.5.dp, lerp(colors.lineStrong, colors.signal, t), CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                // Lambda offset: the knob moves in the layout phase, not by recomposing.
                .offset { IntOffset(((4 + t * 18) * density).toInt(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(lerp(colors.textMute, colors.onSignal, t))
        )
    }
}

/**
 * Pick one of a handful of values.
 *
 * Measures the widest label at the current font scale and works out how many equal columns
 * actually fit, dropping to a 2x2 grid and then a single column rather than letting any label
 * wrap. Every option is the same size, so "1 GB" never looks more important than "100 MB".
 *
 * In a single row the selection is one lime slab that settles from option to option. In a grid,
 * where a slab would have to travel diagonally through other options, the fill cross-fades.
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
    val colors = Sweep.colors
    val style = SweepType.label
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current

    val widest: Dp = remember(options, style, density, measurer) {
        val px = options.maxOf {
            measurer.measure(AnnotatedString(label(it)), style, maxLines = 1, softWrap = false).size.width
        }
        with(density) { px.toDp() }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val inner = maxWidth - TRACK_PADDING * 2
        val cell = widest + CELL_PADDING * 2
        fun fits(columns: Int) = cell * columns <= inner
        val columns = when (options.size) {
            4 -> listOf(4, 2, 1)
            3 -> listOf(3, 1)
            else -> listOf(options.size, 1)
        }.firstOrNull(::fits) ?: 1
        val selectedIndex = options.indexOf(selected).coerceAtLeast(0)

        if (columns == options.size) {
            // One row: a track with a sliding slab.
            val cellWidth = inner / options.size
            val slabX by animateDpAsState(cellWidth * selectedIndex, settleSpec(), label = "slab")
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(SweepShape.control)
                    .background(colors.surface)
                    .border(1.dp, colors.line, SweepShape.control)
                    .padding(TRACK_PADDING)
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(slabX.roundToPx(), 0) }
                        .width(cellWidth)
                        .height(SEGMENT_HEIGHT)
                        .clip(RoundedCornerShape(9.dp))
                        .background(colors.signal)
                )
                Row {
                    options.forEachIndexed { index, option ->
                        Segment(
                            text = label(option),
                            active = index == selectedIndex,
                            onClick = { onSelect(option) },
                            modifier = Modifier.width(cellWidth),
                            filled = false,
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.chunked(columns).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { option ->
                            Segment(
                                text = label(option),
                                active = option == selected,
                                onClick = { onSelect(option) },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .border(1.dp, colors.line, RoundedCornerShape(10.dp)),
                                filled = true,
                            )
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun Segment(
    text: String,
    active: Boolean,
    onClick: () -> Unit,
    filled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val t by animateFloatAsState(if (active) 1f else 0f, effectSpec(), label = "segment")
    Box(
        modifier = modifier
            .height(SEGMENT_HEIGHT)
            .then(if (filled) Modifier.background(lerp(colors.surface, colors.signal, t)) else Modifier)
            .selectable(
                selected = active,
                onClick = onClick,
                role = Role.RadioButton,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = SweepType.label,
            color = lerp(colors.textMute, colors.onSignal, t),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            modifier = Modifier.widthIn(min = 1.dp).padding(horizontal = 4.dp),
        )
    }
}

private val TRACK_PADDING = 3.dp
private val CELL_PADDING = 12.dp
private val SEGMENT_HEIGHT = 42.dp

/** A hairline, for separating rows without drawing boxes around them. */
@Composable
fun Hairline(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    Box(
        modifier
            .padding(start = inset)
            .fillMaxWidth()
            .height(1.dp)
            .background(Sweep.colors.line)
    )
}
