package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.data.model.HomeSection
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

@Composable
fun HomeRowsScreen(
    onNavigate: (String) -> Unit,
    viewModel: HomeRowsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val firstRowFocusRequester = remember { FocusRequester() }

    // Which row is currently "picked up" for reordering, keyed by its id —
    // null means no row is being moved. While a row is grabbed, its drag
    // handle intercepts Up/Down itself (see HomeRowToggleRow) to reorder
    // instead of letting them move focus.
    var grabbedRowId by remember { mutableStateOf<String?>(null) }

    SettingsScaffold(
        title = "Home Rows",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = firstRowFocusRequester,
        titleIcon = Icons.Filled.GridView
    ) {
        Text(
            text = "Toggle categories on or off, and use the handle to reorder them.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))

        when (val state = uiState) {
            is HomeRowsUiState.Loading -> CircularProgressIndicator(color = MangoAmber)
            is HomeRowsUiState.NoAddons -> Text(
                text = "Install an addon first — its rows will show up here once it's added.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            is HomeRowsUiState.Loaded -> {
                if (state.rows.isEmpty()) {
                    Text(
                        text = "Your installed addons aren't reporting any rows right now.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    val orderedRows = remember(state.rows, preferences) { preferences.applyOrder(state.rows) }
                    val displayOrder = remember(orderedRows) { orderedRows.map { it.id } }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(orderedRows, key = { _, row -> row.id }) { index, row ->
                            val visible = row.id !in preferences.hiddenRowIds
                            HomeRowToggleRow(
                                row = row,
                                visible = visible,
                                grabbed = grabbedRowId == row.id,
                                onToggleVisible = { viewModel.setRowVisible(row.id, !visible) },
                                onToggleGrabbed = {
                                    grabbedRowId = if (grabbedRowId == row.id) null else row.id
                                },
                                onMove = { delta -> viewModel.moveRow(displayOrder, row.id, delta) },
                                onHandleFocusLost = { if (grabbedRowId == row.id) grabbedRowId = null },
                                focusRequester = if (index == 0) firstRowFocusRequester else null,
                                focusUp = if (index == 0) navFocusRequester else null
                            )
                        }
                    }

                    HorizontalDivider(color = TextTertiary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Changes are saved automatically",
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

private fun rowSubtitle(row: HomeSection): String {
    val count = row.items.size
    return if (count == 1) "1 title" else "$count titles"
}

@Composable
private fun HomeRowToggleRow(
    row: HomeSection,
    visible: Boolean,
    grabbed: Boolean,
    onToggleVisible: () -> Unit,
    onToggleGrabbed: () -> Unit,
    onMove: (Int) -> Unit,
    onHandleFocusLost: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    val titleColor = if (visible) TextPrimary else TextTertiary
    val subtitleColor = if (visible) TextSecondary else TextTertiary

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TvFocusSurface(
            onClick = onToggleVisible,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            // TvFocusSurface's default focusedScale (1.08x) is tuned for
            // small poster cards, where 8% is only a few dp. This row spans
            // almost the full screen width, so the same percentage was tens
            // of dp of growth per edge -- enough to push past the screen's
            // safe margin and read as clipped/cut off. A much smaller scale
            // keeps the same "grow on focus" feel at a size that stays
            // safely on screen for a wide element.
            focusedScale = 1.02f,
            backgroundColor = MangoSurface,
            focusRequester = focusRequester,
            focusUp = focusUp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.title,
                    color = titleColor,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                Text(text = rowSubtitle(row), color = subtitleColor, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = visible,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MangoBackground,
                        checkedTrackColor = TextPrimary,
                        checkedBorderColor = TextPrimary,
                        uncheckedThumbColor = TextTertiary,
                        uncheckedTrackColor = MangoBackground,
                        uncheckedBorderColor = TextTertiary
                    )
                )
            }
        }

        TvFocusSurface(
            onClick = onToggleGrabbed,
            modifier = Modifier
                .size(40.dp)
                .onPreviewKeyEvent { event ->
                    if (grabbed && event.type == KeyEventType.KeyDown &&
                        (event.key == Key.DirectionUp || event.key == Key.DirectionDown)
                    ) {
                        onMove(if (event.key == Key.DirectionUp) -1 else 1)
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
            backgroundColor = MangoSurface,
            alwaysShowBorder = grabbed,
            borderColor = TextPrimary,
            onFocusChanged = { hasFocus -> if (!hasFocus) onHandleFocusLost() }
        ) {
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = if (grabbed) "Stop moving ${row.title}" else "Reorder ${row.title}",
                tint = if (grabbed) TextPrimary else TextSecondary,
                modifier = Modifier
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}
