package com.mangotv.app.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.ContentCard
import com.mangotv.app.ui.components.ContentRow
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.RowsLoadingSkeleton
import com.mangotv.app.ui.detail.PendingDetailCache
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

sealed interface RowsBrowseUiState {
    data object Loading : RowsBrowseUiState
    data class Loaded(val sections: List<HomeSection>) : RowsBrowseUiState
    data class Error(val message: String) : RowsBrowseUiState
}

// ROWS = the original horizontal-shelf layout (My List keeps this).
// GRID = a vertical, multi-column poster grid (Movies, TV Shows, Genre
// Results) -- see RowsBrowseGridContent for why this is built from
// manually-chunked Rows in the same LazyColumn rather than LazyVerticalGrid.
enum class RowsBrowseLayout { ROWS, GRID }

/**
 * Shared shell for any "stack of ContentRows under the nav bar, no hero"
 * screen — currently Movies, TV Shows, and Genre Results. Structurally
 * HomeScreen's own HomeContent minus the hero item: same nav<->content
 * focus-seam mechanism (a navRegionFocused lock instead of Home's
 * heroRegionFocused, since there's no intermediate hero region here — the
 * nav bar borders row content directly) and the same explicit
 * animateScrollBy row-centering effect, both copied deliberately rather
 * than re-derived, since this app fought several rounds of real stutter/
 * shake bugs to arrive at them on Home.
 *
 * Not wrapped in SettingsScaffold: ContentRow supplies its own
 * ScreenPaddingHorizontal via its LazyRow's contentPadding, and
 * SettingsScaffold's content slot applies that same padding again --
 * stacking both would double the left/right margin.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RowsBrowseContent(
    screenTitle: String,
    navLabel: String,
    uiState: RowsBrowseUiState,
    onNavigate: (String) -> Unit,
    onRetry: () -> Unit,
    emptyMessage: String = "Nothing to show here right now.",
    layout: RowsBrowseLayout = RowsBrowseLayout.ROWS,
    // Grid-only (see RowsBrowseGridContent) -- called as the user scrolls
    // near the bottom so Movies/TV Shows/Genre Results can page in more
    // content instead of dead-ending. Defaults to a no-op so My List (ROWS
    // layout) is unaffected.
    onLoadMore: () -> Unit = {}
) {
    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        when (uiState) {
            // Loaded content owns its own TopNavBar (see
            // RowsBrowseLoadedContent/RowsBrowseGridContent below) because
            // it needs to wire the nav<->content focus seam and
            // scroll-lock into it. Loading/Error have no row content to
            // seam into, so a minimal standalone bar covers them -- these
            // used to render with no nav bar at all, which made it
            // disappear entirely for as long as the fetch was in flight
            // (the common case navigating to a tab on cold boot, before
            // its own data has loaded).
            is RowsBrowseUiState.Loading -> RowsBrowseTransientState(navLabel, onNavigate) {
                RowsLoadingSkeleton()
            }
            is RowsBrowseUiState.Error -> RowsBrowseTransientState(navLabel, onNavigate) {
                FullScreenErrorState(message = uiState.message, onRetry = onRetry)
            }
            is RowsBrowseUiState.Loaded -> if (layout == RowsBrowseLayout.GRID) {
                RowsBrowseGridContent(screenTitle, navLabel, uiState.sections.flatMap { it.items }, onNavigate, emptyMessage, onLoadMore)
            } else {
                RowsBrowseLoadedContent(screenTitle, navLabel, uiState.sections, onNavigate, emptyMessage)
            }
        }
    }
}

@Composable
private fun RowsBrowseTransientState(
    navLabel: String,
    onNavigate: (String) -> Unit,
    content: @Composable () -> Unit
) {
    val navFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { navFocusRequester.requestFocus() }
    }
    Box(Modifier.fillMaxSize()) {
        content()
        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf(navLabel),
            selectedItemFocusRequester = navFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowsBrowseLoadedContent(
    screenTitle: String,
    navLabel: String,
    sections: List<HomeSection>,
    onNavigate: (String) -> Unit,
    emptyMessage: String
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val navFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Mirrors HomeContent's heroRegionFocused, minus the intermediate hero
    // region this screen doesn't have: true while focus is in the nav bar
    // (list stays pinned at the top), false once focus has moved into row
    // content (normal centering/scrolling takes over).
    var navRegionFocused by remember { mutableStateOf(true) }
    var focusedRowIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(sections) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    val navScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (navRegionFocused) available else Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (navRegionFocused && (index != 0 || offset != 0)) {
                    listState.scrollToItem(0, 0)
                }
            }
    }

    LaunchedEffect(focusedRowIndex, navRegionFocused) {
        val rowIndex = focusedRowIndex ?: return@LaunchedEffect
        if (navRegionFocused) return@LaunchedEffect
        val lazyIndex = rowIndex + 1 // offset for the title item at index 0
        val info = listState.layoutInfo.visibleItemsInfo.find { it.index == lazyIndex }
        if (info != null) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemCenter = info.offset + info.size / 2f
            val delta = itemCenter - viewportHeight / 2f
            listState.animateScrollBy(delta)
        } else {
            listState.animateScrollToItem(lazyIndex)
        }
    }

    fun navigateToContent(target: Content) {
        val providerId = target.providerId ?: return
        PendingDetailCache.stash(target)
        onNavigate(MangoRoutes.detail(providerId, target.type, target.id))
    }

    Box(Modifier.fillMaxSize()) {
        if (sections.isEmpty()) {
            Text(
                text = emptyMessage,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
            )
        } else {
            CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.DisabledBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .nestedScroll(navScrollLock)
                        .fillMaxSize()
                        .padding(top = MangoDimens.NavBarHeight + 24.dp)
                ) {
                    item(key = "title") {
                        Text(
                            text = screenTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.padding(
                                horizontal = MangoDimens.ScreenPaddingHorizontal,
                                vertical = 4.dp
                            )
                        )
                    }
                    itemsIndexed(sections, key = { _, section -> section.id }) { index, section ->
                        ContentRow(
                            section = section,
                            onItemClick = ::navigateToContent,
                            modifier = Modifier.padding(bottom = MangoDimens.RowSpacing),
                            posterScale = 0.75f,
                            onFocusChanged = { hasFocus -> if (hasFocus) focusedRowIndex = index },
                            firstItemFocusRequester = if (index == 0) firstCardFocusRequester else null,
                            onNavigateUpPastRow = if (index == 0) {
                                {
                                    navRegionFocused = true
                                    coroutineScope.launch {
                                        listState.scrollToItem(0, 0)
                                        runCatching { navFocusRequester.requestFocus() }
                                    }
                                }
                            } else {
                                null
                            }
                        )
                    }
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf(navLabel),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = if (sections.isNotEmpty()) firstCardFocusRequester else null,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = if (sections.isNotEmpty()) {
                {
                    navRegionFocused = false
                    coroutineScope.launch {
                        listState.scrollToItem(0, 0)
                        runCatching { firstCardFocusRequester.requestFocus() }
                    }
                }
            } else {
                null
            }
        )
    }
}

// Fixed chunk size for items.chunked(GRID_COLUMNS) below -- the actual
// on-screen poster size (posterScale) is computed at runtime from measured
// layout constraints (see RowsBrowseGridContent) so this many columns
// reliably fit regardless of the device's actual dp width, rather than
// assuming a fixed screen size.
private const val GRID_COLUMNS = 7

/**
 * Vertical, multi-column poster grid -- Movies, TV Shows, and Genre Results
 * only (My List keeps RowsBrowseLoadedContent's horizontal rows). Deliberately
 * NOT LazyVerticalGrid: ContentCard sizes itself with a fixed absolute dp
 * width/height rather than filling its cell, which doesn't map cleanly onto
 * GridCells' auto-column-sizing, and this codebase has already fought real
 * "whole page shaking" stutter bugs from Compose's automatic focus-triggered
 * bring-into-view interacting with TvFocusSurface's focus-scale animation
 * (see RowsBrowseLoadedContent's doc comment and HomeScreen.kt/Motion.kt).
 * Chunking the flat item list into fixed-size rows and reusing the exact
 * same LazyColumn + explicit animateScrollBy centering machinery already
 * proven on this screen sidesteps introducing a new, untested API surface
 * into that exact scroll-on-focus scenario.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowsBrowseGridContent(
    screenTitle: String,
    navLabel: String,
    items: List<Content>,
    onNavigate: (String) -> Unit,
    emptyMessage: String,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val navFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    var navRegionFocused by remember { mutableStateOf(true) }
    var focusedGridRowIndex by remember { mutableStateOf<Int?>(null) }

    val rows = remember(items) { items.chunked(GRID_COLUMNS) }

    LaunchedEffect(items) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    val navScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (navRegionFocused) available else Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (navRegionFocused && (index != 0 || offset != 0)) {
                    listState.scrollToItem(0, 0)
                }
            }
    }

    // Infinite scroll: fires (repeatedly, harmlessly -- the ViewModel side
    // guards against duplicate/overlapping fetches) whenever one of the
    // last couple of grid rows is visible, so more content is already
    // loading in before the user actually hits the bottom.
    LaunchedEffect(listState, rows.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastVisibleIndex ->
                if (lastVisibleIndex != null && rows.isNotEmpty() && lastVisibleIndex >= rows.size - 1) {
                    onLoadMore()
                }
            }
    }

    LaunchedEffect(focusedGridRowIndex, navRegionFocused) {
        val rowIndex = focusedGridRowIndex ?: return@LaunchedEffect
        if (navRegionFocused) return@LaunchedEffect
        val lazyIndex = rowIndex + 1 // offset for the title item at index 0
        val info = listState.layoutInfo.visibleItemsInfo.find { it.index == lazyIndex }
        if (info != null) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemCenter = info.offset + info.size / 2f
            val delta = itemCenter - viewportHeight / 2f
            listState.animateScrollBy(delta)
        } else {
            listState.animateScrollToItem(lazyIndex)
        }
    }

    fun navigateToContent(target: Content) {
        val providerId = target.providerId ?: return
        PendingDetailCache.stash(target)
        onNavigate(MangoRoutes.detail(providerId, target.type, target.id))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Driven by width only: the scale that makes exactly GRID_COLUMNS
        // columns fill the available width edge-to-edge within the existing
        // screen margins/card spacing. An earlier version also computed a
        // height-driven scale (targeting a fixed number of visible rows)
        // and took the smaller of the two -- in practice that estimate
        // (built from approximate text/offset heights, not an actual
        // measurement) came out far more conservative than the real
        // available height, which left a large blank gap on the right
        // instead of filling the screen. Width alone reliably fills the
        // screen every time; the LazyColumn already scrolls, so however
        // many rows this scale happens to show without scrolling is fine.
        val availableWidth = maxWidth - MangoDimens.ScreenPaddingHorizontal * 2
        val cardWidth = (availableWidth - MangoDimens.CardSpacing * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val posterScale = (cardWidth / MangoDimens.PosterWidth).coerceIn(0.3f, 1f)

        if (rows.isEmpty()) {
            Text(
                text = emptyMessage,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
            )
        } else {
            CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.DisabledBringIntoViewSpec) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .nestedScroll(navScrollLock)
                        .fillMaxSize()
                        .padding(top = MangoDimens.NavBarHeight + 24.dp)
                ) {
                    item(key = "title") {
                        Text(
                            text = screenTitle,
                            color = TextPrimary,
                            style = MaterialTheme.typography.displayMedium,
                            modifier = Modifier.padding(
                                horizontal = MangoDimens.ScreenPaddingHorizontal,
                                vertical = 4.dp
                            )
                        )
                    }
                    itemsIndexed(rows, key = { index, _ -> "grid_row_$index" }) { rowIndex, rowItems ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = MangoDimens.RowSpacing / 2)
                                .onFocusChanged { state -> if (state.hasFocus) focusedGridRowIndex = rowIndex }
                                .let { base ->
                                    if (rowIndex == 0) {
                                        base.onPreviewKeyEvent { event ->
                                            // Same UP-past-row interception ContentRow uses,
                                            // scoped to only the first grid row -- every other
                                            // row leaves UP unhandled so it falls through to
                                            // Compose's default focus search and lands in the
                                            // row above, same as Home's multi-row precedent.
                                            if (event.key == Key.DirectionUp) {
                                                if (event.type == KeyEventType.KeyDown) {
                                                    navRegionFocused = true
                                                    coroutineScope.launch {
                                                        listState.scrollToItem(0, 0)
                                                        runCatching { navFocusRequester.requestFocus() }
                                                    }
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
                            rowItems.forEachIndexed { colIndex, content ->
                                ContentCard(
                                    content = content,
                                    onClick = { navigateToContent(content) },
                                    focusRequester = if (rowIndex == 0 && colIndex == 0) firstCardFocusRequester else null,
                                    posterScale = posterScale
                                )
                            }
                        }
                    }
                    item(key = "bottom_spacer") {
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
        }

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf(navLabel),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = if (rows.isNotEmpty()) firstCardFocusRequester else null,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = if (rows.isNotEmpty()) {
                {
                    navRegionFocused = false
                    coroutineScope.launch {
                        listState.scrollToItem(0, 0)
                        runCatching { firstCardFocusRequester.requestFocus() }
                    }
                }
            } else {
                null
            }
        )
    }
}

@Composable
fun MoviesScreen(onNavigate: (String) -> Unit, viewModel: MoviesViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RowsBrowseContent(
        screenTitle = "Movies",
        navLabel = "Movies",
        uiState = uiState,
        onNavigate = onNavigate,
        onRetry = viewModel::load,
        layout = RowsBrowseLayout.GRID,
        onLoadMore = viewModel::loadMore
    )
}

@Composable
fun TvShowsScreen(onNavigate: (String) -> Unit, viewModel: TvShowsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    RowsBrowseContent(
        screenTitle = "TV Shows",
        navLabel = "TV Shows",
        uiState = uiState,
        onNavigate = onNavigate,
        onRetry = viewModel::load,
        layout = RowsBrowseLayout.GRID,
        onLoadMore = viewModel::loadMore
    )
}
