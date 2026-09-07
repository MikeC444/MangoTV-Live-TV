package com.mangotv.app.ui.sources

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Stream
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

sealed interface SourcesUiState {
    data object Loading : SourcesUiState
    data class Loaded(
        val content: Content,
        val streams: List<Stream>,
        val recommendedStreamId: String?,
        val season: Int?,
        val episode: Int?
    ) : SourcesUiState
    data class Error(val message: String) : SourcesUiState
}

class SourcesViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

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
    private val season: Int? = savedStateHandle.get<String>("season")?.toIntOrNull()?.takeIf { it >= 0 }
    private val episode: Int? = savedStateHandle.get<String>("episode")?.toIntOrNull()?.takeIf { it >= 0 }

    private val _uiState = MutableStateFlow<SourcesUiState>(SourcesUiState.Loading)
    val uiState: StateFlow<SourcesUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = SourcesUiState.Loading

            val providers = ProviderRegistry.activeProviders()
            val owningProvider = providers.find { it.id == providerId }

            // getDetails and every provider's getStreams are independent of
            // each other (streams only need the raw id/season/episode args,
            // not the resolved Content) — run them all concurrently rather
            // than waiting on getDetails first, so the screen isn't stuck on
            // the loading skeleton for the sum of both round trips.
            val (content, streams) = coroutineScope {
                val contentDeferred = async {
                    owningProvider?.let { runCatching { it.getDetails(contentType, contentId) }.getOrNull() }
                }
                // Different addons can each offer different quality options
                // for the same title, so every active provider is queried
                // and merged — unlike getDetails above, which only makes
                // sense against the one addon that owns this content.
                val streamDeferreds = providers.map { provider ->
                    async { runCatching { provider.getStreams(contentType, contentId, season, episode) }.getOrDefault(emptyList()) }
                }
                contentDeferred.await() to streamDeferreds.awaitAll().flatten()
            }

            if (content == null) {
                _uiState.value = SourcesUiState.Error("Couldn't load details for this title.")
                return@launch
            }

            val recommendedStreamId = streams
                .sortedWith(compareBy<Stream> { it.resolutionTier.ordinal }.thenByDescending { it.seeders ?: -1 })
                .firstOrNull()
                ?.id

            _uiState.value = SourcesUiState.Loaded(content, streams, recommendedStreamId, season, episode)
        }
    }
}
