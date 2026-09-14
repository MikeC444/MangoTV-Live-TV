package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to this account's backend's /user/trakt* endpoints -- never to
 * Trakt's own api.trakt.tv, and never handles a Trakt access/refresh token
 * (those live encrypted server-side; see server/src/services/traktService.ts).
 * Every call here is authenticated, same as every other ApiClient in this
 * package.
 */
class TraktApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getStatus(accessToken: String): TraktStatusResponse = withContext(Dispatchers.IO) {
        val request = authedRequest(accessToken, "$baseUrl/user/trakt").build()
        json.decodeFromString(TraktStatusResponse.serializer(), execute(request))
    }

    suspend fun startLink(accessToken: String): TraktLinkStartResponse = withContext(Dispatchers.IO) {
        val request = authedRequest(accessToken, "$baseUrl/user/trakt/link")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        json.decodeFromString(TraktLinkStartResponse.serializer(), execute(request))
    }

    suspend fun pollLink(accessToken: String): TraktLinkPollResponse = withContext(Dispatchers.IO) {
        val request = authedRequest(accessToken, "$baseUrl/user/trakt/link").build()
        json.decodeFromString(TraktLinkPollResponse.serializer(), execute(request))
    }

    suspend fun disconnect(accessToken: String) = withContext(Dispatchers.IO) {
        val request = authedRequest(accessToken, "$baseUrl/user/trakt").delete().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw toApiException(response.code, response.body?.string().orEmpty())
        }
    }

    private fun authedRequest(accessToken: String, url: String): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken")

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
}
