package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

/** Wire-format response for GET /user/trailer (see server/src/routes/trailers.ts). Null means no trailer was found -- not configured, no matching title, or nothing YouTube-hosted, all collapse to the same thing from here. */
@Serializable
data class TrailerResponse(val youtubeVideoId: String? = null)
