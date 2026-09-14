package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/** One selectable entry in the Default Language list — [code] is an ISO 639-1 code, or null for "no preference" (System Default). */
data class SubtitleLanguageOption(val code: String?, val label: String)

/**
 * Fixed, hand-picked list rather than every Locale on the device: this is
 * a subtitle-track *preference* hint (see PlayerEngine.buildExoPlayer's
 * setPreferredTextLanguage), not an exhaustive locale picker — a short,
 * scannable list matters more here than completeness. "System Default"
 * (null) leaves ExoPlayer's own default track selection alone, i.e.
 * whatever it would already do with no preference set.
 */
val SubtitleLanguageOptions: List<SubtitleLanguageOption> = listOf(
    SubtitleLanguageOption(null, "System Default"),
    SubtitleLanguageOption("en", "English"),
    SubtitleLanguageOption("es", "Spanish"),
    SubtitleLanguageOption("fr", "French"),
    SubtitleLanguageOption("de", "German"),
    SubtitleLanguageOption("it", "Italian"),
    SubtitleLanguageOption("pt", "Portuguese"),
    SubtitleLanguageOption("nl", "Dutch"),
    SubtitleLanguageOption("sv", "Swedish"),
    SubtitleLanguageOption("pl", "Polish"),
    SubtitleLanguageOption("tr", "Turkish"),
    SubtitleLanguageOption("ar", "Arabic"),
    SubtitleLanguageOption("hi", "Hindi"),
    SubtitleLanguageOption("ja", "Japanese"),
    SubtitleLanguageOption("ko", "Korean"),
    SubtitleLanguageOption("zh", "Chinese")
)

/**
 * Settings > Subtitles: a default on/off for new playback sessions (off
 * means every video starts with subtitles off, regardless of what the
 * stream itself would otherwise auto-select) plus a preferred language
 * used when it's on. Both are just starting points for a session — the
 * in-player Subtitles menu can still override either one once a video is
 * actually playing (see PlayerEngine.buildExoPlayer's own kdoc).
 *
 * A ColumnScope extension hosted by SettingsScreen's detail pane -- see
 * AccountSettingsContent's kdoc for why this isn't its own screen anymore.
 *
 * The toggle row, header, and language options are all items of one
 * LazyColumn (see the comment on it below for why), with the toggle row
 * pinned first -- contentFocusRequester/navFocusRequester/sidebarFocusRequester
 * are wired onto it, not any language row, so this is safe the same way
 * HomeRowsSettingsContent's own first-item wiring is safe (see its kdoc):
 * only the selected category is ever composed at all, so switching into
 * this tab always recomposes fresh at scroll position 0, guaranteeing the
 * toggle row (item 0) is present the moment focus could land on it. The
 * language rows themselves still get no FocusRequester of their own (unlike
 * e.g. GenresScreen's genre list) -- pinning one to a row that can scroll
 * out of composition is what actually crashes (see GenresScreen/HomeRowsScreen's
 * own fix for that failure), and nothing here needs a guaranteed seam
 * beyond the one the toggle row already provides.
 */
@Composable
fun ColumnScope.SubtitleSettingsContent(
    navFocusRequester: FocusRequester,
    contentFocusRequester: FocusRequester,
    sidebarFocusRequester: FocusRequester,
    viewModel: SubtitleSettingsViewModel = viewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    // The toggle row, "Default Language" header, and the language list are
    // all items in this one LazyColumn (rather than a static header above a
    // separately-scrolling list) so the whole tab scrolls as a unit -- the
    // header scrolls away with everything else instead of permanently
    // reserving space at the top, leaving more of the screen for language
    // rows once scrolled.
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f),
        // LazyColumn clips to its own bounds, and each row below fills
        // its full width with no margin of its own -- so a row's focus
        // scale-up (TvFocusSurface) had nowhere to grow into and got
        // clipped flush against the list's left/right edges. Same fix
        // as TopNavBar/SourcesScreen/SoundSettingsScreen: reserve a
        // little headroom via contentPadding for the scale to grow into.
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item(key = "toggle") {
            SubtitlesToggleRow(
                enabled = preferences.subtitlesEnabled,
                onToggle = viewModel::setSubtitlesEnabled,
                focusRequester = contentFocusRequester,
                focusUp = navFocusRequester,
                focusLeft = sidebarFocusRequester
            )
        }
        item(key = "language_title") {
            Text(
                text = "Default Language",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        item(key = "language_description") {
            Text(
                text = "Used to automatically pick a matching subtitle track when Subtitles is on.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall
            )
        }
        items(SubtitleLanguageOptions, key = { it.code ?: "system_default" }) { option ->
            LanguageOptionRow(
                label = option.label,
                selected = option.code == preferences.defaultSubtitleLanguage,
                onClick = { viewModel.setDefaultSubtitleLanguage(option.code) }
            )
        }
    }
}

@Composable
private fun SubtitlesToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusLeft: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = { onToggle(!enabled) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        // Same wide-element-safe scale as Home Rows'/Genres' full-width rows.
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary,
        focusRequester = focusRequester,
        focusUp = focusUp,
        focusLeft = focusLeft
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subtitles",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedTrackColor = MangoAmber)
            )
        }
    }
}

@Composable
private fun LanguageOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        borderColor = TextPrimary
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Was 14.dp -- with 16 languages to scroll through via
                // D-pad, a shorter row means more of the list is visible
                // at once without shrinking the tap target unreasonably.
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = "Selected", tint = MangoAmber)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
