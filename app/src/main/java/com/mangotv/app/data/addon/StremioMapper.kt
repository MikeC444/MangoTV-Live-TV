package com.mangotv.app.data.addon

import com.mangotv.app.data.model.CastMember
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.model.QualityTier
import com.mangotv.app.data.model.ResolutionTier
import com.mangotv.app.data.model.Season
import com.mangotv.app.data.model.Stream

fun StremioMetaPreview.toContent(providerId: String): Content {
    val year = releaseInfo
        ?.takeWhile { it.isDigit() }
        ?.takeIf { it.length == 4 }
        ?.toIntOrNull()

    val runtimeMinutes = runtime
        ?.filter { it.isDigit() }
        ?.toIntOrNull()

    return Content(
        id = id,
        type = if (type == "series") ContentType.TV_SHOW else ContentType.MOVIE,
        title = name,
        description = description.orEmpty(),
        posterUrl = poster,
        backdropUrl = background ?: poster,
        logoUrl = logo,
        year = year,
        runtimeMinutes = runtimeMinutes,
        rating = imdbRating?.toDoubleOrNull(),
        genres = genres.orEmpty().map { Genre(id = it.lowercase(), name = it) },
        providerId = providerId
    )
}

fun StremioMeta.toContent(providerId: String): Content {
    val year = releaseInfo
        ?.takeWhile { it.isDigit() }
        ?.takeIf { it.length == 4 }
        ?.toIntOrNull()

    val runtimeMinutes = runtime
        ?.filter { it.isDigit() }
        ?.toIntOrNull()

    return Content(
        id = id,
        type = if (type == "series") ContentType.TV_SHOW else ContentType.MOVIE,
        title = name,
        description = description.orEmpty(),
        posterUrl = poster,
        backdropUrl = background ?: poster,
        logoUrl = logo,
        year = year,
        runtimeMinutes = runtimeMinutes,
        rating = imdbRating?.toDoubleOrNull(),
        genres = genres.orEmpty().map { Genre(id = it.lowercase(), name = it) },
        cast = cast.orEmpty().map { CastMember(name = it) },
        director = director?.takeIf { it.isNotEmpty() }?.joinToString(", "),
        providerId = providerId,
        seasons = videos.orEmpty().toSeasons()
    )
}

/**
 * Groups a series meta's flat video list into per-season, episode-ordered
 * [Season]s. Season 0 ("Specials") is dropped entirely — this app only
 * shows proper numbered seasons.
 */
private fun List<StremioVideo>.toSeasons(): List<Season> =
    filter { it.season != null && it.season != 0 && it.episode != null }
        .groupBy { it.season!! }
        .toSortedMap()
        .map { (seasonNumber, videos) ->
            Season(
                seasonNumber = seasonNumber,
                name = "Season $seasonNumber",
                episodes = videos.sortedBy { it.episode }.map { it.toEpisode() }
            )
        }

private fun StremioVideo.toEpisode(): Episode = Episode(
    id = id,
    seasonNumber = season ?: 0,
    episodeNumber = episode ?: 0,
    title = title ?: name ?: "Episode ${episode ?: 0}",
    description = overview ?: description.orEmpty(),
    thumbnailUrl = thumbnail,
    runtimeMinutes = null
)

// Real Stremio addons (Torrentio-style) embed quality/size/seeders as free
// text in the stream's title/name rather than structured fields, e.g.
// "Movie.2024.2160p.WEB-DL.DDP5.1.Atmos.x265-GROUP\n👤 1200 💾 23.6 GB". Every
// pattern below is independently optional — a miss just leaves that field
// null on the resulting Stream rather than failing the whole row.
private val RESOLUTION_4K = Regex("2160p|4K|UHD", RegexOption.IGNORE_CASE)
private val RESOLUTION_1080P = Regex("1080p", RegexOption.IGNORE_CASE)
private val RESOLUTION_720P = Regex("720p", RegexOption.IGNORE_CASE)
private val RESOLUTION_GENERIC = Regex("(\\d{3,4})p", RegexOption.IGNORE_CASE)
private val SOURCE_TAG = Regex("BluRay|BDRip|BRRip|WEB-?DL|WEBRip|HDTV|DVDRip|REMUX|CAM|TS", RegexOption.IGNORE_CASE)
private val CODEC_HEVC = Regex("x265|HEVC|H\\.?265", RegexOption.IGNORE_CASE)
private val CODEC_H264 = Regex("x264|H\\.?264|AVC", RegexOption.IGNORE_CASE)
private val CODEC_AV1 = Regex("AV1", RegexOption.IGNORE_CASE)
private val AUDIO_TAG = Regex(
    "DDP?5\\.1(\\.Atmos)?|DD5\\.1|DTS-?HD(\\.MA)?|DTS|TrueHD(\\.Atmos)?|AAC(2\\.0|5\\.1)?|Atmos|EAC3|AC3",
    RegexOption.IGNORE_CASE
)
private val SIZE_PATTERN = Regex("(\\d+(?:\\.\\d+)?)\\s?(GB|MB)", RegexOption.IGNORE_CASE)
private val SEEDERS_EMOJI = Regex("👤\\s?(\\d+)")
private val SEEDERS_WORD = Regex("(\\d+)\\s*(?:seeds?|peers?)\\b", RegexOption.IGNORE_CASE)

private fun formatSeederCount(count: Int): String =
    if (count >= 1000) "%.1fK".format(count / 1000.0) else count.toString()

fun StremioStream.toStream(providerId: String, providerLabel: String): Stream {
    val haystack = listOfNotNull(title, name, description).joinToString("\n")

    val (resolutionTier, qualityBadge) = when {
        RESOLUTION_4K.containsMatchIn(haystack) -> ResolutionTier.UHD_4K to "4K"
        RESOLUTION_1080P.containsMatchIn(haystack) -> ResolutionTier.FHD_1080P to "1080p"
        RESOLUTION_720P.containsMatchIn(haystack) -> ResolutionTier.HD_720P to "720p"
        else -> {
            val generic = RESOLUTION_GENERIC.find(haystack)?.groupValues?.get(1)
            ResolutionTier.OTHER to (generic?.let { "${it}p" } ?: "SD")
        }
    }

    val sourceTag = SOURCE_TAG.find(haystack)?.value
    val codec = when {
        CODEC_HEVC.containsMatchIn(haystack) -> "HEVC"
        CODEC_H264.containsMatchIn(haystack) -> "H.264"
        CODEC_AV1.containsMatchIn(haystack) -> "AV1"
        else -> null
    }
    val audioTag = AUDIO_TAG.find(haystack)?.value

    val sizeMatch = SIZE_PATTERN.find(haystack)
    val sizeLabel = sizeMatch?.value
    val sizeBytes = sizeMatch?.let {
        val amount = it.groupValues[1].toDoubleOrNull() ?: return@let null
        val unit = it.groupValues[2]
        val multiplier = if (unit.equals("GB", ignoreCase = true)) 1_000_000_000L else 1_000_000L
        (amount * multiplier).toLong()
    }

    val seeders = (SEEDERS_EMOJI.find(haystack) ?: SEEDERS_WORD.find(haystack))
        ?.groupValues?.get(1)?.toIntOrNull()
    val seedersLabel = seeders?.let { formatSeederCount(it) }
    val qualityTier = seeders?.let {
        when {
            it >= 500 -> QualityTier.VERY_HIGH
            it >= 100 -> QualityTier.HIGH
            it >= 20 -> QualityTier.GOOD
            else -> QualityTier.LOW
        }
    }

    val releaseTitle = title?.lineSequence()?.firstOrNull { it.isNotBlank() }
        ?: name?.takeIf { it.isNotBlank() }
        ?: "Unknown Source"

    val idSeed = infoHash ?: url ?: (title.orEmpty() + name.orEmpty())

    // Some addons prefix `name` with a bracketed tag (e.g. "[TB+] Torrentio")
    // that reads as noise in a compact provider label — strip it for display.
    val cleanedName = name?.replace(Regex("^\\[.*?\\]\\s*"), "")?.takeIf { it.isNotBlank() }

    return Stream(
        id = "$providerId:${idSeed.hashCode()}",
        providerId = providerId,
        providerLabel = cleanedName ?: providerLabel,
        resolutionTier = resolutionTier,
        qualityBadge = qualityBadge,
        releaseTitle = releaseTitle,
        sourceTag = sourceTag,
        codec = codec,
        audioTag = audioTag,
        sizeLabel = sizeLabel,
        sizeBytes = sizeBytes,
        seeders = seeders,
        seedersLabel = seedersLabel,
        qualityTier = qualityTier,
        url = url,
        infoHash = infoHash,
        ytId = ytId
    )
}
