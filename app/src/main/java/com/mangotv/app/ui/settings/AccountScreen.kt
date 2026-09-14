package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Minimal on purpose — device management (Milestone 3's /auth/sessions:
 * viewing or revoking this account's *other* active sessions) is still
 * its own later milestone. "Sync existing data" prompts (Milestone 11)
 * and account switching (Milestone 12: Sign Out here now actually wipes
 * local caches, not just this device's session -- see
 * AccountSwitchCoordinator) are both built. What's here exists so a
 * signed-in build is actually re-testable (create account -> sign out ->
 * sign in again, as the same account or a different one) without
 * clearing app data, which would otherwise be the only way back to the
 * auth screen once past it.
 *
 * Trakt account linking (below the profile info, above Sign Out) is the
 * one other thing this screen manages — see TraktRepository and
 * server/src/services/traktService.ts for the connection itself.
 */
@Composable
fun AccountScreen(
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
    onConnectTrakt: () -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()
    val traktState by viewModel.traktState.collectAsStateWithLifecycle()

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    // Refreshes on every (re)entry into this screen -- including returning
    // from TraktConnectScreen, whether that connect attempt succeeded, was
    // cancelled, or failed, so this section never shows stale state.
    LaunchedEffect(Unit) {
        viewModel.refreshTraktStatus()
    }

    val navFocusRequester = remember { FocusRequester() }
    val traktActionFocusRequester = remember { FocusRequester() }
    val signOutFocusRequester = remember { FocusRequester() }

    // Sign Out's focus-up target depends on whether the Trakt section
    // currently has a focusable button above it (Connect/Disconnect/Retry)
    // -- only Loading and NotConfigured have none, in which case Sign Out
    // goes straight back up to the nav bar, same as before this section
    // existed.
    val traktSectionHasButton = traktState is TraktSectionState.Disconnected ||
        traktState is TraktSectionState.Connected ||
        traktState is TraktSectionState.Error

    SettingsScaffold(
        title = "Account",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = signOutFocusRequester
    ) {
        val user = session?.user
        if (user != null) {
            Text(text = user.displayName ?: user.email, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
            if (user.displayName != null) {
                Text(text = user.email, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }

        Spacer(Modifier.height(36.dp))
        Text(text = "Trakt", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        TraktSection(
            state = traktState,
            onConnect = onConnectTrakt,
            onDisconnect = viewModel::disconnectTrakt,
            onRetry = viewModel::refreshTraktStatus,
            actionFocusRequester = traktActionFocusRequester,
            focusUp = navFocusRequester,
            focusDown = signOutFocusRequester
        )

        Spacer(Modifier.height(28.dp))
        MangoButton(
            text = if (signingOut) "Signing Out…" else "Sign Out",
            icon = Icons.Filled.Logout,
            onClick = viewModel::signOut,
            style = MangoButtonStyle.GLASS,
            focusRequester = signOutFocusRequester,
            focusUp = if (traktSectionHasButton) traktActionFocusRequester else navFocusRequester
        )
    }
}

@Composable
private fun TraktSection(
    state: TraktSectionState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRetry: () -> Unit,
    actionFocusRequester: FocusRequester,
    focusUp: FocusRequester,
    focusDown: FocusRequester
) {
    when (state) {
        is TraktSectionState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(color = MangoAmber, modifier = Modifier.height(20.dp).width(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = "Checking Trakt connection…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }

        is TraktSectionState.NotConfigured -> Text(
            text = "Trakt isn't available on this server yet.",
            color = TextTertiary,
            style = MaterialTheme.typography.bodyMedium
        )

        is TraktSectionState.Error -> {
            Text(text = state.message, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            MangoButton(
                text = "Retry",
                icon = Icons.Filled.Refresh,
                onClick = onRetry,
                style = MangoButtonStyle.GLASS,
                focusRequester = actionFocusRequester,
                focusUp = focusUp,
                focusDown = focusDown
            )
        }

        is TraktSectionState.Disconnected -> {
            Text(
                text = "Connect your Trakt account to keep a history of what you watch, synced with the rest of the Trakt community.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            MangoButton(
                text = "Connect Trakt",
                icon = Icons.Filled.Link,
                onClick = onConnect,
                style = MangoButtonStyle.GLASS,
                focusRequester = actionFocusRequester,
                focusUp = focusUp,
                focusDown = focusDown
            )
        }

        is TraktSectionState.Connected -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Filled.CheckCircle, contentDescription = null, tint = TextPrimary, modifier = Modifier.height(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (state.username != null) "Connected as ${state.username}" else "Connected",
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(16.dp))
            MangoButton(
                text = if (state.actionInProgress) "Disconnecting…" else "Disconnect",
                icon = Icons.Filled.LinkOff,
                onClick = onDisconnect,
                style = MangoButtonStyle.GLASS,
                focusRequester = actionFocusRequester,
                focusUp = focusUp,
                focusDown = focusDown
            )
        }
    }
}
