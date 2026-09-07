package com.mangotv.app.ui.livetv

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.QrCodeImage
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * The paywall shown whenever Live TV is opened without an active
 * entitlement (see LiveTvViewModel.LiveTvUiState.Locked). Never shows a
 * "purchased" state on its own — this screen only ever reflects whatever
 * EntitlementRepository last heard from the backend, and disappears the
 * moment that flips to Active without any user action (see LiveTvScreen).
 */
@Composable
fun PremiumAccessScreen(
    state: LiveTvUiState.Locked,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester? = null
) {
    val retryFocusRequester = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = MangoDimens.NavBarHeight)
            .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.LiveTv, contentDescription = null, tint = MangoAmber, modifier = Modifier.height(22.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "LIVE TV",
                color = TextSecondary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = if (state.justExpired) "Your Live TV access has expired." else "Unlock Live TV",
            color = TextPrimary,
            style = MaterialTheme.typography.displayMedium,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Access live television channels directly from MangoTV.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        when {
            state.notConfigured -> ConfigNoticeCard(
                message = "Live TV's backend isn't configured yet.",
                detail = "Set API_BASE_URL (and PREMIUM_CHECKOUT_URL for the QR code below) in local.properties — see README.md."
            )
            state.checkoutUrl == null -> ConfigNoticeCard(
                message = "Checkout isn't configured yet.",
                detail = "Set PREMIUM_CHECKOUT_URL in local.properties to enable purchases — see README.md."
            )
            else -> {
                QrCodeImage(content = state.checkoutUrl, modifier = Modifier.size(200.dp))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Scan this QR code with your phone to purchase access.",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Pairing code: ${state.pairingCode}",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }

        Spacer(Modifier.height(28.dp))
        StatusRow(state)

        if (state.backendMessage != null) {
            Spacer(Modifier.height(20.dp))
            MangoButton(
                text = "Retry",
                icon = Icons.Filled.Refresh,
                onClick = onRetry,
                style = MangoButtonStyle.FILLED,
                focusRequester = retryFocusRequester,
                focusUp = navFocusRequester
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatusRow(state: LiveTvUiState.Locked) {
    val (dotColor, label, pulsing) = when {
        state.notConfigured -> Triple(TextTertiary, "Not configured", false)
        state.backendMessage != null -> Triple(MangoCoral, "Backend unavailable", false)
        else -> Triple(MangoAmber, "Waiting for payment", true)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Status:", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(10.dp))
        StatusDot(color = dotColor, pulsing = pulsing)
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StatusDot(color: Color, pulsing: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "statusDot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "statusDotAlpha"
    )
    Box(
        modifier = Modifier
            .size(9.dp)
            .background(color.copy(alpha = if (pulsing) pulseAlpha else 1f), CircleShape)
    )
}

@Composable
private fun ConfigNoticeCard(message: String, detail: String) {
    Column(
        modifier = Modifier
            .widthIn(max = 460.dp)
            .background(MangoSurface, RoundedCornerShape(MangoDimens.CardCornerRadius))
            .padding(20.dp)
    ) {
        Text(text = message, color = TextPrimary, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(text = detail, color = TextTertiary, style = MaterialTheme.typography.labelMedium, lineHeight = 18.sp)
    }
}
