package com.mangotv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun FullScreenErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.height(48.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            Text(
                text = "Something went wrong",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
            MangoButton(
                text = "Retry",
                icon = Icons.Filled.Refresh,
                onClick = onRetry,
                style = MangoButtonStyle.FILLED
            )
        }
    }
}

/**
 * Generic "there's nothing here" state: icon, title, message, and an
 * optional action button. [HomeEmptyState] below is the original
 * Home-specific copy, now just a thin wrapper — reused as-is by Search's
 * "no results" state and My List's "your list is empty" state, each with
 * their own icon/copy and no action button where none makes sense.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector = Icons.Filled.Refresh,
    onAction: (() -> Unit)? = null,
    actionFocusRequester: FocusRequester? = null,
    actionFocusUp: FocusRequester? = null
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.height(48.dp)
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(20.dp))
            Text(
                text = title,
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall
            )
            androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 48.dp)
            )
            if (actionLabel != null && onAction != null) {
                androidx.compose.foundation.layout.Spacer(Modifier.height(28.dp))
                MangoButton(
                    text = actionLabel,
                    icon = actionIcon,
                    onClick = onAction,
                    style = MangoButtonStyle.FILLED,
                    focusRequester = actionFocusRequester,
                    focusUp = actionFocusUp
                )
            }
        }
    }
}

@Composable
fun HomeEmptyState(
    onBrowseAddons: () -> Unit,
    modifier: Modifier = Modifier,
    buttonFocusRequester: FocusRequester? = null,
    buttonFocusUp: FocusRequester? = null
) = EmptyState(
    icon = Icons.Filled.Extension,
    title = "Your library is empty",
    message = "Install an addon to bring movies and TV shows into Mango TV.",
    modifier = modifier,
    actionLabel = "Browse Addons",
    actionIcon = Icons.Filled.Extension,
    onAction = onBrowseAddons,
    actionFocusRequester = buttonFocusRequester,
    actionFocusUp = buttonFocusUp
)
