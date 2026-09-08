package com.mangotv.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.ProviderRegistry
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
    private val homeCacheRepository = (application as MangoTvApplication).container.homeCacheRepository

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    // Flips true the first time fetch() has REAL (network-fetched) content
    // to show -- the first batch of rows from any provider, or a genuine
    // empty/error settlement if there's nothing to show -- as opposed to a
    // cache-only paint or one of fetch()'s early-return "still transient,
    // keep waiting" paths. Deliberately fires on the FIRST batch rather
    // than waiting for the entire fetch (every base+genre row across every
    // provider) to finish: LoadingScreen (see its own doc) waits for this
    // so Home can reveal as soon as there's something real to show, with
    // whatever's still in flight filling in live afterward, rather than
    // hiding the whole multi-row fetch behind the loading screen.
    private val _liveDataReady = MutableStateFlow(false)
    val liveDataReady: StateFlow<Boolean> = _liveDataReady.asStateFlow()

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
    private var lastFetchFailed = false
    private var hasFetchedOnce = false

    // True once cold-boot cache has painted Home but before the first real
    // (network) fetch has settled. Used two ways below: (1) an empty
    // provider list during this window means "addon restore hasn't
    // registered anything yet", not "user genuinely has no addons", so it
    // shouldn't wipe a perfectly good cached screen back to empty; (2) a
    // total fetch failure during this window (e.g. no network on this
    // launch) leaves the stale cache up rather than replacing it with a
    // hard error -- still-stale content beats no content, and the next
    // successful fetch (this session or next cold boot) replaces it.
    //
    // Known trade-off: there's no reliable signal here to tell "restore
    // hasn't registered anything yet" apart from "the user really did just
    // remove their last addon" -- both look identical (one empty-list
    // emission, ProviderRegistry never re-emits an unchanged value) from
    // this ViewModel's perspective. Cold-booting right after removing every
    // addon can briefly show stale cached rows instead of the Empty state
    // until providers changes again. Accepted as rare/self-correcting
    // rather than adding cross-repository "restore settled" signaling for
    // it.
    private var showingCacheOnly = false

    fun toggleMyList(content: Content) {
        viewModelScope.launch { myListRepository.toggle(content) }
    }

    init {
        viewModelScope.launch {
            // Paints instantly from whatever the last successful live fetch
            // produced, before the real network fetch even starts -- see
            // HomeCacheRepository's own doc. This is deliberately awaited
            // (not just launched) before starting the providers collector
            // below: ProviderRegistry starts empty and only fills in once
            // addon restore finishes elsewhere, and that collector's very
            // first (possibly still-empty) emission runs fully
            // synchronously up to its own next suspension point. Launching
            // both at once let that first emission's synchronous branch
            // race ahead of this coroutine's disk read and mark
            // hasFetchedOnce = true before the cache ever got a chance --
            // silently defeating caching on exactly the cold-boot case it
            // exists for. Awaiting first removes the race instead of hoping
            // timing favors the cache.
            homeCacheRepository.read()?.let { (hero, sections) ->
                rawHero = hero
                rawSections = sections
                hasFetchedOnce = true
                showingCacheOnly = true
                applyPreferences(homeRowPreferences.preferences.value)
            }
            // Network fetch is keyed ONLY on the provider list (an addon
            // being installed, removed, enabled or disabled) -- NOT on
            // preferences. These two used to be combined into one trigger,
            // which meant the (independently-resolving) preferences
            // DataStore read settling shortly after providers did on cold
            // boot fired a second full network re-fetch, doubling
            // perceived load time for no reason.
            launch {
                ProviderRegistry.providers.collect { providers -> fetch(providers) }
            }
        }
        // Preference changes just re-apply the already-fetched raw data.
        viewModelScope.launch {
            homeRowPreferences.preferences.collect { prefs -> applyPreferences(prefs) }
        }
    }

    fun load() {
        viewModelScope.launch { fetch(ProviderRegistry.activeProviders()) }
    }

    private suspend fun fetch(providers: List<CatalogProvider>) {
        if (providers.isEmpty()) {
            if (showingCacheOnly) return
            rawHero = emptyList()
            rawSections = emptyList()
            lastFetchFailed = false
            hasFetchedOnce = true
            _uiState.value = HomeUiState.Empty
            _liveDataReady.value = true
            return
        }

        // Keep showing cached content while this fetch is in flight rather
        // than flashing back to the loading skeleton -- that would defeat
        // the entire point of painting from cache first. A cache-less cold
        // boot (nothing to show yet) behaves exactly as before.
        if (!showingCacheOnly) _uiState.value = HomeUiState.Loading

        val wasShowingCacheOnly = showingCacheOnly
        val hero = mutableListOf<Content>()
        val sections = mutableListOf<HomeSection>()
        var anyProviderFailed = false

        // Every provider is collected concurrently, and each provider's own
        // rows arrive in batches (see StremioAddonProvider.buildSectionsFlow)
        // rather than all at once -- every batch, from any provider, is
        // published immediately instead of waiting for the entire fetch (up
        // to ~30 rows per provider) to finish. This is the actual "rows
        // appear progressively" behavior; the collect{} block below is where
        // it happens, not a separate pass over a final combined list.
        coroutineScope {
            providers.forEach { provider ->
                launch {
                    var isFirstBatchForProvider = true
                    runCatching {
                        provider.getHomeSections().collect { batch ->
                            sections += batch
                            // Hero items come specifically from each
                            // provider's OWN base/popular row, which is
                            // always in that provider's first batch (see
                            // buildSectionsFlow) -- not "whichever section
                            // happens to be first in whichever batch
                            // arrives", which later batches would get wrong.
                            if (isFirstBatchForProvider) {
                                isFirstBatchForProvider = false
                                batch.firstOrNull()?.items?.let { items ->
                                    if (hero.size < 3) hero += items.take(3 - hero.size)
                                }
                            }
                            rawHero = hero.toList()
                            rawSections = sections.toList()
                            hasFetchedOnce = true
                            showingCacheOnly = false
                            applyPreferences(homeRowPreferences.preferences.value)
                            _liveDataReady.value = true
                        }
                    }.onFailure { anyProviderFailed = true }
                }
            }
        }

        lastFetchFailed = anyProviderFailed

        if (hero.isEmpty() && sections.isEmpty()) {
            // Nothing arrived from any provider. If cache was showing and
            // this is a total failure, leave the stale cache up instead of
            // replacing it with a hard error -- showingCacheOnly was never
            // touched above (the collect{} block that flips it never ran),
            // so it's still true here exactly when that applies.
            if (anyProviderFailed && wasShowingCacheOnly) return
            hasFetchedOnce = true
            showingCacheOnly = false
            applyPreferences(homeRowPreferences.preferences.value)
            _liveDataReady.value = true
        }

        if (hero.isNotEmpty() || sections.isNotEmpty()) {
            homeCacheRepository.write(hero, sections)
        }
    }

    private fun applyPreferences(rowPreferences: HomeRowPreferences) {
        if (!hasFetchedOnce) return

        val visibleSections = rowPreferences.applyOrder(rawSections).filterNot { it.id in rowPreferences.hiddenRowIds }

        _uiState.value = when {
            rawHero.isNotEmpty() || visibleSections.isNotEmpty() -> HomeUiState.Success(rawHero, visibleSections)
            lastFetchFailed -> HomeUiState.Error("Couldn't reach your installed addons. Check your connection and try again.")
            else -> HomeUiState.Empty
        }
    }
}
