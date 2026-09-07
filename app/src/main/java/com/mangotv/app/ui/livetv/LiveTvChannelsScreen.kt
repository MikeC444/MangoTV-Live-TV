package com.mangotv.app.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.mangotv.app.data.livetv.Channel
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
 * Dispatches on catalog load state -- once Loaded, the actual browsing
 * experience is the grid TV guide (see TvGuideScreen); Loading/Error/Empty
 * share a simple, static nav-bar-plus-centered-message layout since none of
 * them scroll.
 */
@Composable
fun LiveTvChannelsScreen(
    catalogState: LiveTvCatalogState,
    windowStart: Long,
    windowEnd: Long,
    epgVersion: Int,
    getBlocks: (Channel) -> List<TimelineBlock>,
    onChannelClick: (Channel) -> Unit,
    onRetry: () -> Unit,
    onNavigate: (String) -> Unit
) {
    when (catalogState) {
        is LiveTvCatalogState.Loaded -> TvGuideScreen(
            channels = catalogState.allChannels,
            windowStart = windowStart,
            windowEnd = windowEnd,
            epgVersion = epgVersion,
            getBlocks = getBlocks,
            onTuneToChannel = onChannelClick,
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
