package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

/** Wire-format response for GET /user/release-date (see server/src/routes/releaseDates.ts). Null means TMDB has no match for this title, or the lookup isn't configured -- both collapse to the same thing from here. */
@Serializable
data class ReleaseDateResponse(val releaseDate: String? = null)
