package com.mangotv.app.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.HomeSection
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.homeRowPrefsDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_home_row_prefs")

/**
 * The user's manual row ordering plus which rows they've hidden, both keyed
 * by HomeSection.id (deterministic per catalog/genre — see
 * StremioAddonProvider), so preferences survive addon re-fetches across app
 * restarts.
 */
@Serializable
data class HomeRowPreferences(
    val order: List<String> = emptyList(),
    val hiddenRowIds: Set<String> = emptySet()
) {
    /**
     * Applies this manual order to a freshly-fetched section list: rows the
     * user has explicitly placed keep that relative order. Any row not yet
     * placed (a brand new addon, or a newly added genre) falls back to
     * [DEFAULT_ROW_PRIORITY] -- "featured"/"popular"-style rows first, then
     * genres in a sensible default order -- rather than whatever order the
     * provider happened to return, so a first-run list already looks
     * curated before the user reorders anything themselves. Does not
     * filter hidden rows -- callers decide whether they want those included
     * (Home doesn't; the Home Rows settings list does, dimmed).
     */
    fun applyOrder(sections: List<HomeSection>): List<HomeSection> {
        val byId = sections.associateBy { it.id }
        val ordered = order.mapNotNull { byId[it] }
        val remaining = sections.filterNot { it.id in order }
            .sortedBy { defaultRank(it) }
        return ordered + remaining
    }

    companion object {
        // Lookup by lowercased row title -- used only as the DEFAULT order
        // for rows the user hasn't manually placed yet (see applyOrder).
        // sortedBy is stable, so anything not in this list (a genre this
        // app doesn't specifically know about, or any other addon-specific
        // row name) simply keeps the order the provider returned it in,
        // after everything recognized here. Multiple aliases per genre are
        // included since different addons label the same genre differently
        // (Cinemeta's "Science Fiction" vs. the more common "Sci-Fi").
        private val DEFAULT_ROW_PRIORITY = listOf(
            "featured", "popular", "trending", "trending now", "new releases",
            "top 10 movies", "top 10 tv shows", "recently added",
            "action", "comedy", "horror", "romance", "thriller", "drama",
            "sci-fi", "science fiction", "fantasy", "mystery", "crime",
            "family", "kids", "children", "anime", "animation",
            "documentary", "music", "musical", "adventure"
        )

        private fun defaultRank(section: HomeSection): Int {
            // StremioAddonProvider's unfiltered "base" row for each addon
            // (its id always ends "_base") is conceptually that addon's
            // main/featured row regardless of what it's actually titled --
            // some addons don't label it "Popular"/"Featured" at all, so
            // relying on title text alone would bury it among 30 genre rows
            // instead of leading them. Ranked ahead of every title match.
            if (section.id.endsWith("_base")) return -1
            val rank = DEFAULT_ROW_PRIORITY.indexOf(section.title.trim().lowercase())
            return if (rank >= 0) rank else DEFAULT_ROW_PRIORITY.size
        }
    }
}

/** Persists [HomeRowPreferences] and exposes it as a StateFlow for Home and Settings > Home Rows. */
class HomeRowPreferencesRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    private val _preferences = MutableStateFlow(HomeRowPreferences())
    val preferences: StateFlow<HomeRowPreferences> = _preferences.asStateFlow()

    /**
     * Fired after a genuine local mutation (setRowHidden/moveRow) finishes
     * persisting — SettingsSyncRepository (Milestone 6) hooks this to push
     * the change to the cloud. Deliberately never invoked from
     * [applyRemote], so a value pulled down from the server can't turn
     * around and trigger an immediate, redundant push right back up.
     */
    var onLocalChange: (() -> Unit)? = null

    init {
        scope.launch { _preferences.value = readPersisted() }
    }

    suspend fun setRowHidden(rowId: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        val current = _preferences.value
        val updatedHidden = if (hidden) current.hiddenRowIds + rowId else current.hiddenRowIds - rowId
        update(current.copy(hiddenRowIds = updatedHidden))
        onLocalChange?.invoke()
    }

    /**
     * Reorders [rowId] by [delta] positions (-1 up, +1 down) within
     * [currentDisplayOrder] -- the full list of row ids exactly as Settings
     * is currently showing them, hidden ones included, so the persisted
     * order always reflects the last thing the user actually saw rather
     * than something recomputed independently of the screen state.
     */
    suspend fun moveRow(currentDisplayOrder: List<String>, rowId: String, delta: Int) = withContext(Dispatchers.IO) {
        val ids = currentDisplayOrder.toMutableList()
        val index = ids.indexOf(rowId)
        if (index < 0) return@withContext
        val newIndex = (index + delta).coerceIn(0, ids.lastIndex)
        if (newIndex == index) return@withContext
        ids.removeAt(index)
        ids.add(newIndex, rowId)
        update(_preferences.value.copy(order = ids))
        onLocalChange?.invoke()
    }

    /** Applies a value pulled from the server — persists locally without notifying [onLocalChange]; see its own kdoc for why. */
    suspend fun applyRemote(prefs: HomeRowPreferences) = withContext(Dispatchers.IO) {
        update(prefs)
    }

    private suspend fun update(new: HomeRowPreferences) {
        _preferences.value = new
        persist(new)
    }

    private suspend fun readPersisted(): HomeRowPreferences {
        val raw = appContext.homeRowPrefsDataStore.data.first()[PREFS_KEY] ?: return HomeRowPreferences()
        return runCatching { json.decodeFromString<HomeRowPreferences>(raw) }.getOrDefault(HomeRowPreferences())
    }

    private suspend fun persist(prefs: HomeRowPreferences) {
        val raw = json.encodeToString(prefs)
        appContext.homeRowPrefsDataStore.edit { it[PREFS_KEY] = raw }
    }

    companion object {
        private val PREFS_KEY = stringPreferencesKey("home_row_prefs_json")
    }
}
