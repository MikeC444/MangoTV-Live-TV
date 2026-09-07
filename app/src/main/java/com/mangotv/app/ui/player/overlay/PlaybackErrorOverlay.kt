package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Crude v1 of the player's error state — a working Try Again/Change
 * Source/Back card so a bad stream never traps the user. Gets its final
 * copy/visual pass in a later phase alongside the rest of the player's
 * polish work.
 */
@Composable
fun PlaybackErrorOverlay(
    message: String,
    onTryAgain: () -> Unit,
    onChangeSource: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Unable to play this source",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(16.dp))
            Row {
                MangoButton(
                    text = "Try Again",
                    icon = Icons.Filled.Refresh,
                    onClick = onTryAgain,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
                Spacer(Modifier.width(12.dp))
                MangoButton(
                    text = "Change Source",
                    icon = Icons.Filled.SwapHoriz,
                    onClick = onChangeSource,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
                Spacer(Modifier.width(12.dp))
                MangoButton(
                    text = "Back",
                    icon = Icons.Filled.ArrowBack,
                    onClick = onBack,
                    style = MangoButtonStyle.GLASS,
                    borderColor = Color.White
                )
            }
        }
    }
}
