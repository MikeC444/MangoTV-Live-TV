package com.mangotv.app.data.audio

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.soundPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_sound_preferences")

@Serializable
data class SoundPreferences(
    // Applies to the nav/click/back sounds only (see UiSoundPlayer) -- boot
    // audio was a separate, since-removed chime system with its own on/off
    // control (a boot video, if any, now carries its own embedded audio).
    val navigationVolume: Float = 0.5f
)

/** Same DataStore+JSON pattern as PlayerPreferencesRepository, applied to the nav/click sound volume. */
class SoundPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _preferences = MutableStateFlow(SoundPreferences())
    val preferences: StateFlow<SoundPreferences> = _preferences.asStateFlow()

    init {
        scope.launch { _preferences.value = readPersisted() }
    }

    // Updates the in-memory value BEFORE the disk write -- the volume slider
    // previews itself by playing a nav tick right after calling this (see
    // SoundSettingsViewModel.setNavigationVolume), and UiSoundPlayer reads
    // its volume from [preferences] reactively, so that preview needs the
    // new value visible immediately rather than only after a background
    // dispatch + disk write round trip.
    suspend fun setNavigationVolume(volume: Float) {
        val updated = _preferences.value.copy(navigationVolume = volume.coerceIn(0f, 1f))
        _preferences.value = updated
        withContext(Dispatchers.IO) { persist(updated) }
    }

    private suspend fun readPersisted(): SoundPreferences {
        val raw = appContext.soundPreferencesDataStore.data.first()[PREFERENCES_KEY] ?: return SoundPreferences()
        return runCatching { json.decodeFromString(SoundPreferences.serializer(), raw) }.getOrDefault(SoundPreferences())
    }

    private suspend fun persist(preferences: SoundPreferences) {
        val raw = json.encodeToString(preferences)
        appContext.soundPreferencesDataStore.edit { it[PREFERENCES_KEY] = raw }
    }

    companion object {
        private val PREFERENCES_KEY = stringPreferencesKey("sound_preferences_json")
    }
}
