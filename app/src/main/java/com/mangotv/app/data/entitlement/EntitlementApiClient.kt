package com.mangotv.app.data.entitlement

import com.mangotv.app.config.LiveTvConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Talks to MangoTV's own backend only -- never to a payment provider
 * directly, and never trusts anything the TV itself claims about payment
 * state. See docs/LIVE_TV_BACKEND.md for the full contract this expects the
 * backend to implement (device registration, entitlement status, payment
 * webhook). Every call surfaces failures as exceptions rather than
 * swallowing them, so EntitlementRepository can tell "backend explicitly
 * says inactive" apart from "couldn't reach the backend at all".
 */
class EntitlementApiClient {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchStatus(deviceId: String): EntitlementStatusResponse = withContext(Dispatchers.IO) {
        val url = "${LiveTvConfig.apiBaseUrl}/v1/entitlements/$deviceId"
        val request = Request.Builder().url(url).get().build()
        httpClient.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code} from $url" }
            val body = response.body?.string() ?: error("Empty response from $url")
            json.decodeFromString(EntitlementStatusResponse.serializer(), body)
        }
    }

    /**
     * Registers this device/pairing code with the backend ahead of payment
     * so the checkout page can greet the user by pairing code. Best-effort
     * by design: the QR code already encodes everything the backend needs
     * to associate a payment with this device (see LiveTvConfig.checkoutUrl),
     * so a failure here must never block showing it.
     */
    suspend fun registerDevice(deviceId: String, pairingCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "${LiveTvConfig.apiBaseUrl}/v1/devices/register"
            val payload = json.encodeToString(
                DeviceRegistrationRequest.serializer(),
                DeviceRegistrationRequest(deviceId, pairingCode)
            )
            val request = Request.Builder()
                .url(url)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            httpClient.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "HTTP ${response.code} from $url" }
            }
        }
    }
}
