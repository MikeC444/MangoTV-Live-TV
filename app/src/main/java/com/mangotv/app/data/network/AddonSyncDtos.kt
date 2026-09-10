package com.mangotv.app.data.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// Wire-format DTOs for /user/addons (see server/src/schemas/addons.ts and
// src/routes/addons.ts) -- the cloud mirror of the local InstalledAddon
// list. manifestJson carries the addon's own AddonManifest, serialized as
// a plain JsonObject rather than decoded into AddonManifest at this layer
// -- the same "opaque, addon-defined JSON" treatment AddonManifest.resources
// already gets locally -- so AddonSyncRepository owns the one place that
// converts between InstalledAddon and this wire shape.

@Serializable
data class AddonSyncDto(
    val manifestUrl: String,
    val addonId: String,
    val name: String,
    val manifestJson: JsonObject,
    val enabled: Boolean,
    val sortOrder: Int,
    val updatedAt: String,
    /** Non-null means this addon is currently removed -- only meaningful on a POST/DELETE response; GET /user/addons never returns a deleted addon. */
    val deletedAt: String? = null
)

@Serializable
data class AddonSyncListResponse(val items: List<AddonSyncDto>)
