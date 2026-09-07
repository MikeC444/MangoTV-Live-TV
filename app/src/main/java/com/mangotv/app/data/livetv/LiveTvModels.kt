package com.mangotv.app.data.livetv

import kotlinx.serialization.Serializable

/**
 * A single IPTV channel normalized from whatever the configured M3U
 * playlist provides (see LiveTvConfig.iptvPlaylistUrl / M3uParser).
 * [Serializable] so the parsed catalog can be cached to disk as-is rather
 * than re-parsed on every app launch (see LiveTvRepository).
 */
@Serializable
data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String?,
    val groupTitle: String?,
    val country: String?,
    val tvgId: String?,
    val streamUrl: String
)

data class ChannelSection(
    val id: String,
    val title: String,
    val channels: List<Channel>
)

/** One EPG guide entry for a channel, parsed from XMLTV — see XmlTvEpgParser. */
data class Programme(
    val title: String,
    val startEpochMs: Long,
    val stopEpochMs: Long
)

data class NowNext(val now: Programme?, val next: Programme?)
