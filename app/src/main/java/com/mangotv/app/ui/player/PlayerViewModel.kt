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
    private val continueWatchingRepository = (application as MangoTvApplication).container.continueWatchingRepository
    private val continueWatchingSyncRepository = (application as MangoTvApplication).container.continueWatchingSyncRepository
    private val lastSourceRepository = (application as MangoTvApplication).container.lastSourceRepository

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

    /**
     * This exact title/episode's stored resume point, if any -- a
     * synchronous read of the local Continue Watching cache (Milestone 8),
     * looked up once when building the Ready state so PlaybackContent can
     * seek to it on first prepare. Guards on season/episode matching
     * too: a stored entry for a different episode of the same show is not
     * a valid resume point for *this* stream.
     */
    fun resumePositionMs(): Long? =
        continueWatchingRepository.findResumePoint(providerId, contentId, contentType)
            ?.takeIf { it.seasonNumber == season && it.episodeNumber == episodeNumber }
            ?.positionMs

    /**
     * Called by PlaybackContent at the player's own "sensible update
     * strategy" trigger points (periodic while playing, on pause, on
     * stop/dispose, on completion) -- never on every position tick, per
     * the milestone's explicit "don't flood the network" requirement.
     * Deliberately not suspend: the dispose-time call happens from a
     * plain onDispose{} lambda, not a coroutine — see
     * ContinueWatchingSyncRepository.reportProgress's own kdoc for why
     * this whole chain stays non-suspend down to the actual network call.
     */
    fun reportProgress(positionMs: Long, durationMs: Long, completed: Boolean) {
        if (durationMs <= 0) return
        // Ignore a barely-started report: resuming from a few seconds in
        // isn't useful, and without this guard a Continue Watching entry
        // would appear the instant playback merely starts, before the
        // user has actually watched anything.
        if (!completed && positionMs < MIN_REPORTABLE_POSITION_MS) return

        val state = uiState.value as? PlayerScreenUiState.Ready ?: return

        // Same gating as the Continue Watching entry this report is about
        // to create (not completed, past the "genuinely started watching"
        // threshold above) -- remembers the source that actually got this
        // title into Continue Watching, so SourcesViewModel can skip
        // straight back to it next time instead of asking the user to pick
        // again. See LastSourceRepository's own doc for why this is a
        // separate, local-only store rather than a field synced with the
        // rest of this report.
        if (!completed) {
            viewModelScope.launch {
                lastSourceRepository.setLastStreamId(providerId, contentId, contentType, season, episodeNumber, streamId)
            }
        }

        continueWatchingSyncRepository.reportProgress(
            providerId = providerId,
            contentId = contentId,
            contentType = contentType,
            seasonNumber = season,
            episodeNumber = episodeNumber,
            episodeTitle = state.episode?.title,
            title = state.content.title,
            posterUrl = state.content.posterUrl,
            backdropUrl = state.content.backdropUrl,
            positionMs = positionMs,
            durationMs = durationMs,
            completed = completed
        )
    }

    companion object {
        private const val MIN_REPORTABLE_POSITION_MS = 10_000L
    }
}
