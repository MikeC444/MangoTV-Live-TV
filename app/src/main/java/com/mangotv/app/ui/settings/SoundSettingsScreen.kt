package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.audio.BootSound
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlin.math.roundToInt

// How much each LEFT/RIGHT press moves the volume row -- 10 presses spans
// silent to full.
private const val VOLUME_STEP = 0.1f

@Composable
fun SoundSettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SoundSettingsViewModel = viewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val volumeRowFocusRequester = remember { FocusRequester() }

    SettingsScaffold(
        title = "Sounds",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = volumeRowFocusRequester,
        titleIcon = Icons.Filled.MusicNote
    ) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item(key = "volume_header") {
                Text(
                    text = "Applies to the navigation and click sounds -- not the boot chime below.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            item(key = "volume_row") {
                NavigationVolumeRow(
                    volume = preferences.navigationVolume,
                    onVolumeChange = viewModel::setNavigationVolume,
                    focusRequester = volumeRowFocusRequester,
                    focusUp = navFocusRequester
                )
            }
            item(key = "boot_header") {
                Text(
                    text = "Pick the chime that plays as the app boots, or turn it off. Selecting one plays a preview.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
                )
            }
            itemsIndexed(BootSound.entries, key = { _, sound -> sound.name }) { _, sound ->
                BootSoundOptionRow(
                    sound = sound,
                    isSelected = sound == preferences.selectedBootSound,
                    onClick = { viewModel.selectBootSound(sound) }
                )
            }
        }
    }
}

@Composable
private fun NavigationVolumeRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val labelColor = if (focused) TextPrimary else TextSecondary

    TvFocusSurface(
        // Nothing to "click" here -- LEFT/RIGHT (intercepted below) is how
        // this row is actually used. clickSound = NONE since a stray
        // DPAD_CENTER/Enter press shouldn't play the click tone for a
        // control that doesn't do anything on select.
        onClick = {},
        clickSound = ClickSound.NONE,
        modifier = Modifier
            .fillMaxWidth()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val delta = when (event.key) {
                    Key.DirectionLeft -> -VOLUME_STEP
                    Key.DirectionRight -> VOLUME_STEP
                    else -> return@onPreviewKeyEvent false
                }
                onVolumeChange((volume + delta).coerceIn(0f, 1f))
                true
            },
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        focusRequester = focusRequester,
        focusUp = focusUp,
        onFocusChanged = { focused = it }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.VolumeUp, contentDescription = null, tint = labelColor)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "Navigation Volume",
                    color = labelColor,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${(volume * 100).roundToInt()}%",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelLarge
                )
            }
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(MangoSurfaceHigh)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(volume.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(MangoAmber)
                )
            }
            if (focused) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "LEFT / RIGHT to adjust",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun BootSoundOptionRow(
    sound: BootSound,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        alwaysShowBorder = isSelected,
        borderColor = MangoAmber
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (sound == BootSound.NONE) Icons.Filled.MusicOff else Icons.Filled.PlayCircle,
                contentDescription = null,
                tint = if (isSelected) MangoAmber else TextTertiary
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sound.label,
                    color = if (isSelected) TextPrimary else TextSecondary,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Selected",
                    tint = MangoAmber
                )
            }
        }
    }
}
