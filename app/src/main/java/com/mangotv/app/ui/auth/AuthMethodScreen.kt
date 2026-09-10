package com.mangotv.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextPrimary

/**
 * The intermediate step between AuthStartScreen and the two actual
 * sign-in implementations — asks how the user wants to authenticate
 * before committing to either. [intent] ("login" or "register") only
 * changes this screen's own headline; see MangoRoutes.authMethod's kdoc
 * for why neither downstream path actually depends on it.
 */
@Composable
fun AuthMethodScreen(
    intent: String,
    onScanQr: () -> Unit,
    onUseRemote: () -> Unit
) {
    val scanQrFocusRequester = remember { FocusRequester() }
    val useRemoteFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { scanQrFocusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 420.dp)
        ) {
            Text(
                text = if (intent == "register") "How would you like to create your account?" else "How would you like to sign in?",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            MangoButton(
                text = "Scan a QR Code",
                icon = Icons.Filled.QrCode2,
                onClick = onScanQr,
                style = MangoButtonStyle.FILLED,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = scanQrFocusRequester,
                focusDown = useRemoteFocusRequester
            )
            Spacer(Modifier.height(16.dp))
            MangoButton(
                text = "Type on My Remote",
                icon = Icons.Filled.Edit,
                onClick = onUseRemote,
                style = MangoButtonStyle.GLASS,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = useRemoteFocusRequester,
                focusUp = scanQrFocusRequester
            )
        }
    }
}
