package com.mangotv.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Subtitles
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
 * The language list below is deliberately NOT given any
 * focusRequester/focusUp/focusDown wiring of its own (unlike e.g.
 * GenresScreen's genre list) — it relies entirely on Compose's default
 * spatial focus search, the same as AddonsScreen's own addon list. That's
 * a deliberate choice, not an oversight: pinning a FocusRequester to a
 * scrollable list's first item crashes the moment that item scrolls out
 * of composition (see GenresScreen/HomeRowsScreen's own fix for this
 * exact failure), and unlike those two screens, nothing here needs a
 * guaranteed-stable seam between the nav bar and this list -- the
 * Subtitles toggle row above it (always composed, never scrolls) is what
 * firstContentFocusRequester below points to instead.
 */
@Composable
fun SubtitleSettingsScreen(
    onNavigate: (String) -> Unit,
    viewModel: SubtitleSettingsViewModel = viewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val navFocusRequester = remember { FocusRequester() }
    val subtitlesToggleFocusRequester = remember { FocusRequester() }

    SettingsScaffold(
        title = "Subtitles",
        onNavigate = onNavigate,
        navFocusRequester = navFocusRequester,
        firstContentFocusRequester = subtitlesToggleFocusRequester,
        titleIcon = Icons.Filled.Subtitles
    ) {
        SubtitlesToggleRow(
            enabled = preferences.subtitlesEnabled,
            onToggle = viewModel::setSubtitlesEnabled,
            focusRequester = subtitlesToggleFocusRequester,
            focusUp = navFocusRequester
        )

        Spacer(Modifier.height(28.dp))

        Text(text = "Default Language", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Used to automatically pick a matching subtitle track when Subtitles is on.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(14.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(SubtitleLanguageOptions, key = { it.code ?: "system_default" }) { option ->
                LanguageOptionRow(
                    label = option.label,
                    selected = option.code == preferences.defaultSubtitleLanguage,
                    onClick = { viewModel.setDefaultSubtitleLanguage(option.code) }
                )
            }
        }
    }
}

@Composable
private fun SubtitlesToggleRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = { onToggle(!enabled) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        // Same wide-element-safe scale as Home Rows'/Genres' full-width rows.
        focusedScale = 1.02f,
        backgroundColor = MangoSurface,
        focusRequester = focusRequester,
        focusUp = focusUp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subtitles",
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
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
        backgroundColor = MangoSurface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(imageVector = Icons.Filled.Check, contentDescription = "Selected", tint = MangoAmber)
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}
