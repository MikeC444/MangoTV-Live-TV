package com.mangotv.app.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.provider.CatalogProvider
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import java.net.URLDecoder

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data class Success(val content: Content, val similar: List<Content>) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

class DetailViewModel(application: Application, private val savedStateHandle: SavedStateHandle) : AndroidViewModel(application) {

    private val myListRepository = (application as MangoTvApplication).container.myListRepository
    private val continueWatchingRepository = (application as MangoTvApplication).container.continueWatchingRepository

    private val providerId: String =
        URLDecoder.decode(savedStateHandle.get<String>("providerId").orEmpty(), "UTF-8")
    private val contentType: ContentType =
        if (savedStateHandle.get<String>("type") == ContentType.TV_SHOW.name) {
            ContentType.TV_SHOW
        } else {
            ContentType.MOVIE
        }
    private val contentId: String =
        URLDecoder.decode(savedStateHandle.get<String>("id").orEmpty(), "UTF-8")

    // Seeded from whatever preview Content the previous screen already had
    // (see PendingDetailCache) so the backdrop/title/poster can render
    // immediately instead of waiting on the full getDetails() round trip --
    // load() below still runs and overwrites this with real data (or an
    // Error) once it resolves, so a missing/stale entry (e.g. a deep link)
    // just falls back to today's Loading-first behavior.
    private val _uiState = MutableStateFlow<DetailUiState>(
        PendingDetailCache.consume(contentId)?.let { DetailUiState.Success(it, similar = emptyList()) }
            ?: DetailUiState.Loading
    )
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    val isInMyList: StateFlow<Boolean> = myListRepository.items
        .map { items -> items.any { it.id == contentId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    // Drives DetailHeroSection's Play -> Resume switch and which episode it
    // targets for a TV show -- reactive (not just a one-off findResumePoint
    // snapshot) so finishing an episode while this screen is still open
    // (e.g. the player's own back button) updates the button immediately
    // instead of only on the next full navigation into Detail.
    val resumeEntry: StateFlow<ContinueWatchingEntry?> = continueWatchingRepository.items
        .map { items -> items.firstOrNull { it.providerId == providerId && it.contentId == contentId && it.contentType == contentType } }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            continueWatchingRepository.findResumePoint(providerId, contentId, contentType)
        )

    fun toggleMyList() {
        val content = (uiState.value as? DetailUiState.Success)?.content ?: return
        viewModelScope.launch { myListRepository.toggle(content) }
    }

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = DetailUiState.Loading

            val provider = ProviderRegistry.activeProviders().find { it.id == providerId }
            if (provider == null) {
                _uiState.value = DetailUiState.Error("This addon is no longer installed.")
                return@launch
            }

            val detail = runCatching { provider.getDetails(contentType, contentId) }.getOrNull()
            if (detail == null) {
                _uiState.value = DetailUiState.Error("Couldn't load details for this title.")
                return@launch
            }

            // Show the core page as soon as the detail fetch resolves,
            // rather than also waiting on "You May Also Like" — that used
            // to re-fetch every catalog the addon defines before anything
            // at all appeared, turning one network round trip into two
            // sequential ones and making every detail page open noticeably
            // slower than it needed to. The row itself just pops in a
            // moment later once it's ready.
            _uiState.value = DetailUiState.Success(detail, similar = emptyList())

            val similar = runCatching { loadSimilar(provider, detail) }.getOrDefault(emptyList())
            if (similar.isNotEmpty()) {
                _uiState.value = DetailUiState.Success(detail, similar)
            }
        }
    }

    private suspend fun loadSimilar(
        provider: CatalogProvider,
        detail: Content
    ): List<Content> {
        val allItems = provider.getHomeSections()
            .toList()
            .flatten()
            .flatMap { it.items }
            .distinctBy { it.id }
            .filterNot { it.id == detail.id }

        val detailGenreIds = detail.genres.map { it.id }.toSet()
        val genreMatches = if (detailGenreIds.isEmpty()) {
            emptyList()
        } else {
            allItems.filter { item -> item.genres.any { it.id in detailGenreIds } }
        }

        return genreMatches.ifEmpty { allItems }.take(15)
    }
}
