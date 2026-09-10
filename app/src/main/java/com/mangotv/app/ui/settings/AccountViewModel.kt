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

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val accountSwitchCoordinator = container.accountSwitchCoordinator

    val session: StateFlow<Session?> = authRepository.session

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

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
}
