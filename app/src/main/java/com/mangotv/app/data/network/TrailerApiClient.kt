package com.mangotv.app.data.network

import com.mangotv.app.data.model.ContentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** Talks to /user/trailer -- a server-side TMDB lookup (see server/src/services/trailerService.ts), authenticated the same as every other endpoint under /user. */
class TrailerApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getTrailer(accessToken: String, title: String, year: Int?, type: ContentType): TrailerResponse =
        withContext(Dispatchers.IO) {
            val urlBuilder = "$baseUrl/user/trailer".toHttpUrl().newBuilder()
                .addQueryParameter("title", title)
                .addQueryParameter("type", type.name)
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
                json.decodeFromString(TrailerResponse.serializer(), bodyString)
            }
        }

    private fun toApiException(code: Int, bodyString: String): ApiException {
        val message = runCatching {
            json.decodeFromString(ErrorResponse.serializer(), bodyString).error
        }.getOrNull()
        return ApiException(code, message ?: "Request failed with HTTP $code")
    }
}
