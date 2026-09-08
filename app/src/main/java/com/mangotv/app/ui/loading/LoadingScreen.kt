package com.mangotv.app.ui.loading

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mangotv.app.R
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary

/**
 * Branded cold-boot gate: shown once, in place of the real UI, the moment
 * the app opens -- see MangoNavHost, which renders this instead of the
 * NavHost until LoadingViewModel decides Home has something ready to show
 * with no visible image pop-in. Never shown again for the rest of the
 * process's lifetime (switching tabs, backgrounding/foregrounding, etc.
 * don't re-trigger it), matching "only on cold boot".
 */
@Composable
fun LoadingScreen(onReady: () -> Unit, viewModel: LoadingViewModel = viewModel()) {
    val isReady by viewModel.isReady.collectAsState()

    LaunchedEffect(isReady) {
        if (isReady) onReady()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Solid MangoBackground with two soft brand-color glows in
                // opposite corners, matching the reference design.
                drawRect(MangoBackground)
                val glowRadius = size.minDimension * 0.7f
                listOf(
                    Offset(0f, 0f) to MangoAmber,
                    Offset(size.width, size.height) to MangoCoral
                ).forEach { (corner, color) ->
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.30f), Color.Transparent),
                            center = corner,
                            radius = glowRadius
                        ),
                        radius = glowRadius,
                        center = corner
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.logo_mango),
                contentDescription = null,
                modifier = Modifier.size(180.dp)
            )
            Spacer(Modifier.height(24.dp))
            Row {
                Text(
                    text = "Mango",
                    color = TextPrimary,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "TV",
                    color = MangoAmber,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = "MOVIES   •   TV SHOWS   •   LIVE TV",
                color = TextSecondary,
                fontSize = 14.sp,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(
                color = MangoAmber,
                trackColor = MangoSurfaceHigh,
                strokeWidth = 3.dp,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Loading your entertainment...",
                color = TextSecondary,
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
