package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/watchlist (see server/src/schemas/watchlist.ts
// and src/routes/watchlist.ts) -- one item-level record per saved title,
// not a whole-list blob. contentType's string values ("MOVIE"/"TV_SHOW")
// match com.mangotv.app.data.model.ContentType's enum names exactly, which
// is also what the Postgres content_type enum uses (see
// migrations/0001_extensions_and_enums.sql) -- kept as a plain String here
// rather than ContentType itself so a future addition to the local enum
// can't silently break deserialization of a server response.

@Serializable
data class WatchlistItemDto(
    val providerId: String,
    val contentId: String,
    val contentType: String,
    val title: String,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val year: Int? = null,
    val rating: Double? = null,
    val updatedAt: String,
    /** Non-null means this slot is currently removed -- only meaningful on a POST/DELETE response (a later write elsewhere raced this one); GET never returns a deleted item at all. */
    val deletedAt: String? = null
)

@Serializable
data class WatchlistListResponse(val items: List<WatchlistItemDto>)
