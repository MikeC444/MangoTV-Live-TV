package com.mangotv.app.ui.genres

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Landscape
import androidx.compose.material.icons.filled.LocalMovies
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.TheaterComedy
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.settings.SettingsScaffold
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoAzure
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.MangoTangerine
import com.mangotv.app.ui.theme.MangoTeal
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

private const val GENRE_GRID_COLUMNS = 5

// How many full rows should be visible in the viewport at once -- card
// height is derived from the actually-measured available height divided
// by this, rather than a fixed aspect ratio, so it holds regardless of
// the real screen's dp dimensions. More rows than this just scroll.
private const val GENRE_GRID_VISIBLE_ROWS = 4

// Cycled by card position so the grid reads as a coherent set of accents
// (the same amber/tangerine/coral/azure/teal family used sparingly
// elsewhere in the app) rather than one repeated color -- there's no
// per-genre image or brand color to key off, since addons only ever
// report genre names, never artwork.
private val GenreCardAccents = listOf(MangoAmber, MangoTangerine, MangoCoral, MangoAzure, MangoTeal)

// Best-effort keyword match against whatever string an addon happens to
// report as a genre -- these aren't a fixed enum, so this only recognizes
// common names and falls back to a generic icon for anything else.
private fun iconForGenre(genre: String): ImageVector {
    val key = genre.lowercase()
    return when {
        key.toIntOrNull() != null -> Icons.Filled.CalendarToday
        key.contains("action") -> Icons.Filled.DirectionsRun
        key.contains("adventure") -> Icons.Filled.Terrain
        key.contains("anima") -> Icons.Filled.Palette
        key.contains("comedy") -> Icons.Filled.Mood
        key.contains("crime") -> Icons.Filled.Gavel
        key.contains("documentary") -> Icons.Filled.Article
        key.contains("drama") -> Icons.Filled.TheaterComedy
        key.contains("famil") -> Icons.Filled.Groups
        key.contains("fantasy") -> Icons.Filled.AutoAwesome
        key.contains("histor") -> Icons.Filled.AccountBalance
        key.contains("horror") -> Icons.Filled.NightsStay
        key.contains("music") -> Icons.Filled.MusicNote
        key.contains("myster") -> Icons.Filled.Search
        key.contains("romance") -> Icons.Filled.Favorite
        key.contains("sci") -> Icons.Filled.Science
        key.contains("thriller") -> Icons.Filled.Visibility
        key.contains("war") -> Icons.Filled.Shield
        key.contains("western") -> Icons.Filled.Landscape
        key.contains("reality") -> Icons.Filled.Videocam
        key.contains("sport") -> Icons.Filled.SportsSoccer
        else -> Icons.Filled.LocalMovies
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GenresScreen(
    onNavigate: (String) -> Unit,
    viewModel: GenresViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val firstGenreFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // See TopNavBar's kdoc on onNavigateDown: firstGenreFocusRequester below
    // is pinned to only the grid's very first card, which this LazyColumn
    // stops composing (and therefore detaches the requester from) once it's
    // scrolled out of view -- a long enough genre+year list makes that the
    // common case, not an edge case. Scrolling back to it first, then
    // focusing, guarantees the target exists before it's used.
    val genreList = (uiState as? GenresUiState.Loaded)?.genres.orEmpty()
    val genreRows = remember(genreList) { genreList.chunked(GENRE_GRID_COLUMNS) }
    val onNavigateDown: (() -> Unit)? = if (genreList.isNotEmpty()) {
        {
            coroutineScope.launch {
                val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == 0 }
                if (!alreadyVisible) {
                    listState.animateScrollToItem(0)
                }
                runCatching { firstGenreFocusRequester.requestFocus() }
            }
        }
    } else {
        null
    }

    SettingsScaffold(
        title = "Genres",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = firstGenreFocusRequester,
        titleIcon = Icons.Filled.Category,
        selectedNavLabel = "Genres",
        onNavigateDown = onNavigateDown
    ) {
        when (uiState) {
            is GenresUiState.Loading -> CircularProgressIndicator(color = MangoAmber)
            is GenresUiState.NoAddons -> Text(
                text = "Install an addon first — genres will show up here once it's added.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            is GenresUiState.Loaded -> {
                if (genreList.isEmpty()) {
                    Text(
                        text = "Your installed addons aren't reporting any genres right now.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    BoxWithConstraints(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        val cardWidth = (maxWidth - MangoDimens.CardSpacing * (GENRE_GRID_COLUMNS - 1)) / GENRE_GRID_COLUMNS
                        val cardHeight = (maxHeight - MangoDimens.CardSpacing * (GENRE_GRID_VISIBLE_ROWS - 1)) / GENRE_GRID_VISIBLE_ROWS
                        // Same fix RowsBrowseGridContent already needed for its own
                        // vertical grid: without this, Compose's automatic focus-
                        // triggered bring-into-view still reacts to a card's own
                        // scale-on-focus transform reporting a shifted rect, and
                        // nudges this whole LazyColumn on every focus move -- read
                        // as the entire page shaking while scrolling/navigating.
                        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.DisabledBringIntoViewSpec) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
                        ) {
                            itemsIndexed(genreRows, key = { index, _ -> "genre_row_$index" }) { rowIndex, rowGenres ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .let { base ->
                                            if (rowIndex == 0) {
                                                // Same UP-past-row-0 interception
                                                // RowsBrowseGridContent uses: every
                                                // other row leaves UP unhandled so it
                                                // falls through to the default focus
                                                // search and lands in the row above.
                                                base.onPreviewKeyEvent { event ->
                                                    if (event.key == Key.DirectionUp && event.type == KeyEventType.KeyDown) {
                                                        coroutineScope.launch {
                                                            listState.scrollToItem(0, 0)
                                                            runCatching { navFocusRequester.requestFocus() }
                                                        }
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                            } else {
                                                base
                                            }
                                        },
                                    horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
                                ) {
                                    rowGenres.forEachIndexed { colIndex, genre ->
                                        val flatIndex = rowIndex * GENRE_GRID_COLUMNS + colIndex
                                        GenreCard(
                                            genre = genre,
                                            accentColor = GenreCardAccents[flatIndex % GenreCardAccents.size],
                                            onClick = { onNavigate(MangoRoutes.genreResults(genre)) },
                                            modifier = Modifier
                                                .width(cardWidth)
                                                .height(cardHeight),
                                            focusRequester = if (rowIndex == 0 && colIndex == 0) firstGenreFocusRequester else null
                                        )
                                    }
                                }
                            }
                            item(key = "bottom_spacer") {
                                Spacer(Modifier.height(24.dp))
                            }
                        }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GenreCard(
    genre: String,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundBrush = Brush.linearGradient(colors = listOf(MangoSurfaceHigh, accentColor.copy(alpha = 0.32f))),
        focusRequester = focusRequester,
        focusedScale = 1.05f
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
        ) {
            Icon(
                imageVector = iconForGenre(genre),
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = genre,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
