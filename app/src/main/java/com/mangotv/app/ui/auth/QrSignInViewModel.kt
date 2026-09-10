package com.mangotv.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.auth.QrPollOutcome
import com.mangotv.app.data.auth.QrSessionInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface QrUiState {
    data object Loading : QrUiState
    data class Ready(val activationUrl: String, val expiresAtMillis: Long) : QrUiState
    data class Error(val message: String) : QrUiState
}

/**
 * Drives the whole QR lifecycle for one screen visit: request a session,
 * show it, poll until it's completed, and transparently swap in a fresh
 * one if the current one expires while still waiting — the "QR
 * expiration"/"QR refresh" requirements, satisfied without the user ever
 * having to do anything. [intent] ("login" or "register") is display-only
 * — see AuthStartScreen's kdoc — and never sent to the backend, since the
 * QR session itself doesn't have or need a notion of which one this is.
 */
class QrSignInViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val settingsSyncRepository = container.settingsSyncRepository
    private val watchlistSyncRepository = container.watchlistSyncRepository
    private val continueWatchingSyncRepository = container.continueWatchingSyncRepository
    private val addonSyncRepository = container.addonSyncRepository
    val intent: String = savedStateHandle.get<String>("intent") ?: "login"

    private val _uiState = MutableStateFlow<QrUiState>(QrUiState.Loading)
    val uiState: StateFlow<QrUiState> = _uiState.asStateFlow()

    // Separate from uiState on purpose: losing the connection mid-poll
    // shouldn't yank a perfectly valid, still-displayed QR code off
    // screen and replace it with a full-screen error — that's only for
    // when there's genuinely nothing to show yet (the initial
    // createQrSession call itself failing). This is just a small
    // "having trouble, still retrying" signal the screen can overlay
    // near the code without hiding it.
    private val _pollingDegraded = MutableStateFlow(false)
    val pollingDegraded: StateFlow<Boolean> = _pollingDegraded.asStateFlow()

    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    private var pollJob: Job? = null

    init {
        startNewQrSession()
    }

    fun startNewQrSession() {
        pollJob?.cancel()
        _pollingDegraded.value = false
        _uiState.value = QrUiState.Loading
        viewModelScope.launch {
            authRepository.createQrSession().fold(
                onSuccess = { info -> beginPolling(info) },
                onFailure = { error ->
                    _uiState.value = QrUiState.Error(error.message ?: "Couldn't reach the server.")
                }
            )
        }
    }

    private fun beginPolling(info: QrSessionInfo) {
        _uiState.value = QrUiState.Ready(info.activationUrl, info.expiresAtMillis)

        pollJob = viewModelScope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                delay(POLL_INTERVAL_MS)

                if (System.currentTimeMillis() >= info.expiresAtMillis) {
                    startNewQrSession()
                    return@launch
                }

                authRepository.pollQrSession(info.token).fold(
                    onSuccess = { outcome ->
                        consecutiveFailures = 0
                        _pollingDegraded.value = false
                        when (outcome) {
                            is QrPollOutcome.Completed -> {
                                _authenticated.value = true
                                settingsSyncRepository.pullFromServer()
                                watchlistSyncRepository.pullFromServer()
                                continueWatchingSyncRepository.pullFromServer()
                                addonSyncRepository.pullFromServer()
                                return@launch
                            }
                            QrPollOutcome.Expired -> {
                                startNewQrSession()
                                return@launch
                            }
                            QrPollOutcome.Pending -> Unit
                        }
                    },
                    onFailure = {
                        // A handful of transient network hiccups shouldn't
                        // kill a perfectly good QR code — keep polling
                        // regardless, only flagging it after several
                        // failures in a row rather than on the first one.
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
        private const val POLL_INTERVAL_MS = 2500L
        private const val MAX_CONSECUTIVE_FAILURES = 4
    }
}
