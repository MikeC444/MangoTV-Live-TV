package com.mangotv.app.data.model

import kotlinx.serialization.Serializable

/**
 * Cross-content player preferences — one record per user, unlike playback
 * progress which is one record per title. Skip-intro has no visible effect
 * yet (no addon/protocol supplies intro/recap timestamps), but the toggle
 * is architected now so it starts working the moment that data exists.
 */
@Serializable
data class PlayerPreferences(
    val autoplayNextEpisode: Boolean = true,
    val skipIntroEnabled: Boolean = true
)
