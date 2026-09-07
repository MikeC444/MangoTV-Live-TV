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
    val streamUrl: String,
    // Playlist-provided on-screen channel number (M3U `tvg-chno`), when the
    // source bothers to include one -- falls back to the channel's position
    // in the guide otherwise. Nullable/defaulted so old cached JSON without
    // this field still decodes fine (see LiveTvRepository's disk cache).
    val channelNumber: Int? = null
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

/**
 * One cell in a channel's guide row: either a real EPG [programme] or a
 * synthetic filler (`programme = null`) for a stretch of time no programme
 * data is known for, spanning [startEpochMs, stopEpochMs).
 */
data class TimelineBlock(
    val programme: Programme?,
    val startEpochMs: Long,
    val stopEpochMs: Long
) {
    val durationMinutes: Long get() = ((stopEpochMs - startEpochMs) / 60_000L).coerceAtLeast(1L)
}

/**
 * Turns a channel's (possibly sparse, possibly totally empty) programme list
 * into a gap-free sequence of blocks covering exactly
 * [windowStart, windowEnd) -- every gap becomes a filler block, so a guide
 * row can lay these out as one plain sequential Row and still have each
 * block's width and position land on the correct wall-clock time, with no
 * manual per-block x-offset math needed. An empty [programmes] list (no EPG
 * configured, or nothing known for this channel) yields a single filler
 * spanning the whole window, which is what makes a channel with no EPG data
 * render as one plain "Live" block rather than an empty row.
 */
fun buildTimelineBlocks(programmes: List<Programme>, windowStart: Long, windowEnd: Long): List<TimelineBlock> {
    if (windowStart >= windowEnd) return emptyList()
    if (programmes.isEmpty()) return listOf(TimelineBlock(null, windowStart, windowEnd))

    val blocks = mutableListOf<TimelineBlock>()
    var cursor = windowStart
    for (programme in programmes.sortedBy { it.startEpochMs }) {
        val start = programme.startEpochMs.coerceAtLeast(windowStart)
        val stop = programme.stopEpochMs.coerceAtMost(windowEnd)
        if (start >= stop || stop <= cursor) continue
        if (start > cursor) blocks += TimelineBlock(null, cursor, start)
        blocks += TimelineBlock(programme, start, stop)
        cursor = stop
    }
    if (cursor < windowEnd) blocks += TimelineBlock(null, cursor, windowEnd)
    return blocks
}
