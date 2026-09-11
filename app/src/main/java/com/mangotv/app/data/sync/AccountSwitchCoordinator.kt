package com.mangotv.app.data.sync

import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.history.ContinueWatchingRepository
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.data.provider.MyListRepository
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
     *
     * Runs a bounded best-effort [retryPending][SettingsSyncRepository.retryPending]
     * sweep first, before any of that, while this session is still valid.
     * Without it, a change made moments before signing out (e.g. an addon
     * just added) that hadn't reached the server yet -- still genuinely
     * pending, not stale -- would be deleted by the outbox clear below
     * before ever getting a chance to send, then be gone for good: the
     * account being signed out of never actually received it server-side,
     * so the very next pullFromServer() (on this device or any other)
     * would never see it either. The outbox clear afterward is still
     * correct and still needed for its own documented reason (never
     * replay a queued push against whichever account signs in next) --
     * this only makes sure a push gets its one real chance to land under
     * the account that actually made it first. Bounded so a dead network
     * can't hang the sign-out action itself; whatever's still unsent when
     * the timeout hits is dropped exactly as it always was.
     */
    suspend fun signOut() {
        withTimeoutOrNull(PRE_SIGN_OUT_FLUSH_TIMEOUT_MS) {
            coroutineScope {
                launch { settingsSyncRepository.retryPending() }
                launch { watchlistSyncRepository.retryPending() }
                launch { continueWatchingSyncRepository.retryPending() }
                launch { addonSyncRepository.retryPending() }
            }
        }

        coroutineScope {
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

    private companion object {
        const val PRE_SIGN_OUT_FLUSH_TIMEOUT_MS = 5_000L
    }
}
