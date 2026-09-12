package com.mangotv.app.data.trailer

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.network.TrailerApiClient
import kotlinx.coroutines.CancellationException

/**
 * Looks up a YouTube trailer for a title via the backend's /user/trailer
 * endpoint -- a server-side TMDB lookup, cached there so repeat views of
 * the same title across every user of the app are cheap (see
 * server/src/services/trailerService.ts). Read-only and one-shot: unlike
 * the sync repositories, there's no local persistence or offline outbox
 * here, since there's nothing to push and a failed lookup just means the
 * Trailer button doesn't show up for that one Detail view, not lost data.
 */
class TrailerRepository(private val authRepository: AuthRepository) {
    private val apiClient = TrailerApiClient(BuildConfig.API_BASE_URL)

    /**
     * Null whenever a trailer isn't available for any reason at all --
     * signed out, a network/server failure, or a genuine "TMDB has
     * nothing for this title" -- every case means the same thing to the
     * caller: don't show the Trailer button. None of them are a reason to
     * fail the Detail page itself.
     */
    suspend fun findTrailer(title: String, year: Int?, type: ContentType): String? {
        val token = freshAccessTokenOrNull() ?: return null
        return try {
            apiClient.getTrailer(token, title, year, type).youtubeVideoId
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
