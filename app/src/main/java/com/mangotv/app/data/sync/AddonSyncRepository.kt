package com.mangotv.app.data.sync

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.addon.AddonChange
import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.AddonManifest
import com.mangotv.app.data.model.InstalledAddon
import com.mangotv.app.data.network.AddonSyncApiClient
import com.mangotv.app.data.network.AddonSyncDto
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromJsonElement
import kotlinx.serialization.encodeToJsonElement
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * The fourth cloud-sync domain (Milestone 9), item-level like Watchlist:
 * an install/enable-toggle pushes just the one changed addon, a remove
 * pushes just that removal, and a pull replaces the local cache with the
 * account's full active addon list -- with one deliberate exception (see
 * [pullFromServer]) that no other domain needed.
 *
 * Every other synced domain starts genuinely empty locally before any
 * account exists (an empty watchlist, default settings, no watch
 * history), so a brand-new account's equally-empty cloud state is a
 * harmless no-op to pull down. Addons are different: AddonRepository
 * auto-installs Cinemeta on first launch, unconditionally, before the
 * auth gate even exists -- so by the time a user finishes creating a
 * brand-new account, the local list is *never* empty. A naive
 * pull-always-wins policy would silently uninstall that default the
 * moment every single new account signs in for the first time, which
 * would be this milestone's own sync code actively destroying working,
 * existing functionality (the one thing the project's rules are most
 * explicit about never doing). So: an empty *cloud* response pushes this
 * device's current local addons up as the account's initial set instead
 * of pulling the empty state down over them. This is deliberately narrow
 * and safe-by-construction (it only ever pushes local state up, never
 * discards it) -- it is not an attempt at Milestone 11's full "reconcile
 * pre-existing local data across every domain, with an explicit user
 * choice" design, which stays exactly as out of scope here as it is for
 * every other sync repository.
 */
class AddonSyncRepository(
    private val addonRepository: AddonRepository,
    private val authRepository: AuthRepository
) {
    private val apiClient = AddonSyncApiClient(BuildConfig.API_BASE_URL)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        addonRepository.onLocalChange = { change -> pushToServer(change) }
    }

    /** Pulls this account's active addon list and replaces the local cache with it -- except when the cloud has nothing yet, see the class kdoc. Same two call sites (auth gate, post-QR-sign-in) as the other sync repositories. Fire-and-forget: must never delay getting the user into the app. */
    suspend fun pullFromServer() {
        val token = freshAccessTokenOrNull() ?: return
        try {
            val response = apiClient.getAddons(token)
            if (response.items.isEmpty()) {
                pushAllLocalAsInitialSeed(token)
            } else {
                val items = response.items.mapNotNull { dto -> runCatching { dto.toInstalledAddon() }.getOrNull() }
                addonRepository.applyRemote(items)
            }
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            // Transient -- local cache stays at its last-known-good state.
        }
    }

    /** This account has never synced an addon from any device -- push whatever this device already has (e.g. the auto-installed Cinemeta default) up as the seed, rather than pulling the empty cloud state down over it. Each addon is pushed independently; one failing doesn't block the rest. */
    private suspend fun pushAllLocalAsInitialSeed(token: String) {
        val localAddons = addonRepository.installedAddons.value
        localAddons.forEachIndexed { index, addon ->
            try {
                apiClient.upsertAddon(token, addon.toDto(sortOrder = index, updatedAt = Iso8601.nowString()))
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    authRepository.clearSessionOnConfirmedUnauthorized()
                    return
                }
            } catch (e: IOException) {
                // Transient -- this one addon didn't make it up this time; the next pull or local change will retry.
            }
        }
    }

    /** Fire-and-forget: AddonRepository calls this via onLocalChange right after a genuine local install/remove/enable-toggle finishes persisting. */
    private fun pushToServer(change: AddonChange) {
        scope.launch {
            val token = freshAccessTokenOrNull() ?: return@launch
            try {
                when (change) {
                    is AddonChange.Upserted -> {
                        val response = apiClient.upsertAddon(
                            token,
                            change.addon.toDto(sortOrder = change.sortOrder, updatedAt = Iso8601.nowString())
                        )
                        reconcile(response)
                    }
                    is AddonChange.Removed -> {
                        val response = apiClient.removeAddon(token, change.manifestUrl, Iso8601.nowString())
                        // null means this addon was never synced from any
                        // device -- nothing server-side to reconcile against.
                        if (response != null) reconcile(response)
                    }
                }
            } catch (e: ApiException) {
                if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            } catch (e: IOException) {
                // Transient -- nothing to reconcile locally; the next
                // change, or the next login's pull, will retry.
            }
        }
    }

    /**
     * Applies one addon's authoritative post-write state into the local
     * list (via AddonRepository.applyRemote's own full-list-replace
     * shape, same as WatchlistSyncRepository's reconcile). Usually a
     * no-op (this write's own values echoed back), but restores or drops
     * a locally-optimistic entry if this push lost a last-write-wins race
     * to a near-simultaneous change from another device.
     */
    private suspend fun reconcile(dto: AddonSyncDto) {
        val current = addonRepository.installedAddons.value
        val matches: (InstalledAddon) -> Boolean = { it.manifestUrl == dto.manifestUrl }
        val updated = if (dto.deletedAt != null) {
            current.filterNot(matches)
        } else {
            val addon = runCatching { dto.toInstalledAddon() }.getOrNull() ?: return
            if (current.any(matches)) current.map { if (matches(it)) addon else it } else current + addon
        }
        addonRepository.applyRemote(updated)
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private fun AddonSyncDto.toInstalledAddon(): InstalledAddon = InstalledAddon(
        manifestUrl = manifestUrl,
        manifest = json.decodeFromJsonElement(AddonManifest.serializer(), manifestJson),
        enabled = enabled
    )

    private fun InstalledAddon.toDto(sortOrder: Int, updatedAt: String): AddonSyncDto = AddonSyncDto(
        manifestUrl = manifestUrl,
        addonId = manifest.id,
        name = manifest.name,
        manifestJson = json.encodeToJsonElement(AddonManifest.serializer(), manifest).jsonObject,
        enabled = enabled,
        sortOrder = sortOrder,
        updatedAt = updatedAt
    )
}
