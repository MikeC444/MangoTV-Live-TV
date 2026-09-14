package com.mangotv.app.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.auth.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the Trakt section of AccountScreen renders. See TraktRepository/traktService.ts for what backs each state. */
sealed interface TraktSectionState {
    data object Loading : TraktSectionState

    /** This server has no Trakt application configured at all -- distinct from Disconnected so the section can explain why, instead of offering a Connect button that would just fail. */
    data object NotConfigured : TraktSectionState

    /** Connect Trakt navigates straight to TraktConnectScreen -- there's no async work on this screen itself to show an in-progress state for. */
    data object Disconnected : TraktSectionState
    data class Connected(val username: String?, val actionInProgress: Boolean = false) : TraktSectionState
    data class Error(val message: String) : TraktSectionState
}

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val accountSwitchCoordinator = container.accountSwitchCoordinator
    private val traktRepository = container.traktRepository

    val session: StateFlow<Session?> = authRepository.session

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    private val _traktState = MutableStateFlow<TraktSectionState>(TraktSectionState.Loading)
    val traktState: StateFlow<TraktSectionState> = _traktState.asStateFlow()

    /**
     * Milestone 12: signs out via [AccountSwitchCoordinator], not
     * [authRepository] directly -- the coordinator both revokes this
     * device's session and wipes every local cache/outbox, so the app is
     * genuinely blank by the time [signedOut] flips and navigation carries
     * the user back to the auth start screen, ready for any account (the
     * same one, or a different one) to sign in clean.
     */
    fun signOut() {
        if (_signingOut.value) return
        _signingOut.value = true
        viewModelScope.launch {
            accountSwitchCoordinator.signOut()
            _signedOut.value = true
        }
    }

    /** Called from AccountScreen's own LaunchedEffect(Unit) -- refreshes every time this screen is (re)entered, including returning from a completed/cancelled Trakt connect attempt. */
    fun refreshTraktStatus() {
        viewModelScope.launch {
            _traktState.value = TraktSectionState.Loading
            traktRepository.getStatus().fold(
                onSuccess = { status ->
                    _traktState.value = when {
                        !status.configured -> TraktSectionState.NotConfigured
                        status.connected -> TraktSectionState.Connected(status.username)
                        else -> TraktSectionState.Disconnected
                    }
                },
                onFailure = { error ->
                    _traktState.value = TraktSectionState.Error(error.message ?: "Couldn't reach the server.")
                }
            )
        }
    }

    fun disconnectTrakt() {
        val current = _traktState.value
        if (current !is TraktSectionState.Connected || current.actionInProgress) return
        _traktState.value = current.copy(actionInProgress = true)
        viewModelScope.launch {
            traktRepository.disconnect().fold(
                onSuccess = { _traktState.value = TraktSectionState.Disconnected },
                onFailure = { error ->
                    _traktState.value = TraktSectionState.Error(error.message ?: "Couldn't disconnect from Trakt. Try again.")
                }
            )
        }
    }
}
