package com.mangotv.app.data.addon

import com.mangotv.app.data.model.AddonManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Talks the Stremio addon protocol: fetch a manifest, then use it to pull
 * catalogs, streams and subtitles. This is the one place in the app that
 * knows about the wire format — everything above it works with normalized
 * models.
 */
class StremioAddonClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        // Default maxRequestsPerHost (5) throttles this app's own
        // coroutine-level fan-out (up to MAX_GENRE_ROWS concurrent catalog
        // requests to the same addon) to 5-at-a-time on the wire -- raising
        // it lets more of that already-parallel work actually run at once
        // instead of queueing in batches. Total request volume/pattern the
        // addon server sees is unchanged.
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 16 })
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchManifest(manifestUrl: String): AddonManifest = withContext(Dispatchers.IO) {
        val body = get(manifestUrl)
        json.decodeFromString(AddonManifest.serializer(), body)
    }

    // extra carries the Stremio protocol's per-catalog filter params (most
    // commonly "genre") as a query-string-shaped, URL-encoded path segment:
    // /catalog/movie/top/genre=Action.json. Left empty, the request is
    // identical to the old unfiltered call.
    suspend fun fetchCatalog(
        manifestUrl: String,
        type: String,
        catalogId: String,
        extra: Map<String, String> = emptyMap()
    ): List<StremioMetaPreview> =
        withContext(Dispatchers.IO) {
            val base = AddonUrl.resourceBase(manifestUrl)
            val extraSegment = if (extra.isEmpty()) {
                ""
            } else {
                "/" + extra.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
            }
            val url = "$base/catalog/$type/${encode(catalogId)}$extraSegment.json"
            val body = get(url)
            json.decodeFromString(StremioCatalogResponse.serializer(), body).metas
        }

    suspend fun fetchStreams(manifestUrl: String, type: String, id: String): List<StremioStream> =
        withContext(Dispatchers.IO) {
            val base = AddonUrl.resourceBase(manifestUrl)
            // Series stream ids are "imdbId:season:episode" — encoding the
            // whole id in one pass would percent-encode the colons
            // themselves, breaking the literal path segment addons expect.
            // Encoding each part separately preserves them; a plain
            // colon-free movie id is unaffected (single-element split).
            val encodedId = id.split(":").joinToString(":") { encode(it) }
            val url = "$base/stream/$type/$encodedId.json"
            val body = get(url)
            json.decodeFromString(StremioStreamResponse.serializer(), body).streams
        }

    suspend fun fetchMeta(manifestUrl: String, type: String, id: String): StremioMeta? =
        withContext(Dispatchers.IO) {
            val base = AddonUrl.resourceBase(manifestUrl)
            val url = "$base/meta/$type/${encode(id)}.json"
            val body = get(url)
            json.decodeFromString(StremioMetaResponse.serializer(), body).meta
        }

    // URLEncoder encodes spaces as "+" (form-encoding semantics), but every
    // caller here uses this for literal path segments or extra-arg values
    // (e.g. a "Science Fiction" genre) where a real "%20" is what's
    // expected -- a bare "+" would round-trip back as a literal plus sign
    // on servers that decode with decodeURIComponent rather than full
    // querystring parsing.
    private fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")

    private fun get(url: String): String {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} from $url" }
            return response.body?.string() ?: error("Empty response from $url")
        }
    }
}
