package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.model.PlayerPreferences
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Backs Settings > Subtitles: default subtitle on/off and preferred language for new playback sessions (see PlayerEngine.buildExoPlayer). */
class SubtitleSettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val playerPreferencesRepository = (application as MangoTvApplication).container.playerPreferencesRepository

    val preferences: StateFlow<PlayerPreferences> = playerPreferencesRepository.preferences

    fun setSubtitlesEnabled(enabled: Boolean) {
        viewModelScope.launch { playerPreferencesRepository.setSubtitlesEnabled(enabled) }
    }

    /** [languageCode] is one of SubtitleLanguageOptions' codes, or null for "no preference". */
    fun setDefaultSubtitleLanguage(languageCode: String?) {
        viewModelScope.launch { playerPreferencesRepository.setDefaultSubtitleLanguage(languageCode) }
    }
}
