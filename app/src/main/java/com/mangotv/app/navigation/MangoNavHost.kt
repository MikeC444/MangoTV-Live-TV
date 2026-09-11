package com.mangotv.app.navigation

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.audio.BootSoundPlayer
import com.mangotv.app.data.audio.LocalUiSoundPlayer
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.ui.auth.AuthGateScreen
import com.mangotv.app.ui.auth.AuthMethodScreen
import com.mangotv.app.ui.auth.AuthStartScreen
import com.mangotv.app.ui.auth.GateDestination
import com.mangotv.app.ui.auth.PasswordSignInScreen
import com.mangotv.app.ui.auth.QrSignInScreen
import com.mangotv.app.ui.browse.MoviesScreen
import com.mangotv.app.ui.browse.TvShowsScreen
import com.mangotv.app.ui.detail.DetailScreen
import com.mangotv.app.ui.genres.GenreResultsScreen
import com.mangotv.app.ui.genres.GenresScreen
import com.mangotv.app.ui.search.SearchScreen
import com.mangotv.app.ui.loading.LoadingScreen
import com.mangotv.app.ui.mylist.MyListScreen
import com.mangotv.app.ui.home.HomeScreen
import com.mangotv.app.ui.home.HomeViewModel
import com.mangotv.app.ui.player.PlayerScreen
import com.mangotv.app.ui.settings.AccountScreen
import com.mangotv.app.ui.settings.AddAddonScreen
import com.mangotv.app.ui.settings.AddonsScreen
import com.mangotv.app.ui.settings.HomeRowsScreen
import com.mangotv.app.ui.settings.SettingsScreen
import com.mangotv.app.ui.settings.SoundSettingsScreen
import com.mangotv.app.ui.sources.SourcesScreen
import java.net.URLDecoder
import kotlinx.coroutines.delay

// Static, argument-less top-level destinations reached from the top nav bar.
// Navigating to one of these reuses/restores its existing back-stack entry
// (and therefore its ViewModelStoreOwner) instead of always pushing a fresh
// one -- without this, every tab switch tore down and rebuilt
// HomeViewModel/MoviesViewModel/etc. from scratch, discarding all
// already-fetched data and re-running every network fetch on every visit.
private val TAB_ROOT_ROUTES = setOf(
    MangoRoutes.HOME, MangoRoutes.MOVIES, MangoRoutes.TV_SHOWS,
    MangoRoutes.GENRES, MangoRoutes.SEARCH, MangoRoutes.MY_LIST, MangoRoutes.SETTINGS
)

// How long after cold boot begins the boot chime starts playing -- see its
// own call site for why this isn't just 0.
private const val BOOT_SOUND_START_DELAY_MS = 1000L

@Composable
fun MangoNavHost() {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as MangoTvApplication).container }

    // Constructed here, outside any NavHost destination, so it's scoped to
    // the Activity rather than to Home's own back-stack entry -- LoadingScreen
    // and the HOME destination below share this exact instance instead of
    // each getting their own. Sharing it is what lets LoadingScreen observe
    // (and preload images for) the SAME fetch Home itself ends up showing,
    // rather than duplicating that fetch a second time once Home mounts.
    val homeViewModel: HomeViewModel = viewModel()

    // Shown once, in place of the real nav graph, on cold boot -- see
    // LoadingScreen's own doc. dataReady mirrors what used to be the whole
    // of isAppReady (LoadingScreen's own onReady callback); isAppReady now
    // also waits on audioMidwayReached (see below) so the boot chime and
    // Home's own readiness are both required before the reveal, not just
    // whichever finishes first. Neither flag is ever reset once true, so
    // isAppReady is still "never re-armed for the rest of the process's
    // lifetime" exactly as before (tab switches, backgrounding, etc. don't
    // recompose MangoNavHost from scratch).
    var dataReady by remember { mutableStateOf(false) }
    var audioMidwayReached by remember { mutableStateOf(false) }
    val isAppReady = dataReady && audioMidwayReached

    // Starts the user's chosen boot chime BOOT_SOUND_START_DELAY_MS after
    // cold boot begins (not instantly -- a beat of silence over the first
    // frame reads more intentional than audio firing before anything's
    // even visible) and holds audioMidwayReached false until the chime
    // reaches its own halfway point after that -- see BootSoundPlayer's own
    // doc for why that's what makes the reveal land on the chime's midpoint
    // rather than its start. bootSoundPlayer is deliberately a plain local
    // instance, not something pulled from AppContainer: it's used exactly
    // once per process and releases itself when the chime finishes, unlike
    // every other AppContainer entry, which is a persistent app-scoped
    // singleton.
    val bootSoundPlayer = remember { BootSoundPlayer(context) }
    LaunchedEffect(Unit) {
        val selectedSound = container.soundPreferencesRepository.awaitSelectedBootSound()
        delay(BOOT_SOUND_START_DELAY_MS)
        bootSoundPlayer.startAndAwaitMidpoint(selectedSound)
        audioMidwayReached = true
    }
    // Defensive only: the chime normally releases itself on natural
    // completion. This just stops it from playing on into the background
    // in the rare case the Activity is torn down (e.g. the user backs out)
    // while it's still going.
    DisposableEffect(bootSoundPlayer) {
        onDispose { bootSoundPlayer.release() }
    }

    // Provided here, above both the loading screen and the real nav graph,
    // so every TvFocusSurface anywhere in the app (cards, buttons, nav
    // items) can play the nav/click sounds without each screen having to
    // thread UiSoundPlayer through its own parameters.
    CompositionLocalProvider(LocalUiSoundPlayer provides container.uiSoundPlayer) {
        if (!isAppReady) {
            LoadingScreen(homeViewModel = homeViewModel, onReady = { dataReady = true })
            return@CompositionLocalProvider
        }

        val navController = rememberNavController()

        // NavHost registers its OWN back-press handling internally (it's
        // what makes plain BACK navigate the back stack, and what drives
        // predictive-back) the moment it composes -- so a BackHandler
        // composed BEFORE the NavHost call below would be registered
        // EARLIER, making it the LESS recently registered callback, which
        // Compose's dispatcher always loses to whatever was registered
        // after it. That's exactly what silently broke this the first time:
        // NavHost's own default handling won, popped the stack correctly,
        // and this BackHandler's sound-playing body just never ran at all.
        // Composing it below, AFTER NavHost, fixes that -- it becomes the
        // most recently registered handler, so it wins by default.
        //
        // PlayerScreen registers its own more specific BackHandler (close
        // menus/scrub-mode/controls before actually leaving) as part of
        // NavHost's own content, i.e. AFTER NavHost's internal handler but
        // BEFORE this one -- so simply being "most recent" would make this
        // one wrongly outrank it too. enabled = !isPlayerActive is what
        // keeps the ordering fix from also swallowing that: disabled here
        // means Compose's dispatcher skips straight past this callback to
        // the next-most-recently-registered enabled one, which is
        // PlayerScreen's.
        val currentBackStackEntry by navController.currentBackStackEntryAsState()
        val isPlayerActive = currentBackStackEntry?.destination?.route == MangoRoutes.PLAYER_PATTERN

        fun navigateTo(route: String) {
            if (route in TAB_ROOT_ROUTES) {
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                }
            } else {
                navController.navigate(route)
            }
        }

        // A signed-out user is never left with anything to navigate back
        // into: both transitions below (auth gate -> a destination, and
        // sign-out -> AuthStart) clear the *entire* back stack via
        // graph.id rather than a specific route, so it doesn't matter what
        // was actually on the stack at the time.
        fun navigateClearingBackStack(route: String) {
            navController.navigate(route) {
                popUpTo(navController.graph.id) { inclusive = true }
            }
        }

        // Where the nav ("scrolling"/focus-move) sound actually plays --
        // deliberately NOT tied to any element's focus-gained state (see
        // TvFocusSurface's own doc for why that played extra, unearned
        // ticks on every screen open/return). A real D-pad direction
        // KeyDown is the one signal that's unambiguously "the user
        // physically moved," so this is the single global place that
        // triggers it, for the whole app. Sits above NavHost, not inside
        // any one screen, so it keeps working across every destination
        // without each one wiring it in separately. Always returns false
        // (never consumes) -- this only ever adds a side effect, it must
        // never interfere with any screen's own key handling (seeking,
        // BACK interception, menu navigation, ...).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // Silenced while the player is active -- D-pad moves there
                    // are scrubbing the timeline and navigating the player's
                    // own overlay (quality/subtitle/settings menus), not
                    // browsing the rest of the interface, and the same tick
                    // firing on every one of those reads as interface chrome
                    // noise over what should just be video/audio. See
                    // PlayerScreen's own composable below for the matching
                    // LocalUiSoundPlayer override that silences its buttons'
                    // click/back sounds the same way.
                    if (!isPlayerActive &&
                        event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionUp || event.key == Key.DirectionDown ||
                            event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
                    ) {
                        container.uiSoundPlayer.playNav()
                    }
                    false
                }
        ) {
            NavHost(navController = navController, startDestination = MangoRoutes.AUTH_GATE) {
                composable(MangoRoutes.AUTH_GATE) {
                    AuthGateScreen(
                        onNavigate = { destination ->
                            val target = when (destination) {
                                GateDestination.Home -> MangoRoutes.HOME
                                GateDestination.AuthStart -> MangoRoutes.AUTH_START
                            }
                            navigateClearingBackStack(target)
                        }
                    )
                }
                composable(MangoRoutes.AUTH_START) {
                    AuthStartScreen(
                        onSignIn = { navController.navigate(MangoRoutes.authMethod("login")) },
                        onCreateAccount = { navController.navigate(MangoRoutes.authMethod("register")) }
                    )
                }
                composable(MangoRoutes.AUTH_METHOD_PATTERN) { backStackEntry ->
                    val intent = backStackEntry.arguments?.getString("intent") ?: "login"
                    AuthMethodScreen(
                        intent = intent,
                        onScanQr = { navController.navigate(MangoRoutes.authQr(intent)) },
                        onUseRemote = { navController.navigate(MangoRoutes.authPassword(intent)) }
                    )
                }
                composable(MangoRoutes.AUTH_QR_PATTERN) {
                    QrSignInScreen(
                        onAuthenticated = { navigateClearingBackStack(MangoRoutes.HOME) }
                    )
                }
                composable(MangoRoutes.AUTH_PASSWORD_PATTERN) {
                    PasswordSignInScreen(
                        onAuthenticated = { navigateClearingBackStack(MangoRoutes.HOME) }
                    )
                }
                composable(MangoRoutes.HOME) {
                    HomeScreen(
                        onNavigate = ::navigateTo,
                        viewModel = homeViewModel
                    )
                }
                composable(MangoRoutes.SETTINGS) {
                    SettingsScreen(
                        onNavigate = ::navigateTo,
                        onOpenAddons = { navController.navigate(MangoRoutes.SETTINGS_ADDONS) },
                        onOpenHomeRows = { navController.navigate(MangoRoutes.SETTINGS_HOME_ROWS) },
                        onOpenSounds = { navController.navigate(MangoRoutes.SETTINGS_SOUNDS) },
                        onOpenAccount = { navController.navigate(MangoRoutes.SETTINGS_ACCOUNT) }
                    )
                }
                composable(MangoRoutes.SETTINGS_HOME_ROWS) {
                    HomeRowsScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SETTINGS_SOUNDS) {
                    SoundSettingsScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SETTINGS_ACCOUNT) {
                    AccountScreen(
                        onNavigate = ::navigateTo,
                        onSignedOut = { navigateClearingBackStack(MangoRoutes.AUTH_START) }
                    )
                }
                composable(MangoRoutes.MOVIES) {
                    MoviesScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.TV_SHOWS) {
                    TvShowsScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.GENRES) {
                    GenresScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.GENRE_RESULTS_PATTERN) {
                    GenreResultsScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SEARCH) {
                    SearchScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.MY_LIST) {
                    MyListScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SETTINGS_ADDONS) {
                    AddonsScreen(
                        onNavigate = ::navigateTo,
                        onAddAddon = { navController.navigate(MangoRoutes.SETTINGS_ADD_ADDON) }
                    )
                }
                composable(MangoRoutes.SETTINGS_ADD_ADDON) {
                    AddAddonScreen(
                        onNavigate = ::navigateTo,
                        onInstalled = { navController.popBackStack() }
                    )
                }
                composable(MangoRoutes.DETAIL_PATTERN) {
                    DetailScreen(
                        onNavigate = ::navigateTo
                    )
                }
                composable(MangoRoutes.SOURCES_PATTERN) {
                    SourcesScreen(
                        onNavigate = ::navigateTo,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(MangoRoutes.PLAYER_PATTERN) { backStackEntry ->
                    // Silences every TvFocusSurface's click/back sound (Play/
                    // Pause, the timeline, quality/subtitle/settings menus, ...)
                    // for as long as the player is on screen -- TvFocusSurface
                    // and PlayerScreen's own BackHandler both read this same
                    // composition local, so overriding it here to null covers
                    // all of them at once instead of threading a "silent"
                    // flag through every individual control. Paired with the
                    // isPlayerActive guard above the NavHost, which handles
                    // the one nav-tick sound that plays from outside any
                    // TvFocusSurface at all.
                    CompositionLocalProvider(LocalUiSoundPlayer provides null) {
                        PlayerScreen(
                            onBack = { navController.popBackStack() },
                            // Pops the player off the back stack before pushing Sources
                            // rather than stacking Sources on top of a dead player
                            // instance the user could otherwise navigate back into.
                            onChangeSource = {
                                val args = backStackEntry.arguments
                                val providerId = URLDecoder.decode(args?.getString("providerId").orEmpty(), "UTF-8")
                                val type = if (args?.getString("type") == ContentType.TV_SHOW.name) {
                                    ContentType.TV_SHOW
                                } else {
                                    ContentType.MOVIE
                                }
                                val id = URLDecoder.decode(args?.getString("id").orEmpty(), "UTF-8")
                                val season = args?.getString("season")?.toIntOrNull()?.takeIf { it >= 0 }
                                val episode = args?.getString("episode")?.toIntOrNull()?.takeIf { it >= 0 }
                                // Explicit "change source" request -- always show
                                // the picker, even for a title that would
                                // otherwise auto-continue with the very source
                                // being changed away from (see sources()'s own
                                // doc on skipAutoSelect).
                                navController.navigate(MangoRoutes.sources(providerId, type, id, season, episode, skipAutoSelect = true)) {
                                    popUpTo(MangoRoutes.PLAYER_PATTERN) { inclusive = true }
                                }
                            }
                        )
                    }
                }
            }
        }

        // Global fallback for the hardware/remote BACK button -- see the
        // doc above (by navController/isPlayerActive) for why this has to
        // be composed here, after NavHost, rather than before it. Falls
        // back to finishing the Activity when there's nothing left to pop
        // (i.e. at Home), matching what BACK would already do with no
        // handler at all -- this replaces that default, so it has to
        // reproduce it itself.
        BackHandler(enabled = !isPlayerActive) {
            container.uiSoundPlayer.playBack()
            if (!navController.popBackStack()) {
                (context as? Activity)?.finish()
            }
        }
    }
}
