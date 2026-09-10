package com.mangotv.app.data.provider

import com.mangotv.app.data.addon.StremioAddonClient
import com.mangotv.app.data.addon.toContent
import com.mangotv.app.data.addon.toStream
import com.mangotv.app.data.model.AddonCatalogDef
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.data.model.Stream
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private val SUPPORTED_CATALOG_TYPES = setOf("movie", "series")

// Addons like Cinemeta expose only one base catalog per type and rely on a
// "genre" extra's declared options to produce the rest (Action, Comedy,
// ...). Now that Settings > Home Rows lets users hide rows they don't want,
// this only needs to be a safety ceiling against a pathological addon
// declaring hundreds of genres, not a curation mechanism -- capping low used
// to mean most of an addon's real genres were simply never fetchable at all,
// which is why they didn't show up as options anywhere.
private const val MAX_GENRE_ROWS = 30

// Stremio's conventional catalog page size -- "skip" is an item-count
// offset, not a page number, so this is an assumption about how many items
// an addon returns per response. If an addon's actual page size differs,
// getMoreItemsByType/getMoreGenreItems's dedup still prevents duplicates
// showing up; the only consequence is possibly a small gap or overlap
// between pages, not a crash.
private const val PAGE_SIZE = 100

// How many base/genre row requests getHomeSections() fans out per batch --
// matches the batch size Nuvio (a comparable Stremio-client app) uses for
// the same job. Small on purpose: it's not about raw throughput (this app's
// own OkHttp client already allows far more concurrent requests than this),
// it's about how soon the FIRST batch -- and therefore the first visible
// rows -- lands, and about being a reasonably polite load against a shared,
// free community addon server rather than firing 30 requests at once.
private const val HOME_BATCH_SIZE = 4

/**
 * A [CatalogProvider] backed by a real, user-installed Stremio-protocol
 * addon. It normalizes whatever the addon returns into Mango TV's own
 * [Content]/[HomeSection] models — the rest of the app never touches the
 * addon's wire format directly.
 */
class StremioAddonProvider(
    private val manifestUrl: String,
    private val manifest: AddonManifest,
    private val client: StremioAddonClient
) : CatalogProvider {

    override val id: String = manifest.id
    override val name: String = manifest.name

    private val supportedCatalogs = manifest.catalogs.filter { it.type in SUPPORTED_CATALOG_TYPES }

    // Addons like Cinemeta declare a separate catalog per content type
    // (movie/top, series/top) that otherwise mirror each other -- same
    // genre options, same intent -- but nothing in the Stremio protocol
    // guarantees they share a literal catalog id, so matching catalogs
    // across types by GENRE NAME (rather than by id, which silently failed
    // to merge anything and produced one "Action" row per type instead of
    // one combined row) is what actually merges movies and TV shows into a
    // single row per genre. Fetches run in batches of HOME_BATCH_SIZE (see
    // its own doc) rather than all at once, emitting each batch's rows as
    // soon as it resolves -- see buildSectionsFlow.
    override fun getHomeSections(): Flow<List<HomeSection>> = buildSectionsFlow(supportedCatalogs, rowKeyPrefix = "")

    // Movies/TV Shows show one flattened, shuffled row -- no genre
    // breakdown -- so unlike getHomeSections() this deliberately does NOT
    // fan out one request per declared genre (previously up to
    // MAX_GENRE_ROWS extra HTTP requests just to throw the grouping away
    // again on the client). The base catalog alone is plenty of content for
    // a shuffled browse row.
    override suspend fun getSectionsByType(type: ContentType): List<HomeSection> {
        val stremioType = if (type == ContentType.TV_SHOW) "series" else "movie"
        val catalogs = supportedCatalogs.filter { it.type == stremioType }
        val baseCatalogs = catalogs.filter { catalogDef ->
            catalogDef.extra.firstOrNull { it.name == "genre" }?.isRequired != true
        }
        if (baseCatalogs.isEmpty()) return emptyList()
        val title = baseCatalogs.firstNotNullOfOrNull { it.name } ?: manifest.name
        return listOfNotNull(fetchMergedSection(baseCatalogs, title = title, extra = emptyMap(), rowKey = "${stremioType}_base"))
    }

    override suspend fun getAvailableGenres(): List<String> = declaredGenres(supportedCatalogs)

    override suspend fun getGenreSection(genre: String): HomeSection? {
        val catalogsForGenre = catalogsMatchingGenre(genre)
        return fetchMergedSection(catalogsForGenre, title = genre, extra = mapOf("genre" to genre), rowKey = "genre_$genre")
    }

    override suspend fun getMoreItemsByType(type: ContentType, page: Int): List<Content> {
        val stremioType = if (type == ContentType.TV_SHOW) "series" else "movie"
        val baseCatalogs = supportedCatalogs.filter { it.type == stremioType }
            .filter { catalogDef -> catalogDef.extra.firstOrNull { it.name == "genre" }?.isRequired != true }
        if (baseCatalogs.isEmpty()) return emptyList()
        return fetchPage(baseCatalogs, extra = emptyMap(), page = page)
    }

    override suspend fun getMoreGenreItems(genre: String, page: Int): List<Content> {
        val catalogsForGenre = catalogsMatchingGenre(genre)
        if (catalogsForGenre.isEmpty()) return emptyList()
        return fetchPage(catalogsForGenre, extra = mapOf("genre" to genre), page = page)
    }

    // Hybrid: real server-side search for any catalog that declares a
    // "search" extra (interleaved+deduped across them same as any other
    // merged row), falling back to a client-side title match over the base
    // catalogs when no catalog supports search or the search itself comes
    // back empty -- so Search still returns something for an addon like
    // Cinemeta that may not declare search support at all.
    override suspend fun search(query: String): List<Content> = coroutineScope {
        if (query.isBlank()) return@coroutineScope emptyList()

        val searchableCatalogs = supportedCatalogs.filter { catalogDef ->
            catalogDef.extra.any { it.name == "search" }
        }
        if (searchableCatalogs.isNotEmpty()) {
            val perCatalog = searchableCatalogs.map { catalogDef ->
                async {
                    runCatching { client.fetchCatalog(manifestUrl, catalogDef.type, catalogDef.id, mapOf("search" to query)) }
                        .getOrNull()
                        ?.map { it.toContent(providerId = id) }
                        .orEmpty()
                }
            }.awaitAll()
            val serverResults = interleave(perCatalog).distinctBy { it.id }
            if (serverResults.isNotEmpty()) return@coroutineScope serverResults
        }

        val baseCatalogs = supportedCatalogs.filter { catalogDef ->
            catalogDef.extra.firstOrNull { it.name == "genre" }?.isRequired != true
        }
        val perBaseCatalog = baseCatalogs.map { catalogDef ->
            async {
                runCatching { client.fetchCatalog(manifestUrl, catalogDef.type, catalogDef.id) }
                    .getOrNull()
                    ?.map { it.toContent(providerId = id) }
                    .orEmpty()
            }
        }.awaitAll()
        interleave(perBaseCatalog).distinctBy { it.id }.filter { it.title.contains(query, ignoreCase = true) }
    }

    // Some addons declare year filters (e.g. "2026", "2025", ...) under the
    // same "genre" extra as real genre names. GenresViewModel extends that
    // declared range further back (e.g. down to 2016) for the picker list,
    // so a selected year here may not be one this catalog's own `options`
    // literally lists -- matching by "does this catalog support year
    // filtering at all" instead of exact membership lets those extended
    // years still resolve to real results instead of always coming back
    // empty. Plain genre names are unaffected -- still an exact match.
    private fun catalogsMatchingGenre(genre: String): List<AddonCatalogDef> =
        supportedCatalogs.filter { catalogDef ->
            val options = catalogDef.extra.firstOrNull { it.name == "genre" }?.options.orEmpty()
            if (genre.isYear()) options.any { it.isYear() } else genre in options
        }

    // Shared by getMoreItemsByType/getMoreGenreItems: fetches one "skip"
    // page across every matching catalog in parallel and interleaves the
    // results, same merge behavior fetchMergedSection uses for page 0 --
    // just without building a titled HomeSection, since both pagination
    // callers only ever want the flat item list.
    private suspend fun fetchPage(catalogDefs: List<AddonCatalogDef>, extra: Map<String, String>, page: Int): List<Content> = coroutineScope {
        val pagedExtra = extra + ("skip" to (page * PAGE_SIZE).toString())
        val perCatalogItems = catalogDefs.map { catalogDef ->
            async {
                runCatching { client.fetchCatalog(manifestUrl, catalogDef.type, catalogDef.id, pagedExtra) }
                    .getOrNull()
                    ?.map { it.toContent(providerId = id) }
                    .orEmpty()
            }
        }.awaitAll()
        interleave(perCatalogItems)
    }

    // Union of every genre any supported catalog declares, capped as a
    // safety ceiling (see MAX_GENRE_ROWS) rather than a curation mechanism
    // now that Settings > Home Rows lets users hide rows they don't want.
    private fun declaredGenres(catalogs: List<AddonCatalogDef>): List<String> =
        catalogs.flatMap { it.extra.firstOrNull { extra -> extra.name == "genre" }?.options.orEmpty() }
            .distinct()
            .take(MAX_GENRE_ROWS)

    // One row per catalog family, PLUS one additional row per genre the
    // family declares -- emitted in HOME_BATCH_SIZE-sized batches (base row
    // always in the first batch) rather than one final list, so Home can
    // reveal rows as they arrive instead of waiting for every genre to
    // resolve. rowKeyPrefix mirrors the plain-list version this replaced,
    // kept for parity even though getSectionsByType doesn't go through this
    // path (it only ever wants the base row, no genre fan-out).
    private fun buildSectionsFlow(catalogs: List<AddonCatalogDef>, rowKeyPrefix: String): Flow<List<HomeSection>> = flow {
        // Base ("no genre filter") row: every catalog that can answer an
        // unfiltered request (its genre extra, if any, isn't required)
        // merges into one row, regardless of type.
        val baseCatalogs = catalogs.filter { catalogDef ->
            catalogDef.extra.firstOrNull { it.name == "genre" }?.isRequired != true
        }
        val baseRowFetch: (suspend () -> HomeSection?)? = if (baseCatalogs.isNotEmpty()) {
            // Prefer the catalog's own declared name (Cinemeta calls its
            // base catalog "Popular") over the addon's name -- besides
            // being the more accurate label, it's also what lets this row
            // match DEFAULT_ROW_PRIORITY's "popular"/"featured" entries and
            // sort to the top instead of getting lost among 30 genre rows.
            val title = baseCatalogs.firstNotNullOfOrNull { it.name } ?: manifest.name
            { fetchMergedSection(baseCatalogs, title = title, extra = emptyMap(), rowKey = "${rowKeyPrefix}base") }
        } else {
            null
        }

        // Genre rows: the union of every genre any of these catalogs
        // declares, each merging every catalog (any type) that lists it.
        val genreRowFetches = declaredGenres(catalogs).map { genre ->
            val catalogsForGenre = catalogs.filter { catalogDef ->
                genre in catalogDef.extra.firstOrNull { extra -> extra.name == "genre" }?.options.orEmpty()
            }
            suspend { fetchMergedSection(catalogsForGenre, title = genre, extra = mapOf("genre" to genre), rowKey = "$rowKeyPrefix$genre") }
        }

        // Base row first (guarantees it lands in the first batch), then
        // genres in declared order -- chunking preserves that order across
        // batches (awaitAll returns results in input order, not completion
        // order), it just controls how many requests are in flight at once
        // and how often a batch's worth of rows gets published.
        val allFetches = listOfNotNull(baseRowFetch) + genreRowFetches
        allFetches.chunked(HOME_BATCH_SIZE).forEach { batch ->
            val ready = coroutineScope {
                batch.map { fetchRow -> async { fetchRow() } }.awaitAll()
            }.filterNotNull()
            if (ready.isNotEmpty()) emit(ready)
        }
    }

    // Fetches every catalog matched for this row in parallel and interleaves
    // their results (movie[0], series[0], movie[1], series[1], ...) rather
    // than concatenating, so a merged row actually reads as mixed content
    // instead of "all the movies, then all the shows".
    private suspend fun fetchMergedSection(
        catalogDefs: List<AddonCatalogDef>,
        title: String,
        extra: Map<String, String>,
        rowKey: String
    ): HomeSection? = coroutineScope {
        val perCatalogItems = catalogDefs.map { catalogDef ->
            async {
                runCatching { client.fetchCatalog(manifestUrl, catalogDef.type, catalogDef.id, extra) }
                    .getOrNull()
                    ?.map { it.toContent(providerId = id) }
                    .orEmpty()
            }
        }.awaitAll()

        // Merging multiple catalogs into one row can produce the same title
        // twice -- e.g. the movie and series catalogs both returning an
        // entry under the same id for a given genre -- and ContentRow's
        // LazyRow keys items by Content.id, which crashes outright
        // (IllegalArgumentException: "Key ... was already used") rather
        // than silently rendering a duplicate. distinctBy keeps the first
        // occurrence, matching interleave's ordering.
        val items = interleave(perCatalogItems).distinctBy { it.id }
        if (items.isEmpty()) return@coroutineScope null

        HomeSection(
            id = "${manifest.id}_$rowKey",
            title = title,
            items = items
        )
    }

    override suspend fun getDetails(type: ContentType, id: String): Content? {
        val stremioType = if (type == ContentType.TV_SHOW) "series" else "movie"
        return runCatching { client.fetchMeta(manifestUrl, stremioType, id) }
            .getOrNull()
            ?.toContent(providerId = this.id)
    }

    override suspend fun getStreams(type: ContentType, id: String, season: Int?, episode: Int?): List<Stream> {
        val stremioType = if (type == ContentType.TV_SHOW) "series" else "movie"
        val requestId = if (season != null && episode != null) "$id:$season:$episode" else id
        return runCatching { client.fetchStreams(manifestUrl, stremioType, requestId) }
            .getOrDefault(emptyList())
            .map { it.toStream(providerId = this.id, providerLabel = this.name) }
    }
}

private fun String.isYear(): Boolean = toIntOrNull()?.let { it in 1900..2100 } == true

private fun <T> interleave(lists: List<List<T>>): List<T> {
    if (lists.size == 1) return lists[0]
    val result = mutableListOf<T>()
    val maxSize = lists.maxOfOrNull { it.size } ?: 0
    for (i in 0 until maxSize) {
        for (list in lists) {
            if (i < list.size) result += list[i]
        }
    }
    return result
}
