package com.mangotv.app.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTest {

    private fun session(accessExpiresInMs: Long, refreshExpiresInMs: Long): Session {
        val now = System.currentTimeMillis()
        return Session(
            accessToken = "access",
            accessTokenExpiresAtMillis = now + accessExpiresInMs,
            refreshToken = "refresh",
            refreshTokenExpiresAtMillis = now + refreshExpiresInMs,
            user = AuthenticatedUser(id = "u1", email = "a@example.com")
        )
    }

    @Test
    fun `access token is valid well before its expiry`() {
        val session = session(accessExpiresInMs = 60_000, refreshExpiresInMs = 1_000_000)
        assertTrue(session.isAccessTokenValid())
    }

    @Test
    fun `access token inside its safety margin is treated as needing refresh`() {
        // Expires in 10s, well inside the 30s safety margin.
        val session = session(accessExpiresInMs = 10_000, refreshExpiresInMs = 1_000_000)
        assertFalse(session.isAccessTokenValid())
    }

    @Test
    fun `access token already past its literal expiry is invalid`() {
        val session = session(accessExpiresInMs = -1_000, refreshExpiresInMs = 1_000_000)
        assertFalse(session.isAccessTokenValid())
    }

    @Test
    fun `refresh token is valid before its expiry`() {
        val session = session(accessExpiresInMs = -1_000, refreshExpiresInMs = 60_000)
        assertTrue(session.isRefreshTokenValid())
    }

    @Test
    fun `refresh token past its expiry is invalid`() {
        val session = session(accessExpiresInMs = -1_000, refreshExpiresInMs = -1_000)
        assertFalse(session.isRefreshTokenValid())
    }
}
