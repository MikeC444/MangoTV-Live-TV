package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for the /auth/qr/* and /auth/refresh endpoints (see
// server/src/schemas/qr.ts and src/routes/auth.ts). The TV never calls
// /auth/register or /auth/login directly — those are the phone/web
// activation page's job — so no request/response shapes for them exist
// on this side.

@Serializable
data class QrCreateRequest(
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String? = null
)

@Serializable
data class QrCreateResponse(
    val token: String,
    val activationUrl: String,
    val expiresAt: String
)

@Serializable
data class QrStatusResponse(
    val status: String,
    val accessToken: String? = null,
    val accessTokenExpiresAt: String? = null,
    val refreshToken: String? = null,
    val refreshTokenExpiresAt: String? = null,
    val user: UserDto? = null
)

@Serializable
data class UserDto(
    val id: String,
    val email: String,
    val displayName: String? = null
)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class TokenPairResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String
)

@Serializable
data class ErrorResponse(val error: String, val requestId: String? = null)
