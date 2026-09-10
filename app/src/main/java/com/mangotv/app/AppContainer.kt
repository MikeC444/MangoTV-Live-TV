package com.mangotv.app

import android.content.Context
import com.mangotv.app.data.addon.AddonRepository
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.history.ContinueWatchingRepository
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.data.provider.MyListRepository
import com.mangotv.app.data.sync.AddonSyncRepository
import com.mangotv.app.data.sync.ContinueWatchingSyncRepository
import com.mangotv.app.data.sync.FirstLoginMigrationCoordinator
import com.mangotv.app.data.sync.FirstSyncState
import com.mangotv.app.data.sync.SettingsSyncRepository
import com.mangotv.app.data.sync.SyncManager
import com.mangotv.app.data.sync.WatchlistSyncRepository

/**
 * A small hand-rolled container instead of a DI framework: this app only has
 * a couple of app-scoped singletons so far, and Hilt/Dagger would be a lot
 * of ceremony for that.
 *
 * addonRepository is constructed eagerly, not lazily: its init block kicks
 * off restoring installed addons from disk back into ProviderRegistry, and
 * Home reads ProviderRegistry directly without ever touching this
 * repository. A lazy property would only construct (and start that
 * restore) the first time something visits Settings > Addons — meaning on
 * a fresh process (e.g. right after a device reboot) Home would show
 * "library is empty" indefinitely even with addons already installed,
 * since nothing ever re-registered them.
 *
 * myListRepository was lazy before Milestone 7 (only Home's hero, Detail's
 * hero, and the My List screen ever touched it) — now eager, for the same
 * reason playerPreferencesRepository/homeRowPreferencesRepository became
 * eager in Milestone 6 (see that paragraph below): watchlistSyncRepository
 * needs a real instance to wire its onLocalChange push hook onto at
 * construction time, and gaining cloud sync means a pull has to be able to
 * replace this cache on every launch regardless of whether My List has
 * been visited yet this session. A lazy property would also be
 * self-defeating here in practice — watchlistSyncRepository (itself eager,
 * for the reason given further below) reads it in its own constructor
 * call, which would force the lazy initializer to run immediately anyway.
 *
 * authRepository is eager like addonRepository, for the same shape of
 * reason: the auth gate is the very first screen the app shows and needs
 * an answer immediately, so its session load gets a head start here
 * rather than waiting for the gate's ViewModel to be constructed. This is
 * safe to do eagerly because SessionManager defers its one genuinely
 * expensive step (TokenCipher's Android Keystore setup) to first actual
 * use on a background dispatcher, never to construction time.
 *
 * playerPreferencesRepository and homeRowPreferencesRepository were lazy
 * before Milestone 6 (only constructed the first time playback started, or
 * Home/Settings > Home Rows was visited) — now eager, because cloud
 * settings sync means both need to be ready the moment syncManager.syncAll()
 * pulls this account's settings from the server on every launch (see
 * AuthGateViewModel): a pull has to write into both local caches
 * regardless of whether the user has touched either screen yet this
 * session, so the previous "only construct when that specific screen is
 * first visited" laziness no longer reflects how these are actually used.
 *
 * settingsSyncRepository and watchlistSyncRepository are both eager so
 * their constructors can wire each domain's onLocalChange push hook before
 * anything has a chance to mutate it.
 *
 * continueWatchingRepository is eager for the same reason myListRepository
 * is (see above) — its own sync repository is constructed eagerly right
 * after it and reads it as a constructor argument, which forces the same
 * lazy-would-be-self-defeating outcome if declared otherwise. It also
 * needs to be ready synchronously the moment PlayerViewModel looks up a
 * resume position for whatever title/episode is about to play — unlike
 * watchlistSyncRepository, continueWatchingSyncRepository has no
 * onLocalChange hook to wire (nothing here is mutated from a local
 * repository call the way My List/Settings are — see its own kdoc), so
 * eagerness here is purely about that resume-position lookup and
 * pull-on-launch being ready immediately, not about hook wiring order.
 *
 * addonSyncRepository is eager for the same onLocalChange-wiring reason
 * as settingsSyncRepository/watchlistSyncRepository — addonRepository
 * itself was already eager well before Milestone 9 for its own,
 * unrelated reason (see its own paragraph above), so this milestone
 * didn't need to change addonRepository's own laziness, only add its
 * sync counterpart alongside it.
 *
 * syncManager (Milestone 10) is eager and constructed last, once every
 * sync repository it orchestrates already exists — its own init{} block
 * registers a device-wide network-connectivity callback that needs to be
 * armed for the whole process lifetime, the same "start this the moment
 * the process starts, not the moment some screen first needs it" reasoning
 * as addonRepository/authRepository above, just for a different kind of
 * side effect (a registered OS callback instead of a disk read).
 *
 * firstLoginMigrationCoordinator (Milestone 11) is lazy, unlike most of
 * the above: nothing else constructs it as an eager constructor argument
 * the way e.g. watchlistSyncRepository forces myListRepository, and it
 * has no init{} side effect of its own to arm early (no hook to wire, no
 * callback to register) -- QrSignInViewModel is its only caller, and only
 * right after a fresh sign-in completes, so there's no reason for it to
 * exist before then.
 */
class AppContainer(context: Context) {
    val addonRepository: AddonRepository = AddonRepository(context)
    val authRepository: AuthRepository = AuthRepository(context)
    val addonSyncRepository: AddonSyncRepository = AddonSyncRepository(context, addonRepository, authRepository)
    val playerPreferencesRepository: PlayerPreferencesRepository = PlayerPreferencesRepository(context)
    val homeRowPreferencesRepository: HomeRowPreferencesRepository = HomeRowPreferencesRepository(context)
    val settingsSyncRepository: SettingsSyncRepository = SettingsSyncRepository(
        context, homeRowPreferencesRepository, playerPreferencesRepository, authRepository
    )
    val myListRepository: MyListRepository = MyListRepository(context)
    val watchlistSyncRepository: WatchlistSyncRepository = WatchlistSyncRepository(context, myListRepository, authRepository)
    val continueWatchingRepository: ContinueWatchingRepository = ContinueWatchingRepository(context)
    val continueWatchingSyncRepository: ContinueWatchingSyncRepository = ContinueWatchingSyncRepository(
        context, continueWatchingRepository, authRepository
    )
    val syncManager: SyncManager = SyncManager(
        context, settingsSyncRepository, watchlistSyncRepository, continueWatchingSyncRepository, addonSyncRepository
    )
    val firstLoginMigrationCoordinator: FirstLoginMigrationCoordinator by lazy {
        FirstLoginMigrationCoordinator(
            firstSyncState = FirstSyncState(context),
            addonRepository = addonRepository,
            myListRepository = myListRepository,
            continueWatchingRepository = continueWatchingRepository,
            homeRowPreferencesRepository = homeRowPreferencesRepository,
            playerPreferencesRepository = playerPreferencesRepository,
            settingsSyncRepository = settingsSyncRepository,
            watchlistSyncRepository = watchlistSyncRepository,
            continueWatchingSyncRepository = continueWatchingSyncRepository,
            addonSyncRepository = addonSyncRepository,
            syncManager = syncManager
        )
    }
}
