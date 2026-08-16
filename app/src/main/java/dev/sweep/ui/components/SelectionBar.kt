package dev.sweep.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.ByteFormat
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springGentle
import dev.sweep.ui.theme.sweepTween

/**
 * The floating selection toolbar.
 *
 * This is one of the few places glass earns its keep: it has to sit over a scrolling list without
 * hiding it, so it is translucent, edged with light, and preceded by a short fade of the page
 * colour so rows dissolve underneath rather than collide with it.
 */
@Composable
fun SelectionBar(
    visible: Boolean,
    count: Int,
    bytes: Long,
    onClear: () -> Unit,
    onClean: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(animationSpec = springGentle()) { it } + fadeIn(sweepTween(180)),
        exit = slideOutVertically(animationSpec = springGentle()) { it } + fadeOut(sweepTween(140)),
        modifier = modifier,
    ) {
        val colors = Sweep.colors
        Column {
            ScrimBehindGlass(height = 44.dp)
            // The strip below the bar is painted with the page colour and carries the gesture
            // inset, so scrolling rows fade out above the bar instead of reappearing beneath it.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.base)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 12.dp)
            ) {
            GlassSurface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 18.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = if (count == 1) "1 item selected" else "$count items selected",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textMute,
                        )
                        Text(
                            text = ByteFormat.short(bytes),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.text,
                        )
                    }
                    SweepTextButton(text = "Clear", onClick = onClear)
                    Spacer(Modifier.width(2.dp))
                    SweepButton(text = "Review", onClick = onClean)
                }
            }
            }
        }
    }
}
