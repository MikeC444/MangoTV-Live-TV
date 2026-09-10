package com.mangotv.app.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Orchestrates the four cloud-sync domains (Settings, Watchlist, Continue
 * Watching, Addons) as one coherent system (Milestone 10), rather than
 * each ViewModel call site independently invoking all four
 * pullFromServer() methods by hand, as AuthGateViewModel/QrSignInViewModel
 * did through Milestone 9.
 *
 * Two triggers:
 * - [syncAll], called once by AuthGateViewModel (an already-usable
 *   session at launch) and once by QrSignInViewModel (right after a
 *   fresh sign-in) -- the exact two call sites every sync repository's
 *   own pullFromServer() has documented since Milestone 6. Pulls all four
 *   domains' authoritative state in parallel first, then drains all four
 *   retry queues in parallel -- pulling first establishes fresh ground
 *   truth; draining after makes sure any of this device's own
 *   not-yet-synced changes still go out on top of it, rather than being
 *   silently clobbered by a pull that runs after them.
 * - A registered ConnectivityManager.NetworkCallback calls
 *   [retryPendingAll] (not a full [syncAll] -- a fresh pull isn't needed
 *   just because the network came back, and would be wasteful to run on
 *   every reconnect) the moment the network transitions from unavailable
 *   to available, so an offline change doesn't sit queued until the user
 *   happens to relaunch the app or make another change of the same kind.
 *
 * Both entry points stay fire-and-forget from the caller's perspective,
 * same as every individual sync repository's own methods -- nothing here
 * ever blocks getting the user into the app or delays interacting with
 * it. Registering the connectivity callback can't fail loudly either: if
 * ConnectivityManager is unavailable for any reason, this degrades to
 * "no proactive reconnect retry" rather than crashing -- [syncAll] on the
 * next login/launch is still what ultimately recovers a queued change
 * either way.
 */
class SyncManager(
    context: Context,
    private val settingsSyncRepository: SettingsSyncRepository,
    private val watchlistSyncRepository: WatchlistSyncRepository,
    private val continueWatchingSyncRepository: ContinueWatchingSyncRepository,
    private val addonSyncRepository: AddonSyncRepository
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        registerConnectivityCallback()
    }

    /** Pulls every domain's authoritative state, then drains every domain's retry queue. */
    suspend fun syncAll() {
        coroutineScope {
            launch { settingsSyncRepository.pullFromServer() }
            launch { watchlistSyncRepository.pullFromServer() }
            launch { continueWatchingSyncRepository.pullFromServer() }
            launch { addonSyncRepository.pullFromServer() }
        }
        retryPendingAll()
    }

    /** Drains every domain's retry queue without a full pull -- what a network reconnect triggers, and what [syncAll] runs after its own pulls complete. */
    suspend fun retryPendingAll() = coroutineScope {
        launch { settingsSyncRepository.retryPending() }
        launch { watchlistSyncRepository.retryPending() }
        launch { continueWatchingSyncRepository.retryPending() }
        launch { addonSyncRepository.retryPending() }
    }

    private fun registerConnectivityCallback() {
        val connectivityManager = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { retryPendingAll() }
            }
        }
        // Registration itself can throw on some OEM/OS variants (e.g. a
        // security-restricted NetworkRequest); this callback is a
        // best-effort enhancement, not a correctness requirement -- see
        // this class's own kdoc for why a failed registration here is
        // safe to just skip rather than crash on.
        runCatching { connectivityManager.registerNetworkCallback(request, callback) }
    }
}
