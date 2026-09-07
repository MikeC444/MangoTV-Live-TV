package com.mangotv.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Episode
import com.mangotv.app.data.model.Season
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

private val EpisodeThumbWidth = MangoDimens.ContinueWatchingWidth

/**
 * Replaces Cast/"You May Also Like" for TV shows: a season selector followed
 * by that season's episodes. Tapping an episode navigates to the source
 * picker for that specific season/episode.
 */
@Composable
fun SeasonsSection(
    seasons: List<Season>,
    modifier: Modifier = Modifier,
    onNavigateUpPastRow: (() -> Unit)? = null,
    onEpisodeClick: (Episode) -> Unit = {}
) {
    if (seasons.isEmpty()) return

    var selectedIndex by remember { mutableIntStateOf(0) }
    val selectedSeason = seasons.getOrNull(selectedIndex) ?: return

    Column(modifier = modifier) {
        Text(
            text = "Seasons",
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                horizontal = MangoDimens.ScreenPaddingHorizontal,
                vertical = 12.dp
            )
        )
        LazyRow(
            modifier = if (onNavigateUpPastRow != null) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionUp) {
                        if (event.type == KeyEventType.KeyDown) {
                            onNavigateUpPastRow()
                        }
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
            contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(seasons, key = { it.seasonNumber }) { season ->
                SeasonPill(
                    seasonNumber = season.seasonNumber,
                    selected = season.seasonNumber == selectedSeason.seasonNumber,
                    onClick = { selectedIndex = seasons.indexOf(season) }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(
            modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = selectedSeason.name,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${selectedSeason.episodes.size} Episodes",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(Modifier.height(16.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
        ) {
            items(selectedSeason.episodes, key = { it.id }) { episode ->
                EpisodeCard(episode = episode, onClick = { onEpisodeClick(episode) })
            }
        }
    }
}

/**
 * Styled to echo the hero's Play button and icon buttons rather than the
 * old flat amber-tinted circle: selected is a solid white fill with bold
 * black text (same "confident, high-contrast active state" as
 * MangoButtonStyle.LIGHT), unselected is the same frosted glass tint
 * HeroIconButton rests at. Focus (amber border/scale/elevation) still
 * comes from TvFocusSurface's own default, unchanged from every other
 * focusable element on this screen.
 */
@Composable
private fun SeasonPill(
    seasonNumber: Int,
    selected: Boolean,
    onClick: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    TvFocusSurface(
        onClick = onClick,
        shape = CircleShape,
        backgroundColor = if (selected) Color.White else Color.White.copy(alpha = 0.12f),
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false,
        modifier = Modifier.size(52.dp)
    ) {
        Text(
            text = seasonNumber.toString(),
            color = when {
                selected -> Color.Black
                focused -> TextPrimary
                else -> TextSecondary
            },
            fontWeight = if (selected || focused) FontWeight.Bold else FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onClick: () -> Unit) {
    Column(modifier = Modifier.width(EpisodeThumbWidth)) {
        TvFocusSurface(
            onClick = onClick,
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            backgroundColor = MangoSurfaceHigh,
            modifier = Modifier
                .width(EpisodeThumbWidth)
                .height(MangoDimens.ContinueWatchingHeight),
            bringIntoViewOnFocus = false
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(
                    model = rememberOpaqueImageRequest(episode.thumbnailUrl),
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextPrimary,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "${episode.episodeNumber}. ${episode.title}",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            episode.runtimeMinutes?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${it}m",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        if (episode.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.description,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
