package com.mangotv.app.ui.genres

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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

@Composable
fun GenresScreen(
    onNavigate: (String) -> Unit,
    viewModel: GenresViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val firstGenreFocusRequester = remember { FocusRequester() }

    SettingsScaffold(
        title = "Genres",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = firstGenreFocusRequester,
        titleIcon = Icons.Filled.Category,
        selectedNavLabel = "Genres"
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
