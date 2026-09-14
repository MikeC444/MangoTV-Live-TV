package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.FullScreenErrorState
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.QrCodeImage
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Reached from AccountScreen's "Connect Trakt" button. Pressing Back here
 * is left to Navigation-Compose's own default behavior (pop back to
 * AccountScreen), same as QrSignInScreen -- the abandoned pairing attempt
 * simply expires on the backend on its own. The on-screen Cancel button
 * below does the exact same thing, just as a more discoverable affordance.
 */
@Composable
fun TraktConnectScreen(
    onConnected: () -> Unit,
    onCancel: () -> Unit,
    viewModel: TraktConnectViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pollingDegraded by viewModel.pollingDegraded.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val primaryActionFocusRequester = remember { FocusRequester() }
    val cancelFocusRequester = remember { FocusRequester() }

    LaunchedEffect(connected) {
        if (connected) onConnected()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is TraktConnectUiState.Loading -> CircularProgressIndicator(color = MangoAmber)

            is TraktConnectUiState.Error -> FullScreenErrorState(
                message = state.message,
                onRetry = { viewModel.startLink() },
                secondaryActionLabel = "Cancel",
                onSecondaryAction = onCancel
            )

            is TraktConnectUiState.Denied -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 480.dp)
            ) {
                Text(
                    text = "Connection request denied",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "You declined the request on trakt.tv. You can try again anytime.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(28.dp))
                MangoButton(
                    text = "Try Again",
                    icon = Icons.Filled.Refresh,
                    onClick = { viewModel.startLink() },
                    style = MangoButtonStyle.FILLED,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = primaryActionFocusRequester,
                    focusDown = cancelFocusRequester
                )
                Spacer(Modifier.height(12.dp))
                MangoButton(
                    text = "Cancel",
                    icon = Icons.Filled.Close,
                    onClick = onCancel,
                    style = MangoButtonStyle.GLASS,
                    modifier = Modifier.fillMaxWidth(),
                    focusRequester = cancelFocusRequester,
                    focusUp = primaryActionFocusRequester,
                    clickSound = ClickSound.BACK
                )
            }

            is TraktConnectUiState.Ready -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Connect Trakt",
                    color = TextPrimary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = "On your phone or computer, go to",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = state.info.verificationUrl,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = state.info.userCode,
                    color = TextPrimary,
                    style = MaterialTheme.typography.displayLarge.copy(letterSpacing = 8.sp)
                )
                Spacer(Modifier.height(24.dp))
                QrCodeImage(content = state.info.directVerificationUrl, modifier = Modifier.size(220.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Or scan this code with your phone's camera.",
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
                Spacer(Modifier.height(28.dp))
                MangoButton(
                    text = "Cancel",
                    icon = Icons.Filled.Close,
                    onClick = onCancel,
                    style = MangoButtonStyle.GLASS,
                    focusRequester = cancelFocusRequester,
                    clickSound = ClickSound.BACK
                )
            }
        }
    }
}
