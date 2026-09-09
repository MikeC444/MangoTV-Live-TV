package com.mangotv.app.ui.auth

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.MangoLogo
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoBrandGradient
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * The first screen a signed-out user can actually act on. Both buttons
 * lead to the same underlying QR flow (ui/auth/QrSignInScreen.kt) — the
 * distinction is purely which headline the TV shows, since the
 * activation page the phone opens always lets the user pick sign-in or
 * create-account regardless of which one was pressed here. No on-screen
 * keyboard entry happens on this screen, or anywhere on the TV: that's
 * the entire reason both paths hand off to a QR code instead.
 */
@Composable
fun AuthStartScreen(
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit
) {
    val logInFocusRequester = remember { FocusRequester() }
    val signUpFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { logInFocusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                .widthIn(max = 620.dp)
        ) {
            MangoLogo(fontSize = 32.sp)
            Spacer(Modifier.height(40.dp))
            Text(
                text = buildAnnotatedString {
                    append("Your Entertainment,\n")
                    withStyle(SpanStyle(brush = MangoBrandGradient)) { append("Your Way") }
                },
                color = TextPrimary,
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Stream the latest movies, TV shows, live TV and more. Create an account to get the full experience.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(40.dp))
            MangoButton(
                text = "Log In",
                icon = Icons.Filled.Person,
                trailingIcon = Icons.Filled.ChevronRight,
                onClick = onSignIn,
                style = MangoButtonStyle.FILLED,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = logInFocusRequester,
                focusDown = signUpFocusRequester
            )
            Spacer(Modifier.height(16.dp))
            MangoButton(
                text = "Sign Up",
                icon = Icons.Filled.Add,
                trailingIcon = Icons.Filled.ChevronRight,
                onClick = onCreateAccount,
                style = MangoButtonStyle.GLASS,
                modifier = Modifier.fillMaxWidth(),
                focusRequester = signUpFocusRequester,
                focusUp = logInFocusRequester
            )
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.QrCode2,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Scan a QR code to create an account from your phone",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}
