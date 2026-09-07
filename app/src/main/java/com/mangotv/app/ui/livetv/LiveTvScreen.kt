package com.mangotv.app.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground

/**
 * Entry point for the Live TV tab. Re-checks entitlement every time this
 * screen is (re)entered (see LiveTvViewModel.onScreenEntered/onScreenLeft,
 * wired to this composable's lifetime via DisposableEffect) rather than
 * trusting whatever the previous visit last saw — that's what satisfies
 * "every time Live TV is opened: check the current entitlement" instead of
 * only checking once per process.
 */
@Composable
fun LiveTvScreen(
    onNavigate: (String) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    viewModel: LiveTvViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        viewModel.onScreenEntered()
        onDispose { viewModel.onScreenLeft() }
    }

    when (val state = uiState) {
        is LiveTvUiState.Unlocked -> LiveTvChannelsScreen(
            catalogState = state.catalog,
            getNowNext = viewModel::nowAndNext,
            onChannelClick = onPlayChannel,
            onRetry = viewModel::retryCatalog,
            onNavigate = onNavigate
        )
        else -> LiveTvGateScreen(state = state, onRetry = viewModel::retryEntitlement, onNavigate = onNavigate)
    }
}

/** CheckingAccess/Locked share the nav bar + a centered content area — only Unlocked (the channel browser) needs its own scroll-aware nav wiring, see LiveTvChannelsScreen. */
@Composable
private fun LiveTvGateScreen(state: LiveTvUiState, onRetry: () -> Unit, onNavigate: (String) -> Unit) {
    val navFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { navFocusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        when (state) {
            is LiveTvUiState.CheckingAccess -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MangoAmber
            )
            is LiveTvUiState.Locked -> PremiumAccessScreen(
                state = state,
                onRetry = onRetry,
                navFocusRequester = navFocusRequester
            )
            is LiveTvUiState.Unlocked -> Unit
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
