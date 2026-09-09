package com.mangotv.app.data.auth

import kotlinx.serialization.Serializable

@Serializable
data class AuthenticatedUser(
    val id: String,
    val email: String,
    val displayName: String? = null
)

/**
 * The locally persisted result of a successful sign-in (see
 * SessionManager). Expiry is stored as epoch millis rather than the
 * server's original ISO-8601 strings — converted once at the network
 * boundary (see util/Iso8601.kt) so everything downstream compares plain
 * Longs instead of re-parsing strings.
 */
@Serializable
data class Session(
    val accessToken: String,
    val accessTokenExpiresAtMillis: Long,
    val refreshToken: String,
    val refreshTokenExpiresAtMillis: Long,
    val user: AuthenticatedUser
) {
    /**
     * False a little before the access token's literal expiry — a request
     * that starts just before expiry shouldn't race the clock and arrive
     * server-side already expired.
     */
    fun isAccessTokenValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        accessTokenExpiresAtMillis > nowMillis + ACCESS_TOKEN_SAFETY_MARGIN_MS

    fun isRefreshTokenValid(nowMillis: Long = System.currentTimeMillis()): Boolean =
        refreshTokenExpiresAtMillis > nowMillis

    companion object {
        private const val ACCESS_TOKEN_SAFETY_MARGIN_MS = 30_000L
    }
}
