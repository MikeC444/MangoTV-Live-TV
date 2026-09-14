package com.mangotv.app.data.trakt

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.TraktApiClient

data class TraktConnectionStatus(
    /** False means this server has no Trakt application configured at all -- see server/src/services/traktService.ts's identically-named field. Settings shows a different message for this than for "configured but not connected." */
    val configured: Boolean,
    val connected: Boolean,
    val username: String?,
    val connectedAt: String?
)

data class TraktLinkInfo(
    val userCode: String,
    val verificationUrl: String,
    /** verificationUrl with userCode appended, e.g. https://trakt.tv/activate/ABCD1234 -- what the connect screen renders as a QR code, to skip manual code entry. */
    val directVerificationUrl: String,
    val expiresAtMillis: Long,
    val intervalSeconds: Int
)

sealed interface TraktPollOutcome {
    data object Pending : TraktPollOutcome
    data class Connected(val username: String?) : TraktPollOutcome
    data object Expired : TraktPollOutcome
    data object Denied : TraktPollOutcome
    data object NotFound : TraktPollOutcome
}

/**
 * Talks only to this account's own backend (/user/trakt*) -- never to
 * Trakt's api.trakt.tv, and never receives a Trakt access/refresh token;
 * those live encrypted server-side (see
 * server/src/services/traktService.ts). Mirrors TrailerRepository's "get a
 * fresh access token, call the backend" shape, but surfaces a Result per
 * call (rather than collapsing every failure to null) since Settings >
 * Account has real error/retry states to show, unlike a Trailer button
 * that simply doesn't appear on failure.
 */
class TraktRepository(private val authRepository: AuthRepository) {
    private val apiClient = TraktApiClient(BuildConfig.API_BASE_URL)

    suspend fun getStatus(): Result<TraktConnectionStatus> = withFreshToken { token ->
        val response = apiClient.getStatus(token)
        TraktConnectionStatus(response.configured, response.connected, response.username, response.connectedAt)
    }

    /** Starts (or restarts) the OAuth Device Code pairing attempt -- see startDeviceLink's kdoc server-side for why this flow needs no redirect back into the app. */
    suspend fun startLink(): Result<TraktLinkInfo> = withFreshToken { token ->
        val response = apiClient.startLink(token)
        TraktLinkInfo(
            userCode = response.userCode,
            verificationUrl = response.verificationUrl,
            directVerificationUrl = response.directVerificationUrl,
            expiresAtMillis = System.currentTimeMillis() + response.expiresInSeconds * 1000L,
            intervalSeconds = response.intervalSeconds
        )
    }

    /** One poll of the in-progress pairing attempt -- call roughly every TraktLinkInfo.intervalSeconds while it's showing. */
    suspend fun pollLink(): Result<TraktPollOutcome> = withFreshToken { token ->
        val response = apiClient.pollLink(token)
        when (response.status) {
            "connected" -> TraktPollOutcome.Connected(response.username)
            "expired" -> TraktPollOutcome.Expired
            "denied" -> TraktPollOutcome.Denied
            "not_found" -> TraktPollOutcome.NotFound
            else -> TraktPollOutcome.Pending
        }
    }

    /** Best-effort revokes with Trakt and clears the stored connection server-side -- see disconnect's kdoc server-side. */
    suspend fun disconnect(): Result<Unit> = withFreshToken { token -> apiClient.disconnect(token) }

    private suspend fun <T> withFreshToken(block: suspend (String) -> T): Result<T> = runCatching {
        val token = if (authRepository.ensureFreshSession()) authRepository.getCurrentSession()?.accessToken else null
        block(token ?: error("Not signed in"))
    }
}
