package com.mangotv.app.data.addon

import kotlinx.serialization.Serializable

/**
 * Wire-format models for the Stremio addon protocol's catalog/meta/stream
 * responses. These are intentionally kept separate from Mango TV's internal
 * [com.mangotv.app.data.model.Content] model — [StremioMetaPreview.toContent]
 * is the single place that normalizes provider data into our own shape.
 */

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMetaPreview> = emptyList()
)

@Serializable
data class StremioMetaPreview(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val runtime: String? = null
)

@Serializable
data class StremioStreamResponse(
    val streams: List<StremioStream> = emptyList()
)

@Serializable
data class StremioMetaResponse(
    val meta: StremioMeta? = null
)

/**
 * The richer object returned by an addon's `/meta/{type}/{id}.json` endpoint,
 * used for detail screens. `cast`/`director` are plain name strings per the
 * base Stremio protocol (github.com/Stremio/stremio-addon-sdk) — addons
 * don't generally provide cast photos or character names, so the detail
 * screen renders whatever subset of this an addon actually fills in rather
 * than assuming all of it is present.
 */
@Serializable
data class StremioMeta(
    val id: String,
    val type: String,
    val name: String,
    val poster: String? = null,
    val background: String? = null,
    val logo: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val runtime: String? = null,
    val director: List<String>? = null,
    val cast: List<String>? = null,
    val videos: List<StremioVideo>? = null
)

/**
 * One episode within a series meta response. Addons are inconsistent about
 * whether they call the episode's text fields "title"/"name" or
 * "overview"/"description" — both are accepted and coalesced when mapping to
 * [com.mangotv.app.data.model.Episode] rather than betting on one spelling.
 */
@Serializable
data class StremioVideo(
    val id: String,
    val title: String? = null,
    val name: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val overview: String? = null,
    val description: String? = null,
    val thumbnail: String? = null
)

@Serializable
data class StremioStream(
    val url: String? = null,
    val ytId: String? = null,
    val infoHash: String? = null,
    val title: String? = null,
    val name: String? = null,
    val description: String? = null
)
