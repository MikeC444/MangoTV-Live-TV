package com.mangotv.app.data.model

import kotlinx.serialization.Serializable

/**
 * Centralized content metadata model. Every screen in the app reads from
 * this shape regardless of which provider/addon originally supplied it —
 * the UI never has provider-specific knowledge.
 */

// Serializable so My List's persisted SavedListItem can store it directly --
// the rest of Content isn't (its full graph would need annotating too), so
// My List keeps its own lightweight DTO rather than persisting Content itself.
@Serializable
enum class ContentType {
    MOVIE,
    TV_SHOW
}

data class Genre(
    val id: String,
    val name: String
)

data class CastMember(
    val name: String,
    val role: String? = null,
    val photoUrl: String? = null
)

data class WatchProgress(
    val positionMs: Long,
    val durationMs: Long,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null
) {
    val fraction: Float
        get() = if (durationMs <= 0) 0f else (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
}

data class Episode(
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
    val runtimeMinutes: Int?,
    val watchProgress: WatchProgress? = null
)

data class Season(
    val seasonNumber: Int,
    val name: String,
    val episodes: List<Episode>
)

data class Content(
    val id: String,
    val type: ContentType,
    val title: String,
    val description: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String? = null,
    val year: Int? = null,
    val ageRating: String? = null,
    val runtimeMinutes: Int? = null,
    val rating: Double? = null,
    val genres: List<Genre> = emptyList(),
    val cast: List<CastMember> = emptyList(),
    val director: String? = null,
    val providerId: String? = null,
    val watchProgress: WatchProgress? = null,
    val seasons: List<Season> = emptyList()
)

enum class RowStyle {
    STANDARD,
    CONTINUE_WATCHING
}

data class HomeSection(
    val id: String,
    val title: String,
    val items: List<Content>,
    val style: RowStyle = RowStyle.STANDARD
)
