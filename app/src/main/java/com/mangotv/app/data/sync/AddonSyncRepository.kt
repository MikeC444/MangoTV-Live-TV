package com.mangotv.app.data.sync

import android.content.Context
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * The fourth cloud-sync domain (Milestone 9), item-level like Watchlist:
 * an install/enable-toggle pushes just the one changed addon, a remove
 * pushes just that removal, and a pull replaces the local cache with the
 * account's full active addon list.
 *
 * Through Milestone 10, [pullFromServer] special-cased an empty cloud
 * response by pushing this device's local addons (e.g. the
 * auto-installed Cinemeta default) up as a seed, on *every* pull, to
 * avoid a brand-new account's first sign-in silently uninstalling it.
 * Milestone 11 found the real bug in doing that unconditionally: a user
 * who deliberately clears every addon on one device would have it
 * silently resurrected the next time a second, already-signed-in device
 * happened to pull with an (now correctly) empty cloud response. That
 * "seed from local if the cloud is empty" behavior is still real and
 * still needed, but only belongs at *first login* — it now lives in
 * FirstLoginMigrationCoordinator (via [pushAllLocalUp], gated by
 * FirstSyncState), not here. An ordinary pull, from here on, follows the
 * same plain "cloud is the source of truth" rule every other domain's
 * pullFromServer() already does.
 *
 * Milestone 10 added [retryPending]: a failed push now persists to a
 * small durable outbox ([pendingStore]) instead of just being dropped --
 * SyncManager drains it on login/launch and when connectivity returns.
 */
class AddonSyncRepository(
    context: Context,
    private val addonRepository: AddonRepository,
    private val authRepository: AuthRepository
) {
    private val apiClient = AddonSyncApiClient(BuildConfig.API_BASE_URL)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingStore = PendingChangeStore(context, "mango_addons_pending", AddonSyncDto.serializer())

    init {
        addonRepository.onLocalChange = { change -> pushToServer(change) }
    }

    /** Pulls this account's active addon list and replaces the local cache with it. Called by SyncManager on login/launch. Fire-and-forget: must never delay getting the user into the app. */
    suspend fun pullFromServer() {
        try {
            val token = freshAccessTokenOrNull() ?: return
            val response = apiClient.getAddons(token)
            val items = response.items.mapNotNull { dto -> runCatching { dto.toInstalledAddon() }.getOrNull() }
            addonRepository.applyRemote(items)
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            // Transient -- local cache stays at its last-known-good state.
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Unexpected (Milestone 13 -- e.g. a malformed response from a
            // degraded backend/database) -- degrade the same way a network
            // failure does rather than crashing the caller.
        }
    }

    /**
     * Peeks whether this account has ever synced an addon from any
     * device, without applying anything locally. Used only by Milestone
     * 11's first-login migration decision. Returns null (not a guess)
     * when the check itself couldn't complete.
     */
    suspend fun isCloudEmpty(): Boolean? {
        val token = freshAccessTokenOrNull() ?: return null
        return try {
            apiClient.getAddons(token).items.isEmpty()
        } catch (e: ApiException) {
            null
        } catch (e: IOException) {
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /** Pushes every addon currently installed on this device up, in their current list order -- used by FirstLoginMigrationCoordinator, both for the user's explicit SYNC choice and for the no-prompt-needed "cloud has nothing yet" auto-resolution. One addon failing doesn't block the rest; a failure is queued for retry like any other failed push. */
    suspend fun pushAllLocalUp() {
        val token = freshAccessTokenOrNull() ?: return
        val localAddons = addonRepository.installedAddons.value
        localAddons.forEachIndexed { index, addon ->
            val dto = addon.toDto(sortOrder = index, updatedAt = Iso8601.nowString())
            try {
                apiClient.upsertAddon(token, dto)
            } catch (e: ApiException) {
                pendingStore.put(addon.manifestUrl, dto)
                if (e.statusCode == 401) {
                    authRepository.clearSessionOnConfirmedUnauthorized()
                    return
                }
            } catch (e: IOException) {
                pendingStore.put(addon.manifestUrl, dto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unexpected -- queue for retry, try the rest of the batch.
                pendingStore.put(addon.manifestUrl, dto)
            }
        }
    }

    /** Drops every pending outbox entry (Milestone 12's account switching) -- see PendingChangeStore.clear()'s own kdoc for why a queued push must never survive into a different account's session. */
    suspend fun clearPending() = pendingStore.clear()

    /** Retries every addon this device has failed to push so far. Called by SyncManager on login/launch (after pullFromServer) and when network connectivity returns. */
    suspend fun retryPending() {
        val pending = try {
            pendingStore.all()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return
        }
        if (pending.isEmpty()) return
        val token = freshAccessTokenOrNull() ?: return

        for ((key, dto) in pending) {
            try {
                if (dto.deletedAt != null) {
                    val response = apiClient.removeAddon(token, dto.manifestUrl, dto.updatedAt)
                    if (response != null) reconcile(response)
                } else {
                    reconcile(apiClient.upsertAddon(token, dto))
                }
                pendingStore.remove(key)
            } catch (e: ApiException) {
                if (e.statusCode == 401) {
                    authRepository.clearSessionOnConfirmedUnauthorized()
                    return
                }
                // Left queued; try the rest of the batch.
            } catch (e: IOException) {
                // Still offline -- leave this and the rest of the batch
                // queued and stop; they'd fail identically right now.
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unexpected -- leave this item queued, try the rest of the batch.
            }
        }
    }

    /** Fire-and-forget: AddonRepository calls this via onLocalChange right after a genuine local install/remove/enable-toggle finishes persisting. */
    private fun pushToServer(change: AddonChange) {
        scope.launch {
            val key = change.naturalKey()
            val pendingDto = change.toPendingDto()
            try {
                val token = freshAccessTokenOrNull()
                if (token == null) {
                    pendingStore.put(key, pendingDto)
                    return@launch
                }
                when (change) {
                    is AddonChange.Upserted -> reconcile(apiClient.upsertAddon(token, pendingDto))
                    is AddonChange.Removed -> {
                        val response = apiClient.removeAddon(token, change.manifestUrl, pendingDto.updatedAt)
                        // null means this addon was never synced from any
                        // device -- nothing server-side to reconcile against.
                        if (response != null) reconcile(response)
                    }
                }
                pendingStore.remove(key)
            } catch (e: ApiException) {
                pendingStore.put(key, pendingDto)
                if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            } catch (e: IOException) {
                pendingStore.put(key, pendingDto)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unexpected -- queue for retry, same as a network failure.
                // An uncaught exception here would otherwise propagate out
                // of this launch{} and crash the app (Milestone 13).
                pendingStore.put(key, pendingDto)
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

    private fun AddonChange.naturalKey(): String = when (this) {
        is AddonChange.Upserted -> addon.manifestUrl
        is AddonChange.Removed -> manifestUrl
    }

    /** A pending removal is stored as a DTO with only the fields a DELETE actually needs (an empty manifestJson placeholder -- irrelevant for a removal), deletedAt set to its own updatedAt as the "this is a removal, not an upsert" marker retryPending() reads. */
    private fun AddonChange.toPendingDto(): AddonSyncDto = when (this) {
        is AddonChange.Upserted -> addon.toDto(sortOrder = sortOrder, updatedAt = Iso8601.nowString())
        is AddonChange.Removed -> {
            val now = Iso8601.nowString()
            AddonSyncDto(
                manifestUrl = manifestUrl,
                addonId = "",
                name = "",
                manifestJson = JsonObject(emptyMap()),
                enabled = false,
                sortOrder = 0,
                updatedAt = now,
                deletedAt = now
            )
        }
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
