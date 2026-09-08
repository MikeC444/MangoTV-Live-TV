package com.mangotv.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

val MangoNavItems = listOf("Home", "Movies", "TV Shows", "Live TV", "Genres", "Search", "My List", "Settings")

@OptIn(ExperimentalFoundationApi::class)
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
        // Was 0.45f -- against a bright/busy hero image behind it (the
        // common case: transparentBackground is true right when Home
        // loads, before any scrolling), that left the nav bar hard to
        // read. A bit darker keeps the see-through hero effect but gives
        // the labels enough contrast.
        targetValue = if (transparentBackground) 0.6f else 0.96f,
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
        // LazyRow rather than a plain Row: with enough nav items (this list
        // has grown since this bar was first built), the fully laid-out
        // width can exceed a real TV screen's — a plain Row still draws
        // every child at its natural size regardless, which just clips the
        // last item(s) off the edge instead of scrolling to reach them.
        // Wrapping only the item list (not the logo) means it's still drawn
        // exactly as before, at its natural (unscrolled) size, whenever it
        // already fits — this only engages once it doesn't.
        // Fast bring-into-view spec (same one every other horizontally-
        // scrolling row in the app already uses, see ContentRow.kt) so a
        // held D-pad moving across nav items doesn't outrun Compose's
        // slower default spring-based scroll and stutter.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.FastBringIntoViewSpec) {
            LazyRow(
                modifier = Modifier.weight(1f, fill = false),
                // LazyRow clips its content to its own laid-out bounds --
                // with no content padding, that boundary sat exactly at
                // the first/last item's un-scaled edge, so the focused
                // scale-up (TvFocusSurface animates to 1.08x on focus)
                // pushed Home's/Settings' border past it and got clipped.
                // A little breathing room on each end gives the scale
                // somewhere to grow into.
                contentPadding = PaddingValues(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                // Tightened from 8dp -- at the old spacing plus the old
                // (larger) label size, 8 items no longer fit one screen
                // width once Live TV was added, so this row started
                // scrolling. See NavItem's smaller labelMedium text below;
                // together these reclaim enough width that it shouldn't
                // need to anymore.
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(MangoNavItems) { index, label ->
                    NavItem(
                        label = label,
                        selected = index == selectedIndex,
                        onClick = { onItemClick(label) },
                        focusRequester = if (index == selectedIndex) selectedItemFocusRequester else null,
                        focusDown = contentFocusRequester
                    )
                }
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
        // White rather than TvFocusSurface's default amber border -- scoped
        // to just the nav bar via this explicit override, not a global
        // FocusBorder change, so every other focusable element in the app
        // (cards, buttons) keeps its usual focus color.
        borderColor = TextPrimary,
        // TvFocusSurface's default focus shadow is a blurred black
        // ambient/spot shadow -- invisible against the amber border/dark
        // cards it was designed for, but at nav-item size it sits right at
        // the white border's inner edge and reads as a faint dark ring
        // inside the border. Nav items don't need the "lift" effect anyway
        // (there's no card underneath to lift off of), so this just turns
        // it off here.
        focusedElevation = 0f,
        // Adjacent nav items are separate TvFocusSurfaces, each fading its
        // own border independently -- with the shared 150ms fade, the
        // outgoing item's fade-out and the incoming item's fade-in overlap
        // and read as the border lagging behind on the previous item.
        // Snapping it instant gives a clean, immediate handoff instead.
        borderAnimationSpec = snap(),
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        focusRequester = focusRequester,
        focusDown = focusDown
    ) {
        Text(
            text = label,
            color = if (focused || selected) TextPrimary else TextSecondary,
            // Keyed on selected only, not focused -- selected stays fixed
            // while moving focus around the bar, but focused changes on
            // every D-pad step, and Bold glyphs measure wider than Medium
            // ones. NavItem isn't a fixed-width box, so that width change
            // reflowed every item after the focused one (and the whole
            // LazyRow, which sizes to fit its content) on every step,
            // reading as the entire bar twitching. Color alone (plus the
            // border) is enough to show focus without moving anything.
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            // labelMedium (13sp) rather than the original titleMedium
            // (16sp) -- see the tightened item spacing above, both
            // together are needed to fit all 8 items (Live TV pushed
            // this over) without the row falling back to scrolling.
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}
