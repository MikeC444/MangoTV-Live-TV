package com.mangotv.app.data.trailer

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "InAppYouTubeExtractor"
private const val EXTRACTOR_TIMEOUT_MS = 30_000L
/** How long a single CDN-reachability probe gets before its candidate is given up on -- see resolveReachableUrl's own kdoc. */
private const val PROBE_TIMEOUT_MS = 6_000L
private const val DEFAULT_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 12; Android TV) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
private const val PREFERRED_SEPARATE_CLIENT = "visionos"

private val VIDEO_ID_REGEX = Regex("^[a-zA-Z0-9_-]{11}$")
private val API_KEY_REGEX = Regex("\"INNERTUBE_API_KEY\":\"([^\"]+)\"")
private val VISITOR_DATA_REGEX = Regex("\"VISITOR_DATA\":\"([^\"]+)\"")
private val QUALITY_LABEL_REGEX = Regex("(\\d{2,4})p")

private data class YouTubeClient(
    val key: String,
    val id: String,
    val version: String,
    val userAgent: String,
    val context: Map<String, Any>,
    val priority: Int
)

private data class WatchConfig(
    val apiKey: String?,
    val visitorData: String?
)

internal data class StreamCandidate(
    val client: String,
    val priority: Int,
    val url: String,
    val score: Double,
    val hasN: Boolean,
    val itag: String,
    val height: Int,
    val fps: Int,
    val ext: String,
    // Only meaningful for audio candidates: false means this format is an
    // alternate-language dub track, not the video's original/default audio.
    // Always true for video/progressive candidates, so it never affects them.
    val isDefaultAudioTrack: Boolean = true
)

private data class ManifestBestVariant(
    val url: String,
    val width: Int,
    val height: Int,
    val bandwidth: Long
)

private data class ManifestCandidate(
    val client: String,
    val priority: Int,
    val manifestUrl: String,
    val selectedVariantUrl: String,
    val height: Int,
    val bandwidth: Long
)

private val DEFAULT_HEADERS = mapOf(
    "accept-language" to "en-US,en;q=0.9",
    "user-agent" to DEFAULT_USER_AGENT
)

// Impersonates YouTube's own official app clients against YouTube's internal
// "innertube" player API (the same backend those apps themselves call) --
// see this file's own kdoc for why, and Nuvio's public sideload build (the
// prior art this was ported from) for the same technique in the wild.
// Multiple clients with fallback priority because any single one of these
// can individually start requiring sign-in or get rate-limited; trying the
// next one is what keeps this working through that.
private val CLIENTS = listOf(
    YouTubeClient(
        key = "visionos",
        id = "101",
        version = "1.02",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
            "(KHTML, like Gecko) Version/26.0 Safari/605.1.15",
        context = mapOf(
            "clientName" to "VISIONOS",
            "clientVersion" to "1.02",
            "deviceMake" to "Apple",
            "deviceModel" to "RealityDevice17,1",
            "osName" to "visionOS",
            "osVersion" to "26.5.23O471",
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 0
    ),
    YouTubeClient(
        key = "android",
        id = "3",
        version = "20.10.35",
        userAgent = "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip",
        context = mapOf(
            "clientName" to "ANDROID",
            "clientVersion" to "20.10.35",
            "osName" to "Android",
            "osVersion" to "14",
            "platform" to "MOBILE",
            "androidSdkVersion" to 34,
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 1
    ),
    YouTubeClient(
        key = "ios",
        id = "5",
        version = "20.10.1",
        userAgent = "com.google.ios.youtube/20.10.1 (iPhone16,2; U; CPU iOS 17_4 like Mac OS X)",
        context = mapOf(
            "clientName" to "IOS",
            "clientVersion" to "20.10.1",
            "deviceModel" to "iPhone16,2",
            "osName" to "iPhone",
            "osVersion" to "17.4.0.21E219",
            "platform" to "MOBILE",
            "hl" to "en",
            "gl" to "US"
        ),
        priority = 2
    )
)

/**
 * Resolves a YouTube video id directly to a playable media URL (or a
 * separate video+audio pair), bypassing YouTube's own web/embed player
 * entirely -- see [com.mangotv.app.ui.trailer.TrailerPlayerScreen]'s own
 * kdoc for why this replaced that approach.
 *
 * This calls YouTube's internal "innertube" player API (the same backend
 * YouTube's own official apps call, not the public Data API) while
 * impersonating one of those apps' client identities. That's a real trade
 * a prior WebView-embed approach didn't carry: it's outside YouTube's
 * Terms of Service and requires ongoing upkeep as YouTube's internal
 * protocol shifts (the multi-client fallback and stale-token retry logic
 * below exist because it already does, periodically) -- accepted here the
 * same way Nuvio's own sideload-only build accepts it (its Play Store
 * build disables in-app trailer playback entirely rather than ship this).
 */
class InAppYouTubeExtractor {
    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // --- Cached watch config (api key + visitor data) ---
    private data class CachedConfig(
        val apiKey: String,
        val visitorData: String?,
        val fetchedAt: Long = System.currentTimeMillis()
    )

    private val cachedConfig = AtomicReference<CachedConfig?>(null)
    private val configMutex = Mutex()

    companion object {
        /** How long cached visitor_data stays valid before a proactive refresh. */
        private const val CONFIG_TTL_MS = 3 * 60 * 60 * 1000L // 3 hours
    }

    /**
     * Returns cached watch config, fetching from the watch page only if:
     *  - No cached config exists yet (first call)
     *  - Cache is older than CONFIG_TTL_MS
     *  - [forceRefresh] is true (e.g. after LOGIN_REQUIRED)
     */
    private suspend fun ensureWatchConfig(forceRefresh: Boolean = false): CachedConfig {
        // Fast path: return valid cache without locking
        if (!forceRefresh) {
            val current = cachedConfig.get()
            if (current != null && !isConfigStale(current)) {
                return current
            }
        }

        // Slow path: fetch new config under mutex (only one fetch at a time)
        return configMutex.withLock {
            // Double-check after acquiring lock
            if (!forceRefresh) {
                val current = cachedConfig.get()
                if (current != null && !isConfigStale(current)) {
                    return@withLock current
                }
            }

            Log.d(TAG, "Fetching watch page for visitor_data (forceRefresh=$forceRefresh)")
            val watchUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ&hl=en"
            val watchResponse = performRequest(
                url = watchUrl,
                method = "GET",
                headers = DEFAULT_HEADERS
            )
            if (!watchResponse.ok) {
                // If we have a stale config, prefer it over failing
                val stale = cachedConfig.get()
                if (stale != null) {
                    Log.w(TAG, "Watch page failed (${watchResponse.status}), using stale config")
                    return@withLock stale
                }
                throw IllegalStateException("Failed to fetch watch page (${watchResponse.status})")
            }

            val parsed = getWatchConfig(watchResponse.body)
            val apiKey = parsed.apiKey ?: "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8" // fallback key
            val newConfig = CachedConfig(
                apiKey = apiKey,
                visitorData = parsed.visitorData
            )
            cachedConfig.set(newConfig)
            Log.d(TAG, "Watch config cached (visitor=${!parsed.visitorData.isNullOrBlank()})")
            newConfig
        }
    }

    private fun isConfigStale(config: CachedConfig): Boolean {
        return System.currentTimeMillis() - config.fetchedAt > CONFIG_TTL_MS
    }

    /** Invalidate cached config so the next extraction re-fetches the watch page. */
    fun invalidateConfig() {
        cachedConfig.set(null)
        Log.d(TAG, "Watch config invalidated")
    }

    suspend fun extractPlaybackSource(youtubeUrl: String): TrailerPlaybackSource? = withContext(Dispatchers.IO) {
        if (youtubeUrl.isBlank()) return@withContext null

        // Log.w rather than Log.d for this and the result below -- on at
        // least one real device tested against, debug-level app logs never
        // reached logcat at all (even freshly cleared, even for a tag/level
        // explicitly requested), while warn-level did. Cheap insurance
        // against losing exactly the two log lines needed to tell whether
        // extraction ran at all and what it found.
        Log.w(TAG, "Starting extraction for ${summarizeUrl(youtubeUrl)}")
        var source: TrailerPlaybackSource? = null
        try {
            source = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                extractPlaybackSourceInternal(youtubeUrl, forceRefreshConfig = false)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (error: Exception) {
            Log.w(TAG, "Extractor failed for $youtubeUrl: ${error.message}")
        }

        // Retry with fresh config if first attempt returned nothing
        if (source == null) {
            Log.d(TAG, "First attempt failed, retrying with fresh watch config...")
            try {
                source = withTimeout(EXTRACTOR_TIMEOUT_MS) {
                    extractPlaybackSourceInternal(youtubeUrl, forceRefreshConfig = true)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (error: Exception) {
                Log.w(TAG, "Extractor retry failed for $youtubeUrl: ${error.message}")
            }
        }

        if (source == null) {
            Log.w(TAG, "Extraction returned no playable source for ${summarizeUrl(youtubeUrl)}")
        } else {
            Log.w(
                TAG,
                "Extraction success for ${summarizeUrl(youtubeUrl)} " +
                    "(video=${summarizeUrl(source.videoUrl)}, audioPresent=${!source.audioUrl.isNullOrBlank()})"
            )
        }

        source
    }

    private suspend fun extractPlaybackSourceInternal(
        youtubeUrl: String,
        forceRefreshConfig: Boolean
    ): TrailerPlaybackSource? {
        val videoId = extractVideoId(youtubeUrl) ?: return null

        // Use cached config instead of fetching the watch page every time
        val config = ensureWatchConfig(forceRefresh = forceRefreshConfig)
        Log.d(TAG, "Using config: apiKey=${config.apiKey.take(10)}... visitor=${!config.visitorData.isNullOrBlank()}")

        val progressive = mutableListOf<StreamCandidate>()
        val adaptiveVideo = mutableListOf<StreamCandidate>()
        val adaptiveAudio = mutableListOf<StreamCandidate>()
        val manifestUrls = mutableListOf<Triple<String, Int, String>>()
        var loginRequiredCount = 0

        for (client in CLIENTS) {
            kotlinx.coroutines.yield()
            try {
                val playerResponse = fetchPlayerResponse(
                    apiKey = config.apiKey,
                    videoId = videoId,
                    client = client,
                    visitorData = config.visitorData,
                    cookieHeader = null
                )

                // Check for LOGIN_REQUIRED which means visitor_data is stale
                val playabilityStatus = playerResponse.mapValue("playabilityStatus")
                val status = playabilityStatus?.stringValue("status")
                if (status == "LOGIN_REQUIRED") {
                    loginRequiredCount++
                    Log.w(TAG, "Client ${client.key}: LOGIN_REQUIRED (visitor may be stale)")
                    continue
                }
                if (status != null && status != "OK") {
                    continue
                }

                val streamingData = playerResponse.mapValue("streamingData") ?: continue
                val hlsManifestUrl = streamingData.stringValue("hlsManifestUrl")
                if (!hlsManifestUrl.isNullOrBlank()) {
                    manifestUrls += Triple(client.key, client.priority, hlsManifestUrl)
                }

                for (format in streamingData.listMapValue("formats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    if (!mimeType.contains("video/") && mimeType.isNotBlank()) continue

                    val height = (format.numberValue("height")
                        ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                        ?: 0.0).toInt()
                    val fps = (format.numberValue("fps") ?: 0.0).toInt()
                    val bitrate = format.numberValue("bitrate")
                        ?: format.numberValue("averageBitrate")
                        ?: 0.0

                    progressive += StreamCandidate(
                        client = client.key,
                        priority = client.priority,
                        url = url,
                        score = videoScore(height, fps, bitrate),
                        hasN = hasNParam(url),
                        itag = format.stringValue("itag").orEmpty(),
                        height = height,
                        fps = fps,
                        ext = if (mimeType.contains("webm")) "webm" else "mp4"
                    )
                }

                for (format in streamingData.listMapValue("adaptiveFormats")) {
                    val url = format.stringValue("url") ?: continue
                    val mimeType = format.stringValue("mimeType").orEmpty()
                    val hasVideo = mimeType.contains("video/")
                    val hasAudio = mimeType.contains("audio/") || mimeType.startsWith("audio/")

                    if (hasVideo) {
                        val height = (format.numberValue("height")
                            ?: parseQualityLabel(format.stringValue("qualityLabel"))?.toDouble()
                            ?: 0.0).toInt()
                        val fps = (format.numberValue("fps") ?: 0.0).toInt()
                        val bitrate = format.numberValue("bitrate")
                            ?: format.numberValue("averageBitrate")
                            ?: 0.0

                        adaptiveVideo += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = videoScore(height, fps, bitrate),
                            hasN = hasNParam(url),
                            itag = format.stringValue("itag").orEmpty(),
                            height = height,
                            fps = fps,
                            ext = if (mimeType.contains("webm")) "webm" else "mp4"
                        )
                    } else if (hasAudio) {
                        val bitrate = format.numberValue("bitrate")
                            ?: format.numberValue("averageBitrate")
                            ?: 0.0
                        val asr = format.numberValue("audioSampleRate") ?: 0.0
                        // Multi-language uploads (common for major-studio trailers)
                        // expose each dub as a separate adaptiveFormats entry with an
                        // audioTrack.audioIsDefault flag. Formats with no audioTrack
                        // are the only audio for that video, so treat them as default.
                        val isDefaultAudioTrack = format.mapValue("audioTrack")
                            ?.booleanValue("audioIsDefault") ?: true

                        adaptiveAudio += StreamCandidate(
                            client = client.key,
                            priority = client.priority,
                            url = url,
                            score = audioScore(bitrate, asr),
                            hasN = hasNParam(url),
                            itag = format.stringValue("itag").orEmpty(),
                            height = 0,
                            fps = 0,
                            ext = if (mimeType.contains("webm")) "webm" else "m4a",
                            isDefaultAudioTrack = isDefaultAudioTrack
                        )
                    }
                }

                Log.w(
                    TAG,
                    "Client ${client.key}: status=$status hls=${!hlsManifestUrl.isNullOrBlank()} " +
                        "progressive=${progressive.count { it.client == client.key }} " +
                        "adaptiveVideo=${adaptiveVideo.count { it.client == client.key }} " +
                        "adaptiveAudio=${adaptiveAudio.count { it.client == client.key }}"
                )
            } catch (error: Exception) {
                Log.w(TAG, "Client ${client.key} failed: ${error.message}")
            }
        }

        // If all clients returned LOGIN_REQUIRED, invalidate config for next attempt
        if (loginRequiredCount == CLIENTS.size) {
            Log.w(TAG, "All ${CLIENTS.size} clients returned LOGIN_REQUIRED, invalidating config")
            invalidateConfig()
            return null
        }

        Log.w(
            TAG,
            "Totals across all clients: manifests=${manifestUrls.size} progressive=${progressive.size} " +
                "adaptiveVideo=${adaptiveVideo.size} adaptiveAudio=${adaptiveAudio.size}"
        )

        if (manifestUrls.isEmpty() && progressive.isEmpty() && adaptiveVideo.isEmpty() && adaptiveAudio.isEmpty()) {
            return null
        }

        var bestManifest: ManifestCandidate? = null
        for ((clientKey, priority, manifestUrl) in manifestUrls) {
            try {
                val variant = parseHlsManifest(manifestUrl) ?: continue
                val candidate = ManifestCandidate(
                    client = clientKey,
                    priority = priority,
                    manifestUrl = manifestUrl,
                    selectedVariantUrl = variant.url,
                    height = variant.height,
                    bandwidth = variant.bandwidth
                )
                if (
                    bestManifest == null ||
                    candidate.height > bestManifest.height ||
                    (candidate.height == bestManifest.height && candidate.bandwidth > bestManifest.bandwidth)
                ) {
                    bestManifest = candidate
                }
            } catch (error: Exception) {
                Log.w(TAG, "Manifest parse failed: ${error.message}")
            }
        }

        val bestProgressive = sortCandidates(progressive).firstOrNull()
        val bestVideo = pickBestForClient(adaptiveVideo, PREFERRED_SEPARATE_CLIENT)
        val bestAudio = pickBestForClient(adaptiveAudio, PREFERRED_SEPARATE_CLIENT)
        Log.w(
            TAG,
            "Best candidates: bestVideo=${bestVideo?.let { "${it.itag}@${it.height}p" }} " +
                "bestAudio=${bestAudio?.itag} bestManifest=${bestManifest != null} bestProgressive=${bestProgressive?.itag}"
        )

        // Try adaptive video + audio first (best quality, separate streams)
        kotlinx.coroutines.yield()
        val resolvedVideo = bestVideo?.url?.let { resolveReachableUrl(it) }
        if (bestVideo != null) {
            Log.w(TAG, "resolveReachableUrl(bestVideo) -> ${resolvedVideo != null}")
        }
        val resolvedAudio = if (resolvedVideo != null) bestAudio?.url?.let { resolveReachableUrl(it) } else null

        if (resolvedVideo != null) {
            return TrailerPlaybackSource(videoUrl = resolvedVideo, audioUrl = resolvedAudio)
        }

        // Adaptive failed (403) -- fall back to HLS manifest (1080p, usually reliable)
        if (bestManifest != null) {
            return TrailerPlaybackSource(videoUrl = bestManifest.manifestUrl, audioUrl = null)
        }

        // No HLS available -- try progressive (combined video+audio, usually low quality)
        val resolvedProgressive = bestProgressive?.url?.let { resolveReachableUrl(it) }
        if (resolvedProgressive != null) {
            return TrailerPlaybackSource(videoUrl = resolvedProgressive, audioUrl = null)
        }

        return null
    }

    private fun extractVideoId(input: String): String? {
        val trimmed = input.trim()
        if (VIDEO_ID_REGEX.matches(trimmed)) return trimmed

        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

        return runCatching {
            val uri = Uri.parse(normalized)
            val host = uri.host?.lowercase().orEmpty()
            if (host.endsWith("youtu.be")) {
                val id = uri.pathSegments.firstOrNull()
                if (!id.isNullOrBlank() && VIDEO_ID_REGEX.matches(id)) {
                    return id
                }
            }

            val queryId = uri.getQueryParameter("v")
            if (!queryId.isNullOrBlank() && VIDEO_ID_REGEX.matches(queryId)) {
                return queryId
            }

            val segments = uri.pathSegments
            if (segments.size >= 2) {
                val first = segments[0]
                val second = segments[1]
                if ((first == "embed" || first == "shorts" || first == "live") && VIDEO_ID_REGEX.matches(second)) {
                    return second
                }
            }

            null
        }.getOrNull()
    }

    private fun getWatchConfig(html: String): WatchConfig {
        val apiKey = API_KEY_REGEX.find(html)?.groupValues?.getOrNull(1)
        val visitorData = VISITOR_DATA_REGEX.find(html)?.groupValues?.getOrNull(1)
        return WatchConfig(apiKey = apiKey, visitorData = visitorData)
    }

    private fun buildPlayerRequestBody(videoId: String, client: YouTubeClient): String {
        val clientContext = JSONObject()
        client.context.forEach { (key, value) -> clientContext.put(key, value) }

        val payload = JSONObject()
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .put("context", JSONObject().put("client", clientContext))
            .put(
                "playbackContext",
                JSONObject().put(
                    "contentPlaybackContext",
                    JSONObject().put("html5Preference", "HTML5_PREF_WANTS")
                )
            )
        return payload.toString()
    }

    private fun fetchPlayerResponse(
        apiKey: String,
        videoId: String,
        client: YouTubeClient,
        visitorData: String?,
        cookieHeader: String?
    ): JSONObject {
        val endpoint = "https://www.youtube.com/youtubei/v1/player?key=${Uri.encode(apiKey)}"

        val headers = buildMap {
            putAll(DEFAULT_HEADERS)
            put("content-type", "application/json")
            put("origin", "https://www.youtube.com")
            put("x-youtube-client-name", client.id)
            put("x-youtube-client-version", client.version)
            put("user-agent", client.userAgent)
            if (!visitorData.isNullOrBlank()) put("x-goog-visitor-id", visitorData)
            if (!cookieHeader.isNullOrBlank()) put("cookie", cookieHeader)
        }

        val response = performRequest(
            url = endpoint,
            method = "POST",
            headers = headers,
            body = buildPlayerRequestBody(videoId, client)
        )
        if (!response.ok) {
            throw IllegalStateException("player API ${client.key} failed (${response.status})")
        }

        return JSONObject(response.body)
    }

    private fun parseHlsManifest(manifestUrl: String): ManifestBestVariant? {
        val response = performRequest(
            url = manifestUrl,
            method = "GET",
            headers = DEFAULT_HEADERS
        )
        if (!response.ok) {
            throw IllegalStateException("Failed to fetch HLS manifest (${response.status})")
        }

        val lines = response.body
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toList()

        var bestVariant: ManifestBestVariant? = null

        for (i in lines.indices) {
            val line = lines[i]
            if (!line.startsWith("#EXT-X-STREAM-INF:")) continue

            val attrs = parseHlsAttributeList(line)
            val nextLine = lines.getOrNull(i + 1) ?: continue
            if (nextLine.startsWith("#")) continue

            val resolution = attrs["RESOLUTION"].orEmpty()
            val (width, height) = parseResolution(resolution)
            val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: 0L

            val candidate = ManifestBestVariant(
                url = absolutizeUrl(manifestUrl, nextLine),
                width = width,
                height = height,
                bandwidth = bandwidth
            )

            if (
                bestVariant == null ||
                candidate.height > bestVariant.height ||
                (candidate.height == bestVariant.height && candidate.bandwidth > bestVariant.bandwidth) ||
                (
                    candidate.height == bestVariant.height &&
                        candidate.bandwidth == bestVariant.bandwidth &&
                        candidate.width > bestVariant.width
                    )
            ) {
                bestVariant = candidate
            }
        }

        return bestVariant
    }

    private fun parseHlsAttributeList(line: String): Map<String, String> {
        val index = line.indexOf(':')
        if (index == -1) return emptyMap()

        val raw = line.substring(index + 1)
        val out = LinkedHashMap<String, String>()
        val key = StringBuilder()
        val value = StringBuilder()
        var inKey = true
        var inQuote = false

        for (ch in raw) {
            if (inKey) {
                if (ch == '=') {
                    inKey = false
                } else {
                    key.append(ch)
                }
                continue
            }

            if (ch == '"') {
                inQuote = !inQuote
                continue
            }

            if (ch == ',' && !inQuote) {
                val k = key.toString().trim()
                if (k.isNotEmpty()) {
                    out[k] = value.toString().trim()
                }
                key.clear()
                value.clear()
                inKey = true
                continue
            }

            value.append(ch)
        }

        val lastKey = key.toString().trim()
        if (lastKey.isNotEmpty()) {
            out[lastKey] = value.toString().trim()
        }

        return out
    }

    private fun parseResolution(raw: String): Pair<Int, Int> {
        val parts = raw.split('x')
        if (parts.size != 2) return 0 to 0
        val width = parts[0].toIntOrNull() ?: 0
        val height = parts[1].toIntOrNull() ?: 0
        return width to height
    }

    private fun parseQualityLabel(label: String?): Int? {
        if (label.isNullOrBlank()) return null
        val match = QUALITY_LABEL_REGEX.find(label) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()
    }

    private fun hasNParam(url: String): Boolean {
        return runCatching {
            !Uri.parse(url).getQueryParameter("n").isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun videoScore(height: Int, fps: Int, bitrate: Double): Double {
        return height * 1_000_000_000.0 + fps * 1_000_000.0 + bitrate
    }

    private fun audioScore(bitrate: Double, audioSampleRate: Double): Double {
        return bitrate * 1_000_000.0 + audioSampleRate
    }

    internal fun sortCandidates(items: List<StreamCandidate>): List<StreamCandidate> {
        return items.sortedWith(
            compareBy<StreamCandidate> { if (it.isDefaultAudioTrack) 0 else 1 }
                .thenByDescending { it.score }
                .thenBy { if (it.hasN) 1 else 0 }
                .thenBy { containerPreference(it.ext) }
                .thenBy { it.priority }
        )
    }

    private fun containerPreference(ext: String): Int {
        return when (ext.lowercase()) {
            "mp4", "m4a" -> 0
            "webm" -> 1
            else -> 2
        }
    }

    private fun pickBestForClient(items: List<StreamCandidate>, clientKey: String): StreamCandidate? {
        val sameClient = items.filter { it.client == clientKey }
        if (sameClient.isNotEmpty()) {
            return sortCandidates(sameClient).firstOrNull()
        }
        return sortCandidates(items).firstOrNull()
    }

    /**
     * Probes CDN nodes for the given googlevideo URL and returns the first reachable one.
     * Returns null if no CDN node responds successfully (all return 403/timeout).
     */
    private suspend fun resolveReachableUrl(url: String): String? {
        if (!url.contains("googlevideo.com")) return url
        val uri = Uri.parse(url)
        val mnParam = uri.getQueryParameter("mn") ?: return url
        val servers = mnParam.split(",").map { it.trim() }.filter { it.isNotBlank() }
        if (servers.size < 2) return url

        val candidates = mutableListOf(url)
        for (server in servers) {
            val mviIndex = servers.indexOf(server)
            val altHost = uri.host?.replaceFirst(
                Regex("^rr\\d+---"),
                "rr${mviIndex + 1}---"
            )?.replaceFirst(
                Regex("sn-[a-z0-9]+-[a-z0-9]+"),
                server
            ) ?: continue
            if (altHost == uri.host) continue
            candidates += url.replace(uri.host!!, altHost)
        }

        if (candidates.size == 1) {
            // Single candidate -- verify it's reachable
            return if (isUrlReachable(candidates[0])) candidates[0] else null
        }

        val result = CompletableDeferred<String>()
        val probeScope = CoroutineScope(Dispatchers.IO)
        candidates.forEach { candidate ->
            probeScope.launch {
                val reachable = isUrlReachable(candidate)
                if (reachable) result.complete(candidate)
            }
        }
        return try {
            // 2s here previously rejected a confirmed-valid 4K adaptive
            // candidate outright, forcing a fallback all the way down to the
            // old progressive format -- this network's connections to
            // googlevideo.com can genuinely take several seconds just to
            // get a first response, which isn't the same thing as the URL
            // being unreachable. 6s gives that room without meaningfully
            // slowing down extraction's overall ~30s budget.
            withTimeoutOrNull(PROBE_TIMEOUT_MS) { result.await() }
        } finally {
            probeScope.cancel()
        }
    }

    private val probeClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun isUrlReachable(url: String): Boolean {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Range", "bytes=0-0")
                .headers(buildHeaders(DEFAULT_HEADERS))
                .build()
            probeClient.newCall(request).execute().use { response ->
                response.code == 200 || response.code == 206
            }
        }.getOrDefault(false)
    }

    private fun absolutizeUrl(baseUrl: String, maybeRelative: String): String {
        return runCatching {
            URL(URL(baseUrl), maybeRelative).toString()
        }.getOrElse { maybeRelative }
    }

    private fun summarizeUrl(url: String): String {
        return runCatching {
            val parsed = URL(url)
            val host = parsed.host ?: "unknown-host"
            val path = parsed.path ?: "/"
            "$host$path"
        }.getOrDefault(url.take(80))
    }

    private fun performRequest(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null
    ): RequestResponse {
        val requestBuilder = Request.Builder()
            .url(url)
            .headers(buildHeaders(headers))

        when (method.uppercase()) {
            "POST" -> requestBuilder.post((body ?: "").toRequestBody())
            "PUT" -> requestBuilder.put((body ?: "").toRequestBody())
            "DELETE" -> requestBuilder.delete()
            else -> requestBuilder.get()
        }

        httpClient.newCall(requestBuilder.build()).execute().use { response ->
            return RequestResponse(
                ok = response.isSuccessful,
                status = response.code,
                statusText = response.message,
                url = response.request.url.toString(),
                body = response.body?.string().orEmpty()
            )
        }
    }

    private fun buildHeaders(source: Map<String, String>): Headers {
        val headers = Headers.Builder()
        source.forEach { (name, value) ->
            if (!name.equals("Accept-Encoding", ignoreCase = true)) {
                headers.add(name, value)
            }
        }
        if (source.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
            headers.add("User-Agent", DEFAULT_USER_AGENT)
        }
        return headers.build()
    }
}

private data class RequestResponse(
    val ok: Boolean,
    val status: Int,
    val statusText: String,
    val url: String,
    val body: String
)

private fun JSONObject.mapValue(key: String): JSONObject? = optJSONObject(key)

private fun JSONObject.listMapValue(key: String): List<JSONObject> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
}

private fun JSONObject.stringValue(key: String): String? {
    val value = opt(key) ?: return null
    return value.toString()
}

private fun JSONObject.booleanValue(key: String): Boolean? {
    return opt(key) as? Boolean
}

private fun JSONObject.numberValue(key: String): Double? {
    val value = opt(key) ?: return null
    return when (value) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull()
        else -> null
    }
}
