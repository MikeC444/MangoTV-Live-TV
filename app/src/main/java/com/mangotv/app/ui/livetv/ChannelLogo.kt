package com.mangotv.app.ui.livetv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import coil.compose.SubcomposeAsyncImage
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.ui.theme.TextSecondary

/**
 * A channel logo with a graceful fallback -- some playlists (including the
 * default one) host logos on hosts like i.imgur.com that are blocked by
 * some ISPs/networks (several UK providers block Imgur outright), and a
 * plain AsyncImage just renders nothing when a load fails, leaving a blank
 * gap. This can't make a blocked host reachable -- that's a network-level
 * block outside the app -- but it does mean a failed (or missing) logo
 * always shows the channel's initials instead of empty space, the same
 * fallback most streaming guides use.
 */
@Composable
fun ChannelLogo(channel: Channel, modifier: Modifier = Modifier, monogramStyle: TextStyle? = null) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!channel.logoUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
                error = { ChannelMonogram(channel.name, monogramStyle) }
            )
        } else {
            ChannelMonogram(channel.name, monogramStyle)
        }
    }
}

@Composable
private fun ChannelMonogram(name: String, style: TextStyle?) {
    Text(
        text = name.trim().take(2).uppercase().ifBlank { "?" },
        color = TextSecondary,
        style = style ?: MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold
    )
}
