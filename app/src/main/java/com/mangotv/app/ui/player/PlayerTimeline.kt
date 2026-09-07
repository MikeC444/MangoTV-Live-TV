package com.mangotv.app.ui.player

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.ProgressTrack
import kotlinx.coroutines.delay

/**
 * The center progress bar: position/buffered/duration, growing visibly when
 * focused and gaining a white glow once the user selects it to scrub.
 * LEFT/RIGHT seeking while selected is handled by the root player key
 * interceptor (zone- and selection-gated, not owned here) — this
 * composable only reports its own focus state and draws the current
 * position/buffered fill plus the scrub-mode indicator.
 */
@Composable
fun PlayerTimeline(
    exoPlayer: ExoPlayer,
    phase: PlaybackPhase,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    isScrubbing: Boolean = false,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    LaunchedEffect(isFocused) { onFocusChanged(isFocused) }

    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(phase) {
        while (true) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0)
            durationMs = exoPlayer.duration.coerceAtLeast(0)
            bufferedMs = exoPlayer.bufferedPosition.coerceAtLeast(0)
            delay(500)
        }
    }

    val playedFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (durationMs > 0) (bufferedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val barTween = tween<Dp>(MangoMotion.FastMillis, easing = MangoMotion.StandardEasing)
    val barHeight by animateDpAsState(if (isFocused) 12.dp else 6.dp, animationSpec = barTween, label = "timelineBarHeight")
    val thumbSize by animateDpAsState(if (isScrubbing) 24.dp else if (isFocused) 20.dp else 12.dp, animationSpec = barTween, label = "timelineThumbSize")
    val glowSize by animateDpAsState(if (isScrubbing) 48.dp else 0.dp, animationSpec = barTween, label = "timelineGlowSize")

    var boxModifier = modifier
        .fillMaxWidth()
        .height(28.dp)
        .focusable(interactionSource = interactionSource)
    if (focusRequester != null) {
        boxModifier = boxModifier.focusRequester(focusRequester)
    }
    if (focusUp != null) {
        boxModifier = boxModifier.focusProperties { up = focusUp }
    }

    BoxWithConstraints(modifier = boxModifier, contentAlignment = Alignment.CenterStart) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .background(ProgressTrack, RoundedCornerShape(percent = 50))
        )
        Box(
            modifier = Modifier
                .width(maxWidth * bufferedFraction)
                .height(barHeight)
                .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(percent = 50))
        )
        Box(
            modifier = Modifier
                .width(maxWidth * playedFraction)
                .height(barHeight)
                .background(Color.White, RoundedCornerShape(percent = 50))
        )
        if (isScrubbing) {
            Box(
                modifier = Modifier
                    .offset(x = maxWidth * playedFraction - (glowSize / 2))
                    .size(glowSize)
                    .background(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)), CircleShape)
            )
        }
        Box(
            modifier = Modifier
                .offset(x = maxWidth * playedFraction - (thumbSize / 2))
                .size(thumbSize)
                .background(Color.White, CircleShape)
        )
    }
}
