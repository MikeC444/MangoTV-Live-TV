package com.mangotv.app.data.sync

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.history.ContinueWatchingRepository
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.ContinueWatchingItemDto
import com.mangotv.app.data.network.PlaybackProgressApiClient
import com.mangotv.app.data.network.WatchProgressRequest
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The third cloud-sync domain (Milestone 8), and the first driven by
 * player lifecycle events rather than a local-repository mutation hook:
 * PlayerViewModel.reportProgress() calls [reportProgress] directly at the
 * trigger points the milestone calls for (periodic while playing, on
 * pause, on stop, on completion) rather than a MyList/Settings-style
 * onLocalChange hook -- there's only ever one caller of this domain's
 * writes (the player), not several independent UI call sites, so there's
 * no separate "something local changed, now tell the sync layer" step to
 * hook; this class *is* that step.
 *
 * [reportProgress] is deliberately not suspend: PlayerScreen's
 * DisposableEffect.onDispose{} -- where the final "stopped playback"
 * report fires -- is not a coroutine context, so this owns its own
 * long-lived scope and fires the local write + network push from there,
 * the same shape WatchlistSyncRepository.pushToServer() already uses.
 */
class ContinueWatchingSyncRepository(
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val authRepository: AuthRepository
) {
    private val apiClient = PlaybackProgressApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Pulls this account's active Continue Watching list and replaces the local cache with it. Same two call sites (auth gate, post-QR-sign-in) as SettingsSyncRepository/WatchlistSyncRepository's own pullFromServer(). Fire-and-forget: must never delay getting the user into the app. */
    suspend fun pullFromServer() {
        val token = freshAccessTokenOrNull() ?: return
        try {
            val response = apiClient.getContinueWatching(token)
            val items = response.items.mapNotNull { dto -> runCatching { dto.toEntry() }.getOrNull() }
            continueWatchingRepository.applyRemote(items)
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            // Transient -- local cache stays at its last-known-good state.
        }
    }

    /**
     * Records one playback-progress report: writes the local cache
     * immediately (so Home reflects it the moment the user backs out of
     * the player, without waiting on the network) and pushes to the
     * backend fire-and-forget. [completed] removes the local entry
     * instead of upserting it, mirroring the server's own recordProgress
     * logic (a finished title isn't resumable).
     */
    fun reportProgress(
        providerId: String,
        contentId: String,
        contentType: ContentType,
        seasonNumber: Int?,
        episodeNumber: Int?,
        episodeTitle: String?,
        title: String,
        posterUrl: String?,
        backdropUrl: String?,
        positionMs: Long,
        durationMs: Long,
        completed: Boolean
    ) {
        val watchedAt = Iso8601.nowString()
        scope.launch {
            // Local write and network push share one try/catch: a
            // DataStore write failure surfaces as IOException just like a
            // network one (see ContinueWatchingRepository/DataStore's own
            // documented behavior), so the same "transient, next report or
            // next pull recovers" handling below already covers both --
            // and either way, an uncaught exception here would otherwise
            // propagate out of this launch{} and crash the app, which a
            // rare disk hiccup during a routine progress report should
            // never do.
            try {
                if (completed) {
                    continueWatchingRepository.remove(providerId, contentId, contentType)
                } else {
                    continueWatchingRepository.upsert(
                        ContinueWatchingEntry(
                            providerId = providerId,
                            contentId = contentId,
                            contentType = contentType,
                            seasonNumber = seasonNumber,
                            episodeNumber = episodeNumber,
                            episodeTitle = episodeTitle,
                            title = title,
                            posterUrl = posterUrl,
                            backdropUrl = backdropUrl,
                            positionMs = positionMs,
                            durationMs = durationMs,
                            lastWatchedAt = watchedAt
                        )
                    )
                }

                val token = freshAccessTokenOrNull() ?: return@launch
                val response = apiClient.postProgress(
                    token,
                    WatchProgressRequest(
                        providerId = providerId,
                        contentId = contentId,
                        contentType = contentType.name,
                        seasonNumber = seasonNumber,
                        episodeNumber = episodeNumber,
                        episodeTitle = episodeTitle,
                        title = title,
                        posterUrl = posterUrl,
                        backdropUrl = backdropUrl,
                        positionMs = positionMs,
                        durationMs = durationMs,
                        completed = completed,
                        watchedAt = watchedAt
                    )
                )
                reconcile(providerId, contentId, contentType, response.continueWatching)
            } catch (e: ApiException) {
                if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            } catch (e: IOException) {
                // Transient -- nothing further to reconcile locally; the
                // next report, or the next login's pull, will retry.
            }
        }
    }

    /**
     * Applies the server's authoritative post-write state. Usually a
     * no-op (this write's own values echoed back), but restores or
     * removes the local entry if this report lost a last-write-wins race
     * to a near-simultaneous report from another device.
     */
    private suspend fun reconcile(providerId: String, contentId: String, contentType: ContentType, dto: ContinueWatchingItemDto?) {
        if (dto == null || dto.deletedAt != null) {
            continueWatchingRepository.remove(providerId, contentId, contentType)
            return
        }
        val entry = runCatching { dto.toEntry() }.getOrNull() ?: return
        continueWatchingRepository.upsert(entry)
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private fun ContinueWatchingItemDto.toEntry(): ContinueWatchingEntry = ContinueWatchingEntry(
        providerId = providerId,
        contentId = contentId,
        contentType = ContentType.valueOf(contentType),
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        title = title,
        posterUrl = posterUrl,
        backdropUrl = backdropUrl,
        positionMs = positionMs,
        durationMs = durationMs,
        lastWatchedAt = lastWatchedAt
    )
}
