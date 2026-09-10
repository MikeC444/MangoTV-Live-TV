package com.mangotv.app.data.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder

/**
 * Talks to this account's backend — the Fire TV app's only network
 * dependency besides addon servers (data/addon/StremioAddonClient.kt),
 * whose OkHttp+kotlinx.serialization style this deliberately mirrors.
 *
 * Only the calls the TV itself ever makes: creating and polling a QR
 * session, refreshing a token pair, and logging out. Account creation and
 * sign-in happen on the phone/web activation page instead (POST
 * /auth/qr/complete), never here — this app never collects or transmits
 * a password.
 */
class AuthApiClient(private val baseUrl: String) {

    private val httpClient = AccountApiHttpClient.client

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun createQrSession(deviceId: String, deviceName: String?, platform: String?): QrCreateResponse =
        withContext(Dispatchers.IO) {
            val body = json.encodeToString(QrCreateRequest.serializer(), QrCreateRequest(deviceId, deviceName, platform))
            json.decodeFromString(QrCreateResponse.serializer(), post("$baseUrl/auth/qr/create", body))
        }

    suspend fun pollQrStatus(token: String): QrStatusResponse = withContext(Dispatchers.IO) {
        val url = "$baseUrl/auth/qr/status?token=${encode(token)}"
        json.decodeFromString(QrStatusResponse.serializer(), get(url))
    }

    suspend fun refresh(refreshToken: String): TokenPairResponse = withContext(Dispatchers.IO) {
        val body = json.encodeToString(RefreshRequest.serializer(), RefreshRequest(refreshToken))
        json.decodeFromString(TokenPairResponse.serializer(), post("$baseUrl/auth/refresh", body))
    }

    /** Best-effort: the caller (AuthRepository.logout) clears the local session regardless of whether this succeeds. */
    suspend fun logout(accessToken: String) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/auth/logout")
            .header("Authorization", "Bearer $accessToken")
            .post(ByteArray(0).toRequestBody(null))
            .build()
        runCatching { httpClient.newCall(request).execute().close() }
        Unit
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun post(url: String, jsonBody: String): String {
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request)
    }

    private fun get(url: String): String = execute(Request.Builder().url(url).build())

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
