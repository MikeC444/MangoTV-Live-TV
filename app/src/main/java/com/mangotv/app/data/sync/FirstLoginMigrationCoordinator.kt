package com.mangotv.app.data.sync

import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.history.ContinueWatchingRepository
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.data.provider.MyListRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** What [FirstLoginMigrationCoordinator.decide] concludes -- see its own kdoc for what each outcome means and when it applies. */
sealed interface MigrationDecision {
    /** No prompt needed -- proceed with an ordinary sync (cloud wins, as always). Covers: this device already resolved the question before, there's no meaningful local data worth protecting, or the cloud couldn't be checked (never guessed at). */
    data object ProceedNormally : MigrationDecision

    /** No prompt needed -- local data exists, and the cloud was confirmed (not assumed) to have nothing yet for this account. Push it up automatically; there's no actual disagreement to ask the user about. */
    data object AutoSyncEmptyCloud : MigrationDecision

    /** Both sides have real, independent data. Only here does the user actually need to choose. */
    data object NeedsUserChoice : MigrationDecision
}

/**
 * Milestone 11 — First-Login Local Data Migration. Decides, once per
 * device (see [FirstSyncState]), whether "sync existing data to your
 * account, or start fresh" needs asking at all, and carries out
 * whichever answer (explicit or auto-resolved) it ends up with.
 *
 * The full decision tree, in the order it's actually evaluated:
 * 1. Already resolved on this device before -> [MigrationDecision.ProceedNormally],
 *    nothing rechecked.
 * 2. No meaningful local data to protect -> [MigrationDecision.ProceedNormally]
 *    (marks first-sync done: there's nothing here worth ever asking
 *    about again, and marking it now is what stops a *later* sign-in as
 *    a *different* account on this same device from being asked to
 *    "sync" data that, by then, actually belongs to a previous account
 *    -- see FirstSyncState's own kdoc for the full cross-account
 *    reasoning).
 * 3. Local data exists; the cloud is confirmed empty for every domain ->
 *    [MigrationDecision.AutoSyncEmptyCloud] -- there's no real
 *    disagreement to resolve (nothing on the cloud side to conflict
 *    with), so this pushes local data up without bothering the user.
 * 4. Local data exists; the cloud has something too -> [MigrationDecision.NeedsUserChoice].
 *
 * A cloud peek that fails outright (offline, server error) is never
 * treated as "empty" -- that would risk silently pushing local data over
 * cloud state this device just couldn't see. It falls back to
 * [MigrationDecision.ProceedNormally] instead, and deliberately leaves
 * first-sync *not* marked done, so a later sign-in gets a genuine chance
 * to resolve this properly instead of the question being silently
 * skipped forever because of one transient failure.
 */
class FirstLoginMigrationCoordinator(
    private val firstSyncState: FirstSyncState,
    private val addonRepository: AddonRepository,
    private val myListRepository: MyListRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val homeRowPreferencesRepository: HomeRowPreferencesRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val watchlistSyncRepository: WatchlistSyncRepository,
    private val continueWatchingSyncRepository: ContinueWatchingSyncRepository,
    private val addonSyncRepository: AddonSyncRepository,
    private val syncManager: SyncManager
) {
    /** The core decision -- call once, right after a fresh QR sign-in completes. Never blocks for long on a dead network: see the class kdoc for why a failed cloud peek resolves to [MigrationDecision.ProceedNormally] rather than guessing. */
    suspend fun decide(): MigrationDecision {
        if (firstSyncState.isDone()) return MigrationDecision.ProceedNormally

        if (!hasLocalData()) {
            firstSyncState.markDone()
            return MigrationDecision.ProceedNormally
        }

        val peeks = coroutineScope {
            val settings = async { settingsSyncRepository.isCloudEmpty() }
            val watchlist = async { watchlistSyncRepository.isCloudEmpty() }
            val continueWatching = async { continueWatchingSyncRepository.isCloudEmpty() }
            val addons = async { addonSyncRepository.isCloudEmpty() }
            listOf(settings.await(), watchlist.await(), continueWatching.await(), addons.await())
        }

        if (peeks.any { it == null }) return MigrationDecision.ProceedNormally

        return if (peeks.all { it == true }) MigrationDecision.AutoSyncEmptyCloud else MigrationDecision.NeedsUserChoice
    }

    /** Carries out the SYNC choice (explicit or auto-resolved via [MigrationDecision.AutoSyncEmptyCloud]): pushes this device's local data up across all four domains in parallel, then marks first-sync done. */
    suspend fun resolveSync() {
        coroutineScope {
            launch { settingsSyncRepository.pushAllLocalUp() }
            launch { watchlistSyncRepository.pushAllLocalUp() }
            launch { continueWatchingSyncRepository.pushAllLocalUp() }
            launch { addonSyncRepository.pushAllLocalUp() }
        }
        firstSyncState.markDone()
    }

    /** Carries out the START FRESH choice: pulls the account's cloud state down over local data (via the same SyncManager.syncAll() an ordinary launch uses), then marks first-sync done. */
    suspend fun resolveStartFresh() {
        syncManager.syncAll()
        firstSyncState.markDone()
    }

    /** Fast and local-only: every check here reads an already-loaded StateFlow, no network involved. */
    private fun hasLocalData(): Boolean {
        val addons = addonRepository.installedAddons.value
        val hasCustomAddons = addons.isNotEmpty() && !addonRepository.isJustDefaultAddon()
        val homeRows = homeRowPreferencesRepository.preferences.value
        return myListRepository.items.value.isNotEmpty() ||
            continueWatchingRepository.items.value.isNotEmpty() ||
            hasCustomAddons ||
            homeRows.order.isNotEmpty() ||
            homeRows.hiddenRowIds.isNotEmpty() ||
            playerPreferencesRepository.preferences.value != PlayerPreferences()
    }
}
