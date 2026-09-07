package com.mangotv.app.data.livetv

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.config.LiveTvConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

private val Context.liveTvCacheMetaDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_live_tv_cache_meta")

sealed interface LiveTvCatalogState {
    data object Loading : LiveTvCatalogState
    data class Loaded(val sections: List<ChannelSection>, val allChannels: List<Channel>) : LiveTvCatalogState
    data class Error(val message: String) : LiveTvCatalogState
    data object Empty : LiveTvCatalogState
}

/**
 * Fetches, parses, and caches the configured IPTV playlist (see
 * LiveTvConfig.iptvPlaylistUrl). The playlist URL and the optional
 * group-title allow-list are both fully configurable precisely so the
 * shipped default -- iptv-org's public aggregate index, which mixes streams
 * of unverified rights status -- can be narrowed or swapped for a licensed/
 * private playlist without touching any code; see LiveTvConfig's own doc
 * for why that legal judgment call is never made here.
 *
 * The playlist is fetched and parsed at most once per [CACHE_TTL_MS] window
 * -- parsed channels are cached to a small JSON file on disk, so reopening
 * Live TV (or restarting the app) within that window skips the network
 * fetch and the M3U parse entirely, and moving D-pad focus around the
 * already-loaded rails never touches this repository again.
 */
class LiveTvRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheFile: File get() = File(appContext.cacheDir, "live_tv_channels_cache.json")

    private val _state = MutableStateFlow<LiveTvCatalogState>(LiveTvCatalogState.Loading)
    val state: StateFlow<LiveTvCatalogState> = _state.asStateFlow()

    fun channelById(id: String): Channel? =
        (_state.value as? LiveTvCatalogState.Loaded)?.allChannels?.find { it.id == id }

    fun load(forceRefresh: Boolean = false) {
        scope.launch { loadInternal(forceRefresh) }
    }

    private suspend fun loadInternal(forceRefresh: Boolean) = loadMutex.withLock {
        if (!forceRefresh && _state.value is LiveTvCatalogState.Loaded) return@withLock

        _state.value = LiveTvCatalogState.Loading
        val channels = (if (!forceRefresh) readCacheIfFresh() else null)
            ?: fetchAndParse()
            ?: readCacheFile() // network failed -- serve a stale cache rather than nothing, if we have one

        if (channels == null) {
            _state.value = LiveTvCatalogState.Error("Live TV is temporarily unavailable.")
            return@withLock
        }
        if (channels.isEmpty()) {
            _state.value = LiveTvCatalogState.Empty
            return@withLock
        }

        _state.value = LiveTvCatalogState.Loaded(buildSections(channels), channels)
    }

    private suspend fun fetchAndParse(): List<Channel>? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(LiveTvConfig.iptvPlaylistUrl).build()
            val body = httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                response.body?.string() ?: error("Empty playlist response")
            }
            val parsed = M3uParser.parse(body)
            writeCache(parsed)
            parsed
        }.getOrNull()
    }

    private fun buildSections(channels: List<Channel>): List<ChannelSection> {
        val sections = mutableListOf<ChannelSection>()
        val distinctChannels = channels.distinctBy { it.id }

        val featured = distinctChannels.take(FEATURED_COUNT)
        if (featured.isNotEmpty()) sections += ChannelSection("featured", "Featured", featured)

        distinctChannels.filter { !it.country.isNullOrBlank() }
            .groupBy { it.country!!.trim().uppercase() }
            .toList()
            .sortedByDescending { it.second.size }
            .take(MAX_COUNTRY_RAILS)
            .forEach { (country, items) -> sections += ChannelSection("country_$country", country, items.take(MAX_RAIL_SIZE)) }

        distinctChannels.filter { !it.groupTitle.isNullOrBlank() }
            .groupBy { it.groupTitle!!.trim() }
            .toList()
            .sortedByDescending { it.second.size }
            .take(MAX_CATEGORY_RAILS)
            .forEach { (group, items) -> sections += ChannelSection("group_$group", group, items.take(MAX_RAIL_SIZE)) }

        return sections
    }

    private suspend fun readCacheIfFresh(): List<Channel>? {
        val timestamp = appContext.liveTvCacheMetaDataStore.data.first()[CACHE_TIMESTAMP_KEY] ?: return null
        if (System.currentTimeMillis() - timestamp > CACHE_TTL_MS) return null
        return readCacheFile()
    }

    private fun readCacheFile(): List<Channel>? = runCatching {
        if (!cacheFile.exists()) return null
        json.decodeFromString(ListSerializer(Channel.serializer()), cacheFile.readText())
    }.getOrNull()

    private suspend fun writeCache(channels: List<Channel>) {
        runCatching { cacheFile.writeText(json.encodeToString(ListSerializer(Channel.serializer()), channels)) }
        appContext.liveTvCacheMetaDataStore.edit { it[CACHE_TIMESTAMP_KEY] = System.currentTimeMillis() }
    }

    companion object {
        private val CACHE_TIMESTAMP_KEY = longPreferencesKey("live_tv_cache_timestamp")
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(12)
        private const val FEATURED_COUNT = 16
        private const val MAX_COUNTRY_RAILS = 6
        private const val MAX_CATEGORY_RAILS = 8
        private const val MAX_RAIL_SIZE = 25
    }
}
