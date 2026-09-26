package dev.sweep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.FileOpener
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DateFormat
import dev.sweep.core.scan.FileClassifier
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.FileKind
import dev.sweep.ui.components.FileThumbnail
import dev.sweep.ui.components.IconAction
import dev.sweep.ui.components.Notice
import dev.sweep.ui.components.NoticeTone
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.TextAction
import dev.sweep.ui.components.bytes
import dev.sweep.ui.components.kind
import dev.sweep.ui.reasonLabel
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.visibleReasons

/**
 * A file, before it is deleted.
 *
 * The content comes first and takes the room: the picture, a video's frame, or for anything Sweep
 * cannot draw, its type set large. The facts sit under it in one compact block. Sweep does not
 * try to be a gallery or a PDF reader; "Open" hands the file to whatever the device already uses,
 * with a read-only grant for that one file.
 */
@Composable
fun PreviewPane(
    item: CleanupItem,
    selected: Boolean,
    /** Wraps the media box, so the caller can connect it to the row it came from. */
    media: @Composable (Modifier) -> Modifier,
    onClose: () -> Unit,
    onToggleSelected: () -> Unit,
    onExclude: () -> Unit,
) {
    val colors = Sweep.colors
    val context = LocalContext.current
    var openResult by remember(item.path) { mutableStateOf<FileOpener.Result?>(null) }
    val kind = item.kind()

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = MaxContentWidth)
                .fillMaxSize()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(SweepIcons.Close, "Close preview", onClose)
                Spacer(Modifier.weight(1f))
                if (!item.isDirectory) {
                    IconAction(SweepIcons.OpenExternal, "Open in another app", {
                        openResult = FileOpener.open(context, item.path)
                    }, tint = colors.textMute)
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (kind == FileKind.OTHER) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FileThumbnail(
                            item = item,
                            iconSize = 36.dp,
                            modifier = media(Modifier)
                                .size(96.dp)
                                .clip(RoundedCornerShape(22.dp)),
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = typeLabel(item),
                            style = SweepType.label,
                            color = colors.textMute,
                        )
                    }
                } else {
                    FileThumbnail(
                        item = item,
                        iconSize = 36.dp,
                        contentScale = ContentScale.Fit,
                        videoFrames = true,
                        contentDescription = "Preview of ${item.name}",
                        modifier = media(Modifier)
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 18.dp, bottom = 12.dp)
            ) {
                Text(item.name, style = SweepType.headline, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Fact("Size", if (item.isDirectory) "Empty folder" else item.size.bytes())
                Fact("Modified", DateFormat.day(item.lastModified))
                Fact("Folder", folderLabel(item.path))
                Fact("Why", whyLine(item))
                item.duplicate?.let {
                    Fact("Kept copy", "${it.keeperName} in ${folderLabel(it.keeperPath)}")
                }

                val failure = openResult?.takeIf { it != FileOpener.Result.OPENED }
                if (failure != null) {
                    Spacer(Modifier.height(10.dp))
                    Notice(
                        text = when (failure) {
                            FileOpener.Result.NO_VIEWER ->
                                "No app on this device opens ${FileClassifier.extensionOf(item.name).takeIf { it.isNotEmpty() }?.let { ".$it files" } ?: "this file"}."
                            FileOpener.Result.MISSING -> "This file is no longer on the device."
                            else -> "Android would not open this file."
                        },
                        icon = SweepIcons.Warning,
                        tone = NoticeTone.Danger,
                    )
                }

                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!item.isDirectory) {
                        SweepButton(
                            text = "Open",
                            icon = SweepIcons.OpenExternal,
                            onClick = { openResult = FileOpener.open(context, item.path) },
                            tone = ButtonTone.Secondary,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SweepButton(
                        text = if (selected) "Selected" else "Select",
                        icon = if (selected) SweepIcons.Check else null,
                        onClick = onToggleSelected,
                        tone = if (selected) ButtonTone.Secondary else ButtonTone.Primary,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextAction(
                    text = "Don't suggest this again",
                    onClick = onExclude,
                    color = colors.textMute,
                    icon = SweepIcons.Exclude,
                )
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    val colors = Sweep.colors
    Row(Modifier.padding(vertical = 5.dp)) {
        Text(label, style = SweepType.meta, color = colors.textMute, modifier = Modifier.width(88.dp))
        Text(value, style = SweepType.meta, color = colors.text, modifier = Modifier.weight(1f))
    }
}

private fun whyLine(item: CleanupItem): String {
    val reasons = visibleReasons(item.reasons).map { reasonLabel(it, item.category) }
    val selection = when {
        item.duplicate != null -> "Another identical copy is kept."
        item.isSafeSuggestion -> "Sweep pre-selects files like this."
        else -> "Sweep never pre-selects files like this."
    }
    return (reasons.joinToString(" · ") + ". " + selection).trimStart('.', ' ')
}

private fun typeLabel(item: CleanupItem): String {
    if (item.isDirectory) return "Folder"
    val extension = FileClassifier.extensionOf(item.name)
    return if (extension.isEmpty()) "File" else "${extension.uppercase()} file"
}
