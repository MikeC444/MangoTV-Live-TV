package com.mangotv.app.ui.livetv

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.livetv.Channel
import com.mangotv.app.data.livetv.ChannelSection
import com.mangotv.app.data.livetv.NowNext
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary

/** A single horizontal rail of channels ("Featured", a country, a category) — structurally ContentRow's own layout adapted to Channel instead of Content. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    section: ChannelSection,
    getNowNext: (Channel) -> NowNext?,
    onItemClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateUpPastRow: (() -> Unit)? = null,
    onFocusChanged: (Boolean) -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null
) {
    Column(modifier = modifier.onFocusChanged { onFocusChanged(it.hasFocus) }) {
        Text(
            text = section.title,
            color = TextPrimary,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal, vertical = 12.dp)
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides MangoMotion.FastBringIntoViewSpec) {
            LazyRow(
                modifier = if (onNavigateUpPastRow != null) {
                    Modifier.onPreviewKeyEvent { event ->
                        if (event.key == Key.DirectionUp) {
                            if (event.type == KeyEventType.KeyDown) onNavigateUpPastRow()
                            true
                        } else {
                            false
                        }
                    }
                } else {
                    Modifier
                },
                contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
                horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
            ) {
                itemsIndexed(section.channels, key = { _, channel -> channel.id }) { index, channel ->
                    ChannelCard(
                        channel = channel,
                        nowNext = getNowNext(channel),
                        onClick = { onItemClick(channel) },
                        focusRequester = if (index == 0) firstItemFocusRequester else null
                    )
                }
            }
        }
    }
}
