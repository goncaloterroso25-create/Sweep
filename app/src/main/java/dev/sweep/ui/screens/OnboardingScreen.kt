package dev.sweep.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.sweep.core.android.PermissionStatus
import dev.sweep.core.android.SweepPermissions
import dev.sweep.core.android.SystemFlows
import dev.sweep.ui.components.AppearIn
import dev.sweep.ui.components.ButtonTone
import dev.sweep.ui.components.SweepButton
import dev.sweep.ui.components.SweepTextButton
import dev.sweep.ui.theme.Grotesk
import dev.sweep.ui.theme.Sweep

/**
 * One screen, two asks, no dark patterns.
 *
 * Both permissions are explained in terms of what the user gets, both are optional, and the
 * button to continue is live from the start — Sweep opens fine with neither of them and says
 * which parts are unavailable rather than nagging.
 */
@Composable
fun OnboardingScreen(
    permissions: PermissionStatus,
    onContinue: () -> Unit,
    onPermissionsChanged: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Sweep.colors
    val context = LocalContext.current

    val runtimeStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermissionsChanged() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.base)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Spacer(Modifier.height(36.dp))

        AppearIn(0) {
            Column {
                Text(
                    text = "Sweep",
                    style = MaterialTheme.typography.displayMedium.copy(fontFamily = Grotesk),
                    color = colors.text,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Find what's wasting space. Decide what goes.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.textMute,
                )
            }
        }

        Spacer(Modifier.height(44.dp))

        AppearIn(1) {
            PermissionBlock(
                icon = Icons.Outlined.FolderOpen,
                title = "See what can go",
                body = "Sweep needs file access to find old downloads, duplicates and leftover " +
                    "installers. Everything stays on your device.",
                granted = permissions.canScanFiles,
                grantedLabel = "File access granted",
                buttonLabel = "Allow file access",
                onGrant = {
                    if (SweepPermissions.usesRuntimeStoragePrompt) {
                        runtimeStorageLauncher.launch(SweepPermissions.runtimeStoragePermissions)
                    } else {
                        SystemFlows.launchFirstAvailable(
                            context,
                            SweepPermissions.fileAccessIntents(context),
                        )
                    }
                },
            )
        }

        Spacer(Modifier.height(14.dp))

        AppearIn(2) {
            PermissionBlock(
                icon = Icons.Outlined.Schedule,
                title = "Find apps you forgot",
                body = "Usage Access lets Sweep see when your apps were last opened. The data " +
                    "stays on the device.",
                granted = permissions.hasUsageAccess,
                grantedLabel = "Usage Access granted",
                buttonLabel = "Allow Usage Access",
                onGrant = {
                    SystemFlows.launchFirstAvailable(
                        context,
                        SweepPermissions.usageAccessIntents(context),
                    )
                },
            )
        }

        Spacer(Modifier.height(22.dp))

        AppearIn(3) {
            Text(
                text = "You can skip both. Sweep will open either way and tell you which parts " +
                    "aren't available.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
            )
        }

        Spacer(Modifier.height(30.dp))

        AppearIn(4) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SweepButton(
                    text = if (permissions.allGranted) "Start" else "Continue",
                    onClick = onContinue,
                    modifier = Modifier.weight(1f),
                )
                if (!permissions.allGranted) {
                    Spacer(Modifier.width(8.dp))
                    SweepTextButton(text = "Skip", onClick = onContinue)
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun PermissionBlock(
    icon: ImageVector,
    title: String,
    body: String,
    granted: Boolean,
    grantedLabel: String,
    buttonLabel: String,
    onGrant: () -> Unit,
) {
    val colors = Sweep.colors
    val shape = MaterialTheme.shapes.large

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surface)
            .border(
                1.dp,
                if (granted) colors.accent.copy(alpha = 0.45f) else colors.line,
                shape,
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (granted) colors.accent.copy(alpha = 0.16f)
                        else colors.surfaceHigh
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (granted) Icons.Outlined.Check else icon,
                    contentDescription = null,
                    tint = if (granted) colors.accent else colors.textMute,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(13.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.text)
        }

        Spacer(Modifier.height(11.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMute)
        Spacer(Modifier.height(16.dp))

        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(colors.accent)
                )
                Spacer(Modifier.width(9.dp))
                Text(
                    grantedLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.accent,
                )
            }
        } else {
            SweepButton(text = buttonLabel, onClick = onGrant, tone = ButtonTone.Neutral)
        }
    }
}
