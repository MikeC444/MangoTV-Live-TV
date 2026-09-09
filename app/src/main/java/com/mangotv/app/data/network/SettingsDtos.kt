package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/settings (see server/src/schemas/settings.ts
// and src/routes/settings.ts) -- the cloud mirror of HomeRowPreferences +
// PlayerPreferences, combined into the one request/response shape the
// backend's single user_settings row uses.

@Serializable
data class SettingsRequest(
    val homeRowOrder: List<String>,
    val hiddenRowIds: List<String>,
    val autoplayNextEpisode: Boolean,
    val skipIntroEnabled: Boolean,
    val updatedAt: String
)

@Serializable
data class SettingsResponse(
    val homeRowOrder: List<String>,
    val hiddenRowIds: List<String>,
    val autoplayNextEpisode: Boolean,
    val skipIntroEnabled: Boolean,
    /** null only for an account that has never pushed settings from any device. */
    val updatedAt: String? = null
)
