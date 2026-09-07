package com.mangotv.app.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.Episode
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.MangoLogo
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Subtle top bar shown with the rest of the controls: back button, a
 * centered title block (title + year/rating/season-count for a show, or
 * runtime for a movie, plus the current episode line when applicable), and
 * the app's own wordmark on the right — mirrors the reference design's
 * three-part layout.
 */
@Composable
fun PlayerTopBar(
    content: Content,
    episode: Episode?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backFocusRequester: FocusRequester? = null,
    backFocusDown: FocusRequester? = null,
    onBackFocusChanged: (Boolean) -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 28.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HeroIconButton(
            icon = Icons.Filled.ArrowBack,
            contentDescription = "Back",
            onClick = onBack,
            focusRequester = backFocusRequester,
            focusDown = backFocusDown,
            onFocusChanged = onBackFocusChanged,
            showBackground = false,
            borderColor = Color.White
        )

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = content.title,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            val metaLine = buildList {
                content.year?.let { add(it.toString()) }
                content.ageRating?.let { add(it) }
                if (episode != null && content.seasons.isNotEmpty()) {
                    val seasonCount = content.seasons.size
                    add("$seasonCount Season${if (seasonCount == 1) "" else "s"}")
                    add("${content.seasons.sumOf { it.episodes.size }} Episodes")
                } else {
                    content.runtimeMinutes?.let { add("${it / 60}h ${it % 60}m") }
                }
            }.joinToString("  •  ")
            if (metaLine.isNotEmpty()) {
                Text(
                    text = metaLine,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (episode != null) {
                Text(
                    text = "S${episode.seasonNumber} E${episode.episodeNumber} • ${episode.title}",
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        MangoLogo(fontSize = 16.sp)
    }
}
