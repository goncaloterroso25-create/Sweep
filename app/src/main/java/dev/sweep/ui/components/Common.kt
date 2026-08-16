package dev.sweep.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.sweep.ui.theme.Sweep
import dev.sweep.ui.theme.springSnappy

/** Small uppercase eyebrow used above every section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Sweep.colors.textFaint) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier,
    )
}

enum class ButtonTone { Accent, Neutral, Danger }

/**
 * The one button style in the app. It is a pill only here — every other surface uses the softer
 * corner scale, so the primary action stays visually unique.
 */
@Composable
fun SweepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: ButtonTone = ButtonTone.Accent,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    /** Inline variant for buttons that sit inside a row rather than driving the screen. */
    dense: Boolean = false,
) {
    val colors = Sweep.colors
    val background = when (tone) {
        ButtonTone.Accent -> colors.accent
        ButtonTone.Neutral -> colors.surfaceHigh
        ButtonTone.Danger -> colors.danger
    }
    val foreground = when (tone) {
        ButtonTone.Accent -> colors.onAccent
        ButtonTone.Neutral -> colors.text
        ButtonTone.Danger -> Color.White
    }
    val alpha by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.38f,
        animationSpec = springSnappy(),
        label = "buttonAlpha",
    )

    Row(
        modifier = modifier
            .alpha(alpha)
            .clip(CircleShape)
            .background(background)
            // In light mode the neutral fill is nearly the same value as the surface behind it,
            // so without an edge the button reads as a floating smudge rather than a control.
            .then(
                if (tone == ButtonTone.Neutral) {
                    Modifier.border(1.dp, colors.line, CircleShape)
                } else {
                    Modifier
                }
            )
            .pressable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (dense) 16.dp else 26.dp,
                vertical = if (dense) 9.dp else 16.dp,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = foreground)
    }
}

/** Quiet secondary action: text only, still compresses under the finger. */
@Composable
fun SweepTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Sweep.colors.textMute,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .pressable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/**
 * The card used for anything Sweep needs to explain: a missing permission, a platform limit, a
 * warning after a partial failure. Always states what is true and what the user can do.
 */
@Composable
fun NoticeCard(
    title: String,
    body: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Sweep.colors.info,
    action: (@Composable () -> Unit)? = null,
) {
    val colors = Sweep.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(colors.surface)
            .border(1.dp, colors.line, MaterialTheme.shapes.large)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(13.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, color = colors.text)
        }
        Spacer(Modifier.height(11.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = colors.textMute)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

/** Compact header used on every screen below Home. */
@Composable
fun SweepTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = Sweep.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .pressable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.text,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMute,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/** Hairline used to separate list sections without drawing a heavy divider. */
@Composable
fun HairLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Sweep.colors.line.copy(alpha = 0.6f))
    )
}

val ScreenPadding = PaddingValues(horizontal = 20.dp)
