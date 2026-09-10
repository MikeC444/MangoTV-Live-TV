package com.mangotv.app.data.provider

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Genre
import com.mangotv.app.data.model.HomeSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.TimeUnit

private val Context.homeCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_home_cache")

/**
 * Just enough of Content to redraw Home's hero + rows instantly on cold
 * boot -- cast/director/watchProgress/seasons are Detail-only fields a
 * catalog preview never populates anyway (same reasoning My List's own
 * SavedListItem DTO already uses; Content itself isn't @Serializable and
 * its full graph would need annotating unnecessarily just for this).
 */
@Serializable
private data class CachedContent(
    val id: String,
    val type: ContentType,
    val title: String,
    val description: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String? = null,
    val year: Int? = null,
    val ageRating: String? = null,
    val runtimeMinutes: Int? = null,
    val rating: Double? = null,
    val genreNames: List<String> = emptyList(),
    val providerId: String? = null
)

@Serializable
private data class CachedHomeSection(
    val id: String,
    val title: String,
    val items: List<CachedContent>
)

@Serializable
private data class CachedHome(
    val hero: List<CachedContent>,
    val sections: List<CachedHomeSection>,
    val cachedAtMillis: Long
)

/**
 * Lets Home paint instantly from the last successful live fetch on cold
 * boot instead of a blank skeleton every single launch -- HomeViewModel
 * still always kicks off a real fetch afterward and overwrites this the
 * moment it completes, so this is purely a "something now" stand-in for
 * "the real thing in a moment", not a substitute for it. Home's catalog
 * data changes too often to serve this cache INSTEAD of a live fetch, so
 * this always refreshes live regardless of the cache's age --
 * [MAX_AGE_MILLIS] only guards against showing something absurdly stale
 * (e.g. the app not opened in weeks) for the brief moment before that
 * live refresh lands.
 */
class HomeCacheRepository(context: Context) {

    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(): Pair<List<Content>, List<HomeSection>>? = withContext(Dispatchers.IO) {
        val raw = appContext.homeCacheDataStore.data.first()[CACHE_KEY] ?: return@withContext null
        runCatching { json.decodeFromString(CachedHome.serializer(), raw) }
            .getOrNull()
            ?.takeIf { System.currentTimeMillis() - it.cachedAtMillis < MAX_AGE_MILLIS }
            ?.let { cached -> cached.hero.map { it.toContent() } to cached.sections.map { it.toHomeSection() } }
    }

    suspend fun write(hero: List<Content>, sections: List<HomeSection>) = withContext(Dispatchers.IO) {
        val cached = CachedHome(
            hero = hero.map { it.toCached() },
            sections = sections.map { section -> CachedHomeSection(section.id, section.title, section.items.map { it.toCached() }) },
            cachedAtMillis = System.currentTimeMillis()
        )
        val raw = json.encodeToString(CachedHome.serializer(), cached)
        appContext.homeCacheDataStore.edit { it[CACHE_KEY] = raw }
    }

    companion object {
        private val CACHE_KEY = stringPreferencesKey("home_cache_json")
        private val MAX_AGE_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}

private fun Content.toCached() = CachedContent(
    id = id,
    type = type,
    title = title,
    description = description,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    logoUrl = logoUrl,
    year = year,
    ageRating = ageRating,
    runtimeMinutes = runtimeMinutes,
    rating = rating,
    genreNames = genres.map { it.name },
    providerId = providerId
)

private fun CachedContent.toContent() = Content(
    id = id,
    type = type,
    title = title,
    description = description,
    posterUrl = posterUrl,
    backdropUrl = backdropUrl,
    logoUrl = logoUrl,
    year = year,
    ageRating = ageRating,
    runtimeMinutes = runtimeMinutes,
    rating = rating,
    genres = genreNames.map { Genre(id = it, name = it) },
    providerId = providerId
)

private fun CachedHomeSection.toHomeSection() = HomeSection(id = id, title = title, items = items.map { it.toContent() })
