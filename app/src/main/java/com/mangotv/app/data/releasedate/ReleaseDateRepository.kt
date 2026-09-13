package com.mangotv.app.data.releasedate

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.network.ReleaseDateApiClient
import kotlinx.coroutines.CancellationException

/**
 * Looks up a movie's real release date via the backend's /user/release-date
 * endpoint -- a server-side TMDB lookup, cached there so repeat views of
 * the same title across every user of the app are cheap (see
 * server/src/services/releaseDateService.ts). Addon catalogs only ever
 * supply a bare year; this exists purely to upgrade that to a full date on
 * the movie detail page. Read-only and one-shot, same shape as
 * TrailerRepository: no local persistence, and a failed lookup just means
 * the page keeps showing the addon-supplied year, not lost data.
 */
class ReleaseDateRepository(private val authRepository: AuthRepository) {
    private val apiClient = ReleaseDateApiClient(BuildConfig.API_BASE_URL)

    /**
     * Null whenever a real release date isn't available for any reason --
     * signed out, a network/server failure, or a genuine "TMDB has nothing
     * for this title" -- every case means the same thing to the caller:
     * keep showing the addon-supplied year instead.
     */
    suspend fun findReleaseDate(title: String, year: Int?): String? {
        val token = freshAccessTokenOrNull() ?: return null
        return try {
            apiClient.getReleaseDate(token, title, year).releaseDate
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }
}
