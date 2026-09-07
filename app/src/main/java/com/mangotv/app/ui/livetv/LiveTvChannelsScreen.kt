package com.mangotv.app.ui.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangotv.app.config.LiveTvConfig
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.ChannelSection
import com.mangotv.app.data.livetv.LiveTvCatalogState
import com.mangotv.app.data.livetv.NowNext
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.EmptyState
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.RowsLoadingSkeleton
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * The unlocked Live TV browsing experience: Featured, then country and
 * category rails built from the parsed playlist (see LiveTvRepository).
 * Structurally identical to RowsBrowseLoadedContent (Movies/TV Shows) --
 * same nav<->content focus-seam mechanism and explicit row-centering
 * effect, copied deliberately rather than re-derived, since this codebase
 * already fought real stutter/focus-trap bugs to arrive at that pattern.
 */
@Composable
fun LiveTvChannelsScreen(
    catalogState: LiveTvCatalogState,
    getNowNext: (Channel) -> NowNext?,
    onChannelClick: (Channel) -> Unit,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when (catalogState) {
        is LiveTvCatalogState.Loaded -> LiveTvChannelsLoadedContent(
            sections = catalogState.sections,
            getNowNext = getNowNext,
            onChannelClick = onChannelClick,
            onNavigate = onNavigate
        )
        else -> LiveTvChannelsPendingContent(catalogState = catalogState, onRetry = onRetry, onNavigate = onNavigate)
    }
}

@Composable
private fun LiveTvChannelsPendingContent(
    catalogState: LiveTvCatalogState,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val navFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { navFocusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        when (catalogState) {
            is LiveTvCatalogState.Loading -> RowsLoadingSkeleton()
            is LiveTvCatalogState.Error -> FullScreenErrorState(message = catalogState.message, onRetry = onRetry)
            is LiveTvCatalogState.Empty -> EmptyState(
                icon = Icons.Filled.LiveTv,
                title = "No channels available",
                message = "There are no live channels to show right now.",
                actionLabel = "Retry",
                onAction = onRetry
            )
            is LiveTvCatalogState.Loaded -> Unit
        }
        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Live TV"),
            selectedItemFocusRequester = navFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LiveTvChannelsLoadedContent(
    sections: List<ChannelSection>,
    getNowNext: (Channel) -> NowNext?,
    onChannelClick: (Channel) -> Unit,
    onNavigate: (String) -> Unit
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val navFocusRequester = remember { FocusRequester() }
    val firstCardFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

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
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (navRegionFocused) available else Offset.Zero
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

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.DisabledBringIntoViewSpec) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .nestedScroll(navScrollLock)
                    .fillMaxSize()
                    .padding(top = MangoDimens.NavBarHeight + 24.dp)
            ) {
                item(key = "title") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = MangoDimens.ScreenPaddingHorizontal,
                            vertical = 4.dp
                        )
                    ) {
                        Text(
                            text = "Live TV",
                            color = TextPrimary,
                            style = MaterialTheme.typography.displayMedium
                        )
                        // Visible on purpose: LiveTvConfig.skipPaywallForTesting
                        // defaults to true so channel browsing/playback can be
                        // tested before a real payment backend exists — this
                        // is here so that's never silently forgotten before
                        // the paywall is meant to go live. Remove once
                        // LIVE_TV_SKIP_PAYWALL is set to false for real.
                        if (LiveTvConfig.skipPaywallForTesting) {
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = "TESTING — PAYWALL BYPASSED",
                                color = MangoBackground,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier
                                    .background(MangoAmber, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                itemsIndexed(sections, key = { _, section -> section.id }) { index, section ->
                    ChannelRow(
                        section = section,
                        getNowNext = getNowNext,
                        onItemClick = onChannelClick,
                        modifier = Modifier.padding(bottom = MangoDimens.RowSpacing),
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

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Live TV"),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = firstCardFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = {
                navRegionFocused = false
                coroutineScope.launch {
                    listState.scrollToItem(0, 0)
                    runCatching { firstCardFocusRequester.requestFocus() }
                }
            }
        )
    }
}
