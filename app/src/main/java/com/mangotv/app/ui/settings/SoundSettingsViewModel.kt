package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.audio.BootSound
import com.mangotv.app.data.audio.BootSoundPlayer
import com.mangotv.app.data.audio.SoundPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Backs Settings > Sounds: lets the user pick one of the 5 boot chimes,
 * previewing it in full the moment it's picked so they can hear it before
 * committing (persisted regardless -- there's no separate "confirm").
 * previewPlayer is its own BootSoundPlayer instance, entirely separate from
 * the one MangoNavHost uses for the real cold-boot chime -- see that
 * class's own doc.
 */
class SoundSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val soundPreferencesRepository = (application as MangoTvApplication).container.soundPreferencesRepository
    private val previewPlayer = BootSoundPlayer(application)

    val preferences: StateFlow<SoundPreferences> = soundPreferencesRepository.preferences

    fun selectBootSound(sound: BootSound) {
        viewModelScope.launch { soundPreferencesRepository.setSelectedBootSound(sound) }
        previewPlayer.playPreview(sound)
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer.release()
    }
}
