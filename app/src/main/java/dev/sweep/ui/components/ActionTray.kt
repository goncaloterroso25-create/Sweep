package dev.sweep.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepMotion
import dev.sweep.ui.theme.SweepShape
import dev.sweep.ui.theme.SweepType

/**
 * The selection, and the one way forward from it.
 *
 * It replaces a floating glass bar. This one is solid and sits on the bottom edge, joined to it,
 * because it is not decoration over the list: it is the end of it, the total of what the list
 * above has been used to choose. The figure updates as rows are ticked, and the only action is
 * Review, which opens the itemised confirmation. Nothing here deletes anything.
 */
@Composable
fun ActionTray(
    visible: Boolean,
    count: Int,
    bytes: Long,
    onClear: () -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = if (reduced) fadeIn(tween(SweepMotion.REDUCED_FADE))
        else slideInVertically(spring(dampingRatio = 0.86f, stiffness = 480f)) { it } + fadeIn(tween(SweepMotion.QUICK)),
        exit = if (reduced) fadeOut(tween(SweepMotion.REDUCED_FADE))
        else slideOutVertically(tween(SweepMotion.BASE, easing = SweepMotion.Clear)) { it } + fadeOut(tween(SweepMotion.BASE)),
        modifier = modifier,
    ) {
        val colors = Sweep.colors
        Box(
            Modifier
                .fillMaxWidth()
                .clip(SweepShape.sheet)
                .background(colors.raised),
            contentAlignment = Alignment.TopCenter,
        ) {
            Hairline(Modifier.padding(horizontal = 0.dp))
            BoxWithConstraints(
                Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                val total: @Composable (Modifier) -> Unit = { totalModifier ->
                    Column(totalModifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                        Text(
                            text = "${plural(count, "file")} selected",
                            style = SweepType.meta,
                            color = colors.textMute,
                        )
                        ByteFigure(bytes = bytes, valueStyle = SweepType.headline, unitStyle = SweepType.label)
                    }
                }
                if (isCompact(maxWidth)) {
                    // Too narrow for figure and buttons side by side: the figure gets its own line,
                    // and Review takes the full width under it.
                    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)) {
                        total(Modifier)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextAction("Clear", onClick = onClear, color = colors.textMute)
                            Spacer(Modifier.width(8.dp))
                            SweepButton("Review", onClick = onReview, compact = true, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        total(Modifier.weight(1f))
                        TextAction("Clear", onClick = onClear, color = colors.textMute)
                        Spacer(Modifier.width(4.dp))
                        SweepButton("Review", onClick = onReview, compact = true)
                    }
                }
            }
        }
    }
}
