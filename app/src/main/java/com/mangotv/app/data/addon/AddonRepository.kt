package com.mangotv.app.data.addon

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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
        if (stored.isEmpty() && !isDefaultAddonBootstrapped()) {
            installAddon(CINEMETA_MANIFEST_URL).onSuccess {
                setDefaultAddonBootstrapped()
            }
        }
    }

    suspend fun installAddon(rawUrl: String): Result<InstalledAddon> = withContext(Dispatchers.IO) {
        runCatching {
            require(rawUrl.isNotBlank()) { "Enter an addon URL first." }
            val manifestUrl = AddonUrl.normalizeManifestUrl(rawUrl)
            val manifest = client.fetchManifest(manifestUrl)
            val record = InstalledAddon(manifestUrl = manifestUrl, manifest = manifest, enabled = true)

            val updated = _installedAddons.value.filterNot { it.manifestUrl == manifestUrl } + record
            _installedAddons.value = updated
            persist(updated)

            ProviderRegistry.register(StremioAddonProvider(manifestUrl, manifest, client))
            record
        }
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
    }
}
