package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to /user/settings — this account's cloud-synced Home Rows +
 * Player preferences (Milestone 6). Every call here is authenticated;
 * unlike AuthApiClient's QR/refresh endpoints, there's no unauthenticated
 * path through this one, so the access token is a required parameter on
 * every method rather than only on logout().
 */
class SettingsApiClient(private val baseUrl: String) {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getSettings(accessToken: String): SettingsResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/user/settings")
            .header("Authorization", "Bearer $accessToken")
            .build()
        json.decodeFromString(SettingsResponse.serializer(), execute(request))
    }

    suspend fun putSettings(accessToken: String, body: SettingsRequest): SettingsResponse = withContext(Dispatchers.IO) {
        val bodyJson = json.encodeToString(SettingsRequest.serializer(), body)
        val request = Request.Builder()
            .url("$baseUrl/user/settings")
            .header("Authorization", "Bearer $accessToken")
            .put(bodyJson.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        json.decodeFromString(SettingsResponse.serializer(), execute(request))
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
