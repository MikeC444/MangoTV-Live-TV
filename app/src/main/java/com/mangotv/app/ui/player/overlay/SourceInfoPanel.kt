package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.model.Stream
import com.mangotv.app.ui.player.AudioTrackOption
import com.mangotv.app.ui.player.SubtitleTrackOption
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/** Read-only — nothing here is selectable, it's just what the spec calls the
 * "information/settings menu" view of the currently playing source. */
@Composable
fun SourceInfoPanel(
    stream: Stream,
    audioTracks: List<AudioTrackOption>,
    subtitleTracks: List<SubtitleTrackOption>,
    modifier: Modifier = Modifier
) {
    val rows = buildList {
        add("Provider" to stream.providerLabel)
        add("Resolution" to stream.qualityBadge)
        stream.codec?.let { add("Video codec" to it) }
        stream.audioTag?.let { add("Audio" to it) }
        if (audioTracks.isNotEmpty()) {
            add("Audio tracks" to audioTracks.size.toString())
        }
        val subtitleCount = subtitleTracks.count { it.trackGroup != null }
        add("Subtitles" to if (subtitleCount > 0) "$subtitleCount available" else "None")
        stream.sizeLabel?.let { add("File size" to it) }
    }

    MenuOverlayScaffold(title = "Source Info", modifier = modifier) {
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = label, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(
                    text = value,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
