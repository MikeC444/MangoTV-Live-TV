package com.mangotv.app.ui.player.overlay

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester

/** Reached from Settings' "Advanced" row — the less commonly used
 * controls grouped out of the main list, matching the reference design's
 * "Additional settings" grouping. */
@Composable
fun AdvancedSettingsPanel(
    skipIntroEnabled: Boolean,
    onSkipIntroChange: (Boolean) -> Unit,
    onOpenSourceInfo: () -> Unit,
    onChangeSource: () -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocusRequester.requestFocus() } }

    SettingsCardScaffold(
        icon = Icons.Filled.Tune,
        title = "Advanced",
        subtitle = "Additional settings",
        modifier = modifier
    ) {
        SettingsRow(
            icon = Icons.Filled.FastForward,
            title = "Skip Intro",
            subtitle = if (skipIntroEnabled) "On" else "Off",
            onClick = { onSkipIntroChange(!skipIntroEnabled) },
            focusRequester = firstFocusRequester,
            trailing = { ToggleSwitch(checked = skipIntroEnabled) }
        )
        SettingsRow(
            icon = Icons.Filled.Info,
            title = "Source Info",
            subtitle = "Resolution, codecs & more",
            onClick = onOpenSourceInfo
        )
        SettingsRow(
            icon = Icons.Filled.SwapHoriz,
            title = "Change Source",
            subtitle = "Pick a different stream",
            onClick = onChangeSource
        )
    }
}
