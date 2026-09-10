package com.mangotv.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * The direct email/password alternative to QrSignInScreen — reached from
 * AuthStartScreen by a user who'd rather type on their Fire TV remote
 * than scan a QR code with a phone. See PasswordSignInViewModel's kdoc
 * for why this exists alongside, not instead of, the QR flow.
 *
 * The form itself stays on screen through Loading and Error alike (only
 * MigrationChoice replaces it) specifically so a failed attempt — a typo,
 * a wrong password — never makes the user re-type an email address they
 * already got right, which matters a lot more here than on a phone
 * keyboard.
 */
@Composable
fun PasswordSignInScreen(
    onAuthenticated: () -> Unit,
    viewModel: PasswordSignInViewModel = viewModel()
) {
    val mode by viewModel.mode.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    val isRegister = mode is PasswordAuthMode.Register
    val isLoading = uiState is PasswordAuthUiState.Loading

    val emailFocusRequester = remember { FocusRequester() }
    val displayNameFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val showPasswordFocusRequester = remember { FocusRequester() }
    val submitFocusRequester = remember { FocusRequester() }
    val modeToggleFocusRequester = remember { FocusRequester() }
    val syncFocusRequester = remember { FocusRequester() }
    val startFreshFocusRequester = remember { FocusRequester() }

    LaunchedEffect(authenticated) { if (authenticated) onAuthenticated() }
    LaunchedEffect(uiState) {
        if (uiState is PasswordAuthUiState.MigrationChoice) runCatching { syncFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        val state = uiState
        if (state is PasswordAuthUiState.MigrationChoice) {
            // Identical content/copy to QrSignInScreen's own MigrationChoice
            // branch -- same decision, same consequences, regardless of
            // which sign-in path reached it.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 520.dp)
            ) {
                Text(
                    text = "Sync existing MangoTV data to your account?",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "This device has watchlist, addon, and viewing data that isn't in this account yet. You can add it to your account, or leave it behind and start fresh with what's already in the cloud.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(32.dp))
                MangoButton(
                    text = "Sync This Device's Data",
                    icon = Icons.Filled.Check,
                    onClick = { viewModel.onSyncChosen() },
                    style = MangoButtonStyle.FILLED,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = syncFocusRequester,
                    focusDown = startFreshFocusRequester
                )
                Spacer(Modifier.height(16.dp))
                MangoButton(
                    text = "Start Fresh",
                    icon = Icons.Filled.Refresh,
                    onClick = { viewModel.onStartFreshChosen() },
                    style = MangoButtonStyle.GLASS,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = startFreshFocusRequester,
                    focusUp = syncFocusRequester
                )
            }
        } else {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Text(
                text = if (isRegister) "Create Your Account" else "Log In",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(28.dp))

            TextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("Email") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(emailFocusRequester)
                    .focusProperties { down = if (isRegister) displayNameFocusRequester else passwordFocusRequester },
                colors = mangoTextFieldColors()
            )
            Spacer(Modifier.height(14.dp))

            if (isRegister) {
                TextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    placeholder = { Text("Display name (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(displayNameFocusRequester)
                        .focusProperties { up = emailFocusRequester; down = passwordFocusRequester },
                    colors = mangoTextFieldColors()
                )
                Spacer(Modifier.height(14.dp))
            }

            TextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("Password") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocusRequester)
                    .focusProperties {
                        up = if (isRegister) displayNameFocusRequester else emailFocusRequester
                        down = showPasswordFocusRequester
                    },
                colors = mangoTextFieldColors()
            )
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                MangoButton(
                    text = if (passwordVisible) "Hide Password" else "Show Password",
                    icon = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    onClick = { passwordVisible = !passwordVisible },
                    style = MangoButtonStyle.GLASS,
                    compact = true,
                    focusRequester = showPasswordFocusRequester,
                    focusUp = passwordFocusRequester,
                    focusDown = submitFocusRequester
                )
            }
            Spacer(Modifier.height(20.dp))

            if (state is PasswordAuthUiState.Error) {
                Text(
                    text = state.message,
                    color = MangoCoral,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
            }

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                MangoButton(
                    text = if (isRegister) "Create Account" else "Log In",
                    icon = if (isRegister) Icons.Filled.Add else Icons.Filled.Person,
                    onClick = { viewModel.submit(email, password, displayName) },
                    style = MangoButtonStyle.FILLED,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = submitFocusRequester,
                    focusUp = showPasswordFocusRequester,
                    focusDown = modeToggleFocusRequester
                )
            }
            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MangoAmber, strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = if (isRegister) "Creating account…" else "Signing in…",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            MangoButton(
                text = if (isRegister) "Already have an account? Log in" else "New here? Create an account",
                icon = Icons.Filled.ChevronRight,
                onClick = {
                    viewModel.switchMode(if (isRegister) PasswordAuthMode.Login else PasswordAuthMode.Register)
                },
                style = MangoButtonStyle.GLASS,
                compact = true,
                focusRequester = modeToggleFocusRequester,
                focusUp = submitFocusRequester
            )
        }
        }
    }
}

@Composable
private fun mangoTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = MangoSurface,
    unfocusedContainerColor = MangoSurface,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    cursorColor = MangoAmber,
    focusedIndicatorColor = MangoAmber,
    unfocusedIndicatorColor = TextTertiary
)
