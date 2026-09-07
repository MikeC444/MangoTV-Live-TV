package com.mangotv.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContentRow(
    section: HomeSection,
    onItemClick: (Content) -> Unit,
    modifier: Modifier = Modifier,
    // Only the first row needs this: its hero buttons no longer
    // auto-scroll into view on focus (see HeroSection), so returning to
    // them from here needs to be handled explicitly instead.
    onNavigateUpPastRow: (() -> Unit)? = null,
    // Used by the movie detail page to fit its whole layout on one screen
    // without scrolling — Home never passes this, so its rows are
    // completely unaffected.
    compact: Boolean = false,
    // Forwarded straight to ContentCard — see its own doc for why this is
    // independent of `compact`. Also scales the gap between cards so a
    // smaller poster grid stays tight instead of looking gappy.
    posterScale: Float = 1f,
    // Reports whether any card in this row holds focus, i.e. whether this
    // is "the" focused row — Home uses this to drive its own explicit
    // scroll-to-center-this-row effect (see HomeScreen.kt) rather than
    // relying on Compose's automatic focus-triggered bring-into-view,
    // which proved impossible to keep smooth for centering.
    onFocusChanged: (Boolean) -> Unit = {},
    // Pinned onto this row's first card so a caller with no hero (Movies,
    // TV Shows, Genre Results, Search, My List) can land the nav bar's DOWN
    // key directly on the first poster. Home/Detail don't pass this — they
    // land DOWN on a hero button instead, so it defaults to null there.
    firstItemFocusRequester: FocusRequester? = null
) {
    Column(
        modifier = modifier.onFocusChanged { onFocusChanged(it.hasFocus) }
    ) {
        Text(
            text = section.title,
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                horizontal = MangoDimens.ScreenPaddingHorizontal,
                vertical = if (compact) 6.dp else 12.dp
            )
        )
        // Fast animation for horizontal card-to-card scroll-into-view;
        // Home's own vertical row centering is driven explicitly instead
        // (see HomeScreen.kt) rather than through this composition local,
        // so this only ever affects this row's own horizontal LazyRow.
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.FastBringIntoViewSpec) {
            LazyRow(
                modifier = if (onNavigateUpPastRow != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        // Consume both KeyDown and KeyUp for this key — an
                        // unconsumed KeyUp can fall through to Compose's default
                        // focus-move handling and trigger its own scroll-into-
                        // view, independent of bringIntoViewOnFocus.
                        if (event.key == Key.DirectionUp) {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateUpPastRow()
                            }
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
                contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
                horizontalArrangement = Arrangement.spacedBy((if (compact) 12.dp else MangoDimens.CardSpacing) * posterScale)
            ) {
                itemsIndexed(section.items, key = { _, content -> content.id }) { index, content ->
                    ContentCard(
                        content = content,
                        style = section.style,
                        onClick = { onItemClick(content) },
                        compact = compact,
                        posterScale = posterScale,
                        focusRequester = if (index == 0) firstItemFocusRequester else null
                    )
                }
            }
        }
    }
}
