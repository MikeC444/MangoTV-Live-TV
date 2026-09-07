package com.mangotv.app.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.Content
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.ContentRow
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.HomeEmptyState
import com.mangotv.app.ui.components.HomeLoadingSkeleton
import com.mangotv.app.ui.detail.PendingDetailCache
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        when (val state = uiState) {
            is HomeUiState.Loading -> HomeLoadingSkeleton()
            is HomeUiState.Error -> FullScreenErrorState(
                message = state.message,
                onRetry = viewModel::load
            )
            is HomeUiState.Empty -> HomeEmptyScreen(onNavigate = onNavigate)
            is HomeUiState.Success -> {
                val savedIds by viewModel.savedIds.collectAsStateWithLifecycle()
                HomeContent(
                    state = state,
                    onNavigate = onNavigate,
                    savedIds = savedIds,
                    onToggleMyList = viewModel::toggleMyList
                )
            }
        }
    }
}

@Composable
private fun HomeEmptyScreen(onNavigate: (String) -> Unit) {
    val navFocusRequester = remember { FocusRequester() }
    val buttonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        runCatching { buttonFocusRequester.requestFocus() }
    }

    Column(Modifier.fillMaxSize()) {
        TopNavBar(
            transparentBackground = false,
            selectedIndex = 0,
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = buttonFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
        HomeEmptyState(
            onBrowseAddons = { onNavigate(MangoRoutes.SETTINGS_ADDONS) },
            modifier = Modifier.weight(1f),
            buttonFocusRequester = buttonFocusRequester,
            buttonFocusUp = navFocusRequester
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeContent(
    state: HomeUiState.Success,
    onNavigate: (String) -> Unit,
    savedIds: Set<String>,
    onToggleMyList: (Content) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }
    val homeNavFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Whether focus is currently somewhere in the nav bar / hero region,
    // where the user asked the screen to stay completely static. True by
    // default since initial focus lands on the nav bar. Flipped false only
    // when focus intentionally leaves the hero downward into the first
    // content row (see onNavigateDownFromHero below); flipped back true by
    // every explicit path that returns focus to the nav bar or hero.
    var heroRegionFocused by remember { mutableStateOf(true) }

    // Which row (index into state.sections, not the LazyColumn's own item
    // index) currently holds focus -- drives the explicit centering effect
    // below. null while focus is in the hero/nav region.
    var focusedRowIndex by remember { mutableStateOf<Int?>(null) }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60
        }
    }

    LaunchedEffect(state) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            // Land on the "Home" nav tab by default, not the Play button —
            // homeNavFocusRequester targets the nav bar overlay, which is
            // always composed (unlike anything inside the LazyColumn below),
            // so this is safe even before the list has laid out.
            runCatching { homeNavFocusRequester.requestFocus() }
        }
    }

    // Primary defense: swallow any incremental scroll delta the list tries
    // to apply on its own while heroRegionFocused is true — e.g. LazyColumn's
    // built-in "bring the newly focused child into view" behavior firing
    // when focus moves onto a hero button, which isn't gated by
    // TvFocusSurface's bringIntoViewOnFocus flag (that only controls this
    // app's own extra, separate BringIntoViewRequester call). That automatic
    // relocation scrolls incrementally (frame by frame), which dispatches
    // through the standard nested-scroll delta path, so intercepting it here
    // stops the bad frame from ever being drawn. Our own explicit
    // scrollToItem(0, 0) calls jump the list directly to a target index/
    // offset rather than applying a delta, so they're unaffected by this.
    val heroScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (heroRegionFocused) available else Offset.Zero
            }
        }
    }

    // Secondary safety net, in case anything still slips past the
    // interception above: watches the list's actual scroll position and
    // snaps it back to (0, 0) any time it drifts while heroRegionFocused is
    // true. Once heroRegionFocused flips false (focus has moved into the
    // content rows), this stops correcting and normal scrolling proceeds
    // freely.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (heroRegionFocused && (index != 0 || offset != 0)) {
                    listState.scrollToItem(0, 0)
                }
            }
    }

    // Explicitly centers whichever row just gained focus, replacing an
    // earlier attempt that relied on Compose's automatic focus-triggered
    // bring-into-view (a custom BringIntoViewSpec recomputed every
    // animation frame) -- that mechanism stuttered no matter how its
    // animation curve was tuned, even on a single isolated press, so the
    // fix is to stop routing through it entirely rather than keep tuning
    // it. LaunchedEffect's own "cancel the previous coroutine when the
    // key changes" behavior still coalesces rapid repeats, same as the
    // watchdog above -- what's different is that the mechanism being
    // restarted is now a single ordinary animateScrollBy, not that
    // per-frame recompute loop.
    LaunchedEffect(focusedRowIndex, heroRegionFocused) {
        val rowIndex = focusedRowIndex ?: return@LaunchedEffect
        if (heroRegionFocused) return@LaunchedEffect
        val lazyIndex = rowIndex + 1 // offset for the "hero" item at index 0
        val info = listState.layoutInfo.visibleItemsInfo.find { it.index == lazyIndex }
        if (info != null) {
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemCenter = info.offset + info.size / 2f
            val delta = itemCenter - viewportHeight / 2f
            listState.animateScrollBy(delta)
        } else {
            // Not yet visible at all (a big jump) -- get it roughly on
            // screen first; the settled position won't be perfectly
            // centered but is correct and visible, which matters more.
            listState.animateScrollToItem(lazyIndex)
        }
    }

    fun navigateToContent(target: Content) {
        val providerId = target.providerId ?: return
        PendingDetailCache.stash(target)
        onNavigate(MangoRoutes.detail(providerId, target.type, target.id))
    }

    Box(modifier = modifier.fillMaxSize()) {
        // The outer list is scrolled exclusively by the explicit
        // centering LaunchedEffect above now -- disabling Compose's own
        // automatic bring-into-view here stops it from also reacting to a
        // card's scale-on-focus rect shifting slightly during purely
        // horizontal moves within a row (see DisabledBringIntoViewSpec's
        // own doc for why that read as the whole page shaking).
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.DisabledBringIntoViewSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .nestedScroll(heroScrollLock)
                    .fillMaxSize()
            ) {
                item(key = "hero") {
                    HeroSection(
                        items = state.heroItems,
                        playFocusRequester = playFocusRequester,
                        onPlay = { content ->
                            content.providerId?.let { pid ->
                                onNavigate(MangoRoutes.sources(pid, content.type, content.id))
                            }
                        },
                        savedIds = savedIds,
                        onAddToList = onToggleMyList,
                        onMoreInfo = ::navigateToContent,
                        navUpFocusRequester = homeNavFocusRequester,
                        onNavigateUpPastHero = {
                            // Imperative, not focusProperties-driven: pressing UP
                            // from any hero button forces the list back to true
                            // top and moves focus straight to the (always
                            // composed, never-virtualized) nav bar, rather than
                            // routing through a FocusRequester on a lazily
                            // composed list item — see HeroSection for why that
                            // approach crashed.
                            heroRegionFocused = true
                            coroutineScope.launch {
                                listState.scrollToItem(0, 0)
                                runCatching { homeNavFocusRequester.requestFocus() }
                            }
                        },
                        onNavigateDownFromHero = {
                            // Leaving the hero/nav region: let the watchdog
                            // above stop pinning the list, since scrolling into
                            // the first content row from here is desired.
                            heroRegionFocused = false
                        }
                    )
                }
                itemsIndexed(state.sections, key = { _, section -> section.id }) { index, section ->
                    ContentRow(
                        section = section,
                        onItemClick = ::navigateToContent,
                        modifier = Modifier.padding(bottom = MangoDimens.RowSpacing),
                        posterScale = 0.75f,
                        onFocusChanged = { hasFocus -> if (hasFocus) focusedRowIndex = index },
                        onNavigateUpPastRow = if (index == 0) {
                            {
                                // Hero buttons no longer auto-scroll into view
                                // (see HeroSection) — returning to them from the
                                // first content row needs to be explicit too.
                                heroRegionFocused = true
                                coroutineScope.launch {
                                    listState.scrollToItem(0, 0)
                                    runCatching { playFocusRequester.requestFocus() }
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

        TopNavBar(
            transparentBackground = !isScrolled,
            modifier = Modifier
                .align(Alignment.TopCenter),
            selectedIndex = 0,
            selectedItemFocusRequester = homeNavFocusRequester,
            contentFocusRequester = playFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = {
                // Scroll first, then focus — guarantees the Play button's
                // hero item is actually composed before we try to focus it.
                heroRegionFocused = true
                coroutineScope.launch {
                    listState.scrollToItem(0, 0)
                    runCatching { playFocusRequester.requestFocus() }
                }
            }
        )
    }
}
