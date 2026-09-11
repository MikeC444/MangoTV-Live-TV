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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import com.mangotv.app.ui.components.GridLoadingSkeleton
import com.mangotv.app.ui.components.RowsLoadingSkeleton
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.detail.PendingDetailCache
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.MangoSurfaceHigh
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
                if (layout == RowsBrowseLayout.GRID) {
                    GridLoadingSkeleton(screenTitle = screenTitle)
                } else {
                    RowsLoadingSkeleton()
                }
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
    // The first row's own horizontal LazyRow state -- passed into ContentRow
    // below (index == 0) so the nav bar's DOWN handler can bring whichever
    // item lastFocusedItemIndex points at into view before jumping focus to
    // firstCardFocusRequester. Without this, a scrolled-away target card
    // isn't composed (scrolled out of the LazyRow's window), requestFocus()
    // throws, gets swallowed by runCatching, and focus is stuck on the nav
    // bar with no way back into the list.
    val firstRowListState = rememberLazyListState()
    // Which card the nav bar's DOWN key returns focus to -- starts at the
    // first title (0), same as before this existed, but updates to whichever
    // card the user actually last hovered (see ContentRow's
    // onItemFocusChanged below) so leaving for the nav bar and coming back
    // re-lands on that exact title instead of always snapping back to the
    // first one.
    var lastFocusedItemIndex by remember { mutableStateOf(0) }
    // Clamped against the first row's CURRENT item count -- if the
    // remembered title was removed from the list while the user was away
    // (e.g. un-saved from Detail), the raw index could point past the end,
    // and neither ContentRow (no item would match it, so
    // firstItemFocusRequester never attaches to anything) nor
    // firstRowListState.scrollToItem below handle an out-of-range index
    // gracefully -- both would leave focus stuck on the nav bar again,
    // exactly the bug this whole mechanism exists to avoid.
    val firstRowLastValidIndex = (sections.firstOrNull()?.items?.size ?: 0) - 1
    val clampedFocusedItemIndex = lastFocusedItemIndex.coerceIn(0, firstRowLastValidIndex.coerceAtLeast(0))
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
                            targetItemIndex = if (index == 0) clampedFocusedItemIndex else 0,
                            onItemFocusChanged = if (index == 0) {
                                { itemIndex -> lastFocusedItemIndex = itemIndex }
                            } else {
                                {}
                            },
                            listState = if (index == 0) firstRowListState else rememberLazyListState(),
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
                        // Only move the row's own horizontal scroll if the
                        // remembered card isn't already on screen -- its
                        // position was never touched while the user was
                        // away, so it usually already is. Calling
                        // scrollToItem unconditionally snaps the target to
                        // the very start of the viewport even when it
                        // didn't need to move at all, which read as the
                        // row jarringly jumping on every single return.
                        val alreadyVisible = firstRowListState.layoutInfo.visibleItemsInfo
                            .any { it.index == clampedFocusedItemIndex }
                        if (!alreadyVisible) {
                            firstRowListState.animateScrollToItem(clampedFocusedItemIndex)
                        }
                        runCatching { firstCardFocusRequester.requestFocus() }
                    }
                }
            } else {
                null
            }
        )
    }
}

enum class RatingFilter(val label: String, val minRating: Double?) {
    ALL("All Ratings", null),
    SEVEN_PLUS("7.0+", 7.0),
    EIGHT_PLUS("8.0+", 8.0),
    NINE_PLUS("9.0+", 9.0)
}

/**
 * A row of rating-tier pills above Movies/TV Shows/Genre Results' grid --
 * same pill look SourceFilterBar already established for the Sources
 * screen's filters. Every pill wires the same focusUp/focusDown
 * (RowsBrowseGridContent's nav bar and remembered-card requesters) rather
 * than just the first, so the seam works no matter which pill happens to
 * be focused when the user presses UP/DOWN.
 */
@Composable
private fun RatingFilterBar(
    selected: RatingFilter,
    onSelect: (RatingFilter) -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(RatingFilter.entries, key = { it.name }) { filter ->
            RatingFilterPill(
                label = filter.label,
                selected = filter == selected,
                onClick = { onSelect(filter) },
                focusRequester = if (filter == RatingFilter.ALL) focusRequester else null,
                focusUp = focusUp,
                focusDown = focusDown
            )
        }
    }
}

@Composable
private fun RatingFilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = when {
        selected -> MangoBackground
        focused -> TextPrimary
        else -> TextSecondary
    }
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(percent = 50),
        backgroundColor = if (selected) MangoAmber else MangoSurfaceHigh,
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusDown = focusDown
    ) {
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

// Fixed chunk size for items.chunked(GRID_COLUMNS) below -- the actual
// on-screen poster size (posterScale) is computed at runtime from measured
// layout constraints (see RowsBrowseGridContent) so this many columns
// reliably fit regardless of the device's actual dp width, rather than
// assuming a fixed screen size. Not private -- GridLoadingSkeleton reuses
// it so the loading skeleton's column count matches the real grid exactly.
const val GRID_COLUMNS = 7

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
    val filterBarFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Plain remember, not rememberSaveable -- same choice SourcesContent
    // makes for its own filter/sort state, and for the same reason: a
    // filter is a transient viewing preference for this visit, not
    // something worth restoring after process death.
    var selectedRatingFilter by remember { mutableStateOf(RatingFilter.ALL) }

    // Which title the grid returns D-pad focus to -- both for the nav bar's
    // DOWN key and, more importantly, for returning from Detail. By default
    // Navigation-Compose tears down this composable's plain `remember` state
    // (hasRequestedInitialFocus, navRegionFocused, the FocusRequesters, ...)
    // every time this screen is navigated away from (e.g. clicking a poster
    // opens Detail) and re-entered, since only the current back-stack
    // entry's composable actually stays part of the composition -- so
    // without this, focus (and the "list is pinned to top" nav-region lock)
    // reset to their initial defaults on every single return, which is
    // exactly the "back always lands at the top of the list" bug this
    // fixes. rememberSaveable survives that round trip (Navigation-Compose
    // keeps a SaveableStateHolder per back-stack entry, the same mechanism
    // that lets a scrolled LazyListState restore its own position), so
    // persisting the focused title's id -- not its index, which shifts as
    // loadMore appends pages -- is what lets the grid re-focus the exact
    // same poster instead of resetting to the nav bar/top of the list.
    var lastFocusedContentId by rememberSaveable { mutableStateOf<String?>(null) }

    // Re-filtered whenever the underlying catalogue changes (mount, a
    // loadMore page landing) or the user picks a different rating tier.
    val filteredItems = remember(items, selectedRatingFilter) {
        val minRating = selectedRatingFilter.minRating
        if (minRating == null) items else items.filter { (it.rating ?: 0.0) >= minRating }
    }

    val rows = remember(filteredItems) { filteredItems.chunked(GRID_COLUMNS) }
    // Computed only when the (filtered) item list itself changes (mount, a
    // loadMore page landing, or a filter change) -- this used to be
    // remember(items, lastFocusedContentId), re-running this
    // items.indexOfFirst scan on every single focus change. Holding the
    // D-pad moves focus (and re-sets lastFocusedContentId) roughly every
    // ~100ms via key-repeat, so on a long, paged-in catalogue this
    // O(items) scan repeating that often stalled the whole screen for as
    // long as the button was held, then caught up in one jump once it was
    // released. targetRowIndex/targetColIndex below now track focus live
    // in O(1) instead (see ContentCard's onFocusChanged) -- this id-based
    // scan only has to run once, to recover where a remembered id
    // (restored via rememberSaveable after a Detail round trip) now lives
    // in the possibly-different item list.
    val restoredFlatIndex = remember(filteredItems) {
        lastFocusedContentId?.let { id -> filteredItems.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
    }
    var targetRowIndex by remember { mutableStateOf(restoredFlatIndex?.let { it / GRID_COLUMNS } ?: 0) }
    var targetColIndex by remember { mutableStateOf(restoredFlatIndex?.let { it % GRID_COLUMNS } ?: 0) }

    // Nav-region starts UNLOCKED (skips the top-pinning watchdog below) when
    // there's a remembered target to restore straight into -- otherwise it
    // would immediately fight the restore in the LaunchedEffect below and
    // snap the list back to the top before the user ever sees it land on
    // the right card.
    var navRegionFocused by remember { mutableStateOf(lastFocusedContentId == null) }
    var focusedGridRowIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(items) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            if (restoredFlatIndex != null) {
                val lazyIndex = targetRowIndex + 2 // offset for the title (0) and filter bar (1) items
                val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex }
                if (!alreadyVisible) {
                    listState.scrollToItem(lazyIndex)
                }
                runCatching { firstCardFocusRequester.requestFocus() }
            } else {
                runCatching { navFocusRequester.requestFocus() }
            }
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
        val lazyIndex = rowIndex + 2 // offset for the title (0) and filter bar (1) items
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

        if (items.isEmpty()) {
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
                    // Always shown once there's a catalogue to filter, even
                    // if the current pick filters it down to zero results --
                    // otherwise picking a filter with no matches would strand
                    // the user with no way to get back to a less restrictive
                    // one without leaving the screen.
                    item(key = "rating_filter") {
                        RatingFilterBar(
                            selected = selectedRatingFilter,
                            onSelect = { selectedRatingFilter = it },
                            modifier = Modifier.padding(bottom = MangoDimens.RowSpacing / 2),
                            focusRequester = filterBarFocusRequester,
                            focusUp = navFocusRequester,
                            focusDown = firstCardFocusRequester
                        )
                    }
                    if (rows.isEmpty()) {
                        item(key = "filtered_empty") {
                            Text(
                                text = "No titles match this filter.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(
                                    horizontal = MangoDimens.ScreenPaddingHorizontal,
                                    vertical = 24.dp
                                )
                            )
                        }
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
                                    focusRequester = if (rowIndex == targetRowIndex && colIndex == targetColIndex) {
                                        firstCardFocusRequester
                                    } else {
                                        null
                                    },
                                    posterScale = posterScale,
                                    onFocusChanged = { isFocused ->
                                        if (isFocused) {
                                            lastFocusedContentId = content.id
                                            targetRowIndex = rowIndex
                                            targetColIndex = colIndex
                                        }
                                    }
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
            // Lands on the filter bar, not directly on a card -- it's the
            // first focusable thing below the nav bar now. RatingFilterBar's
            // own focusDown wiring carries a second DOWN press on through to
            // whichever card lastFocusedContentId points at.
            contentFocusRequester = if (items.isNotEmpty()) filterBarFocusRequester else null,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = if (items.isNotEmpty()) {
                {
                    navRegionFocused = false
                    coroutineScope.launch {
                        // Scrolls the remembered card into view now (same
                        // reasoning as the LaunchedEffect above, and only if
                        // it isn't already on screen) so it's already
                        // visible by the time DOWN from the filter bar
                        // reaches it -- current filter may have none at all.
                        if (rows.isNotEmpty()) {
                            val lazyIndex = targetRowIndex + 2
                            val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == lazyIndex }
                            if (!alreadyVisible) {
                                listState.animateScrollToItem(lazyIndex)
                            }
                        } else {
                            listState.animateScrollToItem(0)
                        }
                        runCatching { filterBarFocusRequester.requestFocus() }
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
