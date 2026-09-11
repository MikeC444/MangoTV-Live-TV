package com.mangotv.app.ui.loading

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import coil.imageLoader
import coil.request.ImageRequest
import com.mangotv.app.R
import com.mangotv.app.ui.home.HomeUiState
import com.mangotv.app.ui.home.HomeViewModel
import com.mangotv.app.ui.player.PlayerSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoCoral
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val PRELOAD_CARD_COUNT = 6

// liveDataReady fires on the FIRST batch of rows (see its own doc) --
// typically just one round of up to HOME_BATCH_SIZE concurrent catalog
// requests, not the entire ~30-row fetch -- so the common-case wait here is
// short. Still meaningfully generous, not tight: a slow connection's first
// batch, plus preloading its images, both need real room. Still bounded --
// a genuinely offline device or a dead image host can't hold this screen up
// forever; Home just appears with whatever didn't finish loading in time,
// same as it would have without this screen at all. Any rows still in
// flight after this screen hands off keep loading live on Home itself,
// same as buildSectionsFlow's later batches always did.
private const val READY_TIMEOUT_MS = 10_000L

// Bounds how long the boot video itself is waited on, independent of data
// readiness -- protects a corrupt or unexpectedly long clip from hanging
// cold boot forever. A well-authored boot clip should always finish well
// inside this.
private const val VIDEO_TIMEOUT_MS = 20_000L

// Where the boot video is expected -- checked for existence at runtime
// rather than referenced as a compiled raw resource, so a build with no
// video dropped in yet still compiles and boots normally (see
// hasVideoAsset below) instead of failing outright. Points at whatever's
// actually been dropped into app/src/main/assets/ -- update this if that
// file is ever renamed or replaced.
private const val BOOT_VIDEO_ASSET = "newboot1.mp4"

/**
 * Branded cold-boot gate: an opaque overlay on top of the real UI (see
 * MangoNavHost's own doc for why it's structured as an overlay rather than
 * gating the real nav graph's existence) that plays a full-screen boot
 * video in place of the old static logo/spinner splash. The video's own
 * embedded audio track plays right along with it through ExoPlayer -- no
 * separate sound wiring needed, and no interaction with the nav/click
 * sound system (UiSoundPlayer) at all.
 *
 * Falls back to a plain branded background with no video and no extra
 * wait if [BOOT_VIDEO_ASSET] isn't present (see hasVideoAsset below), so a
 * build with no video file dropped in yet still boots normally instead of
 * showing a broken or endlessly loading screen.
 *
 * Reveal ([onReady]) waits on BOTH the video reaching its natural end (or
 * erroring out, or simply not existing) AND [homeViewModel] having its
 * first batch of real rows with the resulting hero/poster images preloaded
 * into Coil's cache -- deliberately [HomeViewModel.liveDataReady] rather
 * than [HomeViewModel.uiState] reaching Success, since uiState can reach
 * Success from cache alone, well before any live data (see liveDataReady's
 * own doc). Never shown again for the rest of the process's lifetime
 * (switching tabs, backgrounding/foregrounding, etc. don't re-trigger it).
 */
@OptIn(UnstableApi::class)
@Composable
fun BootVideoScreen(homeViewModel: HomeViewModel, onReady: () -> Unit) {
    val context = LocalContext.current

    // A plain existence check -- opening and immediately closing an asset
    // this small is effectively instant (no network, no real disk seek
    // beyond the APK's own bundled resources), so doing it directly during
    // composition rather than as a suspend call doesn't cost anything
    // worth avoiding. Same runCatching-around-assets.open pattern
    // AddonRepository already uses for its own bundled manifest asset.
    val hasVideoAsset = remember {
        runCatching { context.assets.open(BOOT_VIDEO_ASSET).use { } }.isSuccess
    }

    // Completed once there's nothing left to wait on for the video side of
    // reveal -- immediately, if there's no video asset at all; otherwise on
    // the player reaching STATE_ENDED or hitting an error. A plain
    // CompletableDeferred rather than Compose state, since this is really
    // just a one-shot coroutine signal, not something anything reads
    // reactively during composition.
    val videoFinished = remember { CompletableDeferred<Unit>() }

    val exoPlayer = remember {
        if (!hasVideoAsset) {
            null
        } else {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse("asset:///$BOOT_VIDEO_ASSET")))
                prepare()
                playWhenReady = true
            }
        }
    }

    if (exoPlayer == null) {
        LaunchedEffect(Unit) { videoFinished.complete(Unit) }
    } else {
        DisposableEffect(exoPlayer) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) videoFinished.complete(Unit)
                }

                override fun onPlayerError(error: PlaybackException) {
                    videoFinished.complete(Unit)
                }
            }
            exoPlayer.addListener(listener)
            onDispose {
                exoPlayer.removeListener(listener)
                exoPlayer.release()
            }
        }
    }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                withTimeoutOrNull(READY_TIMEOUT_MS) {
                    homeViewModel.liveDataReady.first { it }

                    val state = homeViewModel.uiState.value
                    if (state is HomeUiState.Success) {
                        val urlsToPreload = buildList {
                            state.heroItems.firstOrNull()?.let { first ->
                                first.backdropUrl?.let(::add)
                                first.logoUrl?.let(::add)
                            }
                            state.sections.firstOrNull()?.items?.take(PRELOAD_CARD_COUNT)?.forEach { item ->
                                item.posterUrl?.let(::add)
                            }
                        }.distinct()

                        val imageLoader = context.imageLoader
                        coroutineScope {
                            urlsToPreload.map { url ->
                                async { runCatching { imageLoader.execute(ImageRequest.Builder(context).data(url).build()) } }
                            }.awaitAll()
                        }
                    }
                }
            }
            launch {
                withTimeoutOrNull(VIDEO_TIMEOUT_MS) { videoFinished.await() }
            }
        }
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Solid MangoBackground with two soft brand-color glows in
                // opposite corners -- shows through behind the video (any
                // letterboxed edge, or a transparent moment in the clip
                // itself) and is all that's visible at all when there's no
                // video asset yet.
                drawRect(MangoBackground)
                val glowRadius = size.minDimension * 0.7f
                listOf(
                    Offset(0f, 0f) to MangoAmber,
                    Offset(size.width, size.height) to MangoCoral
                ).forEach { (corner, color) ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                            center = corner,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = corner
                    )
                }
            }
    ) {
        if (exoPlayer != null) {
            // ZOOM rather than PlayerSurface's own FIT default -- a
            // decorative full-screen boot clip should fill the screen
            // edge-to-edge, unlike real video content where cropping could
            // cut off picture the user actually wants to see.
            PlayerSurface(
                exoPlayer = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )
        } else {
            Image(
                painter = painterResource(R.drawable.logo_mango),
                contentDescription = null,
                modifier = Modifier.size(180.dp).align(Alignment.Center)
            )
        }
    }
}
