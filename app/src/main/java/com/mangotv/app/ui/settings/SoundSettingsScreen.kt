package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.audio.BootSound
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun SoundSettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SoundSettingsViewModel = viewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val firstOptionFocusRequester = remember { FocusRequester() }

    SettingsScaffold(
        title = "Sounds",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = firstOptionFocusRequester,
        titleIcon = Icons.Filled.MusicNote
    ) {
        Text(
            text = "Pick the chime that plays as the app boots. Selecting one plays a preview.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(BootSound.entries, key = { _, sound -> sound.name }) { index, sound ->
                BootSoundOptionRow(
                    sound = sound,
                    isSelected = sound == preferences.selectedBootSound,
                    onClick = { viewModel.selectBootSound(sound) },
                    focusRequester = if (index == 0) firstOptionFocusRequester else null,
                    focusUp = if (index == 0) navFocusRequester else null
                )
            }
        }
    }
}

@Composable
private fun BootSoundOptionRow(
    sound: BootSound,
    isSelected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        alwaysShowBorder = isSelected,
        borderColor = MangoAmber,
        focusRequester = focusRequester,
        focusUp = focusUp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
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
