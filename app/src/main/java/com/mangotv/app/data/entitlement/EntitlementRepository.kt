package com.mangotv.app.data.entitlement

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mangotv.app.config.LiveTvConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.entitlementCacheDataStore: DataStore<Preferences> by preferencesDataStore(name = "mango_live_tv_entitlement_cache")

/**
 * The one question the rest of Live TV cares about: is this device
 * currently entitled to watch. [Active]/[Inactive]/[Expired] all mean the
 * backend was actually reached and gave a real answer -- [BackendUnavailable]
 * and [NotConfigured] are distinct so the premium screen can explain *why*
 * access can't be verified instead of just saying "not paid".
 */
sealed interface EntitlementState {
    data object Checking : EntitlementState
    data class Active(val expiresAtEpochMs: Long?) : EntitlementState
    data object Inactive : EntitlementState
    data object Expired : EntitlementState
    data class BackendUnavailable(val message: String) : EntitlementState
    data object NotConfigured : EntitlementState
}

/**
 * Owns entitlement verification end-to-end. This is deliberately the *only*
 * place in the app that decides whether Live TV unlocks, and it never makes
 * that call from a local flag -- every [Active] result traces back to a real
 * response from [EntitlementApiClient], i.e. the backend. The short-lived
 * disk cache below exists purely to survive a brief network blip without
 * punting a paying customer back to the purchase screen; it is seeded once
 * on start and is never a substitute for the periodic revalidation
 * [pollLoop] keeps doing for as long as Live TV stays open (see
 * REVALIDATE_INTERVAL_MS) -- that's what catches an expired subscription
 * without requiring an app restart.
 */
class EntitlementRepository(
    context: Context,
    private val deviceIdentity: DeviceIdentityRepository = DeviceIdentityRepository(context),
    private val apiClient: EntitlementApiClient = EntitlementApiClient()
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<EntitlementState>(EntitlementState.Checking)
    val state: StateFlow<EntitlementState> = _state.asStateFlow()

    private var pollJob: Job? = null
    private var consecutiveFailures = 0

    suspend fun deviceId(): String = deviceIdentity.getOrCreateDeviceId()
    suspend fun pairingCode(): String = deviceIdentity.getOrCreatePairingCode()

    /** Idempotent: safe to call every time the Live TV screen is entered. */
    fun start() {
        if (pollJob != null) return
        if (!LiveTvConfig.isBackendConfigured) {
            _state.value = EntitlementState.NotConfigured
            return
        }
        _state.value = EntitlementState.Checking
        pollJob = scope.launch {
            seedFromCache()
            apiClient.registerDevice(deviceId(), pairingCode())
            pollLoop()
        }
    }

    /** Stops background polling -- called when Live TV leaves the screen so a backgrounded tab doesn't keep hammering the network on a Fire TV Stick. */
    fun stop() {
        pollJob?.cancel()
        pollJob = null
    }

    /** Forces an immediate out-of-band check, e.g. from a user-pressed Retry button after [BackendUnavailable]. */
    fun retry() {
        if (!LiveTvConfig.isBackendConfigured) {
            _state.value = EntitlementState.NotConfigured
            return
        }
        consecutiveFailures = 0
        scope.launch { refreshOnce() }
    }

    private suspend fun seedFromCache() {
        val cached = readCache() ?: return
        val age = System.currentTimeMillis() - cached.checkedAtEpochMs
        if (age > CACHE_GRACE_MS) return
        toState(cached.status, cached.expiresAtEpochMs)?.let { _state.value = it }
    }

    private suspend fun pollLoop() {
        while (true) {
            refreshOnce()
            val interval = if (_state.value is EntitlementState.Active) REVALIDATE_INTERVAL_MS else POLL_INTERVAL_MS
            delay(interval)
        }
    }

    private suspend fun refreshOnce() {
        val deviceId = deviceId()
        val wasActive = _state.value is EntitlementState.Active
        runCatching { apiClient.fetchStatus(deviceId) }
            .onSuccess { response ->
                consecutiveFailures = 0
                writeCache(response.status, response.expiresAtEpochMs)
                _state.value = toState(response.status, response.expiresAtEpochMs)
                    ?: (if (wasActive) EntitlementState.Expired else EntitlementState.Inactive)
            }
            .onFailure { error ->
                consecutiveFailures++
                // Below the threshold, keep whatever state we already had
                // (cached/previous) rather than flapping the UI on one
                // dropped request -- a real outage still surfaces once
                // failures persist for a few cycles.
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    _state.value = EntitlementState.BackendUnavailable(
                        error.message ?: "Couldn't reach MangoTV's servers to verify Live TV access."
                    )
                }
            }
    }

    private fun toState(status: String, expiresAtEpochMs: Long?): EntitlementState? = when (status.lowercase()) {
        "active" -> if (expiresAtEpochMs != null && expiresAtEpochMs <= System.currentTimeMillis()) {
            EntitlementState.Expired
        } else {
            EntitlementState.Active(expiresAtEpochMs)
        }
        "expired" -> EntitlementState.Expired
        "inactive" -> EntitlementState.Inactive
        else -> null
    }

    private suspend fun readCache(): CachedEntitlement? {
        val raw = appContext.entitlementCacheDataStore.data.first()[CACHE_KEY] ?: return null
        return runCatching { json.decodeFromString(CachedEntitlement.serializer(), raw) }.getOrNull()
    }

    private suspend fun writeCache(status: String, expiresAtEpochMs: Long?) {
        val cached = CachedEntitlement(status, expiresAtEpochMs, System.currentTimeMillis())
        val raw = json.encodeToString(CachedEntitlement.serializer(), cached)
        appContext.entitlementCacheDataStore.edit { it[CACHE_KEY] = raw }
    }

    companion object {
        private val CACHE_KEY = stringPreferencesKey("cached_entitlement_json")

        // Within the 5-10s range asked for while locked/checking.
        private const val POLL_INTERVAL_MS = 8_000L

        // Once unlocked, no need to poll nearly as often -- this only exists
        // to catch expiration/cancellation without requiring an app restart.
        private const val REVALIDATE_INTERVAL_MS = 5 * 60_000L

        // How long a cached "active" answer is trusted before a fresh
        // backend round trip is required -- short on purpose, see class doc.
        private const val CACHE_GRACE_MS = 5 * 60_000L

        private const val MAX_CONSECUTIVE_FAILURES = 4
    }
}
