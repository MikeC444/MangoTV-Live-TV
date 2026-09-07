package com.mangotv.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Tracks
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.data.provider.ProviderRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder

/**
 * Re-derives everything from route args rather than taking anything
 * in-memory from the Sources screen — same convention SourcesViewModel and
 * DetailViewModel already use, and it survives process death since a
 * Stream's id is deterministic across an identical getStreams() re-fetch.
 *
 * Only consumes/exposes state — never touches a live ExoPlayer. The
 * Composable owns the player instance and issues commands (play/pause/
 * seek/track selection) directly; this ViewModel only reacts to
 * Player.Listener callbacks forwarded into [onPlaybackPhaseChanged].
 */
class PlayerViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val preferencesRepository = (application as MangoTvApplication).container.playerPreferencesRepository

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
    private val episodeNumber: Int? = savedStateHandle.get<String>("episode")?.toIntOrNull()?.takeIf { it >= 0 }
    private val streamId: String =
        URLDecoder.decode(savedStateHandle.get<String>("streamId").orEmpty(), "UTF-8")

    private val _uiState = MutableStateFlow<PlayerScreenUiState>(PlayerScreenUiState.Loading)
    val uiState: StateFlow<PlayerScreenUiState> = _uiState.asStateFlow()

    private val _playbackPhase = MutableStateFlow<PlaybackPhase>(PlaybackPhase.Loading)
    val playbackPhase: StateFlow<PlaybackPhase> = _playbackPhase.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<AudioTrackOption>>(emptyList())
    val audioTracks: StateFlow<List<AudioTrackOption>> = _audioTracks.asStateFlow()

    private val _subtitleTracks = MutableStateFlow<List<SubtitleTrackOption>>(emptyList())
    val subtitleTracks: StateFlow<List<SubtitleTrackOption>> = _subtitleTracks.asStateFlow()

    private val _qualityOptions = MutableStateFlow<List<QualityOption>>(emptyList())
    val qualityOptions: StateFlow<List<QualityOption>> = _qualityOptions.asStateFlow()

    val preferences: StateFlow<PlayerPreferences> = preferencesRepository.preferences

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = PlayerScreenUiState.Loading
            _playbackPhase.value = PlaybackPhase.Loading

            val providers = ProviderRegistry.activeProviders()
            val owningProvider = providers.find { it.id == providerId }
            val content = owningProvider?.let { runCatching { it.getDetails(contentType, contentId) }.getOrNull() }
            if (content == null) {
                _uiState.value = PlayerScreenUiState.Error("Couldn't load details for this title.")
                return@launch
            }

            // Same "query every active provider and merge" rule as the
            // Sources screen — run in parallel since the player route pays
            // this cost a second time on top of the one Sources already paid.
            val streams = coroutineScope {
                providers.map { provider ->
                    async { runCatching { provider.getStreams(contentType, contentId, season, episodeNumber) }.getOrDefault(emptyList()) }
                }.awaitAll()
            }.flatten()

            val stream = streams.find { it.id == streamId }
            if (stream == null) {
                _uiState.value = PlayerScreenUiState.Error("This source is no longer available.")
                return@launch
            }

            val episode: Episode? = if (season != null && episodeNumber != null) {
                content.seasons.find { it.seasonNumber == season }
                    ?.episodes?.find { it.episodeNumber == episodeNumber }
            } else {
                null
            }

            _uiState.value = PlayerScreenUiState.Ready(content, episode, stream)
        }
    }

    fun onPlaybackPhaseChanged(phase: PlaybackPhase) {
        _playbackPhase.value = phase
    }

    fun onTracksChanged(tracks: Tracks) {
        _audioTracks.value = tracks.toAudioTrackOptions()
        _subtitleTracks.value = tracks.toSubtitleTrackOptions()
        _qualityOptions.value = tracks.toQualityOptions()
    }

    fun setAutoplayNextEpisode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setAutoplayNextEpisode(enabled) }
    }

    fun setSkipIntroEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSkipIntroEnabled(enabled) }
    }
}
