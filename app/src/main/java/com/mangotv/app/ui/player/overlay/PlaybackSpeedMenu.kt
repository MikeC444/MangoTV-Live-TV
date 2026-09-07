package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp

/** Fixed 0.5x-2x set — Media3 supports pitch-preserving speed uniformly for
 * direct/HLS/DASH VOD, so no runtime capability check is needed here. */
val PLAYBACK_SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

/** Reached from Settings' "Playback speed" row — a plain fixed list, same
 * right-anchored drawer style as Subtitles/Audio/Quality. */
@Composable
fun PlaybackSpeedMenu(
    playbackSpeed: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocusRequester.requestFocus() } }

    MenuOverlayScaffold(title = "Playback Speed", modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PLAYBACK_SPEEDS.forEach { speed ->
                MenuOptionRow(
                    label = formatSpeed(speed),
                    isSelected = speed == playbackSpeed,
                    onClick = { onSelect(speed) },
                    focusRequester = if (speed == PLAYBACK_SPEEDS.first()) firstFocusRequester else null
                )
            }
        }
    }
}
