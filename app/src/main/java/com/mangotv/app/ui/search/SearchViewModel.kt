package com.mangotv.app.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

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
            val movies = merged.filter { it.type == ContentType.MOVIE }
            val tvShows = merged.filter { it.type == ContentType.TV_SHOW }
            _uiState.value = when {
                movies.isNotEmpty() || tvShows.isNotEmpty() -> SearchUiState.Results(movies, tvShows)
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
