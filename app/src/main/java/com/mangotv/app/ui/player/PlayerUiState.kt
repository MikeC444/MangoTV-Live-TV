package com.mangotv.app.ui.player

import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Stream

/** Outer state: do we even have a resolved, playable stream yet. */
sealed interface PlayerScreenUiState {
    data object Loading : PlayerScreenUiState
    data class Ready(val content: Content, val episode: Episode?, val stream: Stream) : PlayerScreenUiState
    data class Error(val message: String) : PlayerScreenUiState
}

/**
 * Inner state: the actual ExoPlayer engine's phase, derived from
 * Player.Listener callbacks. Kept separate from [PlayerScreenUiState] so
 * "we don't have a stream yet" and "we have a stream but it's buffering"
 * are never conflated.
 */
sealed interface PlaybackPhase {
    data object Loading : PlaybackPhase
    data object Playing : PlaybackPhase
    data object Paused : PlaybackPhase
    data object Buffering : PlaybackPhase
    data object Ended : PlaybackPhase
    data class Error(val type: PlaybackErrorType, val message: String) : PlaybackPhase
}

enum class PlaybackErrorType { UNSUPPORTED_SOURCE, TORRENT_UNSUPPORTED, NETWORK, UNKNOWN }

/**
 * Which cluster of the bottom control row currently holds D-pad focus.
 * Drives the zone-gated LEFT/RIGHT behavior: seeking is only intercepted
 * while [NONE] (nothing focused yet/controls hidden) or [TIMELINE] — once
 * focus is on an actual button, LEFT/RIGHT navigates between buttons
 * instead of seeking.
 */
enum class PlayerFocusZone { NONE, TOP_BAR, TRANSPORT, TIMELINE, ICON_ROW }

/**
 * Overlay panels shown on top of the controls. Modeled as a stack (see
 * PlayerScreen's overlayStack) rather than a single nullable value, since
 * Subtitles/Audio/Quality/SourceInfo can each be reached either directly
 * from their own bottom-row icon (dismissing straight back to the plain
 * controls) or via Settings (dismissing back to Settings instead).
 */
enum class PlayerOverlay { SUBTITLES, AUDIO, QUALITY, SETTINGS, SOURCE_INFO, PLAYBACK_SPEED, ADVANCED }
