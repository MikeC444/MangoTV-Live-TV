package com.mangotv.app.data.history

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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.continueWatchingDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_continue_watching")

/**
 * One entry per title (movie or whole show) currently resumable -- the
 * local mirror of the continue_watching table's shape (Milestone 8).
 * Keyed by (providerId, contentId, contentType), the same natural key
 * SavedListItem uses for My List.
 */
@Serializable
data class ContinueWatchingEntry(
    val providerId: String,
    val contentId: String,
    val contentType: ContentType,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val episodeTitle: String? = null,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: String
)

/**
 * Backs Home's Continue Watching row -- the local read-through cache
 * ContinueWatchingSyncRepository keeps mirrored to the account's
 * continue_watching table. Same DataStore+JSON persistence pattern as
 * MyListRepository, but deliberately has no onLocalChange hook of its own:
 * unlike My List (toggled from several independent UI call sites),
 * everything that changes this domain flows through one orchestrator,
 * ContinueWatchingSyncRepository.reportProgress(), which is what the
 * player calls and which itself decides when to write here and when to
 * push to the server -- so there's no separate "local mutation happened,
 * now tell the sync layer" step to hook.
 */
class ContinueWatchingRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow<List<ContinueWatchingEntry>>(emptyList())
    val items: StateFlow<List<ContinueWatchingEntry>> = _items.asStateFlow()

    init {
        scope.launch { _items.value = readPersisted() }
    }

    /** The stored resume point for this exact title, if any -- read by PlayerViewModel to seek on load. A synchronous snapshot read (not suspend): the player needs this the moment it builds its Ready state, not after an extra dispatch. */
    fun findResumePoint(providerId: String, contentId: String, contentType: ContentType): ContinueWatchingEntry? =
        _items.value.firstOrNull { it.matchesKey(providerId, contentId, contentType) }

    /** Adds or refreshes this title's entry, moved to the front (most-recently-watched-first, matching the server's own last_watched_at DESC ordering). */
    suspend fun upsert(entry: ContinueWatchingEntry) = withContext(Dispatchers.IO) {
        val updated = listOf(entry) + _items.value.filterNot { it.matchesKey(entry.providerId, entry.contentId, entry.contentType) }
        _items.value = updated
        persist(updated)
    }

    /** Removes this title's entry (a completed report, or a raced-away removal reconciled from the server's response). */
    suspend fun remove(providerId: String, contentId: String, contentType: ContentType) = withContext(Dispatchers.IO) {
        val updated = _items.value.filterNot { it.matchesKey(providerId, contentId, contentType) }
        _items.value = updated
        persist(updated)
    }

    /** Applies the server's current active list (a pull), replacing the local cache wholesale. */
    suspend fun applyRemote(items: List<ContinueWatchingEntry>) = withContext(Dispatchers.IO) {
        _items.value = items
        persist(items)
    }

    private fun ContinueWatchingEntry.matchesKey(providerId: String, contentId: String, contentType: ContentType) =
        this.providerId == providerId && this.contentId == contentId && this.contentType == contentType

    private suspend fun readPersisted(): List<ContinueWatchingEntry> {
        val raw = appContext.continueWatchingDataStore.data.first()[ITEMS_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(ContinueWatchingEntry.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun persist(items: List<ContinueWatchingEntry>) {
        val raw = json.encodeToString(ListSerializer(ContinueWatchingEntry.serializer()), items)
        appContext.continueWatchingDataStore.edit { it[ITEMS_KEY] = raw }
    }

    companion object {
        private val ITEMS_KEY = stringPreferencesKey("continue_watching_json")
    }
}
