package com.mangotv.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.ContentType
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
import com.mangotv.app.ui.livetv.LiveTvPlayerScreen
import com.mangotv.app.ui.livetv.LiveTvScreen
import com.mangotv.app.ui.player.PlayerScreen
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
    MangoRoutes.HOME, MangoRoutes.MOVIES, MangoRoutes.TV_SHOWS, MangoRoutes.LIVE_TV,
    MangoRoutes.GENRES, MangoRoutes.SEARCH, MangoRoutes.MY_LIST, MangoRoutes.SETTINGS
)

@Composable
fun MangoNavHost() {
    // Constructed here, outside any NavHost destination, so it's scoped to
    // the Activity rather than to Home's own back-stack entry -- LoadingScreen
    // and the HOME destination below share this exact instance instead of
    // each getting their own. Sharing it is what lets LoadingScreen observe
    // (and preload images for) the SAME fetch Home itself ends up showing,
    // rather than duplicating that fetch a second time once Home mounts.
    val homeViewModel: HomeViewModel = viewModel()

    // Shown once, in place of the real nav graph, on cold boot -- see
    // LoadingScreen's own doc. This flag lives only in this composable's
    // memory, so it's never re-armed for the rest of the process's
    // lifetime once flipped (tab switches, backgrounding, etc. don't
    // recompose MangoNavHost from scratch).
    var isAppReady by remember { mutableStateOf(false) }
    if (!isAppReady) {
        LoadingScreen(homeViewModel = homeViewModel, onReady = { isAppReady = true })
        return
    }

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

    NavHost(navController = navController, startDestination = MangoRoutes.HOME) {
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
                onOpenHomeRows = { navController.navigate(MangoRoutes.SETTINGS_HOME_ROWS) }
            )
        }
        composable(MangoRoutes.SETTINGS_HOME_ROWS) {
            HomeRowsScreen(
                onNavigate = ::navigateTo
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
        composable(MangoRoutes.LIVE_TV) {
            LiveTvScreen(
                onNavigate = ::navigateTo,
                onPlayChannel = { channel -> navController.navigate(MangoRoutes.liveTvPlayer(channel.id)) }
            )
        }
        composable(MangoRoutes.LIVE_TV_PLAYER_PATTERN) {
            LiveTvPlayerScreen(
                onBack = { navController.popBackStack() }
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
