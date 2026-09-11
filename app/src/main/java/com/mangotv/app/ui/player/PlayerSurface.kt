package com.mangotv.app.ui.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Thin wrapper around Media3's PlayerView with its built-in controller
 * disabled — every control in this app is custom Compose UI drawn on top,
 * PlayerView here only owns the video Surface/TextureView selection and
 * aspect-ratio handling.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerSurface(
    exoPlayer: ExoPlayer,
    modifier: Modifier = Modifier,
    // FIT (letterboxed, never crops) is right for actual video content,
    // where cropping could cut off picture the user actually wants to see.
    // A caller like the full-screen boot video, which is decorative rather
    // than something to watch frame-accurately, can pass ZOOM instead to
    // fill the screen edge-to-edge with no letterboxing.
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT
) {
    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                this.resizeMode = resizeMode
            }
        }
    )
}
