package com.mangotv.app.ui.livetv

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.config.LiveTvConfig
import com.mangotv.app.data.entitlement.EntitlementState
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.EpgRepository
import com.mangotv.app.data.livetv.LiveTvCatalogState
import com.mangotv.app.data.livetv.TimelineBlock
import com.mangotv.app.data.livetv.buildTimelineBlocks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Whether -- and to what -- the user has narrowed the TV guide down by
 * region this session. [NotChosen] and [Chosen] with a null [Chosen.regionCode]
 * ("All Channels") are deliberately distinct states, not both folded into a
 * single nullable region code, since "haven't picked yet" (show the picker)
 * and "explicitly picked everything" (show the guide, unfiltered) need to
 * be told apart.
 */
sealed interface RegionSelection {
    data object NotChosen : RegionSelection
    data class Chosen(val regionCode: String?) : RegionSelection
}

/**
 * Every state the Live TV entry screen can be in. [Locked] covers "never
 * paid", "just expired", "backend unreachable" and "not configured yet" all
 * at once (see its own fields) rather than as separate top-level states,
 * since they're all rendered by the same premium/access screen with only
 * the copy and status row changing.
 */
sealed interface LiveTvUiState {
    data object CheckingAccess : LiveTvUiState
    data class Locked(
        val pairingCode: String,
        val checkoutUrl: String?,
        val justExpired: Boolean = false,
        val backendMessage: String? = null,
        val notConfigured: Boolean = false
    ) : LiveTvUiState
    data class Unlocked(val catalog: LiveTvCatalogState) : LiveTvUiState
}

/**
 * Orchestrates the two independent things Live TV needs every time it's
 * opened: (1) is this device entitled (EntitlementRepository, backend-
 * verified, never a local flag) and, only once that's true, (2) the channel
 * catalog + EPG. Re-checks entitlement every time the screen is entered
 * (see onScreenEntered) rather than trusting whatever the last session saw,
 * per the "never rely solely on locally cached entitlement" requirement.
 */
class LiveTvViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as MangoTvApplication).container
    private val entitlementRepository = container.entitlementRepository
    private val liveTvRepository = container.liveTvRepository
    private val epgRepository = container.epgRepository

    private val _uiState = MutableStateFlow<LiveTvUiState>(LiveTvUiState.CheckingAccess)
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    // Lives only in memory -- persists across tab switches for as long as
    // this ViewModel does (same lifetime as the rest of Live TV's state,
    // see the class doc), but resets on a fresh app process. That's a
    // deliberate simplification, not an oversight: a DataStore-backed
    // "remember across restarts" version would be a one-line follow-up if
    // wanted (see selectRegion/changeRegion).
    private val _regionSelection = MutableStateFlow<RegionSelection>(RegionSelection.NotChosen)
    val regionSelection: StateFlow<RegionSelection> = _regionSelection.asStateFlow()

    fun selectRegion(regionCode: String?) {
        _regionSelection.value = RegionSelection.Chosen(regionCode)
    }

    fun changeRegion() {
        _regionSelection.value = RegionSelection.NotChosen
    }

    private var deviceId: String = ""
    private var pairingCode: String = ""
    private var wasEverActive = false
    private var catalogObserverStarted = false

    fun onScreenEntered() {
        viewModelScope.launch {
            deviceId = entitlementRepository.deviceId()
            pairingCode = entitlementRepository.pairingCode()
            launch { entitlementRepository.state.collect(::onEntitlementChanged) }
            entitlementRepository.start()
        }
    }

    fun onScreenLeft() {
        entitlementRepository.stop()
    }

    fun retryEntitlement() {
        entitlementRepository.retry()
    }

    fun retryCatalog() {
        liveTvRepository.load(forceRefresh = true)
    }

    val epgVersion: StateFlow<Int> = epgRepository.epgVersion

    /** [-EpgRepository.LOOKBACK_MS, +EpgRepository.LOOKAHEAD_MS] around now -- the guide's own display window is kept identical to what EpgRepository actually keeps parsed in memory, so it never shows a time range wider than the data backing it. */
    fun guideWindow(): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return (now - EpgRepository.LOOKBACK_MS) to (now + EpgRepository.LOOKAHEAD_MS)
    }

    fun timelineBlocksFor(channel: Channel, windowStart: Long, windowEnd: Long): List<TimelineBlock> {
        val programmes = epgRepository.programmesInRange(channel.epgChannelId, windowStart, windowEnd)
        return buildTimelineBlocks(programmes, windowStart, windowEnd)
    }

    override fun onCleared() {
        super.onCleared()
        entitlementRepository.stop()
    }

    private fun onEntitlementChanged(entitlement: EntitlementState) {
        when (entitlement) {
            is EntitlementState.Checking -> _uiState.value = LiveTvUiState.CheckingAccess
            is EntitlementState.Active -> {
                wasEverActive = true
                liveTvRepository.load()
                observeCatalogIfNeeded()
            }
            is EntitlementState.Inactive -> _uiState.value = lockedState(justExpired = false)
            is EntitlementState.Expired -> {
                _uiState.value = lockedState(justExpired = wasEverActive)
                wasEverActive = false
            }
            is EntitlementState.BackendUnavailable ->
                _uiState.value = lockedState(justExpired = false, backendMessage = entitlement.message)
            is EntitlementState.NotConfigured ->
                _uiState.value = lockedState(justExpired = false, notConfigured = true)
        }
    }

    private fun observeCatalogIfNeeded() {
        if (catalogObserverStarted) return
        catalogObserverStarted = true
        viewModelScope.launch {
            liveTvRepository.state.collect { catalogState ->
                _uiState.value = LiveTvUiState.Unlocked(catalogState)
                if (catalogState is LiveTvCatalogState.Loaded) {
                    epgRepository.load(
                        knownTvgIds = catalogState.allChannels.mapNotNull { it.epgChannelId }.toSet(),
                        fallbackUrl = catalogState.discoveredEpgUrl
                    )
                }
            }
        }
    }

    private fun lockedState(justExpired: Boolean, backendMessage: String? = null, notConfigured: Boolean = false) =
        LiveTvUiState.Locked(
            pairingCode = pairingCode,
            checkoutUrl = LiveTvConfig.checkoutUrl(deviceId, pairingCode),
            justExpired = justExpired,
            backendMessage = backendMessage,
            notConfigured = notConfigured
        )
}
