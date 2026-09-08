package com.mangotv.app.data.livetv

import com.mangotv.app.config.LiveTvConfig

/**
 * Minimal, tolerant M3U/M3U8 (extended) parser for IPTV playlists in the
 * iptv-org format: an `#EXTINF` line carrying `tvg-id`/`tvg-logo`/
 * `group-title`/(optionally) `tvg-country`/`tvg-chno` attributes plus a
 * trailing display name, followed by the stream URL on the next non-comment
 * line. Any other line (`#EXTM3U`, `#EXTGRP`, `#EXTVLCOPT`, blank lines, a
 * malformed `#EXTINF`) is tolerated rather than aborting the whole playlist
 * -- one bad entry in a playlist this app doesn't control must never take
 * every other channel down with it (see LiveTvRepository's "Invalid
 * playlist" handling).
 *
 * Country is usually NOT a `tvg-country` attribute in practice -- see
 * countryCodeFromTvgId's own doc for the fallback this relies on instead.
 */
object M3uParser {

    // Written with explicit escapes rather than a """raw string""" -- a
    // pattern ending in a quote immediately before the closing triple-quote
    // is ambiguous for Kotlin's lexer (it finds the first """ it can, which
    // isn't the one that was intended here).
    private val ATTRIBUTE_REGEX = Regex("(\\S+?)=\"([^\"]*)\"")

    fun parse(playlistText: String): List<Channel> {
        val channels = mutableListOf<Channel>()
        var pendingInfo: PendingInfo? = null

        for (line in playlistText.lineSequence()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            when {
                trimmed.startsWith("#EXTINF") -> {
                    pendingInfo = runCatching { parseExtInf(trimmed) }.getOrNull()
                }
                trimmed.startsWith("#") -> {
                    // Any other directive/comment — ignored.
                }
                else -> {
                    val info = pendingInfo
                    pendingInfo = null
                    if (info != null && LiveTvConfig.isGroupAllowed(info.groupTitle)) {
                        channels += Channel(
                            id = info.tvgId?.takeIf { it.isNotBlank() } ?: "${info.name}_${channels.size}",
                            name = info.name,
                            logoUrl = info.logo?.takeIf { it.isNotBlank() },
                            groupTitle = info.groupTitle?.takeIf { it.isNotBlank() },
                            country = info.country?.takeIf { it.isNotBlank() },
                            tvgId = info.tvgId?.takeIf { it.isNotBlank() },
                            streamUrl = trimmed,
                            channelNumber = info.channelNumber
                        )
                    }
                }
            }
        }
        return channels
    }

    private data class PendingInfo(
        val tvgId: String?,
        val logo: String?,
        val country: String?,
        val groupTitle: String?,
        val channelNumber: Int?,
        val name: String
    )

    private fun parseExtInf(line: String): PendingInfo {
        val commaIndex = line.lastIndexOf(',')
        val name = (if (commaIndex != -1) line.substring(commaIndex + 1) else "").trim().ifBlank { "Unknown Channel" }
        val attributesPart = if (commaIndex != -1) line.substring(0, commaIndex) else line
        val attributes = ATTRIBUTE_REGEX.findAll(attributesPart)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val tvgId = attributes["tvg-id"]
        return PendingInfo(
            tvgId = tvgId,
            logo = attributes["tvg-logo"],
            country = attributes["tvg-country"]?.takeIf { it.isNotBlank() } ?: countryCodeFromTvgId(tvgId),
            groupTitle = attributes["group-title"],
            channelNumber = attributes["tvg-chno"]?.toIntOrNull(),
            name = name
        )
    }

    // Most iptv-org playlists don't actually set a tvg-country attribute --
    // country instead lives as a suffix on tvg-id, e.g. "BBCOne.uk" or
    // "CNNInternational.us". This is a fallback, not a guess about every
    // possible source: an explicit tvg-country attribute (e.g. from a
    // licensed/private playlist someone points IPTV_PLAYLIST_URL at later)
    // always wins when present -- see parseExtInf above.
    private fun countryCodeFromTvgId(tvgId: String?): String? {
        if (tvgId.isNullOrBlank()) return null
        val suffix = tvgId.substringAfterLast('.', missingDelimiterValue = "")
        if (suffix.length !in 2..3 || !suffix.all { it.isLetter() }) return null
        return suffix.uppercase()
    }
}
