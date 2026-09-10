package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for the /auth/qr/*, /auth/refresh, /auth/register, and
// /auth/login endpoints (see server/src/schemas/{qr,auth}.ts and
// src/routes/auth.ts). Register/login were built and tested in Milestone
// 3 but never called from the TV until the direct-entry sign-in path was
// added alongside the QR flow -- see PasswordSignInViewModel.

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

/** Body for POST /auth/register — mirrors server/src/schemas/auth.ts's registerSchema exactly. */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val displayName: String? = null,
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String? = null
)

/** Body for POST /auth/login — mirrors server/src/schemas/auth.ts's loginSchema exactly. */
@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
    val deviceId: String,
    val deviceName: String? = null,
    val platform: String? = null
)

/** Response shape shared by /auth/register and /auth/login (see routes/auth.ts's serializeTokens + user) — a flat token pair plus the authenticated user, always present, unlike QrStatusResponse's nullable fields. */
@Serializable
data class AuthResultResponse(
    val accessToken: String,
    val accessTokenExpiresAt: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: String,
    val user: UserDto
)

@Serializable
data class ErrorResponse(val error: String, val requestId: String? = null)
