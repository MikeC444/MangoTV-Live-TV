package com.mangotv.app.data.model

import kotlinx.serialization.Serializable

/**
 * Cross-content player preferences — one record per user, unlike playback
 * progress which is one record per title. Skip-intro has no visible effect
 * yet (no addon/protocol supplies intro/recap timestamps), but the toggle
 * is architected now so it starts working the moment that data exists.
 *
 * subtitlesEnabled/defaultSubtitleLanguage are defaults applied when a new
 * playback session starts (see PlayerEngine.buildExoPlayer) — the
 * in-player Subtitles menu can still override either one for that one
 * session, exactly like autoplay/skip-intro's toggles don't stop the user
 * from acting differently in the moment. defaultSubtitleLanguage is an
 * ISO 639-1 code (e.g. "en"), or null for "no preference" (defer to
 * whatever the stream/system default would otherwise pick) — see
 * SubtitleSettingsScreen's fixed language list.
 */
@Serializable
data class PlayerPreferences(
    val autoplayNextEpisode: Boolean = true,
    val skipIntroEnabled: Boolean = true,
    val subtitlesEnabled: Boolean = true,
    val defaultSubtitleLanguage: String? = null
)
