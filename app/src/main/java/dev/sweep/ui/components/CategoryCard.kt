package dev.sweep.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CategorySummary
import dev.sweep.ui.icon
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.title

/**
 * One cleanup category on Home.
 *
 * The right-hand figure is the category's own total; the line underneath says how much of it
 * Sweep is willing to pre-select. Keeping those two numbers visibly different is the whole point
 * — it is what stops the app from feeling like it is talking the user into deleting things.
 */
@Composable
fun CategoryCard(
    summary: CategorySummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val tint = colors.categoryTint(summary.category)
    val shape = MaterialTheme.shapes.medium

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.8f), shape)
            .pressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                summary.category.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                summary.category.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitleFor(summary),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMute,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = ByteFormat.short(summary.totalBytes),
                style = MaterialTheme.typography.titleMedium,
                color = colors.text,
            )
        }

        Icon(
            SweepIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(20.dp),
        )
    }
}

private fun subtitleFor(summary: CategorySummary): String {
    val items = if (summary.itemCount == 1) "1 item" else "${summary.itemCount} items"
    return when {
        summary.suggestedCount == 0 -> "$items · review only"
        summary.suggestedCount == summary.itemCount -> "$items · all safe to remove"
        else -> "$items · ${summary.suggestedCount} pre-selected"
    }
}

/** Row used for the two non-file sections on Home: unused apps and cache assistance. */
@Composable
fun UtilityCard(
    title: String,
    subtitle: String,
    trailing: String?,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val shape = MaterialTheme.shapes.medium
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.8f), shape)
            .pressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMute,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.titleMedium, color = colors.text)
        }
        Icon(
            SweepIcons.ChevronRight,
            contentDescription = null,
            tint = colors.textFaint,
            modifier = Modifier.size(20.dp),
        )
    }
}
