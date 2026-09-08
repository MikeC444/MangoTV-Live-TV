package com.mangotv.app.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.EpgDiagnostics
import com.mangotv.app.data.livetv.LiveTvCatalogState
import com.mangotv.app.data.livetv.TimelineBlock
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.EmptyState
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.RowsLoadingSkeleton
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoBackground

/**
 * Dispatches on catalog load state, then (once Loaded) on region selection:
 * no region chosen yet -> RegionSelectScreen; a region chosen -> the grid TV
 * guide (see TvGuideScreen), filtered to it. Loading/Error/Empty share a
 * simple, static nav-bar-plus-centered-message layout since none of them
 * scroll and there's nothing to pick a region from yet.
 */
@Composable
fun LiveTvChannelsScreen(
    catalogState: LiveTvCatalogState,
    regionSelection: RegionSelection,
    windowStart: Long,
    windowEnd: Long,
    epgVersion: Int,
    epgDiagnostics: EpgDiagnostics,
    getBlocks: (Channel) -> List<TimelineBlock>,
    onSelectRegion: (String?) -> Unit,
    onChangeRegion: () -> Unit,
    onChannelClick: (Channel) -> Unit,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit
) {
    if (catalogState !is LiveTvCatalogState.Loaded) {
        LiveTvChannelsPendingContent(catalogState = catalogState, onRetry = onRetry, onNavigate = onNavigate)
        return
    }

    when (regionSelection) {
        is RegionSelection.NotChosen -> RegionSelectScreen(
            channels = catalogState.allChannels,
            onSelectRegion = onSelectRegion,
            onNavigate = onNavigate
        )
        is RegionSelection.Chosen -> {
            val filteredChannels = remember(catalogState.allChannels, regionSelection.regionCode) {
                if (regionSelection.regionCode == null) {
                    catalogState.allChannels
                } else {
                    catalogState.allChannels.filter { it.country?.trim()?.uppercase() == regionSelection.regionCode }
                }
            }
            if (filteredChannels.isEmpty()) {
                LiveTvRegionEmptyContent(onChangeRegion = onChangeRegion, onNavigate = onNavigate)
            } else {
                TvGuideScreen(
                    channels = filteredChannels,
                    regionLabel = regionSelection.regionCode ?: "All Channels",
                    windowStart = windowStart,
                    windowEnd = windowEnd,
                    epgVersion = epgVersion,
                    epgDiagnostics = epgDiagnostics,
                    getBlocks = getBlocks,
                    onTuneToChannel = onChannelClick,
                    onChangeRegion = onChangeRegion,
                    onNavigate = onNavigate
                )
            }
        }
    }
}

/** Reached only if a chosen region's channel list comes up empty (e.g. the catalog refreshed and that region disappeared) -- lets the user pick again rather than staring at a guide with zero rows. */
@Composable
private fun LiveTvRegionEmptyContent(onChangeRegion: () -> Unit, onNavigate: (String) -> Unit) {
    val navFocusRequester = remember { FocusRequester() }
    val actionFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { navFocusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        EmptyState(
            icon = Icons.Filled.Public,
            title = "No channels in this region",
            message = "Choose a different region to keep browsing Live TV.",
            actionLabel = "Change Region",
            onAction = onChangeRegion,
            actionFocusRequester = actionFocusRequester,
            actionFocusUp = navFocusRequester
        )
        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Live TV"),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = actionFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
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
