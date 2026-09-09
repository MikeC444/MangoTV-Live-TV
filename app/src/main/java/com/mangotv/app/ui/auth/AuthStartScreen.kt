package com.mangotv.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.MangoLogo
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextSecondary

/**
 * The first screen a signed-out user can actually act on. Both buttons
 * lead to the same underlying QR flow (ui/auth/QrSignInScreen.kt) — the
 * distinction is purely which headline the TV shows, since the
 * activation page the phone opens always lets the user pick sign-in or
 * create-account regardless of which one was pressed here.
 */
@Composable
fun AuthStartScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val signInFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { signInFocusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            MangoLogo(fontSize = 40.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Sign in to start watching",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(40.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                MangoButton(
                    text = "Sign In",
                    icon = Icons.Filled.Login,
                    onClick = onSignIn,
                    style = MangoButtonStyle.FILLED,
                    focusRequester = signInFocusRequester
                )
                MangoButton(
                    text = "Create Account",
                    icon = Icons.Filled.PersonAdd,
                    onClick = onCreateAccount,
                    style = MangoButtonStyle.GLASS
                )
            }
        }
    }
}
