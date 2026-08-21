package dev.sweep.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.DeviceStorageInfo
import dev.sweep.core.model.ByteFormat
import dev.sweep.ui.CleanupSummary
import dev.sweep.ui.components.AnimatedBytes
import dev.sweep.ui.components.AppearIn
import dev.sweep.ui.components.NoticeCard
import dev.sweep.ui.components.SectionLabel
import dev.sweep.ui.components.StorageMeter
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepHaptics
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.SweepIcons
import dev.sweep.ui.title

/**
 * The moment after a clean.
 *
 * The number that counts up is the number of bytes that genuinely left the device — files that
 * could not be deleted are reported separately and contribute nothing to it. The block field
 * carries over from Home with its accent region already drained, so the freed space is something
 * the user watches happen rather than something the app asserts.
 */
@Composable
fun CompletionScreen(
    summary: CleanupSummary,
    storage: DeviceStorageInfo,
    haptics: SweepHaptics,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors

    LaunchedEffect(Unit) {
        if (summary.filesRemoved > 0) haptics.confirm() else haptics.tick()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.weight(0.8f))

        AppearIn(0) {
            Column {
                SectionLabel(if (summary.filesRemoved > 0) "Cleared" else "Finished")
                Spacer(Modifier.height(12.dp))
                AnimatedBytes(
                    bytes = summary.bytesRecovered,
                    valueColor = colors.text,
                    unitColor = colors.textMute,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = closingLine(summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMute,
                )
            }
        }

        Spacer(Modifier.height(30.dp))

        AppearIn(1) {
            Column {
                StorageMeter(
                    usedFraction = storage.usedFraction,
                    reclaimFraction = 0f,
                    scanPhase = null,
                    contentDescription = "${ByteFormat.short(storage.freeBytes)} now free",
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    // Same wording and weight as Home, because it is the same fact and the user
                    // has just watched it change.
                    text = "${ByteFormat.short(storage.freeBytes)} free of " +
                        "${ByteFormat.short(storage.totalBytes)} · " +
                        "${ByteFormat.short(storage.usedBytes)} used",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textMute,
                )
            }
        }

        if (summary.failed.isNotEmpty() || summary.alreadyGone > 0) {
            Spacer(Modifier.height(24.dp))
            AppearIn(2) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (summary.failed.isNotEmpty()) {
                        NoticeCard(
                            title = if (summary.failed.size == 1) "1 item couldn't be deleted"
                            else "${summary.failed.size} items couldn't be deleted",
                            body = failureBody(summary),
                            icon = SweepIcons.Warning,
                            tint = colors.danger,
                        )
                    }
                    if (summary.alreadyGone > 0) {
                        Text(
                            text = "${summary.alreadyGone} " +
                                (if (summary.alreadyGone == 1) "file was" else "files were") +
                                " already gone before Sweep got to them. They aren't counted above.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textFaint,
                        )
                    }
                }
            }
        }

        if (summary.categories.isNotEmpty() && summary.filesRemoved > 0) {
            Spacer(Modifier.height(22.dp))
            AppearIn(3) {
                Text(
                    text = "${summary.filesRemoved} items removed from " +
                        summary.categories.joinToString(", ") { it.title.lowercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textFaint,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        AppearIn(4) {
            Box(Modifier.fillMaxWidth(), Alignment.Center) {
                SweepButton(text = "Done", onClick = onDone, modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(28.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** Understated on purpose. No exclamation marks, no claims about speed or health. */
private fun closingLine(summary: CleanupSummary): String = when {
    summary.filesRemoved == 0 && summary.failed.isNotEmpty() ->
        "Nothing was removed."

    summary.filesRemoved == 0 ->
        "Nothing left to remove."

    // Empty folders are real removals worth zero bytes. Saying "small but it's yours again"
    // over a 0 B figure would read like a bug.
    summary.bytesRecovered == 0L ->
        "Tidied up. There was no space to reclaim from those."

    summary.bytesRecovered >= 1_000_000_000L -> "Much better."
    summary.bytesRecovered >= 100_000_000L -> "That's a decent chunk back."
    else -> "Small, but it's yours again."
}

private fun failureBody(summary: CleanupSummary): String {
    val reasons = summary.failed.map { it.reason }.distinct().take(2)
    val skipped = ByteFormat.short(summary.failed.sumOf { it.size })
    return "$skipped stayed on the device. " + reasons.joinToString(" ") { "$it." } +
        " These are not counted as recovered."
}
