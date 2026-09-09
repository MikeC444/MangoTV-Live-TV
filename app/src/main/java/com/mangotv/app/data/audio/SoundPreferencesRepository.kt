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
    val selectedBootSound: BootSound = BootSound.BOOT_SOUND_1,
    // Applies to the nav/click/back sounds only (see UiSoundPlayer) -- the
    // boot chime has no volume control of its own, only on/off (BootSound.NONE).
    val navigationVolume: Float = 0.5f
)

/** Same DataStore+JSON pattern as PlayerPreferencesRepository, applied to the boot-sound choice. */
class SoundPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _preferences = MutableStateFlow(SoundPreferences())
    val preferences: StateFlow<SoundPreferences> = _preferences.asStateFlow()

    init {
        scope.launch { _preferences.value = readPersisted() }
    }

    suspend fun setSelectedBootSound(sound: BootSound) = withContext(Dispatchers.IO) {
        val updated = _preferences.value.copy(selectedBootSound = sound)
        _preferences.value = updated
        persist(updated)
    }

    // Updates the in-memory value BEFORE the disk write, unlike
    // setSelectedBootSound above -- the volume slider previews itself by
    // playing a nav tick right after calling this (see
    // SoundSettingsViewModel.setNavigationVolume), and UiSoundPlayer reads
    // its volume from [preferences] reactively, so that preview needs the
    // new value visible immediately rather than only after a background
    // dispatch + disk write round trip.
    suspend fun setNavigationVolume(volume: Float) {
        val updated = _preferences.value.copy(navigationVolume = volume.coerceIn(0f, 1f))
        _preferences.value = updated
        withContext(Dispatchers.IO) { persist(updated) }
    }

    /**
     * Reads straight from disk rather than [preferences]' current value --
     * used once, at cold boot, to pick which chime to play before the init
     * block's own background read above is guaranteed to have finished.
     * [preferences] itself stays the reactive source for Settings > Sounds,
     * which doesn't have that same "only matters once, right now" timing
     * pressure.
     */
    suspend fun awaitSelectedBootSound(): BootSound = readPersisted().selectedBootSound

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
