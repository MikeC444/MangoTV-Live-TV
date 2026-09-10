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
 * Talks to /user/addons -- this account's cloud-synced installed addon
 * list (Milestone 9). Item-level, mirroring WatchlistApiClient: getAddons()
 * is the only call returning more than one item; upsertAddon()/removeAddon()
 * each act on exactly the one addon a local install/remove/enable-toggle
 * just changed. Every call here is authenticated.
 */
class AddonSyncApiClient(private val baseUrl: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getAddons(accessToken: String): AddonSyncListResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/addons")
            .header("Authorization", "Bearer $accessToken")
            .build()
        json.decodeFromString(AddonSyncListResponse.serializer(), execute(request))
    }

    /** Installs a new addon, or un-removes/refreshes an existing one (see addonService.upsertAddon's last-write-wins contract). */
    suspend fun upsertAddon(accessToken: String, addon: AddonSyncDto): AddonSyncDto = withContext(Dispatchers.IO) {
        val bodyJson = json.encodeToString(AddonSyncDto.serializer(), addon)
        val request = Request.Builder()
            .url("$baseUrl/user/addons")
            .header("Authorization", "Bearer $accessToken")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(AddonSyncDto.serializer(), execute(request))
    }

    /** Removes one addon, identified by its natural key (manifestUrl). Returns null only when the addon was never synced to this account from any device. */
    suspend fun removeAddon(accessToken: String, manifestUrl: String, updatedAt: String): AddonSyncDto? = withContext(Dispatchers.IO) {
        val url = "$baseUrl/user/addons".toHttpUrl().newBuilder()
            .addQueryParameter("manifestUrl", manifestUrl)
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
            return@withContext json.decodeFromString(AddonSyncDto.serializer(), bodyString)
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
