package dev.sweep.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.ui.icon
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.title

/**
 * The last thing between a selection and a permanent delete.
 *
 * It states the count, the size, and every category involved, because "review before deletion"
 * only means something if the review actually itemises what is going. There is no one-tap
 * shortcut past this sheet anywhere in the app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmCleanSheet(
    items: List<CleanupItem>,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = Sweep.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val totalBytes = items.sumOf { it.size }
    val breakdown = items.groupBy { it.category }
        .toList()
        .sortedByDescending { (_, list) -> list.sumOf { it.size } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        scrimColor = colors.base.copy(alpha = 0.62f),
        dragHandle = {
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), Alignment.Center) {
                Box(
                    Modifier
                        .size(width = 34.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.line)
                )
            }
        },
    ) {
        ApplyDialogBlur()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            SectionLabel("Review before deleting")
            Spacer(Modifier.height(10.dp))

            Text(
                text = if (items.size == 1) "Delete 1 item" else "Delete ${items.size} items",
                style = MaterialTheme.typography.headlineMedium,
                color = colors.text,
            )
            Spacer(Modifier.height(4.dp))
            AnimatedBytes(
                bytes = totalBytes,
                valueStyle = MaterialTheme.typography.displayMedium,
                unitStyle = MaterialTheme.typography.headlineSmall,
                valueColor = colors.text,
                unitColor = colors.textMute,
            )

            Spacer(Modifier.height(18.dp))
            HairLine()
            Spacer(Modifier.height(6.dp))

            breakdown.forEach { (category, list) ->
                CategoryBreakdownRow(category, list.size, list.sumOf { it.size })
            }

            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .background(colors.dangerSoft)
                    .padding(14.dp)
            ) {
                Text(
                    text = "These files are removed from this device. Sweep has no recycle bin " +
                        "and cannot undo this.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SweepButton(
                    text = "Cancel",
                    onClick = onDismiss,
                    tone = ButtonTone.Neutral,
                    modifier = Modifier.weight(1f),
                )
                SweepButton(
                    text = "Delete",
                    onClick = onConfirm,
                    tone = ButtonTone.Danger,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownRow(category: CleanupCategory, count: Int, bytes: Long) {
    val colors = Sweep.colors
    val tint = colors.categoryTint(category)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(category.icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            category.title,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textFaint,
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = ByteFormat.short(bytes),
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
        )
    }
}
