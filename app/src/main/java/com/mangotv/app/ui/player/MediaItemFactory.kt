package com.mangotv.app.ui.player

import androidx.media3.common.MediaItem
import com.mangotv.app.data.model.Stream

/**
 * Streams backed only by a BitTorrent infoHash (no direct url) can't be
 * played — this app has no P2P/BitTorrent engine, and building one is out
 * of scope — so this returns null for them rather than handing ExoPlayer a
 * URI it can never resolve. DefaultMediaSourceFactory sniffs HLS/DASH/
 * progressive playback from the URL itself, so no manual container
 * selection is needed here.
 */
fun Stream.toMediaItemOrNull(): MediaItem? {
    val streamUrl = url ?: return null
    return MediaItem.Builder().setUri(streamUrl).build()
}
