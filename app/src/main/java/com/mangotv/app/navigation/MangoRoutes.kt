package com.mangotv.app.navigation

import com.mangotv.app.data.model.ContentType
import java.net.URLEncoder

object MangoRoutes {
    const val AUTH_GATE = "auth/gate"
    const val AUTH_START = "auth/start"
    const val AUTH_METHOD_PATTERN = "auth/method/{intent}"
    const val AUTH_QR_PATTERN = "auth/qr/{intent}"
    const val AUTH_PASSWORD_PATTERN = "auth/password/{intent}"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val SETTINGS_ADDONS = "settings/addons"
    const val SETTINGS_ADD_ADDON = "settings/addons/add"
    const val SETTINGS_HOME_ROWS = "settings/home_rows"
    const val SETTINGS_ACCOUNT = "settings/account"
    const val MOVIES = "movies"
    const val TV_SHOWS = "tv_shows"
    const val GENRES = "genres"
    const val GENRE_RESULTS_PATTERN = "genres/{genre}"
    const val SEARCH = "search"
    const val MY_LIST = "my_list"
    const val DETAIL_PATTERN = "detail/{providerId}/{type}/{id}"
    const val SOURCES_PATTERN = "sources/{providerId}/{type}/{id}/{season}/{episode}"
    const val PLAYER_PATTERN = "player/{providerId}/{type}/{id}/{season}/{episode}/{streamId}"

    /** [intent] is display-only ("login" or "register" — which button on AuthStartScreen was pressed), carried through AuthMethodScreen unchanged; see [authMethod]'s own kdoc for why it stays display-only here too. */
    fun authQr(intent: String): String = "auth/qr/$intent"

    /** Reached from AuthStartScreen's Log In/Sign Up buttons. [intent] ("login" or "register") only picks which headline AuthMethodScreen shows -- both its options (QR or typed password) remain available regardless, and each downstream screen lets the user override it anyway (the QR activation page's own login/register picker; PasswordSignInScreen's in-screen mode toggle). */
    fun authMethod(intent: String): String = "auth/method/$intent"

    /** See [authQr]'s kdoc -- same display-only [intent] contract, carried through AuthMethodScreen. */
    fun authPassword(intent: String): String = "auth/password/$intent"

    fun genreResults(genre: String): String = "genres/${URLEncoder.encode(genre, "UTF-8")}"

    fun detail(providerId: String, type: ContentType, id: String): String {
        val encodedProviderId = URLEncoder.encode(providerId, "UTF-8")
        val encodedId = URLEncoder.encode(id, "UTF-8")
        return "detail/$encodedProviderId/${type.name}/$encodedId"
    }

    fun sources(providerId: String, type: ContentType, id: String, season: Int? = null, episode: Int? = null): String {
        val encodedProviderId = URLEncoder.encode(providerId, "UTF-8")
        val encodedId = URLEncoder.encode(id, "UTF-8")
        return "sources/$encodedProviderId/${type.name}/$encodedId/${season ?: -1}/${episode ?: -1}"
    }

    fun player(
        providerId: String,
        type: ContentType,
        id: String,
        season: Int? = null,
        episode: Int? = null,
        streamId: String
    ): String {
        val encodedProviderId = URLEncoder.encode(providerId, "UTF-8")
        val encodedId = URLEncoder.encode(id, "UTF-8")
        val encodedStreamId = URLEncoder.encode(streamId, "UTF-8")
        return "player/$encodedProviderId/${type.name}/$encodedId/${season ?: -1}/${episode ?: -1}/$encodedStreamId"
    }
}

/** Maps a top-nav label to the route it should navigate to, or null if that section isn't built yet. */
fun routeForNavLabel(label: String): String? = when (label) {
    "Home" -> MangoRoutes.HOME
    "Movies" -> MangoRoutes.MOVIES
    "TV Shows" -> MangoRoutes.TV_SHOWS
    "Genres" -> MangoRoutes.GENRES
    "Search" -> MangoRoutes.SEARCH
    "My List" -> MangoRoutes.MY_LIST
    "Settings" -> MangoRoutes.SETTINGS
    else -> null
}
