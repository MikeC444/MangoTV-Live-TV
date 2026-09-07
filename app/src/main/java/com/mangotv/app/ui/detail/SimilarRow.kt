package com.mangotv.app.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewResponder
import androidx.compose.foundation.relocation.bringIntoViewResponder
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.ui.components.TvFocusSurface
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextTertiary

private val SimilarCardWidth = 160.dp
private val SimilarCardHeight = 96.dp

/**
 * A smaller, landscape "You May Also Like" row for the compact movie detail
 * page, with the title/year overlaid on the artwork instead of the taller
 * poster-plus-text-below layout ContentRow/ContentCard use elsewhere. Not
 * reused for anything else — Home's rows and the TV show detail page keep
 * ContentRow exactly as before.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimilarRow(
    title: String,
    items: List<Content>,
    onItemClick: (Content) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateUpPastRow: (() -> Unit)? = null
) {
    var rowSize by remember { mutableStateOf(IntSize.Zero) }

    // Same fix as ContentRow: report this row's own fixed bounds to the
    // outer (vertical) list instead of the focused card's shifting sub-rect
    // (which changes slightly as each card scale-animates on focus), so the
    // page doesn't nudge vertically while browsing this row horizontally.
    val rowBringIntoViewResponder = remember {
        object : BringIntoViewResponder {
            override fun calculateRectForParent(localRect: Rect): Rect {
                return Rect(0f, 0f, rowSize.width.toFloat(), rowSize.height.toFloat())
            }

            override suspend fun bringChildIntoView(localRect: () -> Rect?) {
                // No-op — see ContentRow for the full rationale.
            }
        }
    }

    Column(
        modifier = modifier
            .onSizeChanged { rowSize = it }
            .bringIntoViewResponder(rowBringIntoViewResponder)
    ) {
        Text(
            text = title,
            color = TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(
                horizontal = MangoDimens.ScreenPaddingHorizontal,
                vertical = 6.dp
            )
        )
        LazyRow(
            modifier = if (onNavigateUpPastRow != null) {
                Modifier.onPreviewKeyEvent { event ->
                    if (event.key == Key.DirectionUp) {
                        if (event.type == KeyEventType.KeyDown) {
                            onNavigateUpPastRow()
                        }
                        true
                    } else {
                        false
                    }
                }
            } else {
                Modifier
            },
            contentPadding = PaddingValues(horizontal = MangoDimens.ScreenPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(items, key = { it.id }) { content ->
                SimilarCard(content = content, onClick = { onItemClick(content) })
            }
        }
    }
}

@Composable
private fun SimilarCard(content: Content, onClick: () -> Unit) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(MangoDimens.CardCornerRadius),
        backgroundColor = MangoSurface,
        modifier = Modifier
            .width(SimilarCardWidth)
            .height(SimilarCardHeight),
        bringIntoViewOnFocus = false
    ) {
        AsyncImage(
            model = rememberOpaqueImageRequest(content.backdropUrl ?: content.posterUrl),
            contentDescription = content.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                    )
                )
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        ) {
            Text(
                text = content.title,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            content.year?.let {
                Text(
                    text = it.toString(),
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}
