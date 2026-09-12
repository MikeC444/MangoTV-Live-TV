package com.mangotv.app.data.trailer

/**
 * A directly-playable YouTube media source resolved by [InAppYouTubeExtractor]
 * -- either one combined progressive URL (video+audio in one stream), or a
 * separate adaptive video/audio pair to be merged at playback time.
 */
data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null
)
