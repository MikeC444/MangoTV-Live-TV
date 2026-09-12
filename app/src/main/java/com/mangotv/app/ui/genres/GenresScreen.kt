package com.mangotv.app.ui.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.settings.SettingsScaffold
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@Composable
fun GenresScreen(
    onNavigate: (String) -> Unit,
    viewModel: GenresViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val firstGenreFocusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // See TopNavBar's kdoc on onNavigateDown: firstGenreFocusRequester below
    // is pinned to only the list's very first item, which this LazyColumn
    // stops composing (and therefore detaches the requester from) once it's
    // scrolled out of view -- a long enough genre+year list (see
    // GenresViewModel's GENRE_LIST_MIN_YEAR) makes that the common case, not
    // an edge case. Relying on SettingsScaffold's declarative
    // firstContentFocusRequester alone crashed the app (IllegalStateException:
    // "FocusRequester is not initialized") the moment DOWN was pressed from
    // the nav bar while item 0 wasn't currently composed. Scrolling back to
    // it first, then focusing, guarantees the target exists before it's used
    // -- the same pattern RowsBrowseGridContent already uses for the same
    // reason.
    val genreList = (uiState as? GenresUiState.Loaded)?.genres.orEmpty()
    val onNavigateDown: (() -> Unit)? = if (genreList.isNotEmpty()) {
        {
            coroutineScope.launch {
                val alreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == 0 }
                if (!alreadyVisible) {
                    listState.animateScrollToItem(0)
                }
                runCatching { firstGenreFocusRequester.requestFocus() }
            }
        }
    } else {
        null
    }

    SettingsScaffold(
        title = "Genres",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = firstGenreFocusRequester,
        titleIcon = Icons.Filled.Category,
        selectedNavLabel = "Genres",
        onNavigateDown = onNavigateDown
    ) {
        when (val state = uiState) {
            is GenresUiState.Loading -> CircularProgressIndicator(color = MangoAmber)
            is GenresUiState.NoAddons -> Text(
                text = "Install an addon first — genres will show up here once it's added.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            is GenresUiState.Loaded -> {
                if (state.genres.isEmpty()) {
                    Text(
                        text = "Your installed addons aren't reporting any genres right now.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(state.genres) { index, genre ->
                            TvFocusSurface(
                                onClick = { onNavigate(MangoRoutes.genreResults(genre)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
                                // Same wide-element-safe scale as Home Rows'
                                // toggle list -- TvFocusSurface's default 8%
                                // scale is tuned for small poster cards and
                                // clips past the screen edge on a
                                // near-full-width row like this one.
                                focusedScale = 1.02f,
                                backgroundColor = MangoSurface,
                                focusRequester = if (index == 0) firstGenreFocusRequester else null,
                                focusUp = if (index == 0) navFocusRequester else null
                            ) {
                                Text(
                                    text = genre,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.titleLarge,
                                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
