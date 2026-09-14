package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * The 5 existing Settings destinations, now presented as a two-pane
 * master/detail layout (sidebar left, selected category's content filling
 * the remaining 75% on the right) instead of each being its own full-screen
 * navigation destination. icon/title/subtitle are exactly what each
 * category's old standalone screen already used for its own row/title (see
 * git history) -- nothing new added, just consolidated onto one enum so the
 * sidebar row and the detail pane's header stay in sync automatically.
 */
private enum class SettingsCategory(val icon: ImageVector, val title: String, val subtitle: String) {
    ACCOUNT(Icons.Filled.AccountCircle, "Account", "Manage your MangoTV account"),
    ADDONS(Icons.Filled.Extension, "Addons", "Manage installed content providers"),
    HOME_ROWS(Icons.Filled.GridView, "Home Rows", "Choose which rows show up on Home"),
    SOUNDS(Icons.Filled.MusicNote, "Sounds", "Choose your app boot sound"),
    SUBTITLES(Icons.Filled.Subtitles, "Subtitles", "Default on/off and preferred language")
}

@Composable
fun SettingsScreen(
    onNavigate: (String) -> Unit,
    onSignedOut: () -> Unit,
    onAddAddon: () -> Unit
) {
    val navFocusRequester = remember { FocusRequester() }
    val accountRowFocusRequester = remember { FocusRequester() }
    val addonsRowFocusRequester = remember { FocusRequester() }
    val homeRowsRowFocusRequester = remember { FocusRequester() }
    val soundsRowFocusRequester = remember { FocusRequester() }
    val subtitlesRowFocusRequester = remember { FocusRequester() }

    // Shared by every sidebar row's focusRight: only the selected category's
    // content is ever actually composed on the right (see the `when` in
    // SettingsDetailPane below), so every row can point RIGHT at this same
    // instance without needing to know which pane is currently showing --
    // whichever one is on screen is the one that lands the focus.
    val paneContentFocusRequester = remember { FocusRequester() }

    var selected by remember { mutableStateOf(SettingsCategory.ACCOUNT) }

    fun rowFocusRequesterFor(category: SettingsCategory): FocusRequester = when (category) {
        SettingsCategory.ACCOUNT -> accountRowFocusRequester
        SettingsCategory.ADDONS -> addonsRowFocusRequester
        SettingsCategory.HOME_ROWS -> homeRowsRowFocusRequester
        SettingsCategory.SOUNDS -> soundsRowFocusRequester
        SettingsCategory.SUBTITLES -> subtitlesRowFocusRequester
    }

    SettingsScaffold(
        title = "Settings",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = accountRowFocusRequester
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 12.dp)
            ) {
                val categories = remember { SettingsCategory.values() }
                categories.forEachIndexed { index, category ->
                    SettingsSidebarRow(
                        category = category,
                        selected = category == selected,
                        onClick = { selected = category },
                        focusRequester = rowFocusRequesterFor(category),
                        focusUp = if (index == 0) navFocusRequester else null,
                        focusRight = paneContentFocusRequester
                    )
                    if (index != categories.lastIndex) {
                        Spacer(Modifier.height(10.dp))
                    }
                }
            }

            // 1:3 sidebar-to-detail weight ratio -- the sidebar column above
            // takes 1 share, this one takes 3, so the detail pane always
            // ends up at exactly 75% of the row's width regardless of
            // screen size.
            Box(
                modifier = Modifier
                    .weight(3f)
                    .fillMaxHeight()
            ) {
                SettingsDetailPane(
                    category = selected,
                    navFocusRequester = navFocusRequester,
                    contentFocusRequester = paneContentFocusRequester,
                    sidebarFocusRequester = rowFocusRequesterFor(selected),
                    onSignedOut = onSignedOut,
                    onAddAddon = onAddAddon
                )
            }
        }
    }
}

@Composable
private fun SettingsSidebarRow(
    category: SettingsCategory,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusRight: FocusRequester? = null
) {
    val contentColor = if (selected) MangoAmber else TextPrimary

    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        // Same reasoning as HomeRowToggleRow/SubtitlesToggleRow's own
        // focusedScale override: the default (tuned for small poster
        // cards) is too big a jump for a row that spans its whole
        // container's width, and scales past the safe margin.
        focusedScale = 1.02f,
        backgroundColor = if (selected) MangoSurfaceHigh else MangoSurface,
        // Keeps showing which category is active even once focus has moved
        // into the detail pane on the right -- independent of this
        // surface's own transient isFocused state.
        alwaysShowBorder = selected,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusRight = focusRight
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = category.icon, contentDescription = null, tint = contentColor)
            Spacer(Modifier.width(14.dp))
            Text(text = category.title, color = contentColor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * Convention for any category with more content than fits on screen
 * (Addons, Home Rows, Subtitles today): put everything -- header text,
 * toggle rows, list items, footers -- into ONE LazyColumn as items, rather
 * than a static header/footer around a separately-scrolling inner list.
 * That way the whole tab scrolls as a unit instead of permanently pinning
 * a header/footer that eats into the space available for actual list
 * content. A future category with a scrollable list should follow the
 * same shape (see AddonsSettingsContent/SubtitleSettingsContent for the
 * pattern) rather than reintroducing a fixed header above a nested list.
 */
@Composable
private fun SettingsDetailPane(
    category: SettingsCategory,
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    onSignedOut: () -> Unit,
    onAddAddon: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(text = category.title, color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(text = category.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(14.dp))

        when (category) {
            SettingsCategory.ACCOUNT -> AccountSettingsContent(
                onSignedOut = onSignedOut,
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.ADDONS -> AddonsSettingsContent(
                onAddAddon = onAddAddon,
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.HOME_ROWS -> HomeRowsSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
            SettingsCategory.SOUNDS -> SoundSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester
            )
            SettingsCategory.SUBTITLES -> SubtitleSettingsContent(
                navFocusRequester = navFocusRequester,
                contentFocusRequester = contentFocusRequester,
                sidebarFocusRequester = sidebarFocusRequester
            )
        }
    }
}
