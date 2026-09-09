package com.mangotv.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.data.model.WatchProgress
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(
        val heroItems: List<Content>,
        val sections: List<HomeSection>
    ) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val homeRowPreferences = (application as MangoTvApplication).container.homeRowPreferencesRepository
    private val myListRepository = (application as MangoTvApplication).container.myListRepository
    private val continueWatchingRepository = (application as MangoTvApplication).container.continueWatchingRepository

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    val savedIds: StateFlow<Set<String>> = myListRepository.items
        .map { items -> items.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Raw fetch results, cached here so a preferences-only change (row
    // order/hidden state from Settings > Home Rows) can re-apply cheaply
    // without re-hitting the network -- same "cheap in-memory re-sort of
    // already-fetched rows" principle Home Rows' own drag-reorder already
    // relies on, applied here to preference changes instead of drag events.
    private var rawHero: List<Content> = emptyList()
    private var rawSections: List<HomeSection> = emptyList()
    // Not subject to Home Rows' order/hidden-state prefs (those apply to
    // addon-supplied catalog rows) -- always shown first when non-empty,
    // omitted entirely when empty rather than rendering an empty row.
    private var continueWatchingSection: HomeSection? = null
    private var lastFetchFailed = false
    private var hasFetchedOnce = false

    fun toggleMyList(content: Content) {
        viewModelScope.launch { myListRepository.toggle(content) }
    }

    init {
        // Network fetch is keyed ONLY on the provider list (an addon being
        // installed, removed, enabled or disabled) -- NOT on preferences.
        // These two used to be combined into one trigger, which meant the
        // (independently-resolving) preferences DataStore read settling
        // shortly after providers did on cold boot fired a second full
        // network re-fetch, doubling perceived load time for no reason.
        viewModelScope.launch {
            ProviderRegistry.providers.collect { providers -> fetch(providers) }
        }
        // Preference changes just re-apply the already-fetched raw data.
        viewModelScope.launch {
            homeRowPreferences.preferences.collect { prefs -> applyPreferences(prefs) }
        }
        // Continue Watching (Milestone 8) is its own independent trigger,
        // same reasoning as preferences above: re-applying is a cheap
        // local re-combine, never a network re-fetch of the catalog rows.
        viewModelScope.launch {
            continueWatchingRepository.items.collect { entries ->
                continueWatchingSection = entries.toHomeSectionOrNull()
                applyPreferences(homeRowPreferences.preferences.value)
            }
        }
    }

    fun load() {
        viewModelScope.launch { fetch(ProviderRegistry.activeProviders()) }
    }

    private suspend fun fetch(providers: List<CatalogProvider>) {
        _uiState.value = HomeUiState.Loading

        if (providers.isEmpty()) {
            rawHero = emptyList()
            rawSections = emptyList()
            lastFetchFailed = false
            hasFetchedOnce = true
            _uiState.value = HomeUiState.Empty
            return
        }

        val hero = mutableListOf<Content>()
        val sections = mutableListOf<HomeSection>()
        var anyProviderFailed = false

        // Every provider's getHomeSections() is launched together up front,
        // so all of it runs fully concurrently. Hero items are derived from
        // each provider's own base/popular row (always first, per
        // buildSections' ordering) instead of a separate getFeatured() call
        // -- that used to be a second, independent fetch against the exact
        // same catalog endpoint on every single Home load.
        val results = coroutineScope {
            providers.map { provider -> async { runCatching { provider.getHomeSections() } } }.awaitAll()
        }
        results.forEach { result ->
            result.onSuccess { providerSections ->
                sections += providerSections
                hero += providerSections.firstOrNull()?.items.orEmpty().take(3)
            }.onFailure { anyProviderFailed = true }
        }

        rawHero = hero
        rawSections = sections
        lastFetchFailed = anyProviderFailed
        hasFetchedOnce = true

        applyPreferences(homeRowPreferences.preferences.value)
    }

    private fun applyPreferences(rowPreferences: HomeRowPreferences) {
        if (!hasFetchedOnce) return

        val visibleSections = rowPreferences.applyOrder(rawSections).filterNot { it.id in rowPreferences.hiddenRowIds }
        val sections = listOfNotNull(continueWatchingSection) + visibleSections

        _uiState.value = when {
            rawHero.isNotEmpty() || sections.isNotEmpty() -> HomeUiState.Success(rawHero, sections)
            lastFetchFailed -> HomeUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
            else -> HomeUiState.Empty
        }
    }

    private fun List<ContinueWatchingEntry>.toHomeSectionOrNull(): HomeSection? {
        if (isEmpty()) return null
        return HomeSection(
            id = CONTINUE_WATCHING_ROW_ID,
            title = "Continue Watching",
            items = map { it.toContent() },
            style = RowStyle.CONTINUE_WATCHING
        )
    }

    private fun ContinueWatchingEntry.toContent(): Content = Content(
        id = contentId,
        type = contentType,
        title = title,
        description = "",
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        providerId = providerId,
        watchProgress = WatchProgress(
            positionMs = positionMs,
            durationMs = durationMs,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
            episodeTitle = episodeTitle
        )
    )

    companion object {
        private const val CONTINUE_WATCHING_ROW_ID = "continue_watching"
    }
}
