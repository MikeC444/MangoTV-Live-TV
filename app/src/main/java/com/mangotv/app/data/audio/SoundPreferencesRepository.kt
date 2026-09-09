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
    val selectedBootSound: BootSound = BootSound.BOOT_SOUND_1
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
