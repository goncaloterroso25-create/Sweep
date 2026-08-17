package dev.sweep.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cached
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.InstalledApp
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.NoticeCard
import dev.sweep.ui.components.SectionLabel
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.SweepTopBar
import dev.sweep.ui.theme.Sweep

/**
 * Cache assistance, stated plainly.
 *
 * Android does not let one app delete another app's cache. Every "cleaner" that claims otherwise
 * is either measuring nothing or clearing its own files. What Sweep can honestly do is read the
 * per-app cache sizes (with Usage Access), open a specific app's storage page so the user can
 * clear it there, and clear its own cache.
 *
 * There used to be a button here for Android 12's `ACTION_CLEAR_APP_CACHE` dialog. It was removed
 * after device testing: on both phones tried it either did nothing or landed somewhere unrelated,
 * and a prominent button that behaves differently per OEM is worse than no button at all.
 */
@Composable
fun CacheScreen(
    state: SweepUiState,
    loadIcon: suspend (String) -> Drawable?,
    onBack: () -> Unit,
    onClearOwnCache: () -> Unit,
    onRefreshOwnCache: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current

    LaunchedEffect(Unit) { onRefreshOwnCache() }

    val cached = state.apps?.apps
        ?.filter { it.cacheBytes > 0 }
        ?.sortedByDescending { it.cacheBytes }
        .orEmpty()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding()
    ) {
        SweepTopBar(
            title = "App caches",
            subtitle = state.apps?.totalCacheBytes
                ?.takeIf { it > 0 }
                ?.let { "${ByteFormat.short(it)} cached across your apps" },
            onBack = onBack,
        )

        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                NoticeCard(
                    title = "Android owns this one",
                    body = "Android does not let one app delete another app's cache. Sweep can " +
                        "measure the sizes and open each app's storage page, where you clear it " +
                        "yourself. Tap Manage on any app below.",
                    icon = Icons.Outlined.Cached,
                    tint = colors.categoryTint(dev.sweep.core.model.CleanupCategory.SCREENSHOTS),
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .background(colors.surface)
                        .border(1.dp, colors.line, MaterialTheme.shapes.medium)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Sweep's own cache",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.text,
                        )
                        Text(
                            text = ByteFormat.short(state.ownCacheBytes) +
                                " · the only cache Sweep can clear itself",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMute,
                        )
                    }
                    SweepButton(
                        text = "Clear",
                        onClick = onClearOwnCache,
                        tone = ButtonTone.Neutral,
                        enabled = state.ownCacheBytes > 0,
                        dense = true,
                    )
                }
            }

            if (!state.permissions.hasUsageAccess) {
                item {
                    NoticeCard(
                        title = "Cache sizes need Usage Access",
                        body = "Android reports per-app storage only to apps with Usage Access. " +
                            "Without it Sweep can't tell you which apps are worth clearing.",
                        icon = Icons.Outlined.Schedule,
                        tint = colors.info,
                        action = {
                            SweepButton(
                                text = "Open Usage Access",
                                onClick = {
                                    SystemFlows.launchFirstAvailable(
                                        context,
                                        SweepPermissions.usageAccessIntents(context),
                                    )
                                },
                            )
                        },
                    )
                }
            } else if (cached.isEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (state.apps == null) {
                            "Reading app sizes…"
                        } else {
                            "No app on this device is holding a measurable cache right now."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textMute,
                    )
                }
            } else {
                item {
                    Spacer(Modifier.height(6.dp))
                    SectionLabel("Biggest caches")
                }
                items(cached.take(30), key = { it.packageName }) { app ->
                    CacheRow(
                        app = app,
                        loadIcon = loadIcon,
                        onManage = {
                            SystemFlows.launchFirstAvailable(
                                context,
                                listOf(SystemFlows.appDetailsIntent(app.packageName)),
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CacheRow(
    app: InstalledApp,
    loadIcon: suspend (String) -> Drawable?,
    onManage: () -> Unit,
) {
    val colors = Sweep.colors
    var icon by remember(app.packageName) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(app.packageName) {
        icon = runCatching {
            loadIcon(app.packageName)?.toBitmap(ICON_PX, ICON_PX)?.asImageBitmap()
        }.getOrNull()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(colors.surface)
            .border(1.dp, colors.line.copy(alpha = 0.8f), MaterialTheme.shapes.medium)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            icon?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.size(27.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.titleMedium,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = ByteFormat.short(app.cacheBytes),
            style = MaterialTheme.typography.titleSmall,
            color = colors.text,
        )
        SweepTextButton(text = "Manage", onClick = onManage)
    }
}
