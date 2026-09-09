package com.mangotv.app.data.sync

import com.mangotv.app.BuildConfig
import com.mangotv.app.data.auth.AuthRepository
import com.mangotv.app.data.model.PlayerPreferences
import com.mangotv.app.data.network.ApiException
import com.mangotv.app.data.network.SettingsApiClient
import com.mangotv.app.data.network.SettingsRequest
import com.mangotv.app.data.player.PlayerPreferencesRepository
import com.mangotv.app.data.provider.HomeRowPreferences
import com.mangotv.app.data.provider.HomeRowPreferencesRepository
import com.mangotv.app.util.Iso8601
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * The first cloud-sync domain (Milestone 6): keeps HomeRowPreferences and
 * PlayerPreferences — two independent local DataStore-backed repositories
 * — mirrored to the single `user_settings` row the backend stores for
 * this account. Local DataStore stays the read path every existing screen
 * already uses; this only layers a push-after-local-change and a
 * pull-on-login/launch on top, per the project's "local storage is a
 * cache, the backend is the source of truth" rule.
 *
 * Deliberately not a generic SyncManager/retry-queue yet — this is the
 * first of several domains that will eventually need the same shape of
 * sync (watchlist, addons, watch history in later milestones), but with
 * only one real example built so far, a shared abstraction would be
 * guessing at its shape rather than generalizing from something proven.
 * A failed push isn't queued for retry: the next local change, or the
 * next login/launch's pull-then-reconcile, is what recovers from a
 * transient failure.
 */
class SettingsSyncRepository(
    private val homeRowPreferencesRepository: HomeRowPreferencesRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
    private val authRepository: AuthRepository
) {
    private val apiClient = SettingsApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        homeRowPreferencesRepository.onLocalChange = { pushToServer() }
        playerPreferencesRepository.onLocalChange = { pushToServer() }
    }

    /**
     * Pulls this account's cloud settings and applies them to both local
     * caches. Called once per app launch when the auth gate finds an
     * already-usable session, and once right after a fresh QR sign-in
     * completes. Fire-and-forget from both call sites by design — this
     * must never delay getting the user into the app — so a failure here
     * just leaves both local caches exactly as they already were until
     * the next successful pull.
     */
    suspend fun pullFromServer() {
        val token = freshAccessTokenOrNull() ?: return
        try {
            val response = apiClient.getSettings(token)
            applyRemote(response.homeRowOrder, response.hiddenRowIds, response.autoplayNextEpisode, response.skipIntroEnabled)
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            // Transient -- local caches stay at their last-known-good state.
        }
    }

    /** Fire-and-forget: HomeRowPreferencesRepository/PlayerPreferencesRepository call this via onLocalChange after persisting a genuine local mutation. */
    private fun pushToServer() {
        scope.launch {
            val token = freshAccessTokenOrNull() ?: return@launch
            val home = homeRowPreferencesRepository.preferences.value
            val player = playerPreferencesRepository.preferences.value
            val body = SettingsRequest(
                homeRowOrder = home.order,
                hiddenRowIds = home.hiddenRowIds.toList(),
                autoplayNextEpisode = player.autoplayNextEpisode,
                skipIntroEnabled = player.skipIntroEnabled,
                updatedAt = Iso8601.nowString()
            )
            try {
                val response = apiClient.putSettings(token, body)
                // Reconciles the rare case this write lost a last-write-wins
                // race (e.g. a near-simultaneous change from another
                // device): applies whatever the server says is
                // authoritative now, which is just this write's own values
                // echoed back when it won.
                applyRemote(response.homeRowOrder, response.hiddenRowIds, response.autoplayNextEpisode, response.skipIntroEnabled)
            } catch (e: ApiException) {
                if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            } catch (e: IOException) {
                // Transient -- nothing to reconcile locally; the next
                // local change or the next login's pull will retry.
            }
        }
    }

    private suspend fun freshAccessTokenOrNull(): String? {
        if (!authRepository.ensureFreshSession()) return null
        return authRepository.getCurrentSession()?.accessToken
    }

    private suspend fun applyRemote(homeRowOrder: List<String>, hiddenRowIds: List<String>, autoplay: Boolean, skipIntro: Boolean) {
        homeRowPreferencesRepository.applyRemote(HomeRowPreferences(order = homeRowOrder, hiddenRowIds = hiddenRowIds.toSet()))
        playerPreferencesRepository.applyRemote(PlayerPreferences(autoplayNextEpisode = autoplay, skipIntroEnabled = skipIntro))
    }
}
