package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(28.dp)
private val RowShape = RoundedCornerShape(16.dp)
private val IconContainerShape = RoundedCornerShape(10.dp)

/**
 * The floating centered card every settings-family screen (Settings itself,
 * and its Advanced sub-page) is built from — a dim backdrop behind a fixed-
 * width rounded card with an icon+title+subtitle header, matching the
 * reference design precisely rather than reusing the right-anchored full-
 * height drawer the Subtitles/Audio/Quality/Playback Speed menus still use.
 */
@Composable
fun SettingsCardScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(460.dp)
                .background(MangoBackgroundElevated, CardShape)
                .border(BorderStroke(1.dp, DividerSubtle), CardShape)
                .padding(24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconContainer(icon = icon, size = 44.dp, iconSize = 22.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(text = title, color = TextPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
        }
    }
}

@Composable
private fun IconContainer(icon: ImageVector, size: Dp, iconSize: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .background(MangoSurface, IconContainerShape)
            .border(BorderStroke(1.dp, DividerSubtle), IconContainerShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(iconSize))
    }
}

/**
 * One settings row: icon container, title/subtitle, and a trailing chevron
 * (navigates) or [ToggleSwitch] (toggles in place). Shows a soft amber glow
 * around itself while D-pad-focused — approximated with a radial-gradient
 * halo layered behind the row (same no-blur technique GlowPlayBadge already
 * uses) rather than a real blur, consistent with the rest of this app's
 * minSdk=23 constraint.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    trailing: @Composable () -> Unit = {
        Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(22.dp))
    }
) {
    var focused by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxWidth()) {
        if (focused) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(listOf(MangoAmber.copy(alpha = 0.35f), Color.Transparent)),
                        RowShape
                    )
            )
        }
        TvFocusSurface(
            onClick = onClick,
            shape = RowShape,
            backgroundColor = MangoSurface,
            focusRequester = focusRequester,
            borderColor = MangoAmber,
            onFocusChanged = { focused = it },
            bringIntoViewOnFocus = false,
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconContainer(icon = icon, size = 40.dp, iconSize = 20.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                trailing()
            }
        }
    }
}

/** Pill-shaped on/off switch used as a SettingsRow's trailing element. */
@Composable
fun ToggleSwitch(checked: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(44.dp)
            .height(26.dp)
            .background(if (checked) MangoAmber else MangoSurface, RoundedCornerShape(percent = 50))
            .border(BorderStroke(1.dp, DividerSubtle), RoundedCornerShape(percent = 50))
            .padding(3.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .background(Color.White, CircleShape)
        )
    }
}
