package com.mangotv.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.TextPrimary

/** The "«« 10 seconds" / "10 seconds »»" pill shown briefly while seeking. */
@Composable
fun SeekIndicatorPill(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(percent = 50))
            .padding(horizontal = 26.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

/** The brief ▶/❚❚ badge flashed over the video whenever playback toggles —
 * a bare icon, no background circle behind it. */
@Composable
fun PlayPauseIndicator(isPlaying: Boolean, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
        contentDescription = null,
        tint = TextPrimary,
        modifier = modifier.size(56.dp)
    )
}
