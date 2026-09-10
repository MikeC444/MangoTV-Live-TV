package com.mangotv.app.navigation

import com.mangotv.app.data.model.ContentType
import java.net.URLEncoder

object MangoRoutes {
    const val AUTH_GATE = "auth/gate"
    const val AUTH_START = "auth/start"
    const val AUTH_QR_PATTERN = "auth/qr/{intent}"
    const val AUTH_PASSWORD = "auth/password"
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

    /** [intent] is display-only ("login" or "register" — which button on AuthStartScreen was pressed); the QR flow itself is identical either way, since the activation page lets the user pick regardless. */
    fun authQr(intent: String): String = "auth/qr/$intent"

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
