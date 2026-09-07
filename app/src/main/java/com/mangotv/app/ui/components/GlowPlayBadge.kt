package com.mangotv.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground

/**
 * A solid amber play circle with a soft glow behind it, built entirely from
 * plain background/gradient draws — no Modifier.blur() or colored-shadow
 * APIs, both of which are silent no-ops or look wrong below API 31/28 and
 * this app's minSdk is 23. Purely decorative: neither circle is focusable,
 * since every call site already sits inside a larger focusable row/card.
 */
@Composable
fun GlowPlayBadge(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    glowSize: Dp = size * 1.6f,
    iconSize: Dp = 18.dp
) {
    Box(modifier = modifier.size(glowSize), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(glowSize)
                .background(
                    Brush.radialGradient(listOf(MangoAmber.copy(alpha = 0.45f), Color.Transparent)),
                    CircleShape
                )
        )
        Box(
            modifier = Modifier
                .size(size)
                .background(MangoAmber, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MangoBackground,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
