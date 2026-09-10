package com.mangotv.app.data.sync

import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.history.ContinueWatchingRepository
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.data.provider.MyListRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Milestone 12 — Account Switching. The one thing FirstSyncState's own
 * kdoc (Milestone 11) flagged as still missing: through Milestone 11,
 * signing out revoked this device's session but left every local cache
 * exactly as it was. A *different* account signing in right after would
 * have briefly seen the *previous* account's watchlist, continue
 * watching, addons, and settings on screen during the moment between
 * authenticating and the first `syncAll()` pull overwriting that stale
 * data -- and, worse, a queued-but-not-yet-pushed change from the
 * previous account could have later been retried under the new
 * account's own access token, writing the wrong account's data
 * server-side.
 *
 * [signOut] closes both gaps in one parallel sweep:
 * - Every local cache is wiped, never pushed anywhere first -- the
 *   account being signed out of still owns that data server-side; this
 *   device is only forgetting its own local copy (see each `clear()`'s
 *   own kdoc for why it deliberately bypasses that domain's
 *   `onLocalChange` hook).
 * - Every pending outbox is dropped, not retried -- a queued push
 *   belongs to the account it was queued for, never replayed against
 *   whichever account happens to sign in next.
 * - [FirstSyncState] is reset, so a genuinely different account's own
 *   first sign-in on this device gets evaluated fresh instead of being
 *   skipped as "already resolved" for a question a *previous* account,
 *   not this one, actually resolved.
 *
 * Deliberately does not touch device/session management -- viewing or
 * revoking this account's *other* active sessions via the already-built
 * `GET`/`DELETE /auth/sessions` endpoints (Milestone 3). AccountScreen's
 * own Milestone 5 kdoc named that as its own separate later milestone,
 * distinct from account switching, and nothing found while building this
 * one changed that.
 */
class AccountSwitchCoordinator(
    private val authRepository: AuthRepository,
    private val firstSyncState: FirstSyncState,
    private val myListRepository: MyListRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val addonRepository: AddonRepository,
    private val homeRowPreferencesRepository: HomeRowPreferencesRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val watchlistSyncRepository: WatchlistSyncRepository,
    private val continueWatchingSyncRepository: ContinueWatchingSyncRepository,
    private val addonSyncRepository: AddonSyncRepository
) {
    /**
     * Signs out: revokes this device's session (best-effort server call,
     * unconditional local clear regardless of network outcome -- see
     * [AuthRepository.logout]) and wipes every account-scoped local
     * cache and outbox in parallel, so the device is genuinely blank the
     * moment this returns -- ready for any account, the same one
     * signing back in or a different one, to start from a clean slate.
     */
    suspend fun signOut() = coroutineScope {
        launch { authRepository.logout() }
        launch { myListRepository.clear() }
        launch { continueWatchingRepository.clear() }
        launch { addonRepository.clear() }
        launch { homeRowPreferencesRepository.clear() }
        launch { playerPreferencesRepository.clear() }
        launch { settingsSyncRepository.clearPending() }
        launch { watchlistSyncRepository.clearPending() }
        launch { continueWatchingSyncRepository.clearPending() }
        launch { addonSyncRepository.clearPending() }
        launch { firstSyncState.reset() }
    }
}
