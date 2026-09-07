package com.mangotv.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.CastMember
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

private val AvatarSize = 84.dp
private val CastItemWidth = 100.dp

/**
 * Cast members as the base Stremio protocol actually provides them — plain
 * names, no photos or character roles (see StremioMeta in StremioModels.kt).
 * Avatars fall back to a placeholder icon and the role line is simply
 * omitted rather than fabricating anything an addon didn't supply. Items
 * stay focusable (even though there's nothing to navigate to on click yet)
 * so the row remains D-pad scrollable for a long cast list.
 */
@Composable
fun CastRow(
    cast: List<CastMember>,
    modifier: Modifier = Modifier,
    onNavigateUpPastRow: (() -> Unit)? = null,
    // Used by the movie detail page to fit its whole layout on one screen
    // without scrolling — the TV show detail page never passes this, so its
    // Cast row (used when a show has no season data) is unaffected.
    compact: Boolean = false
) {
    if (cast.isEmpty()) return

    val avatarSize = if (compact) 56.dp else AvatarSize
    val itemWidth = if (compact) 76.dp else CastItemWidth

    Column(modifier = modifier) {
        Text(
            text = "Cast",
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(
                start = MangoDimens.ScreenPaddingHorizontal,
                bottom = if (compact) 6.dp else 12.dp
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
            contentPadding = PaddingValues(start = MangoDimens.ScreenPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(if (compact) 14.dp else 20.dp)
        ) {
            items(cast, key = { it.name }) { member ->
                CastCard(member, avatarSize = avatarSize, itemWidth = itemWidth, compact = compact)
            }
        }
    }
}

@Composable
private fun CastCard(
    member: CastMember,
    avatarSize: Dp = AvatarSize,
    itemWidth: Dp = CastItemWidth,
    compact: Boolean = false
) {
    Column(
        modifier = Modifier.width(itemWidth),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TvFocusSurface(
            onClick = {},
            shape = CircleShape,
            backgroundColor = MangoSurfaceHigh,
            modifier = Modifier.size(avatarSize),
            bringIntoViewOnFocus = false
        ) {
            if (member.photoUrl != null) {
                AsyncImage(
                    model = rememberOpaqueImageRequest(member.photoUrl),
                    contentDescription = member.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (compact) 14.dp else 20.dp)
                )
            }
        }
        Spacer(Modifier.height(if (compact) 6.dp else 10.dp))
        Text(
            text = member.name,
            color = TextPrimary,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        member.role?.let { role ->
            Text(
                text = role,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
