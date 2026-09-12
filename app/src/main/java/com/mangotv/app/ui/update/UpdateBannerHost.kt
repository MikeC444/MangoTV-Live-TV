package com.mangotv.app.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mangotv.app.data.update.AppUpdate
import com.mangotv.app.ui.components.ClickSound
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Wraps the whole app: shows [UpdateBanner] above [content] (pushing it
 * down, never overlaying it) whenever an update is available and hasn't
 * been dismissed. [suppressed] hides it without losing state -- passed
 * true while the video player is active, the same way MangoNavHost already
 * silences other chrome (nav sounds, the back-press toast) during playback.
 */
@Composable
fun UpdateBannerHost(
    viewModel: UpdateViewModel,
    suppressed: Boolean,
    content: @Composable () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showReleaseNotes by remember { mutableStateOf(false) }

    val update = state.update
    val showBanner = state.showBanner && update != null && !suppressed

    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showBanner,
            enter = expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 180)),
            exit = shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(durationMillis = 240)
            ) + fadeOut(animationSpec = tween(durationMillis = 150))
        ) {
            update?.let {
                UpdateBanner(
                    state = state,
                    update = it,
                    onDownload = viewModel::downloadUpdate,
                    onInstall = viewModel::installUpdateOrRequestPermission,
                    onShowReleaseNotes = { showReleaseNotes = true },
                    onDismiss = viewModel::dismissBanner
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            content()
        }
    }

    if (showReleaseNotes && update != null) {
        UpdateReleaseNotesOverlay(
            update = update,
            onDismiss = { showReleaseNotes = false }
        )
    }

    if (state.showUnknownSourcesDialog) {
        UpdateUnknownSourcesOverlay(
            onOpenSettings = viewModel::openUnknownSourcesSettings,
            onDismiss = viewModel::dismissUnknownSourcesDialog
        )
    }
}

@Composable
private fun UpdateReleaseNotesOverlay(update: AppUpdate, onDismiss: () -> Unit) {
    val scrollState = rememberScrollState()
    val closeFocusRequester = remember { FocusRequester() }
    // This overlay is a plain Box drawn over everything else, not a real
    // dialog -- nothing moves D-pad focus onto it automatically, so without
    // this the Close button is simply unreachable (focus stays wherever it
    // was on the screen behind, now hidden underneath).
    LaunchedEffect(Unit) {
        runCatching { closeFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MangoSurfaceHigh)
                .padding(28.dp)
        ) {
            Text(
                text = "What's new in ${update.tag}",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Text(
                text = update.notes.ifBlank { "No release notes were provided for this update." },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(scrollState)
            )
            Spacer(modifier = Modifier.padding(top = 20.dp))
            MangoButton(
                text = "Close",
                icon = Icons.Filled.Close,
                onClick = onDismiss,
                style = MangoButtonStyle.GLASS,
                clickSound = ClickSound.BACK,
                focusRequester = closeFocusRequester
            )
        }
    }
}

@Composable
private fun UpdateUnknownSourcesOverlay(onOpenSettings: () -> Unit, onDismiss: () -> Unit) {
    val openSettingsFocusRequester = remember { FocusRequester() }
    // Same focus gap as UpdateReleaseNotesOverlay -- see its own comment.
    LaunchedEffect(Unit) {
        runCatching { openSettingsFocusRequester.requestFocus() }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MangoSurfaceHigh)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Allow installing updates",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.padding(top = 12.dp))
            Text(
                text = "MangoTV needs permission to install app updates it downloads. " +
                    "You'll be taken to a settings screen -- allow it there, then come back and try again.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.padding(top = 20.dp))
            Row {
                MangoButton(
                    text = "Open Settings",
                    icon = Icons.Filled.Settings,
                    onClick = onOpenSettings,
                    style = MangoButtonStyle.FILLED,
                    focusRequester = openSettingsFocusRequester
                )
                Spacer(modifier = Modifier.padding(start = 12.dp))
                MangoButton(
                    text = "Cancel",
                    icon = Icons.Filled.Close,
                    onClick = onDismiss,
                    style = MangoButtonStyle.GLASS,
                    clickSound = ClickSound.BACK
                )
            }
        }
    }
}
