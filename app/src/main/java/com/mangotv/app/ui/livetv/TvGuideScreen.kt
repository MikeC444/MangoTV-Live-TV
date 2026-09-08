package com.mangotv.app.ui.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.config.LiveTvConfig
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.EpgDiagnostics
import com.mangotv.app.data.livetv.TimelineBlock
import com.mangotv.app.navigation.routeForNavLabel
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.home.MangoNavItems
import com.mangotv.app.ui.home.TopNavBar
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ChannelColumnWidth = 220.dp
private val RowHeight = 64.dp
private val PixelsPerMinuteDp = 3.dp
private const val HALF_HOUR_MS = 30 * 60_000L

private data class FocusedGuideEntry(val channel: Channel, val block: TimelineBlock)

/**
 * A Freeview/Sky-style grid TV guide: a fixed channel list on the left, a
 * shared-horizontal-scroll timeline of programme blocks on the right for
 * each channel, an hour ruler, and a header showing whichever
 * programme/channel currently holds focus. This is the primary Live TV
 * experience once unlocked — see LiveTvChannelsScreen.
 *
 * No multi-day paging (channels only show [windowStart, windowEnd), driven
 * by LiveTvViewModel.guideWindow — currently ~2h back to ~12h ahead of
 * "now", matching exactly what EpgRepository keeps parsed in memory) and no
 * live video preview thumbnail (a static channel logo instead) — both
 * would add real complexity/risk (a second live player instance to manage,
 * or a day-boundary paging model) for comparatively little value over
 * actually being able to browse the schedule and tune a channel, which is
 * the core of what a TV guide needs to do.
 *
 * Every focus/scroll mechanism here (nav<->grid seam, explicit scroll-to-
 * center) deliberately mirrors RowsBrowseLoadedContent/LiveTvChannelsScreen's
 * already-proven pattern rather than inventing a new one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvGuideScreen(
    channels: List<Channel>,
    regionLabel: String,
    windowStart: Long,
    windowEnd: Long,
    epgVersion: Int,
    epgDiagnostics: EpgDiagnostics,
    getBlocks: (Channel) -> List<TimelineBlock>,
    onTuneToChannel: (Channel) -> Unit,
    onChangeRegion: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val density = LocalDensity.current
    val pixelsPerMinutePx = remember(density) { with(density) { PixelsPerMinuteDp.toPx() } }
    val sharedHScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val navFocusRequester = remember { FocusRequester() }
    val changeRegionFocusRequester = remember { FocusRequester() }
    val nowFocusRequester = remember { FocusRequester() }
    var navRegionFocused by remember { mutableStateOf(true) }
    var hasRequestedInitialFocus by remember { mutableStateOf(false) }
    var focusedEntry by remember { mutableStateOf<FocusedGuideEntry?>(null) }

    val now = remember { System.currentTimeMillis() }

    // Only computed for the first channel -- purely so the nav bar's DOWN
    // key and the initial focus request have a deterministic, useful
    // target (the live block), not literally index 0 of the day.
    val firstChannelBlocks = remember(channels, windowStart, windowEnd, epgVersion) {
        channels.firstOrNull()?.let(getBlocks) ?: emptyList()
    }
    val firstChannelNowIndex = remember(firstChannelBlocks) {
        firstChannelBlocks.indexOfFirst { now in it.startEpochMs until it.stopEpochMs }.coerceAtLeast(0)
    }
    val defaultEntry = remember(channels, firstChannelBlocks, firstChannelNowIndex) {
        channels.firstOrNull()?.let { channel ->
            FocusedGuideEntry(channel, firstChannelBlocks.getOrNull(firstChannelNowIndex) ?: TimelineBlock(null, windowStart, windowEnd))
        }
    }

    LaunchedEffect(Unit) {
        if (!hasRequestedInitialFocus) {
            hasRequestedInitialFocus = true
            runCatching { navFocusRequester.requestFocus() }
        }
        // Scroll the timeline so "now" starts a little in from the left
        // edge rather than flush against it.
        val leadInPx = with(density) { 24.dp.toPx() }
        val targetPx = (((now - windowStart) / 60_000f) * pixelsPerMinutePx - leadInPx).toInt().coerceAtLeast(0)
        sharedHScroll.scrollTo(targetPx)
    }

    val navScrollLock = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (navRegionFocused) available else Offset.Zero
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                if (navRegionFocused && (index != 0 || offset != 0)) listState.scrollToItem(0, 0)
            }
    }

    Box(Modifier.fillMaxSize().background(MangoBackground)) {
        Column(Modifier.fillMaxSize().padding(top = MangoDimens.NavBarHeight)) {
            GuideTitleBar(
                regionLabel = regionLabel,
                epgDiagnostics = epgDiagnostics,
                onChangeRegion = onChangeRegion,
                changeRegionFocusRequester = changeRegionFocusRequester,
                changeRegionFocusUp = navFocusRequester,
                changeRegionFocusDown = nowFocusRequester
            )
            GuideHeader(focusedEntry ?: defaultEntry)
            TimelineHeaderRow(windowStart = windowStart, windowEnd = windowEnd, pixelsPerMinutePx = pixelsPerMinutePx, sharedHScroll = sharedHScroll)
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .nestedScroll(navScrollLock)
                    .weight(1f)
            ) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    val blocks = remember(channel.id, windowStart, windowEnd, epgVersion) { getBlocks(channel) }
                    GuideChannelRow(
                        channel = channel,
                        displayNumber = index + 1,
                        blocks = blocks,
                        now = now,
                        pixelsPerMinutePx = pixelsPerMinutePx,
                        sharedHScroll = sharedHScroll,
                        onBlockFocused = { block -> focusedEntry = FocusedGuideEntry(channel, block) },
                        onBlockClick = { onTuneToChannel(channel) },
                        firstBlockFocusRequester = if (index == 0) nowFocusRequester else null,
                        firstBlockIndexToFocus = if (index == 0) firstChannelNowIndex else -1,
                        onNavigateUpPastRow = if (index == 0) {
                            {
                                navRegionFocused = true
                                coroutineScope.launch {
                                    listState.scrollToItem(0, 0)
                                    runCatching { changeRegionFocusRequester.requestFocus() }
                                }
                            }
                        } else {
                            null
                        }
                    )
                }
            }
        }

        TopNavBar(
            transparentBackground = false,
            modifier = Modifier.align(Alignment.TopCenter),
            selectedIndex = MangoNavItems.indexOf("Live TV"),
            selectedItemFocusRequester = navFocusRequester,
            contentFocusRequester = changeRegionFocusRequester,
            onItemClick = { label -> routeForNavLabel(label)?.let(onNavigate) },
            onNavigateDown = {
                navRegionFocused = false
                coroutineScope.launch {
                    listState.scrollToItem(0, 0)
                    runCatching { changeRegionFocusRequester.requestFocus() }
                }
            }
        )
    }
}

@Composable
private fun GuideTitleBar(
    regionLabel: String,
    epgDiagnostics: EpgDiagnostics,
    onChangeRegion: () -> Unit,
    changeRegionFocusRequester: FocusRequester,
    changeRegionFocusUp: FocusRequester,
    changeRegionFocusDown: FocusRequester
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "TV GUIDE",
            color = TextSecondary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "• $regionLabel",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium
        )
        // See PremiumAccessScreen/LiveTvChannelsScreen -- visible on purpose
        // so LiveTvConfig.skipPaywallForTesting is never silently forgotten.
        if (LiveTvConfig.skipPaywallForTesting) {
            Spacer(Modifier.width(16.dp))
            Text(
                text = "TESTING — PAYWALL BYPASSED",
                color = MangoBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .background(MangoAmber, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = epgDiagnosticsLabel(epgDiagnostics),
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall
            )
        }
        Spacer(Modifier.weight(1f))
        TvFocusSurface(
            onClick = onChangeRegion,
            shape = RoundedCornerShape(6.dp),
            backgroundColor = MangoSurface,
            focusRequester = changeRegionFocusRequester,
            focusUp = changeRegionFocusUp,
            focusDown = changeRegionFocusDown,
            bringIntoViewOnFocus = false
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Filled.Public, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = "Change Region", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

/** Plain-text summary of EpgDiagnostics for the testing-only status line above -- see EpgRepository's own doc for why this exists. */
private fun epgDiagnosticsLabel(diagnostics: EpgDiagnostics): String = when (diagnostics) {
    is EpgDiagnostics.NotConfigured -> "EPG: not configured"
    is EpgDiagnostics.Loading -> "EPG: loading…"
    is EpgDiagnostics.Failed -> "EPG: failed — ${diagnostics.message}"
    is EpgDiagnostics.Ready -> "EPG: ${diagnostics.matchedChannelCount}/${diagnostics.knownChannelCount} channels matched, ${diagnostics.programmeCount} programmes"
}

@Composable
private fun GuideHeader(entry: FocusedGuideEntry?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(56.dp).background(MangoSurface, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            val channel = entry?.channel
            if (channel != null) {
                ChannelLogo(
                    channel = channel,
                    modifier = Modifier.fillMaxSize(0.75f),
                    monogramStyle = MaterialTheme.typography.titleMedium
                )
            } else {
                Icon(imageVector = Icons.Filled.LiveTv, contentDescription = null, tint = TextTertiary)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry?.block?.programme?.title ?: "Live TV Guide",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry != null) {
                val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
                val metaLine = "${timeFormatter.format(Date(entry.block.startEpochMs))}–" +
                    "${timeFormatter.format(Date(entry.block.stopEpochMs))} • ${entry.block.durationMinutes} min"
                Text(text = metaLine, color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.channel.name,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TimelineHeaderRow(windowStart: Long, windowEnd: Long, pixelsPerMinutePx: Float, sharedHScroll: ScrollState) {
    val density = LocalDensity.current
    val halfHourWidthDp = remember(pixelsPerMinutePx) { with(density) { (30 * pixelsPerMinutePx).toDp() } }
    val marks = remember(windowStart, windowEnd) {
        buildList {
            var t = windowStart
            while (t < windowEnd) {
                add(t)
                t += HALF_HOUR_MS
            }
        }
    }
    val formatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Row(Modifier.fillMaxWidth().height(28.dp)) {
        Spacer(Modifier.width(ChannelColumnWidth))
        Row(Modifier.horizontalScroll(sharedHScroll)) {
            marks.forEach { t ->
                Box(Modifier.width(halfHourWidthDp), contentAlignment = Alignment.CenterStart) {
                    Text(text = formatter.format(Date(t)), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GuideChannelRow(
    channel: Channel,
    displayNumber: Int,
    blocks: List<TimelineBlock>,
    now: Long,
    pixelsPerMinutePx: Float,
    sharedHScroll: ScrollState,
    onBlockFocused: (TimelineBlock) -> Unit,
    onBlockClick: () -> Unit,
    firstBlockFocusRequester: FocusRequester?,
    firstBlockIndexToFocus: Int,
    onNavigateUpPastRow: (() -> Unit)?
) {
    Row(Modifier.fillMaxWidth().height(RowHeight)) {
        ChannelCell(channel = channel, displayNumber = displayNumber, modifier = Modifier.width(ChannelColumnWidth).fillMaxHeight())
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .horizontalScroll(sharedHScroll)
                .let { base ->
                    if (onNavigateUpPastRow != null) {
                        base.onPreviewKeyEvent { event ->
                            if (event.key == Key.DirectionUp) {
                                if (event.type == KeyEventType.KeyDown) onNavigateUpPastRow()
                                true
                            } else {
                                false
                            }
                        }
                    } else {
                        base
                    }
                }
        ) {
            blocks.forEachIndexed { index, block ->
                GuideBlock(
                    block = block,
                    now = now,
                    pixelsPerMinutePx = pixelsPerMinutePx,
                    onFocusChanged = { focused -> if (focused) onBlockFocused(block) },
                    onClick = onBlockClick,
                    focusRequester = if (firstBlockFocusRequester != null && index == firstBlockIndexToFocus) firstBlockFocusRequester else null
                )
            }
        }
    }
}

@Composable
private fun ChannelCell(channel: Channel, displayNumber: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = (channel.channelNumber ?: displayNumber).toString(),
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(28.dp)
        )
        ChannelLogo(channel = channel, modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = channel.name,
            color = TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun GuideBlock(
    block: TimelineBlock,
    now: Long,
    pixelsPerMinutePx: Float,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val density = LocalDensity.current
    val widthDp = remember(block.durationMinutes, pixelsPerMinutePx) {
        with(density) { (block.durationMinutes * pixelsPerMinutePx).toDp() }
    }
    val isLive = now in block.startEpochMs until block.stopEpochMs
    var focused by remember { mutableStateOf(false) }

    TvFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .width(widthDp)
            .fillMaxHeight()
            .padding(horizontal = 1.dp, vertical = 4.dp),
        shape = RoundedCornerShape(4.dp),
        backgroundColor = if (block.programme == null) MangoSurface.copy(alpha = 0.4f) else MangoSurface,
        onFocusChanged = { f -> focused = f; onFocusChanged(f) },
        bringIntoViewOnFocus = false,
        focusRequester = focusRequester,
        alwaysShowBorder = isLive,
        borderColor = if (isLive) MangoAmber else FocusBorder
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            if (isLive) {
                Text(
                    text = "LIVE",
                    color = MangoCoral,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = block.programme?.title ?: "No programme information",
                color = if (focused) TextPrimary else TextSecondary,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
