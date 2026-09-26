package dev.sweep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepShape
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.settleSpec

/** A group heading. Sentence case and quiet; the rows below it are the point. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = SweepType.label,
            color = Sweep.colors.textMute,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        trailing?.invoke()
    }
}

/**
 * The ledger row: Sweep's one row type for things that have a size.
 *
 * Name and reason on the left, the figure on the right in tabular Grotesk, and instead of a
 * divider, a magnitude line. The line's grey length is this row's share of the largest row in the
 * group, and its lime length is how much of it is selected. Sorted by size, a group of these
 * draws the mark's shape out of real data: bars shortening as they descend.
 */
@Composable
fun LedgerRow(
    title: String,
    meta: String,
    figure: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    /** 0..1, relative to the largest row in the group. Null draws a plain hairline. */
    magnitude: Float? = null,
    /** 0..1 of this row's own total that is selected. */
    selectedShare: Float = 0f,
    metaColor: Color = Sweep.colors.textMute,
    showDivider: Boolean = true,
    /** False while the row describes something not yet ready to open, such as a scan in progress. */
    enabled: Boolean = true,
) {
    val colors = Sweep.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .pressable(enabled = enabled, pressedScale = 0.985f, onClick = onClick, onClickLabel = "Open $title"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = colors.text, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, style = SweepType.rowTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Text(meta, style = SweepType.meta, color = metaColor)
            }
            Spacer(Modifier.width(12.dp))
            Text(figure, style = SweepType.rowFigure, color = colors.text, maxLines = 1)
            Icon(
                SweepIcons.ChevronRight,
                contentDescription = null,
                tint = if (enabled) colors.textFaint else Color.Transparent,
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(18.dp),
            )
        }
        if (showDivider) {
            if (magnitude == null) Hairline() else MagnitudeLine(magnitude, selectedShare)
        }
    }
}

/**
 * A hairline that carries a figure. Drawn from the right, where the figure it belongs to sits.
 */
@Composable
fun MagnitudeLine(magnitude: Float, selectedShare: Float, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    val length by animateFloatAsState(magnitude.coerceIn(0f, 1f), settleSpec(), label = "magnitude")
    val selected by animateFloatAsState(selectedShare.coerceIn(0f, 1f), settleSpec(), label = "selectedShare")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(3.dp)
    ) {
        val y = size.height / 2f
        drawLine(colors.line, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        val stroke = 2.5.dp.toPx()
        val cap = stroke / 2f
        val end = size.width - cap
        val barLength = (size.width - stroke) * length
        if (barLength > 0.5f) {
            drawLine(colors.lineStrong, Offset(end - barLength, y), Offset(end, y), stroke, StrokeCap.Round)
            val selectedLength = barLength * selected
            if (selectedLength > 0.5f) {
                drawLine(colors.tallySelected, Offset(end - selectedLength, y), Offset(end, y), stroke, StrokeCap.Round)
            }
        }
    }
}

enum class NoticeTone { Neutral, Danger }

/**
 * Something Sweep needs to say: a missing permission, a platform limit, a partial failure. Always
 * what is true, then what can be done about it. A flat tint with no border or shadow, so a notice
 * reads as annotation rather than as another card competing with the content.
 */
@Composable
fun Notice(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector? = SweepIcons.Info,
    tone: NoticeTone = NoticeTone.Neutral,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Sweep.colors
    val accent = when (tone) {
        NoticeTone.Neutral -> colors.textMute
        NoticeTone.Danger -> colors.danger
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(SweepShape.control)
            .background(
                when (tone) {
                    NoticeTone.Danger -> colors.dangerWash
                    else -> colors.surface
                }
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = SweepType.rowTitle, color = colors.text)
                Spacer(Modifier.height(3.dp))
            }
            Text(text, style = SweepType.meta, color = if (tone == NoticeTone.Danger) colors.text else colors.textMute)
            if (action != null) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.padding(start = 0.dp)) { action() }
            }
        }
    }
}

/**
 * A group of related settings on one surface, separated by inset hairlines. The only rounded
 * container Sweep draws for content, and only where the items genuinely belong together.
 */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(SweepShape.group)
            .background(Sweep.colors.surface),
        content = content,
    )
}

/**
 * The bar at the top of every screen below Home: back, and whatever actions the screen owns.
 * The title is not in the bar. It is the first thing in the content, at title size, and scrolls
 * away with it, which leaves more room for the list on a small phone.
 */
@Composable
fun ScreenBar(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconAction(SweepIcons.Back, contentDescription = "Back", onClick = onBack)
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun ScreenTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 4.dp, bottom = 8.dp)) {
        Text(
            title,
            style = SweepType.title,
            color = Sweep.colors.text,
            modifier = Modifier.semantics { heading() },
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = SweepType.meta, color = Sweep.colors.textMute)
        }
    }
}
