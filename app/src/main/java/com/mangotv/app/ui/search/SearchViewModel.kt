package com.mangotv.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data object Searching : SearchUiState
    data class Results(val movies: List<Content>, val tvShows: List<Content>) : SearchUiState
    data class NoResults(val query: String) : SearchUiState
    data class Error(val message: String) : SearchUiState
}

/**
 * Fires on submit only (not live-as-you-type) -- each search fans out a
 * network call per installed provider, so debouncing every keystroke would
 * mean a lot of avoidable traffic on Fire Stick hardware. Matches the same
 * submit-triggered pattern AddAddonScreen already uses for its own text
 * field.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val myListRepository = (application as MangoTvApplication).container.myListRepository

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    // Pristine (never-stamped) results from the last search() call --
    // currentResults() always re-derives from these rather than from
    // whatever's already in _uiState, so a title removed from My List after
    // being watched correctly loses its tick on the next re-publish instead
    // of staying stuck true.
    private var rawMovies: List<Content> = emptyList()
    private var rawTvShows: List<Content> = emptyList()

    // Ids My List has marked watched -- drives the poster tick on search
    // results too, not just My List's own screen. Plain field + collector
    // (not a StateFlow) since it only needs to feed currentResults() below.
    private var watchedIds: Set<String> = emptySet()

    init {
        // Re-publishes the last results whenever watched status changes, so
        // a title crossing the completion threshold (or being removed from
        // My List) ticks/unticks immediately even if the user is still
        // looking at old results rather than searching again.
        viewModelScope.launch {
            myListRepository.items.collect { items ->
                watchedIds = items.filter { it.watched }.map { it.id }.toSet()
                if (_uiState.value is SearchUiState.Results) {
                    _uiState.value = currentResults()
                }
            }
        }
    }

    private fun Content.withWatchedFlag(): Content = if (id in watchedIds) copy(watched = true) else this

    private fun currentResults(): SearchUiState.Results =
        SearchUiState.Results(rawMovies.map { it.withWatchedFlag() }, rawTvShows.map { it.withWatchedFlag() })

    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.value = SearchUiState.Searching

            val providers = ProviderRegistry.activeProviders()
            if (providers.isEmpty()) {
                _uiState.value = SearchUiState.NoResults(query)
                return@launch
            }

            val perProvider = mutableListOf<List<Content>>()
            var anyProviderFailed = false
            for (provider in providers) {
                runCatching { provider.search(query) }
                    .onSuccess { perProvider += it }
                    .onFailure { anyProviderFailed = true }
            }

            val merged = interleave(perProvider).distinctBy { it.id }
            rawMovies = merged.filter { it.type == ContentType.MOVIE }
            rawTvShows = merged.filter { it.type == ContentType.TV_SHOW }
            _uiState.value = when {
                rawMovies.isNotEmpty() || rawTvShows.isNotEmpty() -> currentResults()
                anyProviderFailed -> SearchUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
                else -> SearchUiState.NoResults(query)
            }
        }
    }
}

// Same small local interleave copy as GenreResultsViewModel -- merging
// across PROVIDERS here, not across one provider's own catalogs.
private fun <T> interleave(lists: List<List<T>>): List<T> {
    if (lists.size == 1) return lists[0]
    val result = mutableListOf<T>()
    val maxSize = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until maxSize) {
        for (list in lists) {
            if (i < list.size) result += list[i]
        }
    }
    return result
}
