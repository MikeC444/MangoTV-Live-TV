package com.mangotv.app.data.trailer

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener

/**
 * A DataSource.Factory that wraps DefaultHttpDataSource and appends YouTube's
 * own `&range=start-end` query parameter on each request. YouTube throttles
 * (and eventually kills) connections that try to download a full adaptive
 * stream in one shot, but serves chunked range-param requests at full speed
 * -- this is how YouTube's own web/app players fetch adaptive video, not a
 * MangoTV-specific workaround.
 *
 * Only activates for googlevideo.com URLs; every other URL passes through
 * to the plain upstream DefaultHttpDataSource untouched.
 */
@UnstableApi
class YoutubeChunkedDataSourceFactory(
    private val chunkSizeBytes: Long = CHUNK_SIZE
) : DataSource.Factory {

    companion object {
        private const val TAG = "YTChunkedDS"
        /** 2 MB chunks -- smaller than the original 10 MB to stay further under whatever size/rate threshold triggers YouTube's own throttling. */
        private const val CHUNK_SIZE = 2L * 1024 * 1024
    }

    override fun createDataSource(): DataSource {
        val upstream = DefaultHttpDataSource.Factory()
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setAllowCrossProtocolRedirects(true)
            .createDataSource()
        return YoutubeChunkedDataSource(upstream, chunkSizeBytes)
    }

    private class YoutubeChunkedDataSource(
        private val upstream: DefaultHttpDataSource,
        private val chunkSize: Long
    ) : DataSource {

        private var currentUri: Uri? = null
        private var isYouTubeStream = false
        private var totalContentLength = C.LENGTH_UNSET.toLong()
        private var currentChunkStart = 0L
        private var currentChunkEnd = 0L
        private var bytesReadInChunk = 0L
        private var originalDataSpec: DataSpec? = null

        // Diagnostics only -- logs at most once/second so a slow trickle of
        // data is visible in Logcat as a rate over time, without flooding it
        // on a fast connection where read() can be called many times/second.
        private var lastProgressLogAt = 0L

        override fun addTransferListener(transferListener: TransferListener) {
            upstream.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val uri = dataSpec.uri
            val host = uri.host.orEmpty()
            isYouTubeStream = host.contains("googlevideo.com")

            if (!isYouTubeStream) {
                return upstream.open(dataSpec)
            }

            originalDataSpec = dataSpec
            currentChunkStart = dataSpec.position
            totalContentLength = dataSpec.length
            Log.w(TAG, "open() googlevideo stream, initial length=$totalContentLength itag-uri=${uri.getQueryParameter("itag")}")

            return openNextChunk()
        }

        private fun openNextChunk(): Long {
            val spec = originalDataSpec ?: throw IllegalStateException("No DataSpec")
            val end = if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                minOf(currentChunkStart + chunkSize - 1, currentChunkStart + totalContentLength - 1)
            } else {
                currentChunkStart + chunkSize - 1
            }
            currentChunkEnd = end

            // Append &range=start-end to the URL (YouTube's own range param, not the HTTP Range header)
            val rangedUri = spec.uri.buildUpon()
                .appendQueryParameter("range", "$currentChunkStart-$currentChunkEnd")
                .build()

            val chunkedSpec = spec.buildUpon()
                .setUri(rangedUri)
                .setPosition(0)           // position within this chunk's response
                .setLength(C.LENGTH_UNSET.toLong()) // let the server decide
                .build()

            bytesReadInChunk = 0
            Log.w(TAG, "requesting chunk range=$currentChunkStart-$currentChunkEnd (${currentChunkEnd - currentChunkStart + 1} bytes)")
            upstream.open(chunkedSpec)
            return if (totalContentLength != C.LENGTH_UNSET.toLong()) totalContentLength else C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (!isYouTubeStream) {
                return upstream.read(buffer, offset, length)
            }

            val bytesRead = upstream.read(buffer, offset, length)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                // Current chunk exhausted -- open the next one
                val chunkBytesReceived = bytesReadInChunk
                val chunkRequestedSize = currentChunkEnd - currentChunkStart + 1
                upstream.close()
                Log.w(TAG, "chunk at $currentChunkStart ended: received=$chunkBytesReceived requested=$chunkRequestedSize")

                // A chunk coming back shorter than requested does NOT reliably mean
                // the file is over -- YouTube's CDN can truncate a single &range=
                // response by a small, arbitrary amount well before the real end
                // (confirmed in testing: a chunk came back ~2KB short of a 2MB
                // request, and the calling MediaSource immediately re-requested
                // from the next chunk boundary, proving it knew more data existed).
                // Only a genuinely empty response is treated as the real end --
                // anything else just continues from wherever this chunk actually
                // left off, not from an assumed full-chunk boundary.
                if (chunkBytesReceived <= 0) {
                    Log.w(TAG, "chunk returned no data, treating as genuine end of stream")
                    return C.RESULT_END_OF_INPUT
                }

                currentChunkStart += chunkBytesReceived
                if (totalContentLength != C.LENGTH_UNSET.toLong()) {
                    totalContentLength -= chunkBytesReceived
                    if (totalContentLength <= 0) {
                        Log.w(TAG, "totalContentLength exhausted, ending stream")
                        return C.RESULT_END_OF_INPUT
                    }
                }

                return try {
                    openNextChunk()
                    upstream.read(buffer, offset, length)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to open next chunk at $currentChunkStart: ${e.message}")
                    C.RESULT_END_OF_INPUT
                }
            }

            bytesReadInChunk += bytesRead
            val now = System.currentTimeMillis()
            if (now - lastProgressLogAt >= 1_000L) {
                lastProgressLogAt = now
                Log.w(TAG, "reading chunk at $currentChunkStart: $bytesReadInChunk bytes so far")
            }
            return bytesRead
        }

        override fun getUri(): Uri? = upstream.uri ?: currentUri

        override fun close() {
            upstream.close()
            currentUri = null
            originalDataSpec = null
        }
    }
}
