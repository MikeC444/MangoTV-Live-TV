package com.mangotv.app.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.playerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_player_preferences")

/**
 * Same DataStore+JSON pattern as AddonRepository, applied to a single
 * always-present record instead of a list keyed by id.
 */
class PlayerPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _preferences = MutableStateFlow(PlayerPreferences())
    val preferences: StateFlow<PlayerPreferences> = _preferences.asStateFlow()

    /**
     * Fired after a genuine local mutation finishes persisting —
     * SettingsSyncRepository (Milestone 6) hooks this to push the change
     * to the cloud. Deliberately never invoked from [applyRemote], so a
     * value pulled down from the server can't turn around and trigger an
     * immediate, redundant push right back up.
     */
    var onLocalChange: (() -> Unit)? = null

    init {
        scope.launch { _preferences.value = readPersisted() }
    }

    suspend fun setAutoplayNextEpisode(enabled: Boolean) = update { it.copy(autoplayNextEpisode = enabled) }

    suspend fun setSkipIntroEnabled(enabled: Boolean) = update { it.copy(skipIntroEnabled = enabled) }

    /** Applies a value pulled from the server — persists locally without notifying [onLocalChange]; see its own kdoc for why. */
    suspend fun applyRemote(preferences: PlayerPreferences) = withContext(Dispatchers.IO) {
        _preferences.value = preferences
        persist(preferences)
    }

    private suspend fun update(transform: (PlayerPreferences) -> PlayerPreferences) = withContext(Dispatchers.IO) {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        persist(updated)
        onLocalChange?.invoke()
    }

    private suspend fun readPersisted(): PlayerPreferences {
        val prefs = appContext.playerPreferencesDataStore.data.first()
        val raw = prefs[PREFERENCES_KEY] ?: return PlayerPreferences()
        return runCatching { json.decodeFromString(PlayerPreferences.serializer(), raw) }.getOrDefault(PlayerPreferences())
    }

    private suspend fun persist(preferences: PlayerPreferences) {
        val raw = json.encodeToString(preferences)
        appContext.playerPreferencesDataStore.edit { it[PREFERENCES_KEY] = raw }
    }

    companion object {
        private val PREFERENCES_KEY = stringPreferencesKey("player_preferences_json")
    }
}
