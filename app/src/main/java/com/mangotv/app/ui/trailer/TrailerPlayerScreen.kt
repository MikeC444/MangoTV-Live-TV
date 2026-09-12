package com.mangotv.app.ui.trailer

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.ui.AspectRatioFrameLayout
import com.mangotv.app.data.trailer.InAppYouTubeExtractor
import com.mangotv.app.data.trailer.TrailerPlaybackSource
import com.mangotv.app.data.trailer.YoutubeChunkedDataSourceFactory
import com.mangotv.app.ui.player.PlayerSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.CancellationException

private const val TAG = "TrailerPlayerScreen"

/**
 * Plays a trailer inside the app, reached from Detail's Trailer button.
 *
 * This used to embed YouTube's own web player in a WebView -- the only
 * approach that stays within YouTube's Terms of Service. That kept hitting
 * "video player configuration error" (YouTube IFrame API onError 152) for
 * every trailer tried, tracing back to how YouTube's embedded player
 * validates the referrer/origin of whatever page is embedding it; multiple
 * rounds of fixes for that (custom-view handling, dropping/re-adding
 * enablejsapi, forcing a referrer policy) never resolved it.
 *
 * This replaces that with [InAppYouTubeExtractor]: it resolves the YouTube
 * video id directly to a playable media URL via YouTube's internal player
 * API (impersonating one of YouTube's own official app clients) and hands
 * that straight to MangoTV's own ExoPlayer, the same way every other video
 * in this app plays -- no WebView, no embedded player, no referrer/origin
 * validation to fail. See [InAppYouTubeExtractor]'s own kdoc for the real
 * trade this carries: it's outside YouTube's Terms of Service and depends
 * on YouTube's internal (undocumented, occasionally-changing) API staying
 * compatible, unlike the embed approach it replaced.
 *
 * The global BackHandler in MangoNavHost already pops this route like any
 * other on BACK, so nothing extra is wired here for that.
 */
@OptIn(UnstableApi::class)
@Composable
fun TrailerPlayerScreen(videoId: String) {
    val context = LocalContext.current
    val extractor = remember { InAppYouTubeExtractor() }

    var playbackSource by remember(videoId) { mutableStateOf<TrailerPlaybackSource?>(null) }
    var failed by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        val source = try {
            extractor.extractPlaybackSource("https://www.youtube.com/watch?v=$videoId")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Extraction threw for $videoId: ${e.message}")
            null
        }
        if (source != null) {
            playbackSource = source
        } else {
            failed = true
        }
    }

    val exoPlayer = remember(playbackSource) {
        playbackSource?.let { source -> buildTrailerExoPlayer(context, source) }
    }

    DisposableEffect(exoPlayer) {
        val player = exoPlayer
        val listener = player?.let {
            object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    Log.w(TAG, "Trailer playback error: ${error.message}")
                    failed = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    Log.w(TAG, "playbackState=${playbackStateName(playbackState)}")
                }

                override fun onRenderedFirstFrame() {
                    Log.w(TAG, "onRenderedFirstFrame")
                }
            }
        }
        listener?.let { player?.addListener(it) }
        onDispose {
            listener?.let { player?.removeListener(it) }
            player?.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (exoPlayer != null) {
            PlayerSurface(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            )
        }

        when {
            failed -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Trailer unavailable",
                        color = TextPrimary,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "This trailer couldn't be played right now.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            exoPlayer == null -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MangoAmber
                )
            }
        }
    }
}

@OptIn(UnstableApi::class)
private fun buildTrailerExoPlayer(context: Context, source: TrailerPlaybackSource): ExoPlayer {
    val player = ExoPlayer.Builder(context).build()
    val audioUrl = source.audioUrl
    if (!audioUrl.isNullOrBlank()) {
        // Separate adaptive video/audio streams -- both googlevideo.com URLs
        // that YouTube throttles unless fetched in range-limited chunks, and
        // merged into one playable timeline. See YoutubeChunkedDataSourceFactory's
        // own kdoc for why the chunking is needed at all.
        val mediaSourceFactory = DefaultMediaSourceFactory(YoutubeChunkedDataSourceFactory())
        val videoSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(source.videoUrl))
        val audioSource = mediaSourceFactory.createMediaSource(MediaItem.fromUri(audioUrl))
        player.setMediaSource(MergingMediaSource(videoSource, audioSource))
    } else {
        // Either an HLS manifest (already segmented, so no throttling risk
        // to work around) or a combined progressive video+audio URL --
        // either way a single MediaItem is enough.
        player.setMediaItem(MediaItem.fromUri(source.videoUrl))
    }
    player.prepare()
    player.playWhenReady = true
    return player
}

private fun playbackStateName(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> "UNKNOWN($state)"
}
