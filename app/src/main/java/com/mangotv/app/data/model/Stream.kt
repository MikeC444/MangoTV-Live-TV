package com.mangotv.app.data.model

/**
 * A single playable source for a title/episode, normalized from whatever an
 * addon's `/stream` response provides. Quality/size/seeders are best-effort
 * parsed from free-text fields (see StremioMapper.toStream) since the
 * Stremio protocol doesn't guarantee structured metadata for them — any
 * field that couldn't be parsed is simply null rather than blocking the row.
 */

enum class ResolutionTier { UHD_4K, FHD_1080P, HD_720P, OTHER }

// Deliberately not named "quality" anything -- that word is already owned by
// ResolutionTier/qualityBadge (4K/1080p/etc, the actual video quality). This
// is purely a seeder-count health reading of a torrent source: a 4K remux
// with 3 seeders is still a 4K remux, just a poorly-seeded one, so labeling
// it "Low Quality" right next to a "4K" badge read as a contradiction (see
// SourceRow, which shows this beside the resolution badge, not in place of
// it).
enum class SourceHealth(val label: String) {
    VERY_HIGH("Excellent Health"),
    HIGH("Good Health"),
    GOOD("Fair Health"),
    LOW("Poor Health")
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
    val sourceHealth: SourceHealth? = null,
    val url: String? = null,
    val infoHash: String? = null,
    val ytId: String? = null
)
