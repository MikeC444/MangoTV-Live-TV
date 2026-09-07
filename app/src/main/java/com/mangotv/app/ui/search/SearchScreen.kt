package com.mangotv.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.ContentRow
import com.mangotv.app.ui.components.EmptyState
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.detail.PendingDetailCache
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun SearchScreen(
    onNavigate: (String) -> Unit,
    viewModel: SearchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val navFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    val searchButtonFocusRequester = remember { FocusRequester() }
    val firstResultFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // Gates the keyboard behind an explicit SELECT press on the field --
    // TextField shows the IME on any focus gain by default (D-pad DOWN from
    // the nav bar included, via TopNavBar's contentFocusRequester below),
    // which pops the keyboard just from navigating past it. readOnly blocks
    // that text-input session while still letting the field take D-pad
    // focus normally; only DirectionCenter/Enter flips it into edit mode.
    var isEditing by remember { mutableStateOf(false) }

    LaunchedEffect(isEditing) {
        if (isEditing) keyboardController?.show()
    }

    LaunchedEffect(Unit) {
        runCatching { navFocusRequester.requestFocus() }
    }

    fun navigateToContent(target: Content) {
        val providerId = target.providerId ?: return
        PendingDetailCache.stash(target)
        onNavigate(MangoRoutes.detail(providerId, target.type, target.id))
    }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = MangoDimens.NavBarHeight + 24.dp)
        ) {
            item(key = "title") {
                Text(
                    text = "Search",
                    color = TextPrimary,
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 4.dp)
                )
            }
            item(key = "field") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        readOnly = !isEditing,
                        placeholder = { Text("Search movies and TV shows") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            viewModel.search(query)
                            isEditing = false
                            keyboardController?.hide()
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(fieldFocusRequester)
                            .focusProperties { up = navFocusRequester }
                            .onFocusChanged { state -> if (!state.isFocused) isEditing = false }
                            .onPreviewKeyEvent { event ->
                                if (!isEditing &&
                                    (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                                    event.type == KeyEventType.KeyDown
                                ) {
                                    isEditing = true
                                    true
                                } else {
                                    false
                                }
                            },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MangoSurface,
                            unfocusedContainerColor = MangoSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = MangoAmber,
                            focusedIndicatorColor = MangoAmber,
                            unfocusedIndicatorColor = TextTertiary
                        )
                    )
                    Spacer(Modifier.width(16.dp))
                    MangoButton(
                        text = "Search",
                        icon = Icons.Filled.Search,
                        onClick = { viewModel.search(query) },
                        style = MangoButtonStyle.FILLED,
                        focusRequester = searchButtonFocusRequester,
                        focusDown = if (uiState is SearchUiState.Results) firstResultFocusRequester else null
                    )
                }
            }
            item(key = "results") {
                when (val state = uiState) {
                    is SearchUiState.Idle -> Text(
                        text = "Search for movies and TV shows across your installed addons.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                    )
                    is SearchUiState.Searching -> Row(
                        modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = MangoAmber, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(text = "Searching…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    is SearchUiState.Results -> {
                        val hasMovies = state.movies.isNotEmpty()
                        val exitToSearchButton = { runCatching { searchButtonFocusRequester.requestFocus() } }
                        Column {
                            if (hasMovies) {
                                ContentRow(
                                    section = HomeSection(id = "search_movies", title = "Movies", items = state.movies),
                                    onItemClick = ::navigateToContent,
                                    modifier = Modifier.padding(bottom = MangoDimens.RowSpacing),
                                    firstItemFocusRequester = firstResultFocusRequester,
                                    onNavigateUpPastRow = { exitToSearchButton() }
                                )
                            }
                            if (state.tvShows.isNotEmpty()) {
                                ContentRow(
                                    section = HomeSection(id = "search_tv_shows", title = "TV Shows", items = state.tvShows),
                                    onItemClick = ::navigateToContent,
                                    firstItemFocusRequester = if (!hasMovies) firstResultFocusRequester else null,
                                    onNavigateUpPastRow = if (!hasMovies) {
                                        { exitToSearchButton() }
                                    } else {
                                        null
                                    }
                                )
                            }
                        }
                    }
                    is SearchUiState.NoResults -> EmptyState(
                        icon = Icons.Filled.SearchOff,
                        title = "No results",
                        message = "Nothing found for \"${state.query}\". Try a different search.",
                        modifier = Modifier.fillMaxWidth()
                    )
                    is SearchUiState.Error -> Text(
                        text = state.message,
                        color = MangoCoral,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                    )
                }
            }
        }

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Search"),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = fieldFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
    }
}
