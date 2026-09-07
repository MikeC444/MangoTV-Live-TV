package com.mangotv.app.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.Content
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
    val addedAtMillis: Long = System.currentTimeMillis()
)

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

    init {
        scope.launch { _items.value = readPersisted() }
    }

    /** Adds [content] if it isn't already saved, removes it (by id) if it is. */
    suspend fun toggle(content: Content) = withContext(Dispatchers.IO) {
        val providerId = content.providerId ?: return@withContext
        val current = _items.value
        val updated = if (current.any { it.id == content.id }) {
            current.filterNot { it.id == content.id }
        } else {
            current + SavedListItem(
                id = content.id,
                type = content.type,
                title = content.title,
                posterUrl = content.posterUrl,
                backdropUrl = content.backdropUrl,
                year = content.year,
                rating = content.rating,
                providerId = providerId
            )
        }
        _items.value = updated
        persist(updated)
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
