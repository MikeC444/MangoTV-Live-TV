package com.mangotv.app.ui.player.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.mangotv.app.data.model.PlayerPreferences

/**
 * Settings' front page — a floating card matching the reference design 1:1:
 * icon+title+subtitle header, then rows for Quality/Subtitles/Audio/
 * Playback speed (each navigating into their own menu), Autoplay next
 * episode (an in-place toggle), and Advanced (Skip Intro/Source Info/
 * Change Source, grouped out of the main list).
 */
@Composable
fun SettingsPanel(
    playbackSpeed: Float,
    preferences: PlayerPreferences,
    onAutoplayChange: (Boolean) -> Unit,
    showSubtitles: Boolean,
    showAudio: Boolean,
    showQuality: Boolean,
    subtitleLabel: String,
    audioLabel: String,
    qualityLabel: String,
    onOpenSubtitles: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenQuality: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    onOpenAdvanced: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocusRequester.requestFocus() } }

    var requestedFirstFocus = false
    fun consumeFirstFocusRequester(): FocusRequester? =
        if (!requestedFirstFocus) { requestedFirstFocus = true; firstFocusRequester } else null

    SettingsCardScaffold(
        icon = Icons.Filled.Settings,
        title = "Settings",
        subtitle = "Adjust your playback preferences",
        modifier = modifier
    ) {
        if (showQuality) {
            SettingsRow(
                icon = Icons.Filled.HighQuality,
                title = "Quality",
                subtitle = qualityLabel,
                onClick = onOpenQuality,
                focusRequester = consumeFirstFocusRequester()
            )
        }
        if (showSubtitles) {
            SettingsRow(
                icon = Icons.Filled.Subtitles,
                title = "Subtitles",
                subtitle = subtitleLabel,
                onClick = onOpenSubtitles,
                focusRequester = consumeFirstFocusRequester()
            )
        }
        if (showAudio) {
            SettingsRow(
                icon = Icons.Filled.GraphicEq,
                title = "Audio",
                subtitle = audioLabel,
                onClick = onOpenAudio,
                focusRequester = consumeFirstFocusRequester()
            )
        }
        SettingsRow(
            icon = Icons.Filled.Speed,
            title = "Playback Speed",
            subtitle = formatSpeed(playbackSpeed),
            onClick = onOpenPlaybackSpeed,
            focusRequester = consumeFirstFocusRequester()
        )
        SettingsRow(
            icon = Icons.Filled.PlayCircle,
            title = "Auto Play Next Episode",
            subtitle = if (preferences.autoplayNextEpisode) "On" else "Off",
            onClick = { onAutoplayChange(!preferences.autoplayNextEpisode) },
            focusRequester = consumeFirstFocusRequester(),
            trailing = { ToggleSwitch(checked = preferences.autoplayNextEpisode) }
        )
        SettingsRow(
            icon = Icons.Filled.Tune,
            title = "Advanced",
            subtitle = "Additional settings",
            onClick = onOpenAdvanced,
            focusRequester = consumeFirstFocusRequester()
        )
    }
}
