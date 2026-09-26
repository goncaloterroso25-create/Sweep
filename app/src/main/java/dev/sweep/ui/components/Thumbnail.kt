package dev.sweep.ui.components

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.os.Build
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import dev.sweep.core.model.CleanupItem
import dev.sweep.core.scan.FileClassifier
import dev.sweep.ui.icon
import dev.sweep.ui.theme.Sweep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class FileKind { IMAGE, VIDEO, OTHER }

fun CleanupItem.kind(): FileKind {
    if (isDirectory) return FileKind.OTHER
    return when (FileClassifier.extensionOf(name)) {
        in FileClassifier.IMAGE_EXTENSIONS -> FileKind.IMAGE
        in FileClassifier.VIDEO_EXTENSIONS -> FileKind.VIDEO
        else -> FileKind.OTHER
    }
}

/**
 * What a file looks like, where that is cheap to find out, and its category mark otherwise.
 *
 * Coil decodes pictures at the size they are drawn, so a folder of 40 MP photos costs row-sized
 * bitmaps rather than 40 MP each. Video frames come from the platform on a background thread,
 * only when asked for, which the list does not do: [videoFrames] is for the preview.
 */
@Composable
fun FileThumbnail(
    item: CleanupItem,
    modifier: Modifier = Modifier,
    iconSize: Dp,
    contentScale: ContentScale = ContentScale.Crop,
    videoFrames: Boolean = false,
    contentDescription: String? = null,
) {
    val colors = Sweep.colors
    val kind = item.kind()
    val inspection = LocalInspectionMode.current

    Box(modifier.background(colors.surface), contentAlignment = Alignment.Center) {
        when {
            inspection -> CategoryGlyph(item, iconSize)
            kind == FileKind.IMAGE -> AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(File(item.path))
                    .crossfade(120)
                    .build(),
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
            kind == FileKind.VIDEO && videoFrames -> {
                val frame = rememberVideoFrame(item.path)
                if (frame != null) {
                    Image(frame, contentDescription, Modifier.fillMaxSize(), contentScale = contentScale)
                } else {
                    CategoryGlyph(item, iconSize)
                }
            }
            else -> CategoryGlyph(item, iconSize)
        }
    }
}

@Composable
private fun CategoryGlyph(item: CleanupItem, size: Dp) {
    Icon(item.category.icon, contentDescription = null, tint = Sweep.colors.textMute, modifier = Modifier.size(size))
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
        ThumbnailUtils.createVideoThumbnail(file, Size(720, 720), null)
    } else {
        ThumbnailUtils.createVideoThumbnail(file.absolutePath, android.provider.MediaStore.Images.Thumbnails.MINI_KIND)
    }
