package com.mangotv.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Mirrors the Stremio addon manifest.json contract
 * (https://github.com/Stremio/stremio-addon-sdk) — the open protocol that
 * both Stremio and the Nuvio addon ecosystem are built on. Any field an
 * addon doesn't declare is simply left null/empty; unknown extra fields on
 * the wire are ignored by the decoder rather than failing the parse, since
 * real-world addons vary a lot in what else they include.
 */
@Serializable
data class AddonManifest(
    val id: String,
    val name: String,
    val version: String = "0.0.0",
    val description: String? = null,
    val logo: String? = null,
    val background: String? = null,
    val types: List<String> = emptyList(),
    // Each entry is either a plain resource name ("catalog") or a scoped
    // object ({"name":"stream","types":[...],"idPrefixes":[...]}) per the
    // Stremio manifest spec — kept as raw JSON since Mango TV doesn't need
    // to inspect it (catalogs are driven by [catalogs] below).
    val resources: List<JsonElement> = emptyList(),
    val catalogs: List<AddonCatalogDef> = emptyList(),
    val idPrefixes: List<String> = emptyList()
)

@Serializable
data class AddonCatalogDef(
    val type: String,
    val id: String,
    val name: String? = null,
    val extra: List<AddonCatalogExtra> = emptyList()
)

/**
 * Describes one filterable parameter a catalog supports (genre, skip,
 * search, ...) per the Stremio addon protocol. Cinemeta and similar addons
 * expose only a single base catalog per type and rely entirely on this —
 * specifically a "genre" extra's [options] — to produce the many
 * genre-specific lists (Action, Comedy, ...) real Stremio shows.
 */
@Serializable
data class AddonCatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String>? = null,
    val optionsLimit: Int? = null
)

/**
 * An addon as tracked by Mango TV: where it came from, its last-known
 * manifest, and whether it's currently contributing to the catalog. This is
 * both the UI-facing model (Settings > Addons) and the persisted form
 * (serialized as-is into DataStore), since the two never need to diverge.
 */
@Serializable
data class InstalledAddon(
    val manifestUrl: String,
    val manifest: AddonManifest,
    val enabled: Boolean = true
)
