package com.mangotv.app.data.player

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.ContentType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.lastSourceDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_last_source")

/**
 * Remembers which Stream the user's playback actually reached "watching"
 * status on (same threshold PlayerViewModel.reportProgress already uses to
 * decide whether a Continue Watching entry exists at all) for each exact
 * title/episode -- keyed the same way, but deliberately its own local-only
 * store rather than a field on ContinueWatchingEntry: Milestone 8's sync
 * protocol (ContinueWatchingSyncRepository/WatchProgressRequest/the server
 * schema) only knows about position/duration, and a per-device "which addon
 * source worked" pick has no business on a cross-device synced record
 * (device A's addons aren't necessarily device B's).
 *
 * SourcesViewModel reads this to skip straight back to the same source
 * when re-opening a title that's already resumable, instead of making the
 * user pick from the list again every time.
 */
class LastSourceRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _entries = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        scope.launch { _entries.value = readPersisted() }
    }

    /** The stream id last used for this exact title/episode, if any -- a synchronous snapshot read, same shape as ContinueWatchingRepository.findResumePoint. */
    fun findLastStreamId(providerId: String, contentId: String, contentType: ContentType, season: Int?, episode: Int?): String? =
        _entries.value[key(providerId, contentId, contentType, season, episode)]

    /**
     * Deliberately NOT suspend, unlike every other write here -- the same
     * reason ContinueWatchingSyncRepository.reportProgress documents for
     * its own non-suspend signature. PlayerViewModel.reportProgress calls
     * this from the player's dispose-time "final report" (PlayerScreen's
     * DisposableEffect.onDispose{}), which isn't a coroutine context and
     * fires right as that ViewModel's own viewModelScope may already be
     * cancelling -- a call site that used to wrap this in
     * viewModelScope.launch{} silently lost exactly this write, the one
     * report that matters most for "the source the user was actually on
     * when they left". Dispatching onto this repository's own long-lived
     * [scope] instead of relying on the caller's means the write survives
     * regardless of what's happening to the caller's own coroutine scope.
     */
    fun setLastStreamId(
        providerId: String,
        contentId: String,
        contentType: ContentType,
        season: Int?,
        episode: Int?,
        streamId: String
    ) {
        scope.launch {
            val updated = _entries.value + (key(providerId, contentId, contentType, season, episode) to streamId)
            _entries.value = updated
            persist(updated)
        }
    }

    /** Wipes the locally-cached map (Milestone 12's account switching) -- same reasoning as ContinueWatchingRepository.clear(): this device is only forgetting its own local copy. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        _entries.value = emptyMap()
        persist(emptyMap())
    }

    private fun key(providerId: String, contentId: String, contentType: ContentType, season: Int?, episode: Int?): String =
        "$providerId|$contentId|${contentType.name}|${season ?: -1}|${episode ?: -1}"

    private suspend fun readPersisted(): Map<String, String> {
        val raw = appContext.lastSourceDataStore.data.first()[ENTRIES_KEY] ?: return emptyMap()
        return runCatching {
            json.decodeFromString(MapSerializer(String.serializer(), String.serializer()), raw)
        }.getOrDefault(emptyMap())
    }

    private suspend fun persist(entries: Map<String, String>) {
        val raw = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), entries)
        appContext.lastSourceDataStore.edit { it[ENTRIES_KEY] = raw }
    }

    companion object {
        private val ENTRIES_KEY = stringPreferencesKey("last_source_json")
    }
}
