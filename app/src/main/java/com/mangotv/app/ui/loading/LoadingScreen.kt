package com.mangotv.app.ui.loading

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import com.mangotv.app.R
import com.mangotv.app.ui.home.HomeUiState
import com.mangotv.app.ui.home.HomeViewModel
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val PRELOAD_CARD_COUNT = 6

// Generous rather than tight: this now covers BOTH waiting for the live
// Cinemeta/addon catalog fetch to actually settle AND preloading the
// images that fetch produces, so it needs real headroom for a slow
// connection on top of the image work. Still bounded -- a genuinely
// offline device or a dead image host can't hold this screen up forever;
// Home just appears with whatever didn't finish loading in time, same
// as it would have without this screen at all.
private const val READY_TIMEOUT_MS = 20_000L

/**
 * Branded cold-boot gate: shown once, in place of the real UI, the moment
 * the app opens -- see MangoNavHost, which renders this instead of the
 * NavHost until [homeViewModel]'s live catalog fetch has actually settled
 * AND the resulting hero/poster images are preloaded into Coil's cache, so
 * Home appears already fully populated with no visible pop-in. Deliberately
 * waits for [HomeViewModel.liveDataReady] rather than [HomeViewModel.uiState]
 * reaching Success -- uiState can reach Success from cache alone, well
 * before the live fetch this screen actually needs to wait for (see
 * liveDataReady's own doc). Never shown again for the rest of the
 * process's lifetime (switching tabs, backgrounding/foregrounding, etc.
 * don't re-trigger it), matching "only on cold boot".
 */
@Composable
fun LoadingScreen(homeViewModel: HomeViewModel, onReady: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
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
        onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Solid MangoBackground with two soft brand-color glows in
                // opposite corners, matching the reference design.
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
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_mango),
                contentDescription = null,
                modifier = Modifier.size(180.dp)
            )
            Spacer(Modifier.height(24.dp))
            Row {
                Text(
                    text = "Mango",
                    color = TextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "TV",
                    color = MangoAmber,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "MOVIES   •   TV SHOWS   •   LIVE TV",
                color = TextSecondary,
                fontSize = 14.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(
                color = MangoAmber,
                trackColor = MangoSurfaceHigh,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Loading your entertainment...",
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
