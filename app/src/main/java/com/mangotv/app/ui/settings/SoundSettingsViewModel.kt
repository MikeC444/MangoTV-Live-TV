package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.audio.SoundPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Backs Settings > Sounds: lets the user set the nav/click/back sounds' shared volume. */
class SoundSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val soundPreferencesRepository = (application as MangoTvApplication).container.soundPreferencesRepository

    val preferences: StateFlow<SoundPreferences> = soundPreferencesRepository.preferences

    fun setNavigationVolume(volume: Float) {
        viewModelScope.launch { soundPreferencesRepository.setNavigationVolume(volume) }
    }
}
