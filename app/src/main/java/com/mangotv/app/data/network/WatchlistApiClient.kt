package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to /user/watchlist -- this account's cloud-synced My List
 * (Milestone 7). Item-level, not a whole-list PUT: getWatchlist() is the
 * only call that returns more than one item; addOrUpdateItem()/removeItem()
 * each act on exactly the one item a local add/remove toggle just changed,
 * matching the backend's item-level design (see watchlistService.ts).
 * Every call here is authenticated, same as SettingsApiClient.
 */
class WatchlistApiClient(private val baseUrl: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getWatchlist(accessToken: String): WatchlistListResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/watchlist")
            .header("Authorization", "Bearer $accessToken")
            .build()
        json.decodeFromString(WatchlistListResponse.serializer(), execute(request))
    }

    /** Adds a new item, or un-removes/refreshes an existing one (see watchlistService.upsertWatchlistItem's last-write-wins contract). */
    suspend fun addOrUpdateItem(accessToken: String, item: WatchlistItemDto): WatchlistItemDto = withContext(Dispatchers.IO) {
        val bodyJson = json.encodeToString(WatchlistItemDto.serializer(), item)
        val request = Request.Builder()
            .url("$baseUrl/user/watchlist")
            .header("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(WatchlistItemDto.serializer(), execute(request))
    }

    /**
     * Removes one item, identified by its natural key rather than a
     * server-side row id the client never needs to know. Returns null only
     * when the item was never synced to this account from any device --
     * there's nothing server-side to reconcile, so the caller's local
     * removal simply stands.
     */
    suspend fun removeItem(
        accessToken: String,
        providerId: String,
        contentId: String,
        contentType: String,
        updatedAt: String
    ): WatchlistItemDto? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/watchlist".toHttpUrl().newBuilder()
            .addQueryParameter("providerId", providerId)
            .addQueryParameter("contentId", contentId)
            .addQueryParameter("contentType", contentType)
            .addQueryParameter("updatedAt", updatedAt)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $accessToken")
            .delete()
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (response.code == 204) return@withContext null
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw toApiException(response.code, bodyString)
            return@withContext json.decodeFromString(WatchlistItemDto.serializer(), bodyString)
        }
    }

    private fun execute(request: Request): String {
        httpClient.newCall(request).execute().use { response ->
            val bodyString = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw toApiException(response.code, bodyString)
            return bodyString
        }
    }

    private fun toApiException(code: Int, bodyString: String): ApiException {
        val message = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), bodyString).error
        }.getOrNull()
        return ApiException(code, message ?: "Request failed with HTTP $code")
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
