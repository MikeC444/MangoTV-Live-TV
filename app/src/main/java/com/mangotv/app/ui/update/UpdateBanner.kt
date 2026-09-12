package com.mangotv.app.ui.update

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.update.AppUpdate
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.DividerSubtle
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

@Composable
internal fun UpdateBanner(
    state: UpdateUiState,
    update: AppUpdate,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onShowReleaseNotes: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val progress = (if (state.isDownloading) state.downloadProgress ?: 0f else 0f).coerceIn(0f, 1f)

    val subtitle = when {
        state.errorMessage != null -> state.errorMessage
        state.isDownloading && state.downloadProgress != null -> "Downloading… ${(state.downloadProgress * 100).toInt().coerceIn(0, 100)}%"
        state.isDownloading -> "Downloading…"
        state.downloadedApkPath != null -> "Ready to install"
        else -> "Update available"
    }
    val updateLabel = listOfNotNull(
        update.tag,
        update.assetSizeBytes?.let { Formatter.formatShortFileSize(context, it) }
    ).joinToString(separator = " • ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(MangoBackgroundElevated)
                if (progress > 0f) {
                    drawRect(
                        color = MangoAmber,
                        alpha = 0.9f,
                        size = Size(width = size.width * progress, height = size.height)
                    )
                }
                drawRect(
                    color = DividerSubtle,
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(width = size.width, height = 1.dp.toPx())
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 32.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.CloudDownload,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(28.dp)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = updateLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.errorMessage != null) MangoAmber else TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            UpdateBannerIconButton(
                icon = Icons.Filled.Info,
                contentDescription = "Release notes",
                onClick = onShowReleaseNotes
            )

            MangoButton(
                text = when {
                    state.isDownloading -> "Downloading…"
                    state.downloadedApkPath != null -> "Install"
                    state.errorMessage != null -> "Retry"
                    else -> "Update"
                },
                icon = Icons.Filled.CloudDownload,
                onClick = {
                    when {
                        state.isDownloading -> Unit
                        state.downloadedApkPath != null -> onInstall()
                        else -> onDownload()
                    }
                },
                style = MangoButtonStyle.FILLED,
                compact = true
            )

            if (!state.isDownloading) {
                UpdateBannerIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Dismiss",
                    onClick = onDismiss
                )
            }
        }
    }
}

@Composable
private fun UpdateBannerIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    HeroIconButton(
        icon = icon,
        contentDescription = contentDescription,
        onClick = onClick,
        compact = true
    )
}
