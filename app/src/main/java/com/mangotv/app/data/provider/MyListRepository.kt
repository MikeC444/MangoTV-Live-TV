package com.mangotv.app.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.util.Iso8601
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

private val Context.myListDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_my_list")

/**
 * A minimal, lightweight record of a saved title -- just enough to render a
 * ContentCard and navigate to Detail (which re-fetches full detail from the
 * network on open regardless) without persisting Content itself. Content
 * isn't @Serializable and its full graph (Genre, CastMember, WatchProgress,
 * Episode, Season) would need annotating unnecessarily just for this.
 */
@Serializable
data class SavedListItem(
    val id: String,
    val type: ContentType,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val year: Int?,
    val rating: Double?,
    val providerId: String,
    val addedAtMillis: Long = System.currentTimeMillis(),
    /**
     * This item's own last-modified time, ISO-8601 UTC — the cloud sync
     * (Milestone 7) analog of PlayerPreferences'/HomeRowPreferences'
     * single account-wide updatedAt, kept per item here since watchlist
     * sync is item-level, not a whole-list blob. Defaulted (not required)
     * so a JSON blob persisted by a pre-Milestone-7 build still decodes —
     * such an item is treated as modified "now" the first time it's read,
     * which only matters once it's next toggled (this milestone doesn't
     * bulk-push pre-existing local items; see WatchlistSyncRepository).
     */
    val updatedAt: String = Iso8601.nowString()
)

/** What WatchlistSyncRepository (Milestone 7) reacts to after a genuine local toggle — never fired from [MyListRepository.applyRemote]. */
sealed interface WatchlistChange {
    data class Added(val item: SavedListItem) : WatchlistChange
    data class Removed(val providerId: String, val contentId: String, val contentType: ContentType, val updatedAt: String) : WatchlistChange
}

/**
 * Backs My List: the two existing "Add to Watchlist"/"Add to My List" stub
 * buttons on Detail and Home's hero, and the My List browse screen. Same
 * DataStore+JSON persistence pattern as HomeRowPreferencesRepository.
 */
class MyListRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _items = MutableStateFlow<List<SavedListItem>>(emptyList())
    val items: StateFlow<List<SavedListItem>> = _items.asStateFlow()

    /** Fired after a genuine local add/remove finishes persisting — WatchlistSyncRepository (Milestone 7) hooks this to push just the one changed item. Never invoked from [applyRemote]. */
    var onLocalChange: ((WatchlistChange) -> Unit)? = null

    init {
        scope.launch { _items.value = readPersisted() }
    }

    /** Adds [content] if it isn't already saved, removes it (by id) if it is. */
    suspend fun toggle(content: Content) = withContext(Dispatchers.IO) {
        val providerId = content.providerId ?: return@withContext
        val current = _items.value
        if (current.any { it.id == content.id }) {
            val updated = current.filterNot { it.id == content.id }
            _items.value = updated
            persist(updated)
            onLocalChange?.invoke(
                WatchlistChange.Removed(
                    providerId = providerId,
                    contentId = content.id,
                    contentType = content.type,
                    updatedAt = Iso8601.nowString()
                )
            )
        } else {
            val item = SavedListItem(
                id = content.id,
                type = content.type,
                title = content.title,
                posterUrl = content.posterUrl,
                backdropUrl = content.backdropUrl,
                year = content.year,
                rating = content.rating,
                providerId = providerId,
                updatedAt = Iso8601.nowString()
            )
            val updated = current + item
            _items.value = updated
            persist(updated)
            onLocalChange?.invoke(WatchlistChange.Added(item))
        }
    }

    /** Applies the server's current active-item list — persists locally without notifying [onLocalChange]; see its own kdoc for why. Replaces the local list wholesale (pull always trusts the server as source of truth), which is safe here because push is item-level, not the other way around. */
    suspend fun applyRemote(items: List<SavedListItem>) = withContext(Dispatchers.IO) {
        _items.value = items
        persist(items)
    }

    private suspend fun readPersisted(): List<SavedListItem> {
        val raw = appContext.myListDataStore.data.first()[MY_LIST_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(SavedListItem.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun persist(items: List<SavedListItem>) {
        val raw = json.encodeToString(ListSerializer(SavedListItem.serializer()), items)
        appContext.myListDataStore.edit { it[MY_LIST_KEY] = raw }
    }

    companion object {
        private val MY_LIST_KEY = stringPreferencesKey("my_list_json")
    }
}
