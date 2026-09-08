package com.mangotv.app.ui.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/** [code] is null for the "All Channels" option -- every other option is a raw M3U tvg-country value (see Channel.country), uppercased, shown as-is since this app has no reliable country-code-to-name table to draw from. */
data class RegionOption(val code: String?, val displayName: String, val channelCount: Int)

// Shown first, in this order, right after "All Channels" -- everything
// else follows alphabetically. A fixed priority list rather than a
// generic "most channels first" heuristic since the point is surfacing
// specific regions the user actually watches, not whichever happens to be
// best-represented in a given playlist.
private val PRIORITY_REGION_CODES = listOf("UK", "US", "ZA")

/** Not capped, unlike LiveTvRepository's own per-country grouping (that one's for a bounded horizontal rail; this one is "give the user every channel in the region they pick"). */
fun regionOptionsFrom(channels: List<Channel>): List<RegionOption> {
    val byCountry = channels
        .filter { !it.country.isNullOrBlank() }
        .groupBy { it.country!!.trim().uppercase() }
        .map { (code, items) -> RegionOption(code = code, displayName = code, channelCount = items.size) }

    // mapNotNull rather than filtering byCountry by this order: a
    // playlist missing one of these (e.g. no ZA channels at all) just
    // omits it here instead of leaving a hole, since byCountry only ever
    // contains codes real channels actually reported.
    val priority = PRIORITY_REGION_CODES.mapNotNull { code -> byCountry.find { it.code == code } }
    val rest = byCountry.filterNot { it.code in PRIORITY_REGION_CODES }.sortedBy { it.code }

    val allOption = RegionOption(code = null, displayName = "All Channels", channelCount = channels.size)
    return listOf(allOption) + priority + rest
}

/**
 * Gate shown between the entitlement check and the TV guide: pick a region
 * once (remembered for the rest of this app session -- see
 * LiveTvViewModel.regionSelection) and only that region's channels populate
 * the guide. "All Channels" is always offered first, both for playlists
 * with no tvg-country data at all and for anyone who just wants everything.
 */
@Composable
fun RegionSelectScreen(
    channels: List<Channel>,
    onSelectRegion: (String?) -> Unit,
    onNavigate: (String) -> Unit
) {
    val regions = remember(channels) { regionOptionsFrom(channels) }
    val navFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { navFocusRequester.requestFocus() } }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        Column(Modifier.fillMaxSize().padding(top = MangoDimens.NavBarHeight + 24.dp)) {
            Text(
                text = "Choose a Region",
                color = TextPrimary,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Only channels from the region you pick will show in the TV guide. You can change this later from the guide.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
            )
            Spacer(Modifier.height(24.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 4.dp)
            ) {
                itemsIndexed(regions, key = { _, region -> region.code ?: "__all__" }) { index, region ->
                    RegionRow(
                        region = region,
                        onClick = { onSelectRegion(region.code) },
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        focusUp = if (index == 0) navFocusRequester else null,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )
                }
            }
        }

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Live TV"),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = firstItemFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) }
        )
    }
}

@Composable
private fun RegionRow(
    region: RegionOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null
) {
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        focusRequester = focusRequester,
        focusUp = focusUp,
        bringIntoViewOnFocus = true
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = region.displayName,
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${region.channelCount} channel${if (region.channelCount == 1) "" else "s"}",
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
