package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.QrCodeImage
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.delay

@Composable
fun AddAddonScreen(
    onNavigate: (String) -> Unit,
    onInstalled: () -> Unit,
    viewModel: AddAddonViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pairingUrl by viewModel.pairingUrl.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val fieldFocusRequester = remember { FocusRequester() }
    var manualUrl by remember { mutableStateOf("") }

    DisposableEffect(Unit) {
        viewModel.startPairingServer()
        onDispose { viewModel.stopPairingServer() }
    }

    LaunchedEffect(uiState) {
        if (uiState is AddAddonUiState.Success) {
            delay(1100)
            onInstalled()
        }
    }

    SettingsScaffold(
        title = "Add Addon",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = fieldFocusRequester
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Scan with your phone", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                val currentPairingUrl = pairingUrl
                if (currentPairingUrl != null) {
                    QrCodeImage(content = currentPairingUrl, modifier = Modifier.size(240.dp))
                    Spacer(Modifier.height(14.dp))
                    Text(text = currentPairingUrl, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                } else {
                    Box(
                        modifier = Modifier
                            .size(240.dp)
                            .background(MangoSurface, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MangoAmber)
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "Your phone must be on the same Wi-Fi network as this Fire TV.",
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(Modifier.width(56.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(text = "Or enter the manifest URL directly", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(16.dp))

                TextField(
                    value = manualUrl,
                    onValueChange = { manualUrl = it },
                    placeholder = { Text("https://example.com/manifest.json") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(fieldFocusRequester)
                        .focusProperties { up = navFocusRequester },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MangoSurface,
                        unfocusedContainerColor = MangoSurface,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = MangoAmber,
                        focusedIndicatorColor = MangoAmber,
                        unfocusedIndicatorColor = TextTertiary
                    )
                )

                Spacer(Modifier.height(18.dp))

                MangoButton(
                    text = "Install",
                    icon = Icons.Filled.CloudUpload,
                    onClick = { viewModel.installAddon(manualUrl) },
                    style = MangoButtonStyle.FILLED
                )

                Spacer(Modifier.height(24.dp))

                when (val state = uiState) {
                    is AddAddonUiState.Idle -> {}
                    is AddAddonUiState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MangoAmber, strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(text = "Installing…", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                    }
                    is AddAddonUiState.Success -> Text(
                        text = "${state.addon.manifest.name} installed ✓",
                        color = MangoAmber,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    is AddAddonUiState.Error -> Text(
                        text = "Couldn't install that addon: ${state.message}",
                        color = MangoCoral,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Start
                    )
                }
            }
        }
    }
}
