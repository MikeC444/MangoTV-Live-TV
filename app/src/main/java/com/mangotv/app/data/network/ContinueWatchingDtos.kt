package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/watch-progress and /user/continue-watching
// (see server/src/schemas/watchProgress.ts and src/routes/history.ts).
// contentType travels as the same "MOVIE"/"TV_SHOW" strings used
// throughout the API (see WatchlistDtos' own note on this). The backend
// also exposes GET /user/history (the durable per-episode log) -- no
// client here yet, since the app has no History browse screen for it to
// feed; only Continue Watching (the per-title resumable pointer) is
// consumed on the Fire TV side.

@Serializable
data class WatchProgressRequest(
    val providerId: String,
    val contentId: String,
    val contentType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val watchedAt: String
)

@Serializable
data class ContinueWatchingItemDto(
    val providerId: String,
    val contentId: String,
    val contentType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: String,
    /** Non-null means this title is no longer resumable -- only meaningful on the watch-progress response; GET /user/continue-watching never returns a deleted item. */
    val deletedAt: String? = null
)

@Serializable
data class WatchProgressResponse(
    val historyEntry: WatchHistoryEntryDto,
    val continueWatching: ContinueWatchingItemDto? = null
)

@Serializable
data class WatchHistoryEntryDto(
    val providerId: String,
    val contentId: String,
    val contentType: String,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val title: String,
    val posterUrl: String? = null,
    val positionMs: Long,
    val durationMs: Long,
    val completed: Boolean,
    val watchedAt: String
)

@Serializable
data class ContinueWatchingListResponse(val items: List<ContinueWatchingItemDto>)
