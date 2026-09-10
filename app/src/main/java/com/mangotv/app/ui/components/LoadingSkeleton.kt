package com.mangotv.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.browse.GRID_COLUMNS
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurfaceHigh
import com.mangotv.app.ui.theme.TextPrimary

@Composable
fun HomeLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp),
            shape = RoundedCornerShape(0.dp)
        )
        Spacer(Modifier.height(32.dp))
        repeat(3) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                        .width(180.dp)
                        .height(20.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
                ) {
                    repeat(6) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(MangoDimens.PosterWidth)
                                .height(MangoDimens.PosterHeight)
                        )
                    }
                }
            }
        }
    }
}

/**
 * A hero-less sibling of [HomeLoadingSkeleton] for screens that are just a
 * stack of rows under the nav bar (Movies, TV Shows, Genre Results) — same
 * shimmer-row shape, minus the big hero block Home has and these don't.
 * Padded to clear the (always-overlaid) nav bar rather than living inside a
 * scaffold, matching how those screens themselves are structured.
 */
@Composable
fun RowsLoadingSkeleton(modifier: Modifier = Modifier, rowCount: Int = 4) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground)
            .padding(top = MangoDimens.NavBarHeight + 24.dp)
    ) {
        repeat(rowCount) {
            Column(modifier = Modifier.padding(bottom = 32.dp)) {
                ShimmerBox(
                    modifier = Modifier
                        .padding(horizontal = MangoDimens.ScreenPaddingHorizontal)
                        .width(180.dp)
                        .height(20.dp)
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.padding(horizontal = MangoDimens.ScreenPaddingHorizontal),
                    horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
                ) {
                    repeat(6) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(MangoDimens.PosterWidth)
                                .height(MangoDimens.PosterHeight)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Grid-shaped sibling of [RowsLoadingSkeleton] for screens that render their
 * loaded content as a vertical poster grid rather than horizontal shelves
 * (Movies, TV Shows, Genre Results -- see RowsBrowseLayout.GRID). Mirrors
 * RowsBrowseGridContent's own structure -- same screen title, same
 * width-driven posterScale computation using the same [GRID_COLUMNS], same
 * padding/spacing -- so the transition from skeleton to real content is a
 * simple crossfade of poster art rather than the whole layout re-flowing
 * from stacked horizontal rows into a grid.
 */
@Composable
fun GridLoadingSkeleton(screenTitle: String, modifier: Modifier = Modifier, rowCount: Int = 3) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MangoBackground)
    ) {
        val availableWidth = maxWidth - MangoDimens.ScreenPaddingHorizontal * 2
        val cardWidth = (availableWidth - MangoDimens.CardSpacing * (GRID_COLUMNS - 1)) / GRID_COLUMNS
        val posterScale = (cardWidth / MangoDimens.PosterWidth).coerceIn(0.3f, 1f)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = MangoDimens.NavBarHeight + 24.dp)
        ) {
            Text(
                text = screenTitle,
                color = TextPrimary,
                style = MaterialTheme.typography.displayMedium,
                modifier = Modifier.padding(
                    horizontal = MangoDimens.ScreenPaddingHorizontal,
                    vertical = 4.dp
                )
            )
            repeat(rowCount) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = MangoDimens.ScreenPaddingHorizontal,
                        vertical = MangoDimens.RowSpacing / 2
                    ),
                    horizontalArrangement = Arrangement.spacedBy(MangoDimens.CardSpacing)
                ) {
                    repeat(GRID_COLUMNS) {
                        ShimmerBox(
                            modifier = Modifier
                                .width(MangoDimens.PosterWidth * posterScale)
                                .height(MangoDimens.PosterHeight * posterScale)
                        )
                    }
                }
            }
        }
    }
}

/** Reused by other screens' own loading skeletons (e.g. Sources), not just this one. */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(MangoDimens.CardCornerRadius)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .clip(shape)
            .background(MangoSurfaceHigh.copy(alpha = alpha))
    )
}
