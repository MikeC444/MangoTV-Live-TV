package com.mangotv.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.auth.QrPollOutcome
import com.mangotv.app.data.auth.QrSessionInfo
import com.mangotv.app.data.sync.MigrationDecision
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

    /** Milestone 11: this device has real local data *and* the account being signed into already has its own real cloud data -- ask which one should win. Only reached when there's a genuine conflict; see FirstLoginMigrationCoordinator's own kdoc for the other, no-prompt-needed outcomes. */
    data object MigrationChoice : QrUiState
}

/**
 * Drives the whole QR lifecycle for one screen visit: request a session,
 * show it, poll until it's completed, and transparently swap in a fresh
 * one if the current one expires while still waiting — the "QR
 * expiration"/"QR refresh" requirements, satisfied without the user ever
 * having to do anything. [intent] ("login" or "register") is display-only
 * — see AuthStartScreen's kdoc — and never sent to the backend, since the
 * QR session itself doesn't have or need a notion of which one this is.
 *
 * Milestone 11: a completed QR session no longer authenticates
 * immediately. It first asks FirstLoginMigrationCoordinator what to do —
 * almost always [MigrationDecision.ProceedNormally], which authenticates
 * exactly as before (fire-and-forget sync, instant navigation, no
 * perceptible change for the common case). Only a genuine local-vs-cloud
 * conflict ([MigrationDecision.NeedsUserChoice]) surfaces
 * [QrUiState.MigrationChoice] and waits for [onSyncChosen]/
 * [onStartFreshChosen] before authenticating -- unlike the automatic
 * paths, this one *does* wait for the chosen push/pull to actually
 * finish first, since it's a deliberate, rare, user-initiated action
 * that deserves visible confirmation rather than the screen instantly
 * vanishing on tap.
 */
class QrSignInViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val syncManager = container.syncManager
    private val migrationCoordinator = container.firstLoginMigrationCoordinator
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

    /** The user picked SYNC on the [QrUiState.MigrationChoice] prompt: push this device's local data up, wait for it to finish, then authenticate. */
    fun onSyncChosen() {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            migrationCoordinator.resolveSync()
            _authenticated.value = true
        }
    }

    /** The user picked START FRESH on the [QrUiState.MigrationChoice] prompt: pull the account's cloud state down over local data, wait for it to finish, then authenticate. */
    fun onStartFreshChosen() {
        viewModelScope.launch {
            _uiState.value = QrUiState.Loading
            migrationCoordinator.resolveStartFresh()
            _authenticated.value = true
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
                                onQrCompleted()
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

    /**
     * The account just authenticated on the backend (a session now
     * exists) -- but [_authenticated] (which drives navigation to Home)
     * doesn't flip yet. First: is there a first-login migration decision
     * to make? [QrUiState.Loading] here briefly covers the up-to-four
     * parallel cloud peeks decide() may need to run.
     */
    private suspend fun onQrCompleted() {
        _uiState.value = QrUiState.Loading
        when (migrationCoordinator.decide()) {
            MigrationDecision.NeedsUserChoice -> _uiState.value = QrUiState.MigrationChoice
            MigrationDecision.AutoSyncEmptyCloud -> {
                _authenticated.value = true
                migrationCoordinator.resolveSync()
            }
            MigrationDecision.ProceedNormally -> {
                _authenticated.value = true
                syncManager.syncAll()
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
