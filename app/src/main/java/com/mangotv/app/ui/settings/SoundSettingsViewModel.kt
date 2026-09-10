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
 * Backs Settings > Sounds: lets the user pick one of the boot chimes (or
 * turn the chime off via BootSound.NONE), previewing it in full the moment
 * it's picked so they can hear it before committing (persisted regardless
 * -- there's no separate "confirm"); and lets them set the nav/click/back
 * sounds' shared volume. previewPlayer is its own BootSoundPlayer instance,
 * entirely separate from the one MangoNavHost uses for the real cold-boot
 * chime -- see that class's own doc.
 *
 * setNavigationVolume deliberately does NOT also play a preview tick here:
 * MangoNavHost's global nav-sound listener already plays one for every
 * LEFT/RIGHT press app-wide, the volume row included (see its own doc) --
 * adding a second one here would double up on exactly the row where it'd
 * be most noticeable. That global tick fires from the key-press itself,
 * fractionally before this function updates the volume, so in practice it
 * plays at the level from just before each step rather than the step just
 * picked -- close enough over a run of adjustments to still work for
 * "dial it in by ear," without the complexity of suppressing one of the
 * two players for this one row.
 */
class SoundSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val soundPreferencesRepository = (application as MangoTvApplication).container.soundPreferencesRepository
    private val previewPlayer = BootSoundPlayer(application)

    val preferences: StateFlow<SoundPreferences> = soundPreferencesRepository.preferences

    fun selectBootSound(sound: BootSound) {
        viewModelScope.launch { soundPreferencesRepository.setSelectedBootSound(sound) }
        previewPlayer.playPreview(sound)
    }

    fun setNavigationVolume(volume: Float) {
        viewModelScope.launch { soundPreferencesRepository.setNavigationVolume(volume) }
    }

    override fun onCleared() {
        super.onCleared()
        previewPlayer.release()
    }
}
