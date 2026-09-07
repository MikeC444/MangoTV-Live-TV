package com.mangotv.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.InstalledAddon
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun AddonsScreen(
    onNavigate: (String) -> Unit,
    onAddAddon: () -> Unit,
    viewModel: AddonsViewModel = viewModel()
) {
    val addons by viewModel.installedAddons.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val addButtonFocusRequester = remember { FocusRequester() }

    SettingsScaffold(
        title = "Addons",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = addButtonFocusRequester
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Stremio-compatible addons contribute their catalogs directly into Home.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(20.dp))
            MangoButton(
                text = "Add Addon",
                icon = Icons.Filled.Add,
                onClick = onAddAddon,
                style = MangoButtonStyle.FILLED,
                focusRequester = addButtonFocusRequester,
                focusUp = navFocusRequester
            )
        }

        Spacer(Modifier.height(28.dp))

        if (addons.isEmpty()) {
            EmptyAddonsHint()
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(addons, key = { it.manifestUrl }) { addon ->
                    AddonRow(
                        addon = addon,
                        onToggle = { enabled -> viewModel.setEnabled(addon.manifestUrl, enabled) },
                        onRemove = { viewModel.remove(addon.manifestUrl) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyAddonsHint() {
    Column {
        Icon(imageVector = Icons.Filled.Extension, contentDescription = null, tint = TextTertiary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = "No addons installed yet",
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add a Stremio-compatible addon to bring its catalog into Mango TV.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun AddonRow(
    addon: InstalledAddon,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MangoSurface, RoundedCornerShape(MangoDimens.CardCornerRadius))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = addon.manifest.name, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Text(text = "v${addon.manifest.version}", color = TextTertiary, style = MaterialTheme.typography.labelSmall)
            }
            addon.manifest.description?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (addon.manifest.types.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = addon.manifest.types.joinToString("  ·  ") { it.replaceFirstChar { c -> c.uppercase() } },
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Switch(
            checked = addon.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedTrackColor = MangoAmber)
        )

        Spacer(Modifier.width(12.dp))

        TvFocusSurface(
            onClick = onRemove,
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MangoBackground
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove ${addon.manifest.name}",
                tint = TextSecondary,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}
