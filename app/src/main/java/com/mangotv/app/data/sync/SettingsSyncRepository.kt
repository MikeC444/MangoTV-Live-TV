package com.mangotv.app.data.sync

import android.content.Context
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
 * Milestone 10 added [retryPending]: a failed push now persists to a
 * small durable outbox ([pendingStore]) instead of just being dropped --
 * SyncManager drains it on login/launch and when connectivity returns.
 * Unlike the item-level domains (Watchlist/Addons/ContinueWatching), this
 * is a single-document domain, so the outbox only ever holds at most one
 * entry, under a fixed key: each new local change's own push attempt
 * already re-reads current state fresh and overwrites whatever was
 * queued from an earlier failed attempt, so retrying just replays
 * whatever is currently queued rather than needing to re-read local state
 * itself.
 */
class SettingsSyncRepository(
    context: Context,
    private val homeRowPreferencesRepository: HomeRowPreferencesRepository,
    private val playerPreferencesRepository: PlayerPreferencesRepository,
    private val authRepository: AuthRepository
) {
    private val apiClient = SettingsApiClient(BuildConfig.API_BASE_URL)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingStore = PendingChangeStore(context, "mango_settings_pending", SettingsRequest.serializer())

    init {
        homeRowPreferencesRepository.onLocalChange = { pushToServer() }
        playerPreferencesRepository.onLocalChange = { pushToServer() }
    }

    /**
     * Pulls this account's cloud settings and applies them to both local
     * caches. Called by SyncManager on login/launch. Fire-and-forget from
     * both call sites by design — this must never delay getting the user
     * into the app — so a failure here just leaves both local caches
     * exactly as they already were until the next successful pull.
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

    /** Retries the last settings push this device failed to complete, if any. Called by SyncManager on login/launch (after pullFromServer) and when network connectivity returns. */
    suspend fun retryPending() {
        val body = pendingStore.all()[PENDING_KEY] ?: return
        val token = freshAccessTokenOrNull() ?: return
        try {
            val response = apiClient.putSettings(token, body)
            applyRemote(response.homeRowOrder, response.hiddenRowIds, response.autoplayNextEpisode, response.skipIntroEnabled)
            pendingStore.remove(PENDING_KEY)
        } catch (e: ApiException) {
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
            // Otherwise left queued -- only one entry in this domain, so
            // there's no "rest of the batch" to fall through to.
        } catch (e: IOException) {
            // Still offline -- leave queued.
        }
    }

    /**
     * Peeks whether this account has ever pushed settings from any
     * device, without applying anything locally. Used only by Milestone
     * 11's first-login migration decision. Returns null (rather than a
     * guess) when the check itself couldn't complete -- the coordinator
     * treats that as "can't safely decide" and falls back to an ordinary
     * sync, never as "assume empty and push over it."
     */
    suspend fun isCloudEmpty(): Boolean? {
        val token = freshAccessTokenOrNull() ?: return null
        return try {
            apiClient.getSettings(token).updatedAt == null
        } catch (e: ApiException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /** Pushes this device's current local settings up as the account's state, suspending until it finishes (or fails and is queued for retry) -- used by Milestone 11's SYNC choice. Unlike the fire-and-forget pushToServer() below, a caller resolving the migration decision needs to know when this actually completes. */
    suspend fun pushAllLocalUp() = doPush()

    /** Drops this domain's pending outbox entry, if any (Milestone 12's account switching) -- see PendingChangeStore.clear()'s own kdoc for why a queued push must never survive into a different account's session. */
    suspend fun clearPending() = pendingStore.clear()

    /** Fire-and-forget: HomeRowPreferencesRepository/PlayerPreferencesRepository call this via onLocalChange after persisting a genuine local mutation. */
    private fun pushToServer() {
        scope.launch { doPush() }
    }

    private suspend fun doPush() {
        val home = homeRowPreferencesRepository.preferences.value
        val player = playerPreferencesRepository.preferences.value
        val body = SettingsRequest(
            homeRowOrder = home.order,
            hiddenRowIds = home.hiddenRowIds.toList(),
            autoplayNextEpisode = player.autoplayNextEpisode,
            skipIntroEnabled = player.skipIntroEnabled,
            updatedAt = Iso8601.nowString()
        )
        val token = freshAccessTokenOrNull()
        if (token == null) {
            pendingStore.put(PENDING_KEY, body)
            return
        }
        try {
            val response = apiClient.putSettings(token, body)
            // Reconciles the rare case this write lost a last-write-wins
            // race (e.g. a near-simultaneous change from another
            // device): applies whatever the server says is
            // authoritative now, which is just this write's own values
            // echoed back when it won.
            applyRemote(response.homeRowOrder, response.hiddenRowIds, response.autoplayNextEpisode, response.skipIntroEnabled)
            pendingStore.remove(PENDING_KEY)
        } catch (e: ApiException) {
            pendingStore.put(PENDING_KEY, body)
            if (e.statusCode == 401) authRepository.clearSessionOnConfirmedUnauthorized()
        } catch (e: IOException) {
            pendingStore.put(PENDING_KEY, body)
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

    companion object {
        private const val PENDING_KEY = "settings"
    }
}
