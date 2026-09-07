package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Shared shell for the Subtitles/Audio/Quality/Settings overlays: a dim
 * backdrop (video stays visible behind it) plus a right-anchored panel,
 * matching the spec's "elegant overlay, not a full navigation away from
 * the video" requirement. Dismissing on BACK is handled once, globally, by
 * the player's own overlay-stack BackHandler — these panels don't each
 * register their own.
 */
@Composable
fun MenuOverlayScaffold(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f)),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .background(MangoBackground.copy(alpha = 0.97f))
                .padding(vertical = 32.dp, horizontal = 24.dp)
        ) {
            Text(text = title, color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun MenuOptionRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    supportingText: String? = null
) {
    val shape = RoundedCornerShape(MangoDimens.CardCornerRadius)
    TvFocusSurface(
        onClick = onClick,
        shape = shape,
        backgroundColor = if (isSelected) Color.White.copy(alpha = 0.16f) else MangoSurfaceHigh,
        focusRequester = focusRequester,
        borderColor = Color.White,
        modifier = modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}
