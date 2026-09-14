package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.trakt.TraktLinkInfo
import com.mangotv.app.data.trakt.TraktPollOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface TraktConnectUiState {
    data object Loading : TraktConnectUiState
    data class Ready(val info: TraktLinkInfo) : TraktConnectUiState
    data class Error(val message: String) : TraktConnectUiState

    /** The user declined the request on trakt.tv -- distinct from Error since this isn't a failure, just a "no". */
    data object Denied : TraktConnectUiState
}

/**
 * Drives the Trakt Device Code pairing lifecycle for one screen visit:
 * request a code, show it (plus a QR code shortcut), poll roughly every
 * server-told interval until it's connected/denied/expired, and
 * transparently start a fresh code if the current one expires while still
 * waiting -- the exact same shape as QrSignInViewModel's own QR lifecycle,
 * since this is functionally the same kind of flow (show a code, wait for
 * approval elsewhere, keep polling).
 */
class TraktConnectViewModel(application: Application) : AndroidViewModel(application) {
    private val traktRepository = (application as MangoTvApplication).container.traktRepository

    private val _uiState = MutableStateFlow<TraktConnectUiState>(TraktConnectUiState.Loading)
    val uiState: StateFlow<TraktConnectUiState> = _uiState.asStateFlow()

    // Same reasoning as QrSignInViewModel's identically-named flow: a
    // handful of transient poll failures shouldn't yank a perfectly good
    // code off screen, just quietly flag that polling is having trouble.
    private val _pollingDegraded = MutableStateFlow(false)
    val pollingDegraded: StateFlow<Boolean> = _pollingDegraded.asStateFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private var pollJob: Job? = null

    init {
        startLink()
    }

    fun startLink() {
        pollJob?.cancel()
        _pollingDegraded.value = false
        _uiState.value = TraktConnectUiState.Loading
        viewModelScope.launch {
            traktRepository.startLink().fold(
                onSuccess = { info -> beginPolling(info) },
                onFailure = { error ->
                    _uiState.value = TraktConnectUiState.Error(error.message ?: "Couldn't reach the server.")
                }
            )
        }
    }

    private fun beginPolling(info: TraktLinkInfo) {
        _uiState.value = TraktConnectUiState.Ready(info)

        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(info.intervalSeconds * 1000L)

                if (System.currentTimeMillis() >= info.expiresAtMillis) {
                    startLink()
                    return@launch
                }

                traktRepository.pollLink().fold(
                    onSuccess = { outcome ->
                        consecutiveFailures = 0
                        _pollingDegraded.value = false
                        when (outcome) {
                            is TraktPollOutcome.Connected -> {
                                _connected.value = true
                                return@launch
                            }
                            TraktPollOutcome.Expired, TraktPollOutcome.NotFound -> {
                                startLink()
                                return@launch
                            }
                            TraktPollOutcome.Denied -> {
                                _uiState.value = TraktConnectUiState.Denied
                                return@launch
                            }
                            TraktPollOutcome.Pending -> Unit
                        }
                    },
                    onFailure = {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            _pollingDegraded.value = true
                        }
                    }
                )
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
    }

    companion object {
        private const val MAX_CONSECUTIVE_FAILURES = 4
    }
}
