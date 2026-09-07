package com.mangotv.app.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Stream
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.ShimmerBox
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun SourcesScreen(
    onNavigate: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SourcesViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        when (val state = uiState) {
            is SourcesUiState.Loading -> SourcesLoadingSkeleton(onBack = onBack)
            is SourcesUiState.Error -> FullScreenErrorState(
                message = state.message,
                onRetry = viewModel::load
            )
            is SourcesUiState.Loaded -> SourcesContent(
                state = state,
                onBack = onBack,
                onManageAddons = { onNavigate(MangoRoutes.SETTINGS_ADDONS) },
                onSelectSource = { stream ->
                    state.content.providerId?.let { pid ->
                        onNavigate(
                            MangoRoutes.player(pid, state.content.type, state.content.id, state.season, state.episode, stream.id)
                        )
                    }
                }
            )
        }
    }
}

/**
 * Shown the instant Play is tapped, before getDetails/getStreams resolve —
 * mirrors [SourcesContent]'s actual layout (info panel + header + source
 * list) with shimmer placeholders standing in for data, rather than the
 * generic Home-shaped skeleton this screen used to borrow. Everything that
 * doesn't depend on network data — the back button, the "Select a Source"
 * header, and the safety footer — renders for real immediately, so the
 * source selector reads as already there rather than as an unrelated
 * loading interstitial.
 */
@Composable
private fun SourcesLoadingSkeleton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val rowShape = RoundedCornerShape(MangoDimens.CardCornerRadius)

    Row(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .padding(22.dp)
        ) {
            HeroIconButton(icon = Icons.Filled.ArrowBack, contentDescription = "Back", onClick = onBack)
            Spacer(Modifier.height(16.dp))
            ShimmerBox(modifier = Modifier.size(width = 84.dp, height = 126.dp))
            Spacer(Modifier.height(14.dp))
            ShimmerBox(modifier = Modifier.width(90.dp).height(18.dp))
            Spacer(Modifier.height(10.dp))
            ShimmerBox(modifier = Modifier.width(200.dp).height(26.dp))
            Spacer(Modifier.height(10.dp))
            ShimmerBox(modifier = Modifier.width(160.dp).height(16.dp))
            Spacer(Modifier.height(12.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(14.dp))
            Spacer(Modifier.height(6.dp))
            ShimmerBox(modifier = Modifier.width(140.dp).height(14.dp))
            Spacer(Modifier.weight(1f))
            ShimmerBox(modifier = Modifier.fillMaxWidth().height(62.dp))
        }

        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxSize()
                .padding(horizontal = 36.dp, vertical = 22.dp)
        ) {
            Text(
                text = "Select a Source",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Choose the best quality and server for your stream.",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(Modifier.height(16.dp))
            ShimmerBox(modifier = Modifier.width(260.dp).height(36.dp))
            Spacer(Modifier.height(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                repeat(5) {
                    ShimmerBox(modifier = Modifier.fillMaxWidth().height(76.dp), shape = rowShape)
                }
            }
            Spacer(Modifier.height(14.dp))
            SafetyBar()
        }
    }
}

@Composable
private fun SourcesContent(
    state: SourcesUiState.Loaded,
    onBack: () -> Unit,
    onManageAddons: () -> Unit,
    onSelectSource: (Stream) -> Unit
) {
    var selectedFilter by remember { mutableStateOf(SourceFilter.ALL) }
    var selectedSort by remember { mutableStateOf(SourceSort.QUALITY) }

    val filtered = remember(state.streams, selectedFilter) {
        val tier = selectedFilter.tier
        if (tier == null) state.streams else state.streams.filter { it.resolutionTier == tier }
    }
    val sorted = remember(filtered, selectedSort) {
        when (selectedSort) {
            SourceSort.QUALITY -> filtered.sortedWith(
                compareBy<Stream> { it.resolutionTier.ordinal }.thenByDescending { it.seeders ?: -1 }
            )
            SourceSort.SEEDERS -> filtered.sortedByDescending { it.seeders ?: -1 }
            SourceSort.SIZE -> filtered.sortedByDescending { it.sizeBytes ?: -1 }
        }
    }

    // Land the D-pad cursor on the first (top/best) source as soon as the
    // screen opens, rather than the default heuristic focusing the back
    // button — same "request focus exactly once" pattern DetailScreen uses
    // for its Play button.
    val firstSourceFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    LaunchedEffect(sorted) {
        if (!hasRequestedInitialFocus && sorted.isNotEmpty()) {
            hasRequestedInitialFocus = true
            runCatching { firstSourceFocusRequester.requestFocus() }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // The backdrop now spans the entire screen (both the info panel and
        // the source list) instead of being cropped separately inside just
        // the left panel, so it reads as one continuous, centered photo
        // rather than a narrow sliver — only lightly dimmed for contrast.
        AsyncImage(
            model = rememberOpaqueImageRequest(state.content.backdropUrl ?: state.content.posterUrl),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MangoBackground.copy(alpha = 0.4f))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Weighted, not a fixed dp width — a fixed width can eat a hugely
            // disproportionate share of the screen on non-standard displays
            // (e.g. an ultrawide monitor with unusual density reporting),
            // starving the row content on the right of the space it needs.
            SourcesInfoPanel(
                content = state.content,
                onBack = onBack,
                modifier = Modifier
                    .weight(0.35f)
                    .fillMaxHeight()
            )

            Column(
                modifier = Modifier
                    .weight(0.65f)
                    .fillMaxSize()
                    // Deliberately smaller than MangoDimens.ScreenPaddingHorizontal/
                    // Vertical (this screen's own local values, not the shared
                    // tokens other screens use) — this page packs a header, filter
                    // bar, several rows and a bottom bar into one non-scrolling
                    // view, so it needs tighter margins than a normal content page.
                    .padding(horizontal = 36.dp, vertical = 22.dp)
            ) {
                Text(
                    text = "Select a Source",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Choose the best quality and server for your stream.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )

                Spacer(Modifier.height(16.dp))

                SourceFilterBar(
                    selectedFilter = selectedFilter,
                    onFilterChange = { selectedFilter = it },
                    selectedSort = selectedSort,
                    onSortChange = { selectedSort = it }
                )

                Spacer(Modifier.height(14.dp))

                if (sorted.isEmpty()) {
                    SourcesEmptyState(onManageAddons = onManageAddons, modifier = Modifier.weight(1f))
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        // Top padding so the "Recommended" badge — which floats
                        // above its row via a negative offset — has room to
                        // show fully instead of being clipped by the list's own
                        // top edge when that row is first/near the top.
                        contentPadding = PaddingValues(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(sorted, key = { _, stream -> stream.id }) { index, stream ->
                            SourceRow(
                                stream = stream,
                                isRecommended = stream.id == state.recommendedStreamId,
                                onClick = { onSelectSource(stream) },
                                focusRequester = if (index == 0) firstSourceFocusRequester else null
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                SafetyBar()
            }
        }
    }
}

@Composable
private fun SourcesEmptyState(onManageAddons: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.SearchOff,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.height(40.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No sources found",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Try installing more addons to find sources for this title.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        MangoButton(
            text = "Manage Addons",
            icon = Icons.Filled.Extension,
            onClick = onManageAddons,
            style = MangoButtonStyle.GLASS
        )
    }
}

@Composable
private fun SafetyBar() {
    // A hairline divider rather than a filled card — reads as part of the
    // screen, not a separate popped-out alert box.
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(DividerSubtle)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.height(18.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Safe & secure",
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "All sources are scanned for your safety",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Spacer(Modifier.width(14.dp))
            MangoButton(
                text = "How it works",
                icon = Icons.Filled.Info,
                onClick = {},
                style = MangoButtonStyle.GLASS,
                compact = true
            )
        }
    }
}
