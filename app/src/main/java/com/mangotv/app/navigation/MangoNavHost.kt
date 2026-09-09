package com.mangotv.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.ui.auth.AuthGateScreen
import com.mangotv.app.ui.auth.AuthStartScreen
import com.mangotv.app.ui.auth.GateDestination
import com.mangotv.app.ui.auth.QrSignInScreen
import com.mangotv.app.ui.browse.MoviesScreen
import com.mangotv.app.ui.browse.TvShowsScreen
import com.mangotv.app.ui.detail.DetailScreen
import com.mangotv.app.ui.genres.GenreResultsScreen
import com.mangotv.app.ui.genres.GenresScreen
import com.mangotv.app.ui.search.SearchScreen
import com.mangotv.app.ui.mylist.MyListScreen
import com.mangotv.app.ui.home.HomeScreen
import com.mangotv.app.ui.player.PlayerScreen
import com.mangotv.app.ui.settings.AccountScreen
import com.mangotv.app.ui.settings.AddAddonScreen
import com.mangotv.app.ui.settings.AddonsScreen
import com.mangotv.app.ui.settings.HomeRowsScreen
import com.mangotv.app.ui.settings.SettingsScreen
import com.mangotv.app.ui.sources.SourcesScreen
import java.net.URLDecoder

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

@Composable
fun MangoNavHost() {
    val navController = rememberNavController()

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
                onSignIn = { navController.navigate(MangoRoutes.authQr("login")) },
                onCreateAccount = { navController.navigate(MangoRoutes.authQr("register")) }
            )
        }
        composable(MangoRoutes.AUTH_QR_PATTERN) {
            QrSignInScreen(
                onAuthenticated = { navigateClearingBackStack(MangoRoutes.HOME) }
            )
        }
        composable(MangoRoutes.HOME) {
            HomeScreen(
                onNavigate = ::navigateTo
            )
        }
        composable(MangoRoutes.SETTINGS) {
            SettingsScreen(
                onNavigate = ::navigateTo,
                onOpenAddons = { navController.navigate(MangoRoutes.SETTINGS_ADDONS) },
                onOpenHomeRows = { navController.navigate(MangoRoutes.SETTINGS_HOME_ROWS) },
                onOpenAccount = { navController.navigate(MangoRoutes.SETTINGS_ACCOUNT) }
            )
        }
        composable(MangoRoutes.SETTINGS_HOME_ROWS) {
            HomeRowsScreen(
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
                    navController.navigate(MangoRoutes.sources(providerId, type, id, season, episode)) {
                        popUpTo(MangoRoutes.PLAYER_PATTERN) { inclusive = true }
                    }
                }
            )
        }
    }
}
