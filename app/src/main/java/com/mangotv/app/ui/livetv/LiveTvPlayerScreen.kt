package com.mangotv.app.ui.livetv

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import coil.compose.AsyncImage
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.NowNext
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.player.PlaybackPhase
import com.mangotv.app.ui.player.PlayerListenerBridge
import com.mangotv.app.ui.player.PlayerSurface
import com.mangotv.app.ui.player.buildExoPlayer
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

/**
 * Live channel playback. Deliberately reuses the same ExoPlayer engine as
 * the main player (buildExoPlayer/PlayerSurface/PlayerListenerBridge/
 * PlaybackPhase, all from ui/player) rather than standing up a second
 * player stack — only the controls surface is different, and much simpler:
 * live channels have no timeline/seek/subtitle/audio-track menu, just play/
 * retry/back and a lightweight identity overlay (logo, name, LIVE, now/
 * next), so nothing here duplicates PlayerScreen's own state machine.
 */
@Composable
fun LiveTvPlayerScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveTvPlayerViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        when (val state = uiState) {
            is LiveTvPlayerUiState.Loading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White
            )
            is LiveTvPlayerUiState.Error -> LiveTvPlaybackErrorOverlay(
                message = state.message,
                onTryAgain = viewModel::load,
                onBack = onBack
            )
            is LiveTvPlayerUiState.Ready -> LiveTvPlaybackContent(
                channel = state.channel,
                nowNext = viewModel.nowAndNext(state.channel),
                onBack = onBack
            )
        }
    }

    BackHandler { onBack() }
}

@Composable
private fun LiveTvPlaybackContent(channel: Channel, nowNext: NowNext, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val exoPlayer = remember { buildExoPlayer(context) }
    var phase by remember { mutableStateOf<PlaybackPhase>(PlaybackPhase.Loading) }

    DisposableEffect(exoPlayer) {
        val listener = PlayerListenerBridge(onPhaseChanged = { phase = it })
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Same pause-not-release-on-stop convention as the main PlayerScreen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) exoPlayer.pause() }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun startPlayback() {
        phase = PlaybackPhase.Loading
        exoPlayer.setMediaItem(MediaItem.Builder().setUri(channel.streamUrl).build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    LaunchedEffect(channel.id) { startPlayback() }

    var overlayVisible by remember { mutableStateOf(true) }
    LaunchedEffect(overlayVisible, phase) {
        if (overlayVisible && phase is PlaybackPhase.Playing) {
            delay(4000)
            overlayVisible = false
        }
    }

    val rootFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { rootFocusRequester.requestFocus() } }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionCenter, Key.Enter, Key.DirectionUp, Key.DirectionDown -> {
                            overlayVisible = true
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
    ) {
        PlayerSurface(exoPlayer = exoPlayer, modifier = Modifier.fillMaxSize())

        if (phase is PlaybackPhase.Loading || phase is PlaybackPhase.Buffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center).size(48.dp),
                color = Color.White
            )
        }

        val errorPhase = phase as? PlaybackPhase.Error
        if (errorPhase != null) {
            LiveTvPlaybackErrorOverlay(message = errorPhase.message, onTryAgain = ::startPlayback, onBack = onBack)
        }

        AnimatedVisibility(
            visible = overlayVisible && errorPhase == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth()
        ) {
            LiveTvPlayerOverlay(channel = channel, nowNext = nowNext)
        }
    }
}

@Composable
private fun LiveTvPlayerOverlay(channel: Channel, nowNext: NowNext) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
            .padding(horizontal = 40.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(40.dp).width(80.dp)
            )
            Spacer(Modifier.width(16.dp))
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(MangoCoral, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(text = channel.name, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            }
            nowNext.now?.title?.let { title ->
                Text(text = title, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
            nowNext.next?.title?.let { title ->
                Text(text = "Next: $title", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun LiveTvPlaybackErrorOverlay(message: String, onTryAgain: () -> Unit, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Unable to play this channel", color = TextPrimary, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row {
                MangoButton(
                    text = "Try Again",
                    icon = Icons.Filled.Refresh,
                    onClick = onTryAgain,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
                Spacer(Modifier.width(12.dp))
                MangoButton(
                    text = "Back",
                    icon = Icons.Filled.ArrowBack,
                    onClick = onBack,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
            }
        }
    }
}
