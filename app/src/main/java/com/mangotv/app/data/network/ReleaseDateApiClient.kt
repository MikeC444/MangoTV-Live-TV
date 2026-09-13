package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Talks to /user/release-date -- a server-side TMDB lookup (see server/src/services/releaseDateService.ts), authenticated the same as every other endpoint under /user. Movies only, for now. */
class ReleaseDateApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getReleaseDate(accessToken: String, title: String, year: Int?): ReleaseDateResponse =
        withContext(Dispatchers.IO) {
            val urlBuilder = "$baseUrl/user/release-date".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
            if (year != null) {
                urlBuilder.addQueryParameter("year", year.toString())
            }
            val request = Request.Builder()
                .url(urlBuilder.build())
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val bodyString = response.body?.string().orEmpty()
                if (!response.isSuccessful) throw toApiException(response.code, bodyString)
                json.decodeFromString(ReleaseDateResponse.serializer(), bodyString)
            }
        }

    private fun toApiException(code: Int, bodyString: String): ApiException {
        val message = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), bodyString).error
        }.getOrNull()
        return ApiException(code, message ?: "Request failed with HTTP $code")
    }
}
