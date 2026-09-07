package com.mangotv.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.MangoLogo
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

val MangoNavItems = listOf("Home", "Movies", "TV Shows", "Genres", "Search", "My List", "Settings")

@Composable
fun TopNavBar(
    transparentBackground: Boolean,
    modifier: Modifier = Modifier,
    selectedIndex: Int = 0,
    selectedItemFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    onItemClick: (String) -> Unit = {},
    // Imperative override for the DOWN seam into content. Prefer this over
    // relying only on contentFocusRequester/focusDown when that requester
    // targets something inside a lazily-composed list: focusProperties
    // pointing at a FocusRequester with no currently-attached node throws,
    // and a scrolled-far-enough list item can be disposed. This callback
    // lets the caller scroll first, then focus, guaranteeing the target
    // exists before it's used. contentFocusRequester still applies as a
    // harmless fallback when this isn't provided (e.g. non-scrolling
    // screens, where the declarative path is already safe).
    onNavigateDown: (() -> Unit)? = null
) {
    val scrimAlpha by animateFloatAsState(
        targetValue = if (transparentBackground) 0.45f else 0.96f,
        animationSpec = tween(300),
        label = "navBarScrimAlpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .let { base ->
                if (onNavigateDown != null) {
                    base.onPreviewKeyEvent { event ->
                        // Consume both KeyDown and KeyUp for this key so
                        // neither phase falls through to Compose's default
                        // focus-move handling, which appears to run its own
                        // scroll-into-view independent of
                        // bringIntoViewOnFocus and was causing a second,
                        // unwanted scroll after the imperative one above.
                        if (event.key == Key.DirectionDown) {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateDown()
                            }
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    base
                }
            }
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MangoBackground.copy(alpha = scrimAlpha),
                        Color.Transparent
                    )
                )
            )
            .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MangoLogo()
        Spacer(Modifier.width(56.dp))
        MangoNavItems.forEachIndexed { index, label ->
            NavItem(
                label = label,
                selected = index == selectedIndex,
                onClick = { onItemClick(label) },
                focusRequester = if (index == selectedIndex) selectedItemFocusRequester else null,
                focusDown = contentFocusRequester
            )
            if (index != MangoNavItems.lastIndex) {
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}

@Composable
private fun NavItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusDown: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        backgroundColor = Color.Transparent,
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        focusRequester = focusRequester,
        focusDown = focusDown
    ) {
        Text(
            text = label,
            color = if (focused || selected) TextPrimary else TextSecondary,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        )
    }
}
