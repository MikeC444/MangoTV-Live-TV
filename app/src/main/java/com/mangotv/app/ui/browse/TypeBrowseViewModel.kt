package com.mangotv.app.ui.browse

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.model.HomeSection
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs Movies and TV Shows: loops every installed provider's
 * getSectionsByType(type), same reactive-to-ProviderRegistry pattern as
 * HomeViewModel. Unlike Home, this deliberately shows no genre breakdown --
 * every provider's base + genre rows are flattened into one deduplicated,
 * shuffled row, so genres never touch the user's saved Home Rows state and
 * a hidden Home row still shows up here. loadMore() extends that same row
 * with additional pages as the user scrolls, so it doesn't dead-end after
 * one base-catalog page's worth of items.
 */
open class TypeBrowseViewModel(application: Application, private val type: ContentType) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<RowsBrowseUiState>(RowsBrowseUiState.Loading)
    val uiState: StateFlow<RowsBrowseUiState> = _uiState.asStateFlow()

    private val allItems = mutableListOf<Content>()
    private val seenIds = mutableSetOf<String>()
    private var providersSnapshot: List<CatalogProvider> = emptyList()
    private var nextPage = 1
    private var hasMore = true
    private var isLoadingMore = false

    init {
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> load(providers) }
        }
    }

    fun load() {
        viewModelScope.launch { load(ProviderRegistry.activeProviders()) }
    }

    private suspend fun load(providers: List<CatalogProvider>) {
        _uiState.value = RowsBrowseUiState.Loading
        providersSnapshot = providers
        nextPage = 1
        hasMore = true
        isLoadingMore = false
        allItems.clear()
        seenIds.clear()

        if (providers.isEmpty()) {
            _uiState.value = RowsBrowseUiState.Loaded(emptyList())
            return
        }

        val results = coroutineScope {
            providers.map { provider -> async { runCatching { provider.getSectionsByType(type) } } }.awaitAll()
        }
        val sections = mutableListOf<HomeSection>()
        var anyProviderFailed = false
        results.forEach { result ->
            result.onSuccess { sections += it }.onFailure { anyProviderFailed = true }
        }

        // Flatten every provider's base + genre rows into one deduplicated,
        // shuffled row -- no genre breakdown here, and a fresh shuffle each
        // time this loads so the order varies on revisit.
        val items = sections.flatMap { it.items }.distinctBy { it.id }.shuffled()
        allItems += items
        seenIds += items.map { it.id }

        _uiState.value = when {
            allItems.isNotEmpty() -> RowsBrowseUiState.Loaded(listOf(HomeSection(id = "flat_$type", title = "", items = allItems.toList())))
            anyProviderFailed -> RowsBrowseUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
            else -> RowsBrowseUiState.Loaded(emptyList())
        }
    }

    // Called as the grid scrolls near the bottom (see RowsBrowseScreen.kt) --
    // fetches the next page from every provider and appends it to the same
    // row, so scrolling feels endless instead of dead-ending after one
    // base-catalog page. Only the newly-fetched batch is shuffled, not the
    // whole accumulated list -- re-shuffling everything on every page would
    // visibly reorder rows the user has already scrolled past.
    fun loadMore() {
        if (isLoadingMore || !hasMore || providersSnapshot.isEmpty()) return
        isLoadingMore = true
        viewModelScope.launch {
            val page = nextPage
            val results = coroutineScope {
                providersSnapshot.map { provider -> async { runCatching { provider.getMoreItemsByType(type, page) } } }.awaitAll()
            }
            val newItems = results.flatMap { it.getOrElse { emptyList() } }
                .filterNot { it.id in seenIds }
                .distinctBy { it.id }
                .shuffled()

            if (newItems.isEmpty()) {
                hasMore = false
            } else {
                nextPage++
                allItems += newItems
                seenIds += newItems.map { it.id }
                _uiState.value = RowsBrowseUiState.Loaded(listOf(HomeSection(id = "flat_$type", title = "", items = allItems.toList())))
            }
            isLoadingMore = false
        }
    }
}

class MoviesViewModel(application: Application) : TypeBrowseViewModel(application, ContentType.MOVIE)

class TvShowsViewModel(application: Application) : TypeBrowseViewModel(application, ContentType.TV_SHOW)
