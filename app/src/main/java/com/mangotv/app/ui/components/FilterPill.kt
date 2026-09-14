package com.mangotv.app.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * A single rounded filter chip -- originally SourceFilterBar's own private
 * composable, lifted here once My List's own filter bar (All/Watched)
 * needed the exact same look rather than a second, subtly-different copy.
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    val contentColor = when {
        selected -> MangoBackground
        focused -> TextPrimary
        else -> TextSecondary
    }
    TvFocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(percent = 50),
        backgroundColor = if (selected) MangoAmber else MangoSurfaceHigh,
        focusRequester = focusRequester,
        onFocusChanged = { focused = it },
        bringIntoViewOnFocus = false
    ) {
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (focused || selected) FontWeight.Bold else FontWeight.Medium,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
        )
    }
}
