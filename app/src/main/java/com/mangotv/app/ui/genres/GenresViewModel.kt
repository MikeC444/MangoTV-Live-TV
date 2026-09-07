package com.mangotv.app.ui.genres

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GenresUiState {
    data object Loading : GenresUiState
    data object NoAddons : GenresUiState
    data class Loaded(val genres: List<String>) : GenresUiState
}

// Some addons declare year filters (e.g. "2026", "2025", ...) under the same
// "genre" extra as real genre names -- the user wants them kept in the same
// list (not split into a separate screen) but grouped at the bottom, and
// extended further back than whatever the addon itself happens to declare.
private const val GENRE_LIST_MIN_YEAR = 2016

/** Backs the Genres picker: unions getAvailableGenres() across every installed provider. */
class GenresViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<GenresUiState>(GenresUiState.Loading)
    val uiState: StateFlow<GenresUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> load(providers) }
        }
    }

    private suspend fun load(providers: List<CatalogProvider>) {
        if (providers.isEmpty()) {
            _uiState.value = GenresUiState.NoAddons
            return
        }
        _uiState.value = GenresUiState.Loading
        val allOptions = mutableSetOf<String>()
        for (provider in providers) {
            runCatching { provider.getAvailableGenres() }.onSuccess { allOptions += it }
        }

        val (years, genreNames) = allOptions.partition { it.toIntOrNull()?.let { y -> y in 1900..2100 } == true }
        val sortedGenres = genreNames.sorted()
        val extendedYears = if (years.isNotEmpty()) {
            val declaredYears = years.mapNotNull { it.toIntOrNull() }
            val maxYear = declaredYears.max()
            val minYear = minOf(declaredYears.min(), GENRE_LIST_MIN_YEAR)
            (minYear..maxYear).sortedDescending().map { it.toString() }
        } else {
            emptyList()
        }

        _uiState.value = GenresUiState.Loaded(sortedGenres + extendedYears)
    }
}
