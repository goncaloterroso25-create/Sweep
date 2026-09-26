package dev.sweep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.ByteFigure
import dev.sweep.ui.components.Hairline
import dev.sweep.ui.components.Notice
import dev.sweep.ui.components.NoticeTone
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.bytes
import dev.sweep.ui.components.grouped
import dev.sweep.ui.components.plural
import dev.sweep.ui.icon
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepShape
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.title

/**
 * The last step before a permanent delete, and the only way to reach one.
 *
 * It itemises what is going by category, calls out anything the user picked from categories
 * Sweep never pre-selects (those are the pictures and big files most worth a second look), and
 * says plainly that there is no undo. The delete button names the count, so the tap that commits
 * is also the last thing read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteSheet(
    items: List<CleanupItem>,
    /** Kept files whose every copy this selection would remove. Normally empty. */
    lastCopies: List<String>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Sweep.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val total = remember(items) { items.sumOf { it.size } }
    val breakdown = remember(items) {
        items.groupBy { it.category }.toList().sortedByDescending { (_, list) -> list.sumOf { it.size } }
    }
    val personal = remember(items) {
        items.filter { it.category == CleanupCategory.SCREENSHOTS || it.category == CleanupCategory.LARGE_FILES }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SweepShape.sheet,
        containerColor = colors.raised,
        scrimColor = colors.scrim,
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.lineStrong)
                )
            }
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxContentWidth)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            ) {
                Text("Delete ${plural(items.size, "item")}", style = SweepType.headline, color = colors.text)
                Spacer(Modifier.height(4.dp))
                ByteFigure(bytes = total, valueStyle = SweepType.figure, unitStyle = SweepType.headline, animate = false)

                Spacer(Modifier.height(16.dp))
                Hairline()
                breakdown.forEach { (category, list) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(category.icon, contentDescription = null, tint = colors.textMute, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(category.title, style = SweepType.rowTitle, color = colors.text, modifier = Modifier.weight(1f))
                        Text(list.size.grouped(), style = SweepType.meta, color = colors.textMute)
                        Spacer(Modifier.width(14.dp))
                        Text(
                            text = if (category == CleanupCategory.EMPTY_FOLDERS) "-" else list.sumOf { it.size }.bytes(),
                            style = SweepType.rowFigure,
                            color = colors.text,
                        )
                    }
                }
                Hairline()

                if (lastCopies.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Notice(
                        title = if (lastCopies.size == 1) "No copy of this file would be left"
                        else "No copy of ${lastCopies.size} files would be left",
                        text = "The selection includes every copy of " +
                            lastCopies.take(2).joinToString(" and ") +
                            (if (lastCopies.size > 2) " and others" else "") +
                            ", including the one Sweep would normally keep.",
                        icon = SweepIcons.Warning,
                        tone = NoticeTone.Danger,
                    )
                }

                if (personal.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Notice(
                        title = "Includes files you chose yourself",
                        text = listOfNotNull(
                            personal.count { it.category == CleanupCategory.SCREENSHOTS }.takeIf { it > 0 }?.let { plural(it, "screenshot") },
                            personal.count { it.category == CleanupCategory.LARGE_FILES }.takeIf { it > 0 }?.let { plural(it, "large file") },
                        ).joinToString(" and ") + ". " +
                            "Sweep never selects these on its own, so check they are the ones you meant.",
                        icon = SweepIcons.Info,
                        tone = NoticeTone.Neutral,
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Deleted files cannot be recovered. Sweep has no recycle bin.",
                    style = SweepType.meta,
                    color = colors.danger,
                )

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SweepButton(
                        text = "Cancel",
                        onClick = onDismiss,
                        tone = ButtonTone.Secondary,
                        modifier = Modifier.weight(1f),
                    )
                    SweepButton(
                        text = "Delete ${items.size.grouped()}",
                        onClick = onConfirm,
                        tone = ButtonTone.Danger,
                        icon = SweepIcons.Delete,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
