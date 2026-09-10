package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to /user/watch-progress and /user/continue-watching (Milestone 8).
 * Every call here is authenticated, same as SettingsApiClient/
 * WatchlistApiClient. postProgress() is the single write that updates both
 * watch_history and continue_watching server-side in one request -- see
 * playbackProgressService.recordProgress's own kdoc for why this is one
 * call, not two.
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
