package com.mangotv.app.ui.livetv

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.NowNext
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

private val CardWidth = 200.dp
private val CardHeight = 112.dp

/**
 * One channel tile: logo (or a text fallback when the playlist doesn't
 * provide one) inside the focusable artwork area, current programme (or
 * "Live" when EPG data isn't available for this channel — see
 * EpgRepository) as a bottom overlay, and name/country/category below —
 * mirrors ContentCard's own image-plus-caption structure so Live TV reads
 * as the same design system as Movies/TV Shows rather than a bolted-on
 * screen.
 *
 * Logos are loaded at their natural size with plain AsyncImage rather than
 * rememberOpaqueImageRequest's forced RGB_565 — see that helper's own doc:
 * that path is for opaque poster art only, and channel logos need their
 * alpha channel.
 */
@Composable
fun ChannelCard(
    channel: Channel,
    nowNext: NowNext?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(CardWidth)) {
        TvFocusSurface(
            onClick = onClick,
            modifier = Modifier.width(CardWidth).height(CardHeight),
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            backgroundColor = MangoSurface,
            focusRequester = focusRequester,
            onFocusChanged = { focused = it },
            // Same reasoning as ContentCard: the enclosing LazyRow already
            // scrolls focused children into view on its own.
            bringIntoViewOnFocus = false
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth(0.72f).height(56.dp)
                    )
                } else {
                    Text(
                        text = channel.name,
                        color = TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                val nowTitle = nowNext?.now?.title
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))))
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = nowTitle ?: "Live",
                        color = if (nowTitle != null) TextSecondary else MangoCoral,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (nowTitle == null) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = channel.name,
            color = if (focused) TextPrimary else TextSecondary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        val metaLine = listOfNotNull(channel.country, channel.groupTitle).joinToString(" • ")
        if (metaLine.isNotEmpty()) {
            Text(
                text = metaLine,
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
