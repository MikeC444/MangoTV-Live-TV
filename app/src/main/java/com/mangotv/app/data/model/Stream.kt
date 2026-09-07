package com.mangotv.app.data.model

/**
 * A single playable source for a title/episode, normalized from whatever an
 * addon's `/stream` response provides. Quality/size/seeders are best-effort
 * parsed from free-text fields (see StremioMapper.toStream) since the
 * Stremio protocol doesn't guarantee structured metadata for them — any
 * field that couldn't be parsed is simply null rather than blocking the row.
 */

enum class ResolutionTier { UHD_4K, FHD_1080P, HD_720P, OTHER }

enum class QualityTier(val label: String) {
    VERY_HIGH("Very High Quality"),
    HIGH("High Quality"),
    GOOD("Good Quality"),
    LOW("Low Quality")
}

data class Stream(
    val id: String,
    val providerId: String,
    val providerLabel: String,
    val resolutionTier: ResolutionTier,
    val qualityBadge: String,
    val releaseTitle: String,
    val sourceTag: String? = null,
    val codec: String? = null,
    val audioTag: String? = null,
    val sizeLabel: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val seedersLabel: String? = null,
    val qualityTier: QualityTier? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val ytId: String? = null
)
