package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * The cinematic dark overlay shown behind controls: video stays visible in
 * the middle of the screen, darkening only near the top (for the title
 * bar's text) and — more strongly — near the bottom (for the transport
 * row/timeline), rather than a single flat scrim over the whole frame.
 */
@Composable
fun PlayerControlsScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.5f),
                    0.16f to Color.Black.copy(alpha = 0f),
                    0.55f to Color.Black.copy(alpha = 0f),
                    0.78f to Color.Black.copy(alpha = 0.45f),
                    1f to Color.Black.copy(alpha = 0.88f)
                )
            )
    )
}
