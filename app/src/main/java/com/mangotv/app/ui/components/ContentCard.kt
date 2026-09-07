package com.mangotv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.RowStyle
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.ProgressFill
import com.mangotv.app.ui.theme.ProgressTrack
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun ContentCard(
    content: Content,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: RowStyle = RowStyle.STANDARD,
    focusRequester: FocusRequester? = null,
    // Used by the movie detail page to fit its whole layout on one screen
    // without scrolling — Home and the TV show detail page never pass this,
    // so their card sizing is completely unaffected.
    compact: Boolean = false,
    // Independent of `compact` (a fixed 2/3 ratio tied to that specific
    // layout need) — an additional multiplier a caller can apply on top,
    // e.g. Home shrinking its poster rows. Defaults to 1f so every other
    // existing caller is unaffected.
    posterScale: Float = 1f
) {
    val isContinueWatching = style == RowStyle.CONTINUE_WATCHING
    val scale = (if (compact) 2f / 3f else 1f) * posterScale
    val width = (if (isContinueWatching) MangoDimens.ContinueWatchingWidth else MangoDimens.PosterWidth) * scale
    val height = (if (isContinueWatching) MangoDimens.ContinueWatchingHeight else MangoDimens.PosterHeight) * scale
    var focused by remember { mutableStateOf(false) }
    val imageUrl = if (isContinueWatching) content.backdropUrl else content.posterUrl
    val titleStyle = if (scale < 0.85f) {
        androidx.compose.material3.MaterialTheme.typography.labelMedium
    } else {
        androidx.compose.material3.MaterialTheme.typography.titleMedium
    }

    Column(modifier = modifier.width(width)) {
        TvFocusSurface(
            onClick = onClick,
            modifier = Modifier.width(width).height(height),
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            backgroundColor = MangoSurface,
            focusRequester = focusRequester,
            onFocusChanged = { focused = it },
            // The enclosing LazyRow already has its own built-in
            // scroll-into-view behavior that runs as focus moves from card
            // to card. Leaving this surface's own explicit bringIntoView
            // call enabled meant two slightly-independent scroll animations
            // running at once for the same focus change, which showed up as
            // a very small shimmer/jitter on the posters while scrolling
            // through a row. The row's own scrolling is sufficient on its
            // own, so this one is redundant — same fix already applied to
            // the hero's buttons for the equivalent double-scroll issue.
            bringIntoViewOnFocus = false
        ) {
            AsyncImage(
                model = rememberOpaqueImageRequest(imageUrl),
                contentDescription = content.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            val ratingOverlayAlpha = animateFloatAsState(
                targetValue = if (focused && !isContinueWatching) 1f else 0f,
                label = "ratingOverlayAlpha"
            )
            val ratingOverlayGradient = remember {
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = ratingOverlayAlpha.value }
                    .background(ratingOverlayGradient)
                    .padding(10.dp)
            ) {
                content.rating?.let {
                    Text(
                        text = "★ ${"%.1f".format(it)}",
                        color = TextPrimary,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                    )
                }
            }

            if (isContinueWatching) {
                val progressGradient = remember {
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(progressGradient)
                )
                content.watchProgress?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(bottom = 0.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(ProgressTrack)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.fraction)
                                    .height(4.dp)
                                    .background(ProgressFill)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(if (scale < 0.85f) 6.dp else 8.dp))

        Text(
            text = content.title,
            color = if (focused) TextPrimary else TextSecondary,
            style = titleStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (isContinueWatching) {
            val progressLabel = content.watchProgress?.let { p ->
                if (p.seasonNumber != null && p.episodeNumber != null) {
                    "S${p.seasonNumber} E${p.episodeNumber}"
                } else null
            }
            if (progressLabel != null) {
                Text(
                    text = progressLabel,
                    color = TextTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            content.year?.let {
                Text(
                    text = it.toString(),
                    color = TextTertiary,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
