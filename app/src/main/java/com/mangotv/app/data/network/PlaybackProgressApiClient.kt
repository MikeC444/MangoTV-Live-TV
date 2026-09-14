package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to /user/watch-progress, /user/continue-watching, and /user/history
 * (Milestone 8). Every call here is authenticated, same as
 * SettingsApiClient/WatchlistApiClient. postProgress() is the single write
 * that updates both watch_history and continue_watching server-side in one
 * request -- see playbackProgressService.recordProgress's own kdoc for why
 * this is one call, not two.
 */
class PlaybackProgressApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun postProgress(accessToken: String, body: WatchProgressRequest): WatchProgressResponse = withContext(Dispatchers.IO) {
        val bodyJson = json.encodeToString(WatchProgressRequest.serializer(), body)
        val request = Request.Builder()
            .url("$baseUrl/user/watch-progress")
            .header("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(WatchProgressResponse.serializer(), execute(request))
    }

    suspend fun getContinueWatching(accessToken: String): ContinueWatchingListResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/continue-watching")
            .header("Authorization", "Bearer $accessToken")
            .build()
        json.decodeFromString(ContinueWatchingListResponse.serializer(), execute(request))
    }

    // Keyset-paginated, newest first -- mirrors historyQuerySchema/
    // listWatchHistory server-side exactly (limit capped at 200 there;
    // before is the previous page's oldest item's own watchedAt). Used by
    // WatchlistSyncRepository's watched-history backfill; still no History
    // browse screen consumes this directly.
    suspend fun getHistory(accessToken: String, limit: Int, before: String? = null): WatchHistoryListResponse = withContext(Dispatchers.IO) {
        val urlBuilder = "$baseUrl/user/history".toHttpUrl().newBuilder()
            .addQueryParameter("limit", limit.toString())
        if (before != null) {
            urlBuilder.addQueryParameter("before", before)
        }
        val request = Request.Builder()
            .url(urlBuilder.build())
            .header("Authorization", "Bearer $accessToken")
            .build()
        json.decodeFromString(WatchHistoryListResponse.serializer(), execute(request))
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching {
                    json.decodeFromString(ErrorResponse.serializer(), bodyString).error
                }.getOrNull()
                throw ApiException(response.code, message ?: "Request failed with HTTP ${response.code}")
            }
            return bodyString
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
