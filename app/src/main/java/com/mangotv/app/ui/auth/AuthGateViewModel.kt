package com.mangotv.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GateDestination {
    data object Home : GateDestination
    data object AuthStart : GateDestination
}

/**
 * Decides, once, whether this launch goes straight to the existing app or
 * needs to authenticate first — the entire "App Launch -> Check local
 * session -> ..." flow the account system is built around. The check
 * itself is local-only and fast (no network round trip gates navigation);
 * a session is treated as good enough to proceed on as long as its
 * refresh token hasn't expired, since the access token can always be
 * silently renewed. If the sign-in the user reached this launch with
 * doesn't hold up server-side, the *next* thing that actually needs the
 * network to succeed (starting with Milestones 6+'s data sync) is what
 * ultimately discovers that, not this screen re-litigating it here.
 */
class AuthGateViewModel(application: Application) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val settingsSyncRepository = container.settingsSyncRepository

    private val _destination = MutableStateFlow<GateDestination?>(null)
    val destination: StateFlow<GateDestination?> = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            val session = authRepository.getCurrentSession()
            val hasUsableSession = session != null && session.isRefreshTokenValid()
            _destination.value = if (hasUsableSession) GateDestination.Home else GateDestination.AuthStart

            if (hasUsableSession) {
                // Both fire after the navigation decision, not before —
                // this is purely about keeping the access token fresh and
                // this account's settings current for whenever they're
                // next needed; neither must ever delay getting the user
                // into the app.
                launch { authRepository.ensureFreshSession() }
                launch { settingsSyncRepository.pullFromServer() }
            }
        }
    }
}
