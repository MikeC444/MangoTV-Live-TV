package com.mangotv.app.ui.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.mangotv.app.MangoTvApplication
import com.mangotv.app.data.sync.MigrationDecision
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PasswordAuthMode {
    data object Login : PasswordAuthMode
    data object Register : PasswordAuthMode
}

sealed interface PasswordAuthUiState {
    data object Idle : PasswordAuthUiState
    data object Loading : PasswordAuthUiState
    data class Error(val message: String) : PasswordAuthUiState

    /** Same meaning as QrUiState.MigrationChoice -- see that class's kdoc. Reachable from this path too, since it depends on the device's local data and the account's cloud state, not on how the session was obtained. */
    data object MigrationChoice : PasswordAuthUiState
}

private const val MIN_PASSWORD_LENGTH = 8
private val EMAIL_PATTERN = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+\$")

/**
 * A minimal, deliberately-not-RFC5322-exact check: catches obvious typos
 * (no @, no domain dot, stray whitespace) before wasting a network round
 * trip, without trying to out-validate the server's own zod `.email()`
 * check, which remains the real authority. Plain Kotlin regex rather than
 * android.util.Patterns.EMAIL_ADDRESS specifically so this stays testable
 * on the plain JVM unit tests this project already has (no Robolectric
 * dependency exists here to make an Android-framework class usable in a
 * unit test).
 */
internal fun validateCredentials(email: String, password: String): String? = when {
    email.isBlank() -> "Enter your email address."
    !EMAIL_PATTERN.matches(email.trim()) -> "Enter a valid email address."
    password.length < MIN_PASSWORD_LENGTH -> "Password must be at least $MIN_PASSWORD_LENGTH characters."
    else -> null
}

/**
 * The direct email/password sign-in path — an alternative to
 * ui/auth/QrSignInScreen.kt for a user who'd rather type on their Fire TV
 * remote than use a phone. Talks to the same /auth/register and
 * /auth/login endpoints Milestone 3 built and tested; nothing on the TV
 * had ever called them before this, since Milestones 4/5 deliberately
 * routed every TV sign-in through the QR/activation-page flow instead
 * (see docs/ARCHITECTURE.md's authentication section). Reached via
 * ui/auth/AuthMethodScreen.kt, itself reached from AuthStartScreen — the
 * QR flow remains fully intact as the other option there.
 *
 * [PasswordAuthMode] starts as whichever of Login/Register matches the
 * "intent" nav argument ("login" or "register" — see MangoRoutes.authMethod's
 * kdoc for why this is display-only, same as QrSignInViewModel's own
 * [intent][QrSignInViewModel.intent]): a user who pressed Sign Up upstream
 * shouldn't land on a Log In form by default. The in-screen mode toggle
 * still lets them switch either way, exactly as before.
 *
 * Deliberately duplicates, rather than shares, QrSignInViewModel's own
 * post-auth migration-decision handling and its exact fire-and-forget-
 * vs-await semantics (see that class's kdoc). Two real call sites isn't
 * yet enough to justify guessing at a shared abstraction's shape; worth
 * revisiting if a third ever appears.
 */
class PasswordSignInViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val container = (application as MangoTvApplication).container
    private val authRepository = container.authRepository
    private val syncManager = container.syncManager
    private val migrationCoordinator = container.firstLoginMigrationCoordinator

    private val initialMode: PasswordAuthMode =
        if (savedStateHandle.get<String>("intent") == "register") PasswordAuthMode.Register else PasswordAuthMode.Login

    private val _mode = MutableStateFlow(initialMode)
    val mode: StateFlow<PasswordAuthMode> = _mode.asStateFlow()

    private val _uiState = MutableStateFlow<PasswordAuthUiState>(PasswordAuthUiState.Idle)
    val uiState: StateFlow<PasswordAuthUiState> = _uiState.asStateFlow()

    private val _authenticated = MutableStateFlow(false)
    val authenticated: StateFlow<Boolean> = _authenticated.asStateFlow()

    fun switchMode(mode: PasswordAuthMode) {
        _mode.value = mode
        _uiState.value = PasswordAuthUiState.Idle
    }

    fun submit(email: String, password: String, displayName: String?) {
        val validationError = validateCredentials(email, password)
        if (validationError != null) {
            _uiState.value = PasswordAuthUiState.Error(validationError)
            return
        }
        viewModelScope.launch {
            _uiState.value = PasswordAuthUiState.Loading
            val result = when (_mode.value) {
                PasswordAuthMode.Login -> authRepository.loginWithPassword(email.trim(), password)
                PasswordAuthMode.Register -> authRepository.registerWithPassword(email.trim(), password, displayName?.takeIf { it.isNotBlank() })
            }
            result.fold(
                onSuccess = { onAuthenticatedSession() },
                onFailure = { error -> _uiState.value = PasswordAuthUiState.Error(error.message ?: "Something went wrong. Please try again.") }
            )
        }
    }

    /** The user picked SYNC on the MigrationChoice prompt — see QrSignInViewModel.onSyncChosen()'s identical kdoc. */
    fun onSyncChosen() {
        viewModelScope.launch {
            _uiState.value = PasswordAuthUiState.Loading
            migrationCoordinator.resolveSync()
            _authenticated.value = true
        }
    }

    /** The user picked START FRESH — see QrSignInViewModel.onStartFreshChosen()'s identical kdoc. */
    fun onStartFreshChosen() {
        viewModelScope.launch {
            _uiState.value = PasswordAuthUiState.Loading
            migrationCoordinator.resolveStartFresh()
            _authenticated.value = true
        }
    }

    private suspend fun onAuthenticatedSession() {
        when (migrationCoordinator.decide()) {
            MigrationDecision.NeedsUserChoice -> _uiState.value = PasswordAuthUiState.MigrationChoice
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
}
