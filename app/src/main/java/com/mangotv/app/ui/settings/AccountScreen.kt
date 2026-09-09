package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Minimal on purpose — device management (Milestone 3's /auth/sessions),
 * "sync existing data" prompts, and account switching are their own
 * later milestones. What's here exists so a signed-in build is actually
 * re-testable (create account -> sign out -> sign in again) without
 * clearing app data, which would otherwise be the only way back to the
 * auth screen once past it.
 */
@Composable
fun AccountScreen(
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val signingOut by viewModel.signingOut.collectAsStateWithLifecycle()
    val signedOut by viewModel.signedOut.collectAsStateWithLifecycle()

    LaunchedEffect(signedOut) {
        if (signedOut) onSignedOut()
    }

    val navFocusRequester = remember { FocusRequester() }
    val signOutFocusRequester = remember { FocusRequester() }

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
        Spacer(Modifier.height(28.dp))
        MangoButton(
            text = if (signingOut) "Signing Out…" else "Sign Out",
            icon = Icons.Filled.Logout,
            onClick = viewModel::signOut,
            style = MangoButtonStyle.GLASS,
            focusRequester = signOutFocusRequester,
            focusUp = navFocusRequester
        )
    }
}
