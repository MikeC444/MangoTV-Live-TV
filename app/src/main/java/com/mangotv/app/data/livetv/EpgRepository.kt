package com.mangotv.app.data.livetv

import android.content.Context
import com.mangotv.app.config.LiveTvConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStreamReader
import java.io.Reader
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

/** Real, on-device visibility into what EpgRepository actually did on its last load attempt -- see its own doc. */
sealed interface EpgDiagnostics {
    data object NotConfigured : EpgDiagnostics
    data class Loading(val sourceUrl: String) : EpgDiagnostics
    data class Failed(val sourceUrl: String, val message: String) : EpgDiagnostics
    data class Ready(
        val sourceUrl: String,
        val matchedChannelCount: Int,
        val knownChannelCount: Int,
        val programmeCount: Int,
        val guideChannelCount: Int,
        val sampleGuideChannelIds: List<String>,
        val sampleKnownChannelIds: List<String>
    ) : EpgDiagnostics
}

/**
 * Best-effort EPG support: uses EPG_URL if explicitly configured (see
 * LiveTvConfig.isEpgConfigured), otherwise falls back to whatever EPG
 * source the playlist itself declared (see [load]'s fallbackUrl param) --
 * disabled entirely only if neither is available. Bounded on every axis
 * that matters for a low-RAM Fire TV Stick: a hard cap on the downloaded
 * response size, a narrow time window kept in memory, and only channels
 * this app actually has loaded. Any failure here (unreachable URL, oversized
 * response, malformed XML) just means channels fall back to showing "Live"
 * instead of now/next -- never a crash. See LiveTvChannelsScreen /
 * LiveTvPlayerScreen.
 */
class EpgRepository(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val loadMutex = Mutex()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val cacheFile: File get() = File(appContext.cacheDir, "live_tv_epg_cache.xml")

    @Volatile private var programmesByChannel: Map<String, List<Programme>> = emptyMap()
    @Volatile private var isReady = false

    // Bumped every time a load finishes (successfully or not) so UI that
    // already rendered fallback "Live" blocks before EPG data arrived can
    // recompute once it does, without needing a full screen reload -- see
    // LiveTvViewModel.epgVersion.
    private val _epgVersion = MutableStateFlow(0)
    val epgVersion: StateFlow<Int> = _epgVersion.asStateFlow()

    // Real, on-device visibility into what actually happened -- this
    // wasn't originally exposed anywhere, which made "the guide shows no
    // programme information" impossible to root-cause without adding
    // logging and reading logcat. Surfaced by LiveTvViewModel/TvGuideScreen
    // (only while LiveTvConfig.skipPaywallForTesting is on) as a plain
    // status line, so what's actually happening can be read straight off
    // the TV instead of guessed at.
    private val _diagnostics = MutableStateFlow<EpgDiagnostics>(EpgDiagnostics.NotConfigured)
    val diagnostics: StateFlow<EpgDiagnostics> = _diagnostics.asStateFlow()

    fun nowAndNext(tvgId: String?, atEpochMs: Long = System.currentTimeMillis()): NowNext {
        if (tvgId == null) return NowNext(null, null)
        val programmes = programmesByChannel[tvgId] ?: return NowNext(null, null)
        val now = programmes.find { atEpochMs in it.startEpochMs until it.stopEpochMs }
        val next = programmes.filter { it.startEpochMs > (now?.stopEpochMs ?: atEpochMs) }.minByOrNull { it.startEpochMs }
        return NowNext(now, next)
    }

    /** Every known programme for [tvgId] overlapping [fromEpochMs, toEpochMs) — the raw data behind a TV guide row; see buildTimelineBlocks for turning this into gap-free display blocks. */
    fun programmesInRange(tvgId: String?, fromEpochMs: Long, toEpochMs: Long): List<Programme> {
        if (tvgId == null) return emptyList()
        val programmes = programmesByChannel[tvgId] ?: return emptyList()
        return programmes.filter { it.stopEpochMs > fromEpochMs && it.startEpochMs < toEpochMs }
    }

    /**
     * [fallbackUrl] is only used when EPG_URL isn't explicitly configured --
     * see LiveTvConfig.isEpgConfigured -- so a playlist that declares its
     * own EPG source (an M3U `#EXTM3U x-tvg-url`/`url-tvg` attribute, parsed
     * by M3uParser into LiveTvCatalogState.Loaded.discoveredEpgUrl) still
     * gets used automatically, without ever overriding an operator's own
     * explicit EPG_URL choice.
     */
    fun load(knownTvgIds: Set<String>, fallbackUrl: String? = null) {
        val url = if (LiveTvConfig.isEpgConfigured) LiveTvConfig.epgUrl else fallbackUrl?.takeIf { it.isNotBlank() }
        if (url == null) {
            _diagnostics.value = EpgDiagnostics.NotConfigured
            return
        }
        if (knownTvgIds.isEmpty() || isReady) return
        _diagnostics.value = EpgDiagnostics.Loading(url)
        scope.launch { loadInternal(knownTvgIds, url) }
    }

    private suspend fun loadInternal(knownTvgIds: Set<String>, url: String) = loadMutex.withLock {
        if (isReady) return@withLock
        withContext(Dispatchers.IO) {
            val bytes = fetchOrReadCache(url)
            if (bytes == null) {
                _diagnostics.value = EpgDiagnostics.Failed(url, "Couldn't download the guide and no cached copy was available.")
                return@withContext
            }
            runCatching {
                val now = System.currentTimeMillis()
                openReader(bytes).use { reader ->
                    XmlTvEpgParser.parse(
                        reader = reader,
                        knownTvgIds = knownTvgIds,
                        minEpochMs = now - LOOKBACK_MS,
                        maxEpochMs = now + LOOKAHEAD_MS
                    )
                }
            }.onSuccess { parsed ->
                programmesByChannel = parsed.programmesByChannel
                isReady = true
                _diagnostics.value = EpgDiagnostics.Ready(
                    sourceUrl = url,
                    matchedChannelCount = parsed.programmesByChannel.size,
                    knownChannelCount = knownTvgIds.size,
                    programmeCount = parsed.programmesByChannel.values.sumOf { it.size },
                    guideChannelCount = parsed.distinctChannelIdCount,
                    sampleGuideChannelIds = parsed.sampleChannelIds,
                    sampleKnownChannelIds = knownTvgIds.take(12)
                )
            }.onFailure { error ->
                _diagnostics.value = EpgDiagnostics.Failed(url, error.message ?: "Failed to parse the guide.")
            }
        }
        _epgVersion.value++
    }

    private fun openReader(bytes: ByteArray): Reader {
        val isGzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val stream = if (isGzip) GZIPInputStream(bytes.inputStream()) else bytes.inputStream()
        return InputStreamReader(stream, Charsets.UTF_8)
    }

    private fun fetchOrReadCache(url: String): ByteArray? {
        readCacheIfFresh()?.let { return it }
        return runCatching {
            val request = Request.Builder().url(url).build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = response.body ?: error("Empty EPG response")
                val bytes = readBodyBounded(body.byteStream(), MAX_RESPONSE_BYTES)
                cacheFile.writeBytes(bytes)
                bytes
            }
        }.getOrElse { runCatching { cacheFile.takeIf { it.exists() }?.readBytes() }.getOrNull() }
    }

    /** Reads a response stream into memory but refuses to keep going past [maxBytes] -- EPG is opt-in/best-effort, so an oversized guide is dropped rather than risking an OOM on entry-level hardware. */
    private fun readBodyBounded(input: java.io.InputStream, maxBytes: Long): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        input.use {
            var total = 0L
            while (true) {
                val read = it.read(buffer)
                if (read == -1) break
                total += read
                if (total > maxBytes) error("EPG response exceeded $maxBytes bytes")
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun readCacheIfFresh(): ByteArray? {
        if (!cacheFile.exists()) return null
        if (System.currentTimeMillis() - cacheFile.lastModified() > CACHE_TTL_MS) return null
        return runCatching { cacheFile.readBytes() }.getOrNull()
    }

    companion object {
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(3)
        private const val MAX_RESPONSE_BYTES = 20L * 1024 * 1024

        // Also the TV guide's own display window (see LiveTvViewModel.guideWindow)
        // -- kept as the single source of truth so the guide never shows a
        // time range wider than what's actually been parsed into memory.
        val LOOKBACK_MS = TimeUnit.HOURS.toMillis(2)
        val LOOKAHEAD_MS = TimeUnit.HOURS.toMillis(12)
    }
}
