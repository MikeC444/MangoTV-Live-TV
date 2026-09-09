package com.mangotv.app.data.auth

import android.content.Context
import android.os.Build
import com.mangotv.app.BuildConfig
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.AuthApiClient
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException

data class QrSessionInfo(val token: String, val activationUrl: String, val expiresAtMillis: Long)

sealed interface QrPollOutcome {
    data object Pending : QrPollOutcome
    data object Expired : QrPollOutcome
    data class Completed(val session: Session) : QrPollOutcome
}

/**
 * Orchestrates device identity, the backend API, and locally persisted
 * session state — the single entry point everything else in the app uses
 * for "am I signed in" and "sign in via QR". Every backend response
 * timestamp gets converted to epoch millis at this boundary (see
 * Iso8601), so nothing downstream deals with date strings.
 */
class AuthRepository(context: Context) {
    private val deviceIdentity = DeviceIdentity(context)
    private val sessionManager = SessionManager(context)
    private val apiClient = AuthApiClient(BuildConfig.API_BASE_URL)

    val session: StateFlow<Session?> = sessionManager.session

    suspend fun getCurrentSession(): Session? = sessionManager.current()

    suspend fun createQrSession(): Result<QrSessionInfo> = runCatching {
        val deviceId = deviceIdentity.getOrCreate()
        val response = apiClient.createQrSession(deviceId, Build.MODEL, PLATFORM)
        QrSessionInfo(
            token = response.token,
            activationUrl = response.activationUrl,
            expiresAtMillis = Iso8601.parseToEpochMillis(response.expiresAt)
        )
    }

    suspend fun pollQrSession(token: String): Result<QrPollOutcome> = runCatching {
        val response = apiClient.pollQrStatus(token)
        when (response.status) {
            "completed" -> {
                val user = requireNotNull(response.user) { "completed QR session missing user" }
                val session = Session(
                    accessToken = requireNotNull(response.accessToken),
                    accessTokenExpiresAtMillis = Iso8601.parseToEpochMillis(requireNotNull(response.accessTokenExpiresAt)),
                    refreshToken = requireNotNull(response.refreshToken),
                    refreshTokenExpiresAtMillis = Iso8601.parseToEpochMillis(requireNotNull(response.refreshTokenExpiresAt)),
                    user = AuthenticatedUser(user.id, user.email, user.displayName)
                )
                sessionManager.save(session)
                QrPollOutcome.Completed(session)
            }
            "pending" -> QrPollOutcome.Pending
            else -> QrPollOutcome.Expired
        }
    }

    /**
     * Attempts to refresh the access token if the current session looks
     * like it needs it. Returns true if the session is (now) usable.
     * Only a *confirmed* server rejection (HTTP 401 — see ApiException)
     * clears the local session; a network-level failure (IOException)
     * leaves everything as-is and reports the session still usable,
     * since a transient outage is never a reason to sign someone out.
     */
    suspend fun ensureFreshSession(): Boolean {
        val current = sessionManager.current() ?: return false
        if (!current.isRefreshTokenValid()) {
            sessionManager.clear()
            return false
        }
        if (current.isAccessTokenValid()) return true

        return try {
            val response = apiClient.refresh(current.refreshToken)
            val refreshed = current.copy(
                accessToken = response.accessToken,
                accessTokenExpiresAtMillis = Iso8601.parseToEpochMillis(response.accessTokenExpiresAt),
                refreshToken = response.refreshToken,
                refreshTokenExpiresAtMillis = Iso8601.parseToEpochMillis(response.refreshTokenExpiresAt)
            )
            sessionManager.save(refreshed)
            true
        } catch (e: ApiException) {
            if (e.statusCode == 401) {
                sessionManager.clear()
                false
            } else {
                true
            }
        } catch (e: IOException) {
            true
        }
    }

    suspend fun logout() {
        val current = sessionManager.current()
        if (current != null) {
            runCatching { apiClient.logout(current.accessToken) }
        }
        sessionManager.clear()
    }

    companion object {
        private const val PLATFORM = "fire_tv"
    }
}
