package com.mangotv.app.ui.player.overlay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.player.QualityOption

@Composable
fun QualityMenu(
    options: List<QualityOption>,
    onSelect: (QualityOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocusRequester.requestFocus() } }

    MenuOverlayScaffold(title = "Quality", modifier = modifier) {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // Keyed on list position, not label+trackIndex: trackIndex only
            // resets to 0 within its own track group, so two renditions in
            // different groups with the same label previously produced a
            // duplicate key and crashed the LazyColumn.
            itemsIndexed(options, key = { index, _ -> index }) { index, option ->
                MenuOptionRow(
                    label = option.label,
                    isSelected = option.isSelected,
                    onClick = { onSelect(option) },
                    focusRequester = if (index == 0) firstFocusRequester else null
                )
            }
        }
    }
}
