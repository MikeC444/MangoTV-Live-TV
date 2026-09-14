package com.mangotv.app.data.network

import kotlinx.serialization.Serializable

// Wire-format DTOs for /user/trakt* (see server/src/routes/trakt.ts and
// server/src/services/traktService.ts). The Fire TV app never talks to
// Trakt's own api.trakt.tv directly, and never sees a Trakt access/refresh
// token -- these three calls are this backend's own connection-management
// surface, not a proxy that forwards Trakt's wire format verbatim.

@Serializable
data class TraktStatusResponse(
    val configured: Boolean,
    val connected: Boolean,
    val username: String? = null,
    val connectedAt: String? = null
)

@Serializable
data class TraktLinkStartResponse(
    val userCode: String,
    val verificationUrl: String,
    val directVerificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

/** status is one of "pending" | "connected" | "expired" | "denied" | "not_found" -- see DevicePollResult in traktService.ts. username is only ever present alongside "connected". */
@Serializable
data class TraktLinkPollResponse(
    val status: String,
    val username: String? = null
)
