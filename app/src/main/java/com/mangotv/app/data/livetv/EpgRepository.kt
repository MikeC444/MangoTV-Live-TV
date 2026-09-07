package com.mangotv.app.data.livetv

import android.content.Context
import com.mangotv.app.config.LiveTvConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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

/**
 * Best-effort EPG support (see LiveTvConfig.isEpgConfigured -- disabled
 * entirely unless EPG_URL is set). Bounded on every axis that matters for a
 * low-RAM Fire TV Stick: a hard cap on the downloaded response size, a
 * narrow time window kept in memory, and only channels this app actually
 * has loaded. Any failure here (unreachable URL, oversized response,
 * malformed XML) just means channels fall back to showing "Live" instead of
 * now/next -- never a crash. See LiveTvChannelsScreen / LiveTvPlayerScreen.
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

    fun nowAndNext(tvgId: String?, atEpochMs: Long = System.currentTimeMillis()): NowNext {
        if (tvgId == null) return NowNext(null, null)
        val programmes = programmesByChannel[tvgId] ?: return NowNext(null, null)
        val now = programmes.find { atEpochMs in it.startEpochMs until it.stopEpochMs }
        val next = programmes.filter { it.startEpochMs > (now?.stopEpochMs ?: atEpochMs) }.minByOrNull { it.startEpochMs }
        return NowNext(now, next)
    }

    fun load(knownTvgIds: Set<String>) {
        if (!LiveTvConfig.isEpgConfigured || knownTvgIds.isEmpty() || isReady) return
        scope.launch { loadInternal(knownTvgIds) }
    }

    private suspend fun loadInternal(knownTvgIds: Set<String>) = loadMutex.withLock {
        if (isReady) return@withLock
        withContext(Dispatchers.IO) {
            val bytes = fetchOrReadCache() ?: return@withContext
            runCatching {
                val now = System.currentTimeMillis()
                openReader(bytes).use { reader ->
                    programmesByChannel = XmlTvEpgParser.parse(
                        reader = reader,
                        knownTvgIds = knownTvgIds,
                        minEpochMs = now - TimeUnit.HOURS.toMillis(2),
                        maxEpochMs = now + TimeUnit.HOURS.toMillis(12)
                    )
                }
                isReady = true
            }
        }
    }

    private fun openReader(bytes: ByteArray): Reader {
        val isGzip = bytes.size > 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()
        val stream = if (isGzip) GZIPInputStream(bytes.inputStream()) else bytes.inputStream()
        return InputStreamReader(stream, Charsets.UTF_8)
    }

    private fun fetchOrReadCache(): ByteArray? {
        readCacheIfFresh()?.let { return it }
        return runCatching {
            val request = Request.Builder().url(LiveTvConfig.epgUrl).build()
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
    }
}
