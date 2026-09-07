package com.mangotv.app.ui.player

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import okhttp3.OkHttpClient

/**
 * Builds the ExoPlayer instance for one playback session. Uses OkHttp (via
 * media3-datasource-okhttp) instead of Media3's default HTTP stack purely
 * for consistency with the rest of the app's networking, which is all
 * OkHttp-based (see StremioAddonClient).
 */
@OptIn(UnstableApi::class)
fun buildExoPlayer(context: Context): ExoPlayer {
    val httpDataSourceFactory = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
    val dataSourceFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
    return ExoPlayer.Builder(context)
        .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
        .build()
}

/**
 * Translates raw ExoPlayer callbacks into this app's own [PlaybackPhase]
 * model, plus [onTracksChanged] so the ViewModel can derive the audio/
 * subtitle/quality option lists shown in Phase 3's menus without ever
 * holding a live player reference itself.
 */
class PlayerListenerBridge(
    private val onPhaseChanged: (PlaybackPhase) -> Unit,
    private val onTracksChangedCallback: (Tracks) -> Unit = {}
) : Player.Listener {

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> onPhaseChanged(PlaybackPhase.Buffering)
            Player.STATE_ENDED -> onPhaseChanged(PlaybackPhase.Ended)
            else -> Unit // STATE_READY/STATE_IDLE handled via onIsPlayingChanged/onPlayWhenReadyChanged below
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) onPhaseChanged(PlaybackPhase.Playing)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady) onPhaseChanged(PlaybackPhase.Paused)
    }

    override fun onPlayerError(error: PlaybackException) {
        onPhaseChanged(
            PlaybackPhase.Error(PlaybackErrorType.UNKNOWN, error.message ?: "The selected stream could not be played.")
        )
    }

    override fun onTracksChanged(tracks: Tracks) {
        onTracksChangedCallback(tracks)
    }
}
