package com.mangotv.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.TextPrimary

/** Small circular icon button used next to a hero's pill-shaped Play button
 * (e.g. Watchlist, More Info, Mark as watched) on both Home and the detail
 * page. */
@Composable
fun HeroIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    focusLeft: FocusRequester? = null,
    focusRight: FocusRequester? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    compact: Boolean = false,
    // The video player wants bare icons with no resting fill (only the
    // TvFocusSurface focus border/scale on focus) — every other screen
    // keeps the translucent circle behind the icon as before.
    showBackground: Boolean = true,
    borderColor: Color = FocusBorder
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        backgroundColor = if (showBackground) Color.White.copy(alpha = 0.12f) else Color.Transparent,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusDown = focusDown,
        focusLeft = focusLeft,
        focusRight = focusRight,
        onFocusChanged = onFocusChanged,
        borderColor = borderColor,
        bringIntoViewOnFocus = false
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = TextPrimary,
            modifier = Modifier.padding(if (compact) 10.dp else 14.dp)
        )
    }
}
