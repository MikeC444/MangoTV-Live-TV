package com.mangotv.app.data.addon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.InstalledAddon
import com.mangotv.app.data.provider.ProviderRegistry
import com.mangotv.app.data.provider.StremioAddonProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.addonDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_addons")

/**
 * Owns the addon lifecycle: fetching + validating a manifest, persisting the
 * installed list across restarts, and keeping [ProviderRegistry] (which
 * feeds Home) in sync with what's actually enabled.
 */
class AddonRepository(context: Context) {

    private val appContext = context.applicationContext
    private val client = StremioAddonClient()
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _installedAddons = MutableStateFlow<List<InstalledAddon>>(emptyList())
    val installedAddons: StateFlow<List<InstalledAddon>> = _installedAddons.asStateFlow()

    init {
        scope.launch { restoreFromDisk() }
    }

    private suspend fun restoreFromDisk() {
        val stored = readPersisted()
        _installedAddons.value = stored
        stored.filter { it.enabled }.forEach { addon ->
            ProviderRegistry.register(StremioAddonProvider(addon.manifestUrl, addon.manifest, client))
        }

        // First-ever launch (nothing installed yet, and we've never
        // successfully bootstrapped before) gets Cinemeta pre-installed so
        // Home has content out of the box instead of the empty-library
        // state — without the bootstrapped flag this would also refire
        // every time a user deliberately removes their last addon. Only
        // marked bootstrapped on actual success, so a launch with no
        // network simply retries next time instead of leaving the user
        // permanently addon-less.
        //
        // Registers from a bundled copy of Cinemeta's manifest
        // (assets/cinemeta_manifest.json) rather than fetching it live --
        // on a genuinely fresh install this used to be a full network
        // round-trip that had to finish before ProviderRegistry had
        // anything in it at all, which meant Home's own catalog fetch
        // couldn't even start yet, stacked on top of Home having no cache
        // yet either. The bundled copy only stands in for that one manifest
        // fetch; every real catalog/stream/meta request still goes to the
        // live Cinemeta server exactly as before, since those are resolved
        // from manifestUrl, not from the manifest content. Falls back to
        // the original live fetch if the bundled asset is ever missing or
        // fails to decode, so a packaging mistake degrades gracefully
        // instead of breaking first-launch bootstrap outright.
        if (stored.isEmpty() && !isDefaultAddonBootstrapped()) {
            val bundled = readBundledCinemetaManifest()
            if (bundled != null) {
                registerAndPersist(CINEMETA_MANIFEST_URL, bundled)
                setDefaultAddonBootstrapped()
            } else {
                installAddon(CINEMETA_MANIFEST_URL).onSuccess {
                    setDefaultAddonBootstrapped()
                }
            }
        }
    }

    private fun readBundledCinemetaManifest(): AddonManifest? = runCatching {
        appContext.assets.open(CINEMETA_MANIFEST_ASSET).bufferedReader().use { it.readText() }
    }.mapCatching { raw ->
        json.decodeFromString(AddonManifest.serializer(), raw)
    }.getOrNull()

    suspend fun installAddon(rawUrl: String): Result<InstalledAddon> = withContext(Dispatchers.IO) {
        runCatching {
            require(rawUrl.isNotBlank()) { "Enter an addon URL first." }
            val manifestUrl = AddonUrl.normalizeManifestUrl(rawUrl)
            val manifest = client.fetchManifest(manifestUrl)
            registerAndPersist(manifestUrl, manifest)
        }
    }

    private suspend fun registerAndPersist(manifestUrl: String, manifest: AddonManifest): InstalledAddon {
        val record = InstalledAddon(manifestUrl = manifestUrl, manifest = manifest, enabled = true)

        val updated = _installedAddons.value.filterNot { it.manifestUrl == manifestUrl } + record
        _installedAddons.value = updated
        persist(updated)

        ProviderRegistry.register(StremioAddonProvider(manifestUrl, manifest, client))
        return record
    }

    suspend fun removeAddon(manifestUrl: String) = withContext(Dispatchers.IO) {
        val addon = _installedAddons.value.firstOrNull { it.manifestUrl == manifestUrl }
        val updated = _installedAddons.value.filterNot { it.manifestUrl == manifestUrl }
        _installedAddons.value = updated
        persist(updated)
        addon?.let { ProviderRegistry.unregister(it.manifest.id) }
    }

    suspend fun setEnabled(manifestUrl: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val addon = _installedAddons.value.firstOrNull { it.manifestUrl == manifestUrl } ?: return@withContext
        val updated = _installedAddons.value.map { if (it.manifestUrl == manifestUrl) it.copy(enabled = enabled) else it }
        _installedAddons.value = updated
        persist(updated)

        if (enabled) {
            ProviderRegistry.register(StremioAddonProvider(manifestUrl, addon.manifest, client))
        } else {
            ProviderRegistry.unregister(addon.manifest.id)
        }
    }

    private suspend fun readPersisted(): List<InstalledAddon> {
        val prefs = appContext.addonDataStore.data.first()
        val raw = prefs[ADDONS_KEY] ?: return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(InstalledAddon.serializer()), raw)
        }.getOrDefault(emptyList())
    }

    private suspend fun persist(records: List<InstalledAddon>) {
        val raw = json.encodeToString(ListSerializer(InstalledAddon.serializer()), records)
        appContext.addonDataStore.edit { it[ADDONS_KEY] = raw }
    }

    private suspend fun isDefaultAddonBootstrapped(): Boolean =
        appContext.addonDataStore.data.first()[DEFAULT_ADDON_BOOTSTRAPPED_KEY] ?: false

    private suspend fun setDefaultAddonBootstrapped() {
        appContext.addonDataStore.edit { it[DEFAULT_ADDON_BOOTSTRAPPED_KEY] = true }
    }

    companion object {
        private val ADDONS_KEY = stringPreferencesKey("installed_addons_json")
        private val DEFAULT_ADDON_BOOTSTRAPPED_KEY = booleanPreferencesKey("default_addon_bootstrapped")

        // Stremio's own official Cinemeta addon (IMDb-sourced movie/series
        // catalogs and metadata) -- installed automatically on first launch
        // so a fresh install has real content immediately rather than
        // showing the empty-library state until a user manually finds and
        // adds an addon of their own.
        private const val CINEMETA_MANIFEST_URL = "https://v3-cinemeta.strem.io/manifest.json"

        // A verbatim copy of CINEMETA_MANIFEST_URL's response (see
        // readBundledCinemetaManifest) -- lets first-launch bootstrap skip
        // waiting on that one network round-trip. Addon manifests are only
        // ever fetched once at install time and trusted from persisted
        // storage from then on (same as every other addon in this app), so
        // bundling this doesn't introduce any new staleness behavior.
        private const val CINEMETA_MANIFEST_ASSET = "cinemeta_manifest.json"
    }
}
