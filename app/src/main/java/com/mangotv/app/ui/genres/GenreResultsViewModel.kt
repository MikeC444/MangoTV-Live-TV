package com.mangotv.app.ui.genres

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.ui.browse.RowsBrowseUiState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Backs the Genre Results screen (reuses RowsBrowseContent). Fetches
 * getGenreSection(genre) from every installed provider and merges them into
 * one HomeSection -- each provider's own section is already mixed-type
 * (movies+series) via StremioAddonProvider's own merge, this just combines
 * across providers on top of that. loadMore() extends that same section
 * with additional pages as the user scrolls, so it doesn't dead-end after
 * one page's worth of items.
 */
class GenreResultsViewModel(application: Application, savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    val genre: String = URLDecoder.decode(savedStateHandle.get<String>("genre").orEmpty(), "UTF-8")

    private val _uiState = MutableStateFlow<RowsBrowseUiState>(RowsBrowseUiState.Loading)
    val uiState: StateFlow<RowsBrowseUiState> = _uiState.asStateFlow()

    private val allItems = mutableListOf<Content>()
    private val seenIds = mutableSetOf<String>()
    private var providersSnapshot: List<CatalogProvider> = emptyList()
    private var nextPage = 1
    private var hasMore = true
    private var isLoadingMore = false

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = RowsBrowseUiState.Loading

            val providers = ProviderRegistry.activeProviders()
            providersSnapshot = providers
            nextPage = 1
            hasMore = true
            isLoadingMore = false
            allItems.clear()
            seenIds.clear()

            if (providers.isEmpty()) {
                _uiState.value = RowsBrowseUiState.Loaded(emptyList())
                return@launch
            }

            val results = coroutineScope {
                providers.map { provider -> async { runCatching { provider.getGenreSection(genre) } } }.awaitAll()
            }
            val perProviderItems = mutableListOf<List<Content>>()
            var anyProviderFailed = false
            results.forEach { result ->
                result.onSuccess { section -> section?.let { perProviderItems += it.items } }
                    .onFailure { anyProviderFailed = true }
            }

            val merged = interleave(perProviderItems).distinctBy { it.id }
            allItems += merged
            seenIds += merged.map { it.id }

            _uiState.value = when {
                allItems.isNotEmpty() -> RowsBrowseUiState.Loaded(listOf(HomeSection(id = "genre_$genre", title = genre, items = allItems.toList())))
                anyProviderFailed -> RowsBrowseUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
                else -> RowsBrowseUiState.Loaded(emptyList())
            }
        }
    }

    // Called as the grid scrolls near the bottom (see RowsBrowseScreen.kt).
    // Genre results aren't shuffled (server order is preserved), so unlike
    // TypeBrowseViewModel this just appends each new page in fetched order.
    fun loadMore() {
        if (isLoadingMore || !hasMore || providersSnapshot.isEmpty()) return
        isLoadingMore = true
        viewModelScope.launch {
            val page = nextPage
            val results = coroutineScope {
                providersSnapshot.map { provider -> async { runCatching { provider.getMoreGenreItems(genre, page) } } }.awaitAll()
            }
            val newItems = interleave(results.map { it.getOrElse { emptyList() } })
                .filterNot { it.id in seenIds }
                .distinctBy { it.id }

            if (newItems.isEmpty()) {
                hasMore = false
            } else {
                nextPage++
                allItems += newItems
                seenIds += newItems.map { it.id }
                _uiState.value = RowsBrowseUiState.Loaded(listOf(HomeSection(id = "genre_$genre", title = genre, items = allItems.toList())))
            }
            isLoadingMore = false
        }
    }
}

// Small local copy of the same interleave pattern StremioAddonProvider uses
// internally -- not worth extracting that private helper into shared code
// for this one extra caller (cross-PROVIDER merging here, vs. cross-catalog
// merging there).
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
