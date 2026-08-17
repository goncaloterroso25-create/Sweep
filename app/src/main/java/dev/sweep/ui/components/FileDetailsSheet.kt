package dev.sweep.ui.components

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.sweep.core.android.FileOpener
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.model.DateFormat
import dev.sweep.core.scan.FileClassifier
import dev.sweep.ui.icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What a file actually is, before it is deleted.
 *
 * Sweep does not try to render every format itself — it shows the facts it already has plus a
 * thumbnail where one is cheap, and hands the file to the device's own viewer for the rest. That
 * keeps the feature to one sheet and one intent, and means a PDF opens in the user's PDF reader
 * rather than in something Sweep pretended to build.
 *
 * Selecting and opening stay separate throughout: this sheet is reached by tapping the row, the
 * checkbox never opens anything, and the sheet's own select button says which it is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileDetailsSheet(
    item: CleanupItem,
    selected: Boolean,
    onToggleSelected: () -> Unit,
    onExclude: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = dev.sweep.ui.theme.Sweep.colors
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var openResult by remember(item.path) { mutableStateOf<FileOpener.Result?>(null) }

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
            // The thumbnail settles out of the size it had in the row, which is as close to a
            // shared element as this is worth taking: no extra layout coordination, no chance of
            // the sheet arriving before the image has anywhere to land.
            ExpandIn { Preview(item) }

            Spacer(Modifier.height(16.dp))
            RevealIn(index = 0) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))
            HairLine()
            Spacer(Modifier.height(4.dp))

            RevealIn(index = 1) { DetailRow("Folder", folderOf(item.path)) }
            RevealIn(index = 2) {
                DetailRow("Size", if (item.isDirectory) "Folder" else ByteFormat.short(item.size))
            }
            RevealIn(index = 3) { DetailRow("Modified", DateFormat.day(item.lastModified)) }
            item.duplicate?.let {
                RevealIn(index = 4) {
                    DetailRow("Duplicate of", "${it.keeperName} · ${folderOf(it.keeperPath)}")
                }
            }

            Spacer(Modifier.height(6.dp))

            if (openResult != null && openResult != FileOpener.Result.OPENED) {
                Text(
                    text = when (openResult) {
                        FileOpener.Result.NO_VIEWER ->
                            "No app on this device can open " +
                                (FileClassifier.extensionOf(item.name)
                                    .takeIf { it.isNotEmpty() }
                                    ?.let { ".$it files" } ?: "this file") + "."
                        FileOpener.Result.MISSING ->
                            "This file is no longer on the device."
                        else -> "Android wouldn't open this file."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.danger,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!item.isDirectory) {
                    SweepButton(
                        text = "Open",
                        onClick = { openResult = FileOpener.open(context, item.path) },
                        modifier = Modifier.weight(1f),
                    )
                }
                SweepButton(
                    text = if (selected) "Deselect" else "Select",
                    onClick = onToggleSelected,
                    tone = ButtonTone.Neutral,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(4.dp))
            SweepTextButton(
                text = "Don't suggest this again",
                onClick = onExclude,
                modifier = Modifier.padding(start = 0.dp),
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    val colors = dev.sweep.ui.theme.Sweep.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textFaint,
            modifier = Modifier.width(96.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A picture where one can be had cheaply, the category mark otherwise.
 *
 * Coil decodes images straight from the file at the size drawn. Video frames are not something
 * Coil handles without an extra artifact, so [ThumbnailUtils] does that one — a single frame on a
 * background thread rather than a decoding library Sweep would otherwise not need.
 */
@Composable
private fun Preview(item: CleanupItem) {
    val colors = dev.sweep.ui.theme.Sweep.colors
    val tint = colors.categoryTint(item.category)
    val extension = FileClassifier.extensionOf(item.name)
    val isImage = !item.isDirectory && extension in FileClassifier.IMAGE_EXTENSIONS
    val isVideo = !item.isDirectory && extension in FileClassifier.VIDEO_EXTENSIONS
    val shape = RoundedCornerShape(16.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isImage || isVideo) 168.dp else 92.dp)
            .clip(shape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isImage -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .crossfade(true)
                    .build(),
                contentDescription = "Preview of ${item.name}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            isVideo -> {
                val frame = rememberVideoFrame(item.path)
                if (frame != null) {
                    androidx.compose.foundation.Image(
                        bitmap = frame,
                        contentDescription = "Preview of ${item.name}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        item.category.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            else -> Icon(
                item.category.icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun rememberVideoFrame(path: String): ImageBitmap? {
    var frame by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        frame = withContext(Dispatchers.IO) {
            runCatching { videoThumbnail(File(path)) }.getOrNull()?.asImageBitmap()
        }
    }
    return frame
}

@Suppress("DEPRECATION")
private fun videoThumbnail(file: File): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ThumbnailUtils.createVideoThumbnail(file, Size(512, 512), null)
    } else {
        ThumbnailUtils.createVideoThumbnail(
            file.absolutePath,
            android.provider.MediaStore.Images.Thumbnails.MINI_KIND,
        )
    }

private fun folderOf(path: String): String =
    File(path).parentFile?.absolutePath?.substringAfter("/storage/emulated/0/", "")
        ?.takeIf { it.isNotBlank() }
        ?: File(path).parent
        ?: "Unknown"
