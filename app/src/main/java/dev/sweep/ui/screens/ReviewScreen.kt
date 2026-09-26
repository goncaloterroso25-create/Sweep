package dev.sweep.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.blurb
import dev.sweep.ui.components.ActionTray
import dev.sweep.ui.components.FileThumbnail
import dev.sweep.ui.components.Hairline
import dev.sweep.ui.components.IconAction
import dev.sweep.ui.components.ScreenBar
import dev.sweep.ui.components.ScreenTitle
import dev.sweep.ui.components.SelectMark
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.components.TextAction
import dev.sweep.ui.components.bytes
import dev.sweep.ui.components.grouped
import dev.sweep.ui.components.isCompact
import dev.sweep.ui.components.plural
import dev.sweep.ui.emptyLine
import dev.sweep.ui.reasonLabel
import dev.sweep.ui.theme.LocalReducedMotion
import dev.sweep.ui.theme.MaxContentWidth
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.theme.SweepMotion
import dev.sweep.ui.theme.SweepShape
import dev.sweep.ui.theme.SweepType
import dev.sweep.ui.theme.effectSpec
import dev.sweep.ui.title
import dev.sweep.ui.visibleReasons

private enum class SortOrder(val label: String) {
    LARGEST("Largest first"),
    OLDEST("Oldest first"),
    NAME("Name"),
}

/** A row in the list: a file, or the head of a duplicate group naming the copy that is kept. */
private sealed interface ReviewRow {
    val key: String

    data class FileEntry(val item: CleanupItem) : ReviewRow {
        override val key get() = "file:" + item.path
    }

    data class KeptCopy(val groupId: String, val name: String, val path: String, val copies: Int, val size: Long) : ReviewRow {
        override val key get() = "kept:$groupId"
    }
}

/**
 * One category, reviewed before anything goes.
 *
 * The rules of the screen: the select mark and the row are separate targets, because one marks a
 * file for deletion and the other only looks at it. Every row says why Sweep suggested it and
 * where it lives. Duplicates are shown in their groups, with the surviving copy listed and
 * untouchable, so "one copy is always kept" is something the user can see rather than trust.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun ReviewScreen(
    category: CleanupCategory,
    state: SweepUiState,
    haptics: SweepHaptics,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectSuggested: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExclude: (CleanupItem) -> Unit,
    onReview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val reduced = LocalReducedMotion.current
    var sort by rememberSaveable { mutableStateOf(SortOrder.LARGEST) }
    var sortMenu by remember { mutableStateOf(false) }
    var previewPath by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val items = state.itemsIn(category)
    val rows = remember(items, sort) { buildRows(items, sort) }
    val previewing = previewPath?.let { path -> items.firstOrNull { it.path == path } }
    val selectedHere = remember(items, state.selectedPaths) { items.count { it.path in state.selectedPaths } }
    val suggested = remember(items) { items.count { it.isSafeSuggestion } }
    val suggestedUnselected = remember(items, state.selectedPaths) {
        items.count { it.isSafeSuggestion && it.path !in state.selectedPaths }
    }

    BackHandler(enabled = previewing != null) { previewPath = null }

    SharedTransitionLayout(
        modifier
            .fillMaxSize()
            .background(colors.base)
    ) {
        val shared: SharedTransitionScope = this
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                contentPadding = PaddingValues(bottom = 132.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                item(key = "bar") {
                    ScreenBar(
                        onBack = onBack,
                        modifier = Modifier.widthIn(max = MaxContentWidth + 12.dp),
                        actions = {
                            if (items.size > 1) {
                                Box {
                                    IconAction(SweepIcons.Sort, "Sort, currently ${sort.label}", { sortMenu = true }, tint = colors.textMute)
                                    DropdownMenu(
                                        expanded = sortMenu,
                                        onDismissRequest = { sortMenu = false },
                                        containerColor = colors.raised,
                                    ) {
                                        SortOrder.entries.forEach { option ->
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        option.label,
                                                        style = SweepType.rowTitle,
                                                        color = if (option == sort) colors.signalInk else colors.text,
                                                    )
                                                },
                                                trailingIcon = if (option == sort) {
                                                    { Icon(SweepIcons.Check, null, tint = colors.signalInk, modifier = Modifier.size(18.dp)) }
                                                } else null,
                                                onClick = {
                                                    sort = option
                                                    sortMenu = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        },
                    )
                }

                item(key = "title") {
                    Column(Modifier.reviewColumn()) {
                        ScreenTitle(
                            title = category.title,
                            subtitle = if (items.isEmpty()) null
                            else "${items.sumOf { it.size }.bytes()} in ${plural(items.size, if (category == CleanupCategory.EMPTY_FOLDERS) "folder" else "file")}",
                        )
                        Text(
                            text = if (items.isEmpty()) category.emptyLine else category.blurb,
                            style = SweepType.body.copy(fontSize = SweepType.meta.fontSize, lineHeight = SweepType.meta.lineHeight),
                            color = colors.textMute,
                        )
                    }
                }

                if (items.isNotEmpty()) {
                    item(key = "controls") {
                        FlowRow(
                            modifier = Modifier
                                .reviewColumn()
                                .padding(top = 10.dp, bottom = 4.dp)
                                .padding(start = 0.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (suggestedUnselected > 0) {
                                TextAction(
                                    text = "Select suggested (${suggestedUnselected.grouped()})",
                                    onClick = {
                                        haptics.select()
                                        onSelectSuggested()
                                    },
                                    color = colors.signalInk,
                                    modifier = Modifier.padding(start = 0.dp),
                                )
                            }
                            if (selectedHere < items.size) {
                                TextAction(
                                    text = "Select all",
                                    onClick = {
                                        haptics.select()
                                        onSelectAll()
                                    },
                                )
                            }
                            if (selectedHere > 0) {
                                TextAction(text = "Clear", onClick = onClearSelection, color = colors.textMute)
                            }
                        }
                        if (suggested == 0) {
                            Text(
                                text = "Nothing here is chosen for you. Look, then decide one at a time.",
                                style = SweepType.meta,
                                color = colors.textMute,
                                modifier = Modifier
                                    .reviewColumn()
                                    .padding(bottom = 6.dp),
                            )
                        }
                    }
                }

                items(rows, key = { it.key }, contentType = { it::class }) { row ->
                    when (row) {
                        is ReviewRow.KeptCopy -> KeptRow(
                            row = row,
                            selectedElsewhere = row.path in state.selectedPaths,
                            modifier = Modifier.reviewColumn().animateItem(),
                        )
                        is ReviewRow.FileEntry -> {
                            val item = row.item
                            FileRow(
                                item = item,
                                selected = item.path in state.selectedPaths,
                                grouped = category == CleanupCategory.DUPLICATES,
                                onToggle = {
                                    haptics.select()
                                    onToggle(item.path)
                                },
                                onOpen = { previewPath = item.path },
                                onExclude = {
                                    haptics.tick()
                                    onExclude(item)
                                },
                                thumbnail = {
                                    val thumbModifier = if (reduced) Modifier else with(shared) {
                                        Modifier.sharedElementWithCallerManagedVisibility(
                                            sharedContentState = rememberSharedContentState("thumb:" + item.path),
                                            visible = previewPath != item.path,
                                        )
                                    }
                                    FileThumbnail(
                                        item = item,
                                        iconSize = 20.dp,
                                        modifier = thumbModifier
                                            .size(44.dp)
                                            .clip(SweepShape.thumb),
                                    )
                                },
                                modifier = Modifier
                                    .reviewColumn()
                                    .animateItem(),
                            )
                        }
                    }
                }
            }

            ActionTray(
                visible = state.selection.count > 0 && previewing == null,
                count = state.selection.count,
                bytes = state.selection.bytes,
                onClear = { onClearSelection() },
                onReview = onReview,
                modifier = Modifier.align(Alignment.BottomCenter),
            )

            // The last item stays on screen while the preview closes, so the image has something
            // to travel back into.
            var lastPreviewed by remember { mutableStateOf<CleanupItem?>(null) }
            if (previewing != null) lastPreviewed = previewing
            AnimatedVisibility(
                visible = previewing != null,
                enter = fadeIn(tween(if (reduced) SweepMotion.REDUCED_FADE else SweepMotion.BASE)),
                exit = fadeOut(tween(if (reduced) SweepMotion.REDUCED_FADE else SweepMotion.QUICK + 60)),
            ) {
                val item = lastPreviewed ?: return@AnimatedVisibility
                val visibility: AnimatedVisibilityScope = this
                PreviewPane(
                    item = item,
                    selected = item.path in state.selectedPaths,
                    media = { mediaModifier ->
                        val sharedModifier = if (reduced) Modifier else with(shared) {
                            Modifier.sharedElement(
                                state = rememberSharedContentState("thumb:" + item.path),
                                animatedVisibilityScope = visibility,
                            )
                        }
                        sharedModifier.then(mediaModifier)
                    },
                    onClose = { previewPath = null },
                    onToggleSelected = {
                        haptics.select()
                        onToggle(item.path)
                    },
                    onExclude = {
                        haptics.tick()
                        previewPath = null
                        onExclude(item)
                    },
                )
            }
        }
    }
}

private fun Modifier.reviewColumn(): Modifier = this
    .widthIn(max = MaxContentWidth)
    .fillMaxWidth()
    .padding(horizontal = 20.dp)

private fun buildRows(items: List<CleanupItem>, sort: SortOrder): List<ReviewRow> {
    val comparator: Comparator<CleanupItem> = when (sort) {
        SortOrder.LARGEST -> compareByDescending<CleanupItem> { it.size }.thenBy { it.name.lowercase() }
        SortOrder.OLDEST -> compareBy<CleanupItem> { it.lastModified }.thenBy { it.name.lowercase() }
        SortOrder.NAME -> compareBy<CleanupItem> { it.name.lowercase() }.thenBy { it.path }
    }
    val grouped = items.filter { it.duplicate != null }.groupBy { it.duplicate!!.groupId }
    if (grouped.isEmpty()) return items.sortedWith(comparator).map { ReviewRow.FileEntry(it) }

    // Groups are ordered by what the group as a whole would give back, or by its first member.
    val groups = grouped.values.sortedWith { a, b ->
        when (sort) {
            SortOrder.LARGEST -> b.sumOf { it.size }.compareTo(a.sumOf { it.size })
            else -> comparator.compare(a.sortedWith(comparator).first(), b.sortedWith(comparator).first())
        }
    }
    val rows = ArrayList<ReviewRow>()
    for (group in groups) {
        val info = group.first().duplicate!!
        rows += ReviewRow.KeptCopy(info.groupId, info.keeperName, info.keeperPath, info.copiesInGroup, group.first().size)
        group.sortedWith(comparator).forEach { rows += ReviewRow.FileEntry(it) }
    }
    items.filter { it.duplicate == null }.sortedWith(comparator).forEach { rows += ReviewRow.FileEntry(it) }
    return rows
}

/**
 * The copy of a duplicate Sweep keeps. It has no select mark here, on purpose. If the same file also
 * qualifies for another category it can still be chosen there by hand, and the delete sheet warns
 * when a selection would leave no copy at all.
 */
@Composable
private fun KeptRow(row: ReviewRow.KeptCopy, selectedElsewhere: Boolean, modifier: Modifier = Modifier) {
    val colors = Sweep.colors
    Column(modifier.padding(top = 18.dp)) {
        Text(
            text = "${row.copies} identical copies · ${row.size.bytes()} each",
            style = SweepType.label,
            color = colors.textMute,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .semantics(mergeDescendants = true) {},
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(SweepIcons.Keep, contentDescription = null, tint = colors.signalInk, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(row.name, style = SweepType.rowTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "Kept · ${folderLabel(row.path)}",
                    style = SweepType.meta,
                    color = colors.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (selectedElsewhere) {
                    // The same file qualified for another category and was chosen there by hand.
                    Text(
                        text = "Also selected in another category, so it would be deleted too.",
                        style = SweepType.meta,
                        color = colors.danger,
                    )
                }
            }
        }
        Hairline(inset = 56.dp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    item: CleanupItem,
    selected: Boolean,
    grouped: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onExclude: () -> Unit,
    thumbnail: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    var menu by remember { mutableStateOf(false) }
    val wash by animateFloatAsState(if (selected) 1f else 0f, effectSpec(), label = "wash")

    Box(modifier) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(lerp(Color.Transparent, colors.signalWash, wash))
                    .combinedClickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = "Preview",
                        onLongClickLabel = "More options",
                        onClick = onOpen,
                        onLongClick = { menu = true },
                    )
                    .heightIn(min = 68.dp)
                    .padding(end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Its own target, sized for a thumb, and deliberately apart from the rest of the
                // row: this is the control that decides what gets deleted.
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .toggleable(
                            value = selected,
                            onValueChange = { onToggle() },
                            role = Role.Checkbox,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        )
                        .semantics {
                            contentDescription = "Delete ${item.name}"
                            stateDescription = if (selected) "Selected" else "Not selected"
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    SelectMark(selected)
                }
                BoxWithConstraints(Modifier.weight(1f)) {
                    val size = if (item.isDirectory) "-" else item.size.bytes()
                    if (isCompact(maxWidth + 54.dp)) {
                        // Narrow, or very large text: no thumbnail, and the size gets its own line
                        // instead of fighting the name for the width.
                        Column {
                            Text(item.name, style = SweepType.rowTitle, color = colors.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            Text(size, style = SweepType.rowFigure, color = colors.text)
                            Text(rowMeta(item, grouped), style = SweepType.meta, color = colors.textMute, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(6.dp))
                            thumbnail()
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = SweepType.rowTitle, color = colors.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = rowMeta(item, grouped),
                                    style = SweepType.meta,
                                    color = colors.textMute,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(text = size, style = SweepType.rowFigure, color = colors.text, maxLines = 1)
                        }
                    }
                }
            }
            Hairline(inset = 48.dp)
        }

        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = Sweep.colors.raised) {
            DropdownMenuItem(
                text = { Text("Preview", style = SweepType.rowTitle, color = colors.text) },
                leadingIcon = { Icon(SweepIcons.Preview, null, tint = colors.textMute, modifier = Modifier.size(20.dp)) },
                onClick = {
                    menu = false
                    onOpen()
                },
            )
            DropdownMenuItem(
                text = { Text("Don't suggest again", style = SweepType.rowTitle, color = colors.text) },
                leadingIcon = { Icon(SweepIcons.Exclude, null, tint = colors.textMute, modifier = Modifier.size(20.dp)) },
                onClick = {
                    menu = false
                    onExclude()
                },
            )
        }
    }
}

private fun rowMeta(item: CleanupItem, grouped: Boolean): String {
    val reasons = visibleReasons(item.reasons)
        .filterNot { grouped && it is dev.sweep.core.model.Reason.DuplicateCopies }
        .take(1)
        .map { reasonLabel(it, item.category) }
    return (reasons + folderLabel(item.path)).joinToString(" · ")
}

/** Where a file lives, as the user would describe it: relative to their storage, not to "/". */
internal fun folderLabel(path: String): String {
    val normalised = path.replace('\\', '/')
    val parent = normalised.substringBeforeLast('/', missingDelimiterValue = "").ifEmpty { return "Unknown folder" }
    val relative = parent.substringAfter("/storage/emulated/0", missingDelimiterValue = parent).trimStart('/')
    return when {
        relative.isEmpty() -> "Main storage"
        relative.startsWith("storage/") -> relative.substringAfter("storage/").substringAfter('/').ifEmpty { "Removable storage" }
        else -> relative
    }
}
