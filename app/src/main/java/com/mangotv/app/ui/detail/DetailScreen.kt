package com.mangotv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.ContentRow
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.HomeLoadingSkeleton
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import kotlinx.coroutines.launch

@Composable
fun DetailScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        when (val state = uiState) {
            is DetailUiState.Loading -> HomeLoadingSkeleton()
            is DetailUiState.Error -> FullScreenErrorState(
                message = state.message,
                onRetry = viewModel::load
            )
            is DetailUiState.Success -> {
                val isInMyList by viewModel.isInMyList.collectAsStateWithLifecycle()
                val resumeEntry by viewModel.resumeEntry.collectAsStateWithLifecycle()
                DetailContent(
                    content = state.content,
                    similar = state.similar,
                    onNavigate = onNavigate,
                    isInMyList = isInMyList,
                    onToggleMyList = viewModel::toggleMyList,
                    resumeEntry = resumeEntry,
                    lastStreamIdFor = viewModel::lastStreamIdFor
                )
            }
        }
    }
}

@Composable
private fun DetailContent(
    content: Content,
    similar: List<Content>,
    onNavigate: (String) -> Unit,
    isInMyList: Boolean,
    onToggleMyList: () -> Unit,
    resumeEntry: ContinueWatchingEntry?,
    lastStreamIdFor: (season: Int?, episode: Int?) -> String?,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val playFocusRequester = remember { FocusRequester() }
    val navFocusRequester = remember { FocusRequester() }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }

    // Same "keep the nav bar / hero region completely static, only scroll
    // when focus genuinely moves into the content below" setup already
    // proven on Home — see HomeScreen.kt for the full rationale on why both
    // the NestedScrollConnection block and the snapshotFlow watchdog exist.
    var heroRegionFocused by remember { mutableStateOf(true) }

    val isScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 60
        }
    }

    LaunchedEffect(content.id) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    val heroScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (heroRegionFocused) available else Offset.Zero
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (heroRegionFocused && (index != 0 || offset != 0)) {
                    listState.scrollToItem(0, 0)
                }
            }
    }

    fun returnToHero() {
        heroRegionFocused = true
        coroutineScope.launch {
            listState.scrollToItem(0, 0)
            runCatching { playFocusRequester.requestFocus() }
        }
    }

    fun navigateToContent(target: Content) {
        val providerId = target.providerId ?: return
        PendingDetailCache.stash(target)
        onNavigate(MangoRoutes.detail(providerId, target.type, target.id))
    }

    // Shared by the hero's Play/Resume button and a season's individual
    // episode rows: whenever a source is actually remembered for this exact
    // title/season/episode, skip the Sources picker entirely and jump
    // straight into Player on it -- what "Resume" actually means to the
    // user, rather than routing through Sources just to have it
    // auto-redirect there (which still flashed the picker screen/loading
    // state on the way through). Deliberately does NOT also require this to
    // match resumeEntry's own season/episode -- lastStreamIdFor is already
    // keyed on the exact season/episode being requested here, so adding
    // that check on top only made this fragile: if DetailHeroSection's own
    // "find this episode in content.seasons" lookup ever fell back to
    // episode 1 for any reason (a data hiccup on a later fetch, say), the
    // season/episode passed here would silently stop matching resumeEntry
    // and this would wrongly fall through to the picker even though a
    // perfectly good remembered source existed for the episode actually
    // being requested. Falls back to the normal picker for a first-ever
    // play, an episode nothing was ever remembered for, or a remembered
    // source that's gone missing from a fresh fetch (Player's own error
    // state then offers "Choose a Different Source" back to the picker, so
    // this is never a dead end).
    fun navigateToPlayback(providerId: String, season: Int?, episode: Int?) {
        val streamId = lastStreamIdFor(season, episode)
        val route = if (streamId != null) {
            MangoRoutes.player(providerId, content.type, content.id, season, episode, streamId)
        } else {
            MangoRoutes.sources(providerId, content.type, content.id, season, episode)
        }
        onNavigate(route)
    }

    // Movies only, for now: shrink the whole page so it fits on one screen
    // without scrolling. TV shows (whether they have season data or not)
    // keep the existing, larger layout untouched.
    val compact = content.type == ContentType.MOVIE

    Box(modifier = modifier.fillMaxSize()) {
        // Fixed, full-screen, completely static backdrop that stays put
        // behind the scrolling content instead of scrolling away with the
        // hero item — the page background the way Nuvio treats it, rather
        // than an image confined to a "hero" region. No zoom/pan animation
        // and no blur-on-scroll — just the plain image.
        DetailBackdrop(
            url = content.backdropUrl,
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .background(MangoSurfaceHigh)
        )

        // Left-to-right gradient so hero text stays legible. Cast/Seasons
        // further down sit on their own opaque card backgrounds rather
        // than directly on the image, so this doesn't need a bottom fade
        // too — the backdrop just stays visible underneath the whole page.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MangoBackground.copy(alpha = 0.55f),
                            MangoBackground.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .nestedScroll(heroScrollLock)
                .fillMaxSize()
        ) {
            item(key = "hero") {
                DetailHeroSection(
                    content = content,
                    playFocusRequester = playFocusRequester,
                    onPlay = { episode ->
                        content.providerId?.let { pid ->
                            navigateToPlayback(pid, episode?.seasonNumber, episode?.episodeNumber)
                        }
                    },
                    onWatched = {},
                    onWatchlist = onToggleMyList,
                    isInMyList = isInMyList,
                    onMore = {},
                    navUpFocusRequester = navFocusRequester,
                    onNavigateUpPastHero = { returnToHero() },
                    onNavigateDownFromHero = { heroRegionFocused = false },
                    compact = compact,
                    resumeEntry = resumeEntry
                )
            }
            item(key = "seasons_or_cast_and_similar") {
                // TV shows with real season/episode data get a season
                // picker + episode list instead — cast and "similar" don't
                // apply the same way once there's something more useful
                // (and more central to actually watching the show) to show.
                if (content.type == ContentType.TV_SHOW && content.seasons.isNotEmpty()) {
                    SeasonsSection(
                        seasons = content.seasons,
                        modifier = Modifier.fillMaxWidth(),
                        onNavigateUpPastRow = { returnToHero() },
                        onEpisodeClick = { episode ->
                            content.providerId?.let { pid ->
                                navigateToPlayback(pid, episode.seasonNumber, episode.episodeNumber)
                            }
                        }
                    )
                } else {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CastRow(
                            cast = content.cast,
                            modifier = Modifier.weight(1f),
                            onNavigateUpPastRow = { returnToHero() },
                            compact = compact
                        )
                        if (similar.isNotEmpty()) {
                            // Movies get the smaller, landscape-card
                            // treatment; the TV show fallback (a show with
                            // no season data) keeps the original ContentRow
                            // layout, since compact is only ever true for
                            // movies.
                            if (compact) {
                                SimilarRow(
                                    title = "You May Also Like",
                                    items = similar,
                                    onItemClick = ::navigateToContent,
                                    modifier = Modifier.weight(2f),
                                    onNavigateUpPastRow = { returnToHero() }
                                )
                            } else {
                                ContentRow(
                                    section = HomeSection(
                                        id = "similar",
                                        title = "You May Also Like",
                                        items = similar
                                    ),
                                    onItemClick = ::navigateToContent,
                                    modifier = Modifier.weight(2f),
                                    onNavigateUpPastRow = { returnToHero() }
                                )
                            }
                        }
                    }
                }
            }
            item(key = "bottom_spacer") {
                Spacer(Modifier.height(if (compact) 16.dp else 48.dp))
            }
        }

        TopNavBar(
            transparentBackground = !isScrolled,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = 0,
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = playFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = { returnToHero() }
        )
    }
}

@Composable
private fun DetailBackdrop(url: String?, modifier: Modifier = Modifier) {
    AsyncImage(
        model = rememberOpaqueImageRequest(url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize()
    )
}
