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

/** What AddonSyncRepository (Milestone 9) reacts to after a genuine local install/remove/enable-toggle -- never fired from [AddonRepository.applyRemote]. sortOrder travels with Upserted rather than being re-derived by the sync layer, since AddonRepository already knows an addon's position in its own list at the moment it changes. */
sealed interface AddonChange {
    data class Upserted(val addon: InstalledAddon, val sortOrder: Int) : AddonChange
    data class Removed(val manifestUrl: String) : AddonChange
}

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

    /** Fired after a genuine local install/remove/enable-toggle finishes persisting -- AddonSyncRepository (Milestone 9) hooks this to push just the one changed addon. Never invoked from [applyRemote]. */
    var onLocalChange: ((AddonChange) -> Unit)? = null

    /** True only when the installed list is exactly the untouched first-launch default (just Cinemeta, enabled) -- used by Milestone 11's FirstLoginMigrationCoordinator to tell "genuinely customized" apart from "never touched since install," without leaking the Cinemeta URL constant itself outside this class. */
    fun isJustDefaultAddon(): Boolean {
        val current = _installedAddons.value
        return current.size == 1 && current[0].manifestUrl == CINEMETA_MANIFEST_URL && current[0].enabled
    }

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
            onLocalChange?.invoke(AddonChange.Upserted(record, updated.lastIndex))
            record
        }
    }

    suspend fun removeAddon(manifestUrl: String) = withContext(Dispatchers.IO) {
        val addon = _installedAddons.value.firstOrNull { it.manifestUrl == manifestUrl }
        val updated = _installedAddons.value.filterNot { it.manifestUrl == manifestUrl }
        _installedAddons.value = updated
        persist(updated)
        addon?.let { ProviderRegistry.unregister(it.manifest.id) }
        if (addon != null) onLocalChange?.invoke(AddonChange.Removed(manifestUrl))
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

        val sortOrder = updated.indexOfFirst { it.manifestUrl == manifestUrl }
        onLocalChange?.invoke(AddonChange.Upserted(addon.copy(enabled = enabled), sortOrder))
    }

    /**
     * Applies the server's current active addon list (a pull), replacing
     * the local cache wholesale and reconciling [ProviderRegistry] to
     * match -- unlike MyListRepository/ContinueWatchingRepository's own
     * applyRemote, this one has a live side effect to keep in sync, not
     * just a DataStore-backed cache. Unregisters every addon this device
     * previously knew about, then registers whichever of the new list is
     * enabled, the same "clear and rebuild from a fresh list" shape
     * restoreFromDisk() already uses at startup, rather than diffing old
     * vs. new.
     */
    suspend fun applyRemote(remoteAddons: List<InstalledAddon>) = withContext(Dispatchers.IO) {
        val previous = _installedAddons.value
        _installedAddons.value = remoteAddons
        persist(remoteAddons)

        previous.forEach { ProviderRegistry.unregister(it.manifest.id) }
        remoteAddons.filter { it.enabled }.forEach { addon ->
            ProviderRegistry.register(StremioAddonProvider(addon.manifestUrl, addon.manifest, client))
        }
    }

    /** Wipes every locally-cached addon (Milestone 12's account switching) without notifying [onLocalChange] — unregisters everything from [ProviderRegistry], same as [applyRemote]'s own cleanup step, but replaces with an empty list rather than a new one. The account being signed out of still owns these addons server-side; this device is only forgetting its own local copy. Deliberately leaves the Cinemeta auto-bootstrap flag untouched — that flag is a device-scoped "has this install ever shown default content" concept, not an account-scoped one, so a second account signing in on this same device is treated the same as a user who's deliberately removed every addon: no auto-reinstall. */
    suspend fun clear() = withContext(Dispatchers.IO) {
        val previous = _installedAddons.value
        _installedAddons.value = emptyList()
        persist(emptyList())
        previous.forEach { ProviderRegistry.unregister(it.manifest.id) }
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
