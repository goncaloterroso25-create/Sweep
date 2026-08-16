package dev.sweep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.scan.FileClassifier
import dev.sweep.ui.icon
import dev.sweep.ui.reasonLabel
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springBouncy
import dev.sweep.ui.theme.springSnappy
import dev.sweep.ui.visibleReasons
import java.io.File

/**
 * A selectable file. Every row states its size and why it was suggested, and duplicate rows also
 * name the copy that will survive — the single most reassuring thing this screen can say.
 *
 * Long-press adds the file to the local exclusion list instead of hiding the option in a menu
 * the user has to go hunting for.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: CleanupItem,
    selected: Boolean,
    onToggle: () -> Unit,
    onExclude: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    var menuOpen by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }

    val tint by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = springSnappy(),
        label = "rowTint",
    )
    val shape = MaterialTheme.shapes.medium

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(lerp(Color.Transparent, colors.accentSoft, tint))
                .border(
                    1.dp,
                    lerp(colors.line.copy(alpha = 0.55f), colors.accent.copy(alpha = 0.45f), tint),
                    shape,
                )
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onToggle,
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SelectionBox(selected = selected)
            Spacer(Modifier.width(12.dp))
            FileGlyph(item)
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = reasonSummary(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.duplicate?.let { duplicate ->
                    Spacer(Modifier.height(4.dp))
                    Text(
                        // Copies of a file usually share its name, so naming the survivor
                        // without its folder would point at nothing the user can distinguish.
                        text = "Keeping ${keeperLabel(duplicate.keeperPath, duplicate.keeperName)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(Modifier.width(10.dp))
            Text(
                text = if (item.isDirectory) "—" else ByteFormat.short(item.size),
                style = MaterialTheme.typography.titleSmall,
                color = colors.text,
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = colors.surfaceHigh,
        ) {
            DropdownMenuItem(
                text = { Text("Don't suggest this again", color = colors.text) },
                leadingIcon = {
                    Icon(Icons.Outlined.Block, contentDescription = null, tint = colors.textMute)
                },
                onClick = {
                    menuOpen = false
                    onExclude()
                },
            )
        }
    }
}

private fun keeperLabel(keeperPath: String, keeperName: String): String {
    val folder = File(keeperPath).parentFile?.name
    return if (folder.isNullOrBlank()) keeperName else "$folder/$keeperName"
}

private fun reasonSummary(item: CleanupItem): String {
    val reasons = visibleReasons(item.reasons).take(2).map { reasonLabel(it, item.category) }
    val folder = File(item.path).parentFile?.name
    return (reasons + listOfNotNull(folder)).joinToString(" · ")
}

/** Thumbnail for pictures, a tinted category glyph for everything else. */
@Composable
private fun FileGlyph(item: CleanupItem) {
    val colors = Sweep.colors
    val tint = colors.categoryTint(item.category)
    val isImage = FileClassifier.extensionOf(item.name) in FileClassifier.IMAGE_EXTENSIONS
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.13f)),
        contentAlignment = Alignment.Center,
    ) {
        if (isImage && !item.isDirectory) {
            // Coil decodes to the composable's size, so a folder of 40 MP photos costs
            // 38dp-worth of bitmap each rather than 40 MP each.
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                item.category.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * The selection control. Drawn rather than composed so the tick can stroke itself on, which is
 * the small moment of feedback that makes selecting a long list feel good.
 */
@Composable
fun SelectionBox(selected: Boolean, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    val t by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = springBouncy(),
        label = "check",
    )

    Canvas(modifier = modifier.size(22.dp)) {
        val corner = CornerRadius(7.dp.toPx())
        val strokeWidth = 1.6.dp.toPx()

        drawRoundRect(
            color = lerp(Color.Transparent, colors.accent, t),
            cornerRadius = corner,
        )
        drawRoundRect(
            color = lerp(colors.textFaint, colors.accent, t),
            cornerRadius = corner,
            style = Stroke(width = strokeWidth),
        )

        if (t > 0.02f) {
            val w = size.width
            val h = size.height
            val p0 = Offset(w * 0.27f, h * 0.52f)
            val p1 = Offset(w * 0.44f, h * 0.69f)
            val p2 = Offset(w * 0.75f, h * 0.32f)
            val stroke = 2.1.dp.toPx()

            val first = (t / 0.42f).coerceIn(0f, 1f)
            drawLine(
                color = colors.onAccent,
                start = p0,
                end = Offset(p0.x + (p1.x - p0.x) * first, p0.y + (p1.y - p0.y) * first),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            val second = ((t - 0.42f) / 0.58f).coerceIn(0f, 1f)
            if (second > 0f) {
                drawLine(
                    color = colors.onAccent,
                    start = p1,
                    end = Offset(p1.x + (p2.x - p1.x) * second, p1.y + (p2.y - p1.y) * second),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
