package com.mangotv.app.data.sync

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.WatchlistApiClient
import com.mangotv.app.data.network.WatchlistItemDto
import com.mangotv.app.data.provider.MyListRepository
import com.mangotv.app.data.provider.SavedListItem
import com.mangotv.app.data.provider.WatchlistChange
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The second cloud-sync domain (Milestone 7), item-level rather than a
 * whole-list PUT like SettingsSyncRepository's single row: an add pushes
 * just the added item, a remove pushes just that item's removal, and a
 * pull replaces the local cache with the account's full active list. This
 * is what the milestone's "prefer item-level records, don't upload and
 * replace the entire list every time" requirement actually means in
 * practice — the *push* path never re-sends items that didn't change; the
 * *pull* path legitimately does return the whole current list, because
 * that's just what "give me this account's watchlist" means.
 *
 * Same fire-and-forget, no-retry-queue posture as Milestone 6 (see
 * SettingsSyncRepository's own kdoc for the reasoning) — a failed push
 * isn't queued; the next local change, or the next login/launch's pull,
 * is what recovers from a transient failure.
 *
 * Deliberately does not bulk-push whatever is already sitting in
 * MyListRepository the first time this runs on an existing install — that
 * is Milestone 11's "first-login local data migration" (SYNC vs. START
 * FRESH), not this milestone's job. Until that ships, a pull always wins:
 * an account's local list is replaced by whatever the server has (empty,
 * for a brand new account), and only *future* toggles get pushed.
 */
class WatchlistSyncRepository(
    private val myListRepository: MyListRepository,
    private val authRepository: AuthRepository
) {
    private val apiClient = WatchlistApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        myListRepository.onLocalChange = { change -> pushToServer(change) }
    }

    /** Pulls this account's active watchlist and replaces the local cache with it. Called once per app launch when the auth gate finds an already-usable session, and once right after a fresh QR sign-in — same two call sites as SettingsSyncRepository.pullFromServer(). Fire-and-forget: must never delay getting the user into the app. */
    suspend fun pullFromServer() {
        val token = freshAccessTokenOrNull() ?: return
        try {
            val response = apiClient.getWatchlist(token)
            val items = response.items.mapNotNull { dto -> runCatching { dto.toSavedListItem() }.getOrNull() }
            myListRepository.applyRemote(items)
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            // Transient -- local cache stays at its last-known-good state.
        }
    }

    /** Fire-and-forget: MyListRepository calls this via onLocalChange right after a genuine local add/remove finishes persisting. */
    private fun pushToServer(change: WatchlistChange) {
        scope.launch {
            val token = freshAccessTokenOrNull() ?: return@launch
            try {
                when (change) {
                    is WatchlistChange.Added -> reconcile(apiClient.addOrUpdateItem(token, change.item.toDto()))
                    is WatchlistChange.Removed -> {
                        val response = apiClient.removeItem(
                            token,
                            providerId = change.providerId,
                            contentId = change.contentId,
                            contentType = change.contentType.name,
                            updatedAt = change.updatedAt
                        )
                        // null means this item was never synced from any
                        // device -- nothing server-side to reconcile against.
                        if (response != null) reconcile(response)
                    }
                }
            } catch (e: ApiException) {
                if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            } catch (e: IOException) {
                // Transient -- nothing to reconcile locally; the next local
                // change or the next login's pull will retry.
            }
        }
    }

    /**
     * Applies one item's authoritative post-write state into the local
     * list. Covers both the ordinary case (this write's own values echoed
     * back) and the rarer one where it lost a last-write-wins race to a
     * near-simultaneous change from another device — in which case the
     * server's outcome (possibly the opposite of what this device just did
     * locally) is what the local list needs to reflect instead.
     */
    private suspend fun reconcile(dto: WatchlistItemDto) {
        val current = myListRepository.items.value
        val matches: (SavedListItem) -> Boolean = {
            it.providerId == dto.providerId && it.id == dto.contentId && it.type.name == dto.contentType
        }
        val remoteItem = runCatching { dto.toSavedListItem() }.getOrNull()
        val updated = when {
            dto.deletedAt != null -> current.filterNot(matches)
            remoteItem == null -> current // unrecognized contentType (build/server skew) -- leave local state alone rather than guessing
            current.any(matches) -> current.map { if (matches(it)) remoteItem else it }
            else -> current + remoteItem
        }
        myListRepository.applyRemote(updated)
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private fun WatchlistItemDto.toSavedListItem(): SavedListItem = SavedListItem(
        id = contentId,
        type = ContentType.valueOf(contentType),
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        providerId = providerId,
        updatedAt = updatedAt
    )

    private fun SavedListItem.toDto(): WatchlistItemDto = WatchlistItemDto(
        providerId = providerId,
        contentId = id,
        contentType = type.name,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        year = year,
        rating = rating,
        updatedAt = updatedAt
    )
}
