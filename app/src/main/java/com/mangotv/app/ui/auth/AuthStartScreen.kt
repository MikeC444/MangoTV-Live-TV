package com.mangotv.app.ui.auth

import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.R
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
 * The first screen a signed-out user can actually act on. Log In and Sign
 * Up both lead to ui/auth/AuthMethodScreen.kt — a small intermediate
 * screen that asks how they'd rather authenticate (scan a QR code with
 * their phone, or type an email/password on the remote) before handing
 * off to ui/auth/QrSignInScreen.kt or ui/auth/PasswordSignInScreen.kt
 * respectively. Which of these two buttons was pressed only decides the
 * headline text shown downstream (sign-in vs create-account framing) —
 * see MangoRoutes.authMethod's own kdoc.
 *
 * The right half shows a static hero photo (res/drawable-nodpi/
 * auth_hero_living_room.webp) that fades into MangoBackground toward the
 * left, via one gradient-scrim draw pass rather than a second overlapping
 * composable — see the `drawWithCache` block below. `nodpi` deliberately
 * opts this one image out of Android's per-density resource buckets: it's
 * a single fixed photo scaled to fill by Compose (`ContentScale.Crop`),
 * not a density-specific icon set. The left column is deliberately capped
 * well short of the image's fade-in point (which starts at the screen's
 * horizontal midpoint) so neither the buttons nor the body text ever
 * overlap it.
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
        Image(
            painter = painterResource(R.drawable.auth_hero_living_room),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.CenterEnd,
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    // Solid MangoBackground for the left half (matching the
                    // rest of the screen exactly, not just approximating
                    // it), then a linear fade to fully transparent by the
                    // right edge -- alpha-only, RGB held constant, so the
                    // transition doesn't muddy through black the way
                    // fading to Color.Transparent would.
                    val scrim = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0f to MangoBackground,
                            0.5f to MangoBackground,
                            1f to MangoBackground.copy(alpha = 0f)
                        ),
                        startX = 0f,
                        endX = size.width
                    )
                    onDrawWithContent {
                        drawContent()
                        drawRect(scrim)
                    }
                }
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                .widthIn(max = 400.dp)
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
                text = "Stream the latest movies, TV shows and more. Create an account to get the full experience.",
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
