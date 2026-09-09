package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onOpenAddons: () -> Unit,
    onOpenHomeRows: () -> Unit,
    onOpenAccount: () -> Unit
) {
    val navFocusRequester = remember { FocusRequester() }
    val accountFocusRequester = remember { FocusRequester() }
    val addonsFocusRequester = remember { FocusRequester() }
    val homeRowsFocusRequester = remember { FocusRequester() }

    SettingsScaffold(
        title = "Settings",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = accountFocusRequester
    ) {
        SettingsCategoryRow(
            icon = Icons.Filled.AccountCircle,
            title = "Account",
            subtitle = "Manage your MangoTV account",
            onClick = onOpenAccount,
            focusRequester = accountFocusRequester,
            focusUp = navFocusRequester
        )
        Spacer(Modifier.height(14.dp))
        SettingsCategoryRow(
            icon = Icons.Filled.Extension,
            title = "Addons",
            subtitle = "Manage installed content providers",
            onClick = onOpenAddons,
            focusRequester = addonsFocusRequester,
            focusUp = accountFocusRequester
        )
        Spacer(Modifier.height(14.dp))
        SettingsCategoryRow(
            icon = Icons.Filled.ViewList,
            title = "Home Rows",
            subtitle = "Choose which rows show up on Home",
            onClick = onOpenHomeRows,
            focusRequester = homeRowsFocusRequester
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = "Mango TV · v0.1.0",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            text = "More settings — playback, subtitles, audio, appearance — are coming in a later update.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SettingsCategoryRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        focusRequester = focusRequester,
        focusUp = focusUp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = TextPrimary)
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Text(text = subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, tint = TextSecondary)
        }
    }
}
