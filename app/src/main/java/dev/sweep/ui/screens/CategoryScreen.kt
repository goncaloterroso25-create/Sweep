package dev.sweep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import dev.sweep.core.model.ByteFormat
import dev.sweep.core.model.CleanupCategory
import dev.sweep.core.model.CleanupItem
import dev.sweep.ui.SweepUiState
import dev.sweep.ui.blurb
import dev.sweep.ui.components.EmptyState
import dev.sweep.ui.components.FileDetailsSheet
import dev.sweep.ui.components.FileRow
import dev.sweep.ui.components.RevealIn
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.components.SweepTopBar
import dev.sweep.ui.components.pressable
import dev.sweep.ui.emptyLine
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.title

private enum class SortOrder(val label: String) {
    LARGEST("Largest first"),
    OLDEST("Oldest first"),
    NAME("Name"),
}

/** Roughly one screenful. Past this, rows appear immediately. */
private const val ANIMATED_ROWS = 8

/**
 * A category's contents, reviewable before anything is deleted.
 *
 * "Select safe" only picks what the safety policy already marked as redundant, so it stays
 * conservative in categories like Large files where it selects nothing at all — and the button
 * says so rather than quietly doing nothing.
 */
@Composable
fun CategoryScreen(
    category: CleanupCategory,
    state: SweepUiState,
    haptics: SweepHaptics,
    onBack: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectSafe: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onExclude: (CleanupItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    var sort by remember { mutableStateOf(SortOrder.LARGEST) }
    var sortMenu by remember { mutableStateOf(false) }
    // The item whose details sheet is open. Held here rather than per row so it survives the row
    // being recycled or reordered underneath it.
    var previewing by remember { mutableStateOf<CleanupItem?>(null) }

    val items = state.itemsIn(category)
    val sorted = remember(items, sort) {
        when (sort) {
            SortOrder.LARGEST -> items.sortedByDescending { it.size }
            SortOrder.OLDEST -> items.sortedBy { it.lastModified }
            SortOrder.NAME -> items.sortedBy { it.name.lowercase() }
        }
    }
    val selectedHere = state.selectedIn(category)
    val safeCount = items.count { it.isSafeSuggestion }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            SweepTopBar(
                title = category.title,
                subtitle = if (items.isEmpty()) null
                else "${items.size} items · ${ByteFormat.short(items.sumOf { it.size })}",
                onBack = onBack,
            )

            if (items.isEmpty()) {
                EmptyState(
                    title = "Nothing here",
                    body = category.emptyLine,
                    accent = false,
                )
                return@Column
            }

            Text(
                text = category.blurb,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textMute,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            Text(
                text = "Tap a row to see what it is. The checkbox marks it for deletion.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .border(1.dp, colors.line, RoundedCornerShape(10.dp))
                            .pressable(onClick = { sortMenu = true })
                            .padding(horizontal = 11.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            SweepIcons.Sort,
                            contentDescription = null,
                            tint = colors.textMute,
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            sort.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textMute,
                        )
                    }
                    DropdownMenu(
                        expanded = sortMenu,
                        onDismissRequest = { sortMenu = false },
                        containerColor = colors.surfaceHigh,
                    ) {
                        SortOrder.entries.forEach { option ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        option.label,
                                        color = if (option == sort) colors.accent else colors.text,
                                    )
                                },
                                onClick = {
                                    sort = option
                                    sortMenu = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                if (selectedHere.size == items.size) {
                    SweepTextButton(text = "Clear", onClick = onClearSelection)
                } else {
                    if (safeCount > 0 && selectedHere.size < safeCount) {
                        SweepTextButton(
                            text = "Select safe ($safeCount)",
                            onClick = {
                                haptics.select()
                                onSelectSafe()
                            },
                            color = colors.accent,
                        )
                    }
                    SweepTextButton(
                        text = "Select all",
                        onClick = {
                            haptics.select()
                            onSelectAll()
                        },
                    )
                }
            }

            if (safeCount == 0) {
                Text(
                    text = "Nothing here is pre-selected. Decide these one by one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                )
            }

            Spacer(Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    bottom = 150.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                itemsIndexed(sorted, key = { _, item -> item.path }) { index, item ->
                    val row = @Composable {
                        FileRow(
                            item = item,
                            selected = item.path in state.selectedPaths,
                            onToggle = {
                                haptics.select()
                                onToggle(item.path)
                            },
                            onOpen = { previewing = item },
                            onExclude = {
                                haptics.tick()
                                onExclude(item)
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    // Only the rows that are on screen when the category opens get an entrance.
                    // Staggering a list that can run to hundreds of files would cost scrolling
                    // performance for an effect nobody would see past the first screenful.
                    if (index < ANIMATED_ROWS) RevealIn(index = index) { row() } else row()
                }
            }
        }
    }

    previewing?.let { item ->
        FileDetailsSheet(
            item = item,
            selected = item.path in state.selectedPaths,
            onToggleSelected = {
                haptics.select()
                onToggle(item.path)
            },
            onExclude = {
                haptics.tick()
                previewing = null
                onExclude(item)
            },
            onDismiss = { previewing = null },
        )
    }
}
