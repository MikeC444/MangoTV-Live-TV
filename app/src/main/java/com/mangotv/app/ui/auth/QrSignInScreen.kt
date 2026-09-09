package com.mangotv.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.QrCodeImage
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Reached from either AuthStartScreen button. Pressing Back here is left
 * to Navigation-Compose's own default behavior (pop back to
 * AuthStartScreen) — nothing here needs to intercept it; the abandoned QR
 * session simply expires on the backend on its own.
 */
@Composable
fun QrSignInScreen(
    onAuthenticated: () -> Unit,
    viewModel: QrSignInViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pollingDegraded by viewModel.pollingDegraded.collectAsStateWithLifecycle()
    val authenticated by viewModel.authenticated.collectAsStateWithLifecycle()

    LaunchedEffect(authenticated) {
        if (authenticated) onAuthenticated()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is QrUiState.Loading -> CircularProgressIndicator(color = MangoAmber)

            is QrUiState.Error -> FullScreenErrorState(
                message = state.message,
                onRetry = { viewModel.startNewQrSession() }
            )

            is QrUiState.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (viewModel.intent == "register") "Scan to create your account" else "Scan to sign in",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(24.dp))
                QrCodeImage(content = state.activationUrl, modifier = Modifier.size(280.dp))
                Spacer(Modifier.height(20.dp))
                Text(
                    text = state.activationUrl,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Scan with your phone, or open this link. This code refreshes automatically if it expires.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 48.dp)
                )
                if (pollingDegraded) {
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Filled.WifiOff, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Can't reach the server right now — still trying…",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}
