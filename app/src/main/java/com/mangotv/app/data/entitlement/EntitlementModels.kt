package com.mangotv.app.data.entitlement

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire shape for `GET {API_BASE_URL}/v1/entitlements/{deviceId}` — see docs/LIVE_TV_BACKEND.md. */
@Serializable
data class EntitlementStatusResponse(
    val status: String, // "active" | "inactive" | "expired"
    @SerialName("expires_at") val expiresAtEpochMs: Long? = null
)

/** Wire shape for `POST {API_BASE_URL}/v1/devices/register` — best-effort, see EntitlementApiClient. */
@Serializable
data class DeviceRegistrationRequest(
    @SerialName("device_id") val deviceId: String,
    @SerialName("pairing_code") val pairingCode: String,
    val platform: String = "android-tv"
)

/** Short-lived local cache of the last real backend answer — see EntitlementRepository's own doc for why this is never treated as a source of truth on its own. */
@Serializable
data class CachedEntitlement(
    val status: String,
    val expiresAtEpochMs: Long?,
    val checkedAtEpochMs: Long
)
