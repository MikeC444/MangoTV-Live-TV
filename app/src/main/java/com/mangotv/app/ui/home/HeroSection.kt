package com.mangotv.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.mangotv.app.data.model.Content
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.components.rememberOpaqueImageRequest
import com.mangotv.app.ui.theme.MangoBackground
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.MangoMotion
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester

private const val HERO_ROTATE_MILLIS = 9000L

private val HeroTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.85f),
    offset = Offset(0f, 2f),
    blurRadius = 10f
)

@Composable
fun HeroSection(
    items: List<Content>,
    playFocusRequester: FocusRequester,
    onPlay: (Content) -> Unit,
    onAddToList: (Content) -> Unit,
    onMoreInfo: (Content) -> Unit,
    modifier: Modifier = Modifier,
    navUpFocusRequester: FocusRequester? = null,
    onNavigateUpPastHero: () -> Unit = {},
    onNavigateDownFromHero: () -> Unit = {},
    savedIds: Set<String> = emptySet()
) {
    if (items.isEmpty()) return

    var index by remember { mutableIntStateOf(0) }
    val current = items[index % items.size]

    LaunchedEffect(items) {
        if (items.size <= 1) return@LaunchedEffect
        while (true) {
            delay(HERO_ROTATE_MILLIS)
            index = (index + 1) % items.size
        }
    }

    // The backdrop's minimum cinematic height, as a floor rather than a
    // fixed height: title/description length varies, and a fixed/exact
    // height risks the bottom-aligned text column needing more room than
    // that — since Box doesn't clip by default, it would silently overflow
    // above the box's own top edge, invisible and unreachable by scrolling.
    // matchParentSize() below defers the backdrop's size to whatever the
    // text column actually needs (at least this floor, more if required),
    // rather than the other way around.
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val heroMinHeight = screenHeightDp * 0.82f

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = heroMinHeight)
            // graphicsLayer's own clip (set on the backdrop's AsyncImage
            // below) only clips content to that layer's own shape, which
            // travels WITH the scaleX/scaleY transform — it does nothing to
            // stop the now-larger scaled layer from visually extending past
            // THIS Box's bounds, since Box doesn't clip its children by
            // default. Clipping has to happen here, at the parent, so the
            // Ken Burns zoom stays contained within the hero regardless of
            // scale.
            .clipToBounds()
    ) {
        Crossfade(
            targetState = current,
            animationSpec = tween(MangoMotion.HeroCrossfadeMillis),
            label = "heroBackdrop",
            modifier = Modifier.matchParentSize()
        ) { item ->
            KenBurnsBackdrop(url = item.backdropUrl)
        }

        // A much lighter left-to-right gradient than before — just enough
        // of an assist that text stays readable, without darkening the
        // artwork into "dark spots" across most of the hero. Text itself
        // also carries a drop shadow (HeroTextShadow) as the primary
        // legibility mechanism now, so this can stay subtle.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MangoBackground.copy(alpha = 0.55f),
                            MangoBackground.copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Bottom fade so the hero blends into the row content below. This
        // now ramps all the way to a fully opaque MangoBackground at the
        // very bottom edge, matching the solid background the "Popular"
        // row sits on — previously it topped out at 0.4 alpha, so the
        // backdrop was still partly visible right up to the hero's bottom
        // edge and cut off abruptly instead of blending in.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            MangoBackground.copy(alpha = 0.15f),
                            MangoBackground.copy(alpha = 0.6f),
                            MangoBackground
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = MangoDimens.ScreenPaddingHorizontal,
                    end = MangoDimens.ScreenPaddingHorizontal,
                    bottom = 56.dp
                )
                .widthIn(max = 760.dp),
            // The column can be measured taller than its content needs (it's
            // floored to heroMinHeight above) — pack content to the bottom
            // of that space so it stays visually pinned to the hero's
            // bottom edge instead of stranding a gap below the buttons.
            verticalArrangement = Arrangement.Bottom
        ) {
            // Prefer the addon-supplied clearlogo (a stylized title graphic,
            // the way Stremio/Nuvio render hero titles) when one's
            // available, falling back to plain text otherwise — catalog
            // preview responses don't always carry a logo the way a full
            // meta fetch does, so this is frequently the fallback path here
            // even when it isn't on the detail page.
            if (current.logoUrl != null) {
                // A fully fixed box (not height-plus-max-width) so the
                // rendered logo is always the same footprint regardless of
                // the source image's own aspect ratio — a height-plus-
                // widthIn(max) combination let a wide logo render far
                // beyond the intended cap.
                AsyncImage(
                    model = current.logoUrl,
                    contentDescription = current.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier.size(width = 380.dp, height = 90.dp)
                )
            } else {
                Text(
                    text = current.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.displayMedium.copy(shadow = HeroTextShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(14.dp))

            Row {
                val metaParts = buildList {
                    current.year?.let { add(it.toString()) }
                    current.ageRating?.let { add(it) }
                    current.runtimeMinutes?.let { add("${it / 60}h ${it % 60}m") }
                    current.rating?.let { add("★ ${"%.1f".format(it)}") }
                }
                // Sized down from the shared titleMedium/bodyMedium/bodyLarge
                // tokens via .copy() (font family/weight/letter-spacing still
                // come from them) rather than editing those tokens directly
                // in Type.kt -- this is scoped to the hero's own meta/genre/
                // description text specifically, not every other screen that
                // happens to use the same named styles.
                Text(
                    text = metaParts.joinToString("   •   "),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        shadow = HeroTextShadow
                    ),
                    fontWeight = FontWeight.Medium
                )
            }

            if (current.genres.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = current.genres.joinToString("  ·  ") { it.name },
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        shadow = HeroTextShadow
                    )
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = current.description,
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    shadow = HeroTextShadow
                ),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(28.dp))

            // Handling UP here (imperatively) rather than only through
            // focusProperties/focusUp: that mechanism points at a
            // FocusRequester, and if it ever targeted something inside the
            // lazily-composed list that's been scrolled far enough to be
            // disposed, using it throws. This scroll-then-focus path only
            // ever targets the always-composed nav bar overlay, so it can't
            // hit that. focusUp below still points at navUpFocusRequester
            // as a harmless fallback in case this doesn't consume the event.
            Row(
                modifier = Modifier.onPreviewKeyEvent { event ->
                    when {
                        // Consume BOTH the KeyDown and KeyUp phases of UP —
                        // leaving KeyUp unconsumed let it fall through to
                        // Compose's default focus-move handling (which
                        // appears to run its own scroll-into-view
                        // independent of bringIntoViewOnFocus), causing a
                        // second unwanted scroll after the imperative one
                        // below already ran on KeyDown.
                        event.key == Key.DirectionUp -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateUpPastHero()
                            }
                            true
                        }
                        // DOWN is intentionally NOT consumed: moving from
                        // the hero into the first content row is supposed
                        // to scroll (that's the one legitimate case), so
                        // default focus-move handling is left to do it.
                        // This only flags that the hero/nav "must stay
                        // static" region is being left, so HomeContent's
                        // watchdog stops correcting the list back to (0, 0).
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            onNavigateDownFromHero()
                            false
                        }
                        else -> false
                    }
                }
            ) {
                // bringIntoViewOnFocus = false on all three: the hero is
                // sized to always fit within one viewport (see heroMinHeight
                // above), so these buttons are already fully visible
                // whenever the list is at the top — automatic scroll-into-
                // view has nothing legitimate to do here and was instead
                // firing on stale/pre-layout coordinates right after the
                // explicit scrollToItem(0, 0) in onNavigateUpPastHero,
                // causing a second, unwanted scroll. Returning to the hero
                // from a scrolled-down content row is handled explicitly by
                // ContentRow's onNavigateUpPastRow instead.
                MangoButton(
                    text = "Play",
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay(current) },
                    style = MangoButtonStyle.LIGHT,
                    focusRequester = playFocusRequester,
                    focusUp = navUpFocusRequester,
                    bringIntoViewOnFocus = false
                )
                Spacer(Modifier.width(16.dp))
                val isSaved = current.id in savedIds
                HeroIconButton(
                    icon = if (isSaved) Icons.Filled.Check else Icons.Filled.Add,
                    contentDescription = if (isSaved) "Remove from My List" else "Add to My List",
                    onClick = { onAddToList(current) },
                    focusUp = navUpFocusRequester
                )
                Spacer(Modifier.width(16.dp))
                HeroIconButton(
                    icon = Icons.Filled.Info,
                    contentDescription = "More Info",
                    onClick = { onMoreInfo(current) },
                    focusUp = navUpFocusRequester
                )
            }
        }
    }
}

@Composable
private fun KenBurnsBackdrop(url: String?, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "kenBurns")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(MangoMotion.HeroKenBurnsMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kenBurnsScale"
    )
    // The backdrop shows nothing until it loads (no placeholder), so
    // animating its scale before then is both pointless and, now that Home
    // can paint from cache the instant the app opens, a continuous
    // per-frame graphicsLayer transform competing with every other
    // now-simultaneously-loading row image for the same cold-boot window.
    // Deferring the transform until there's an actual image on screen
    // fixes both at once.
    var isLoaded by remember(url) { mutableStateOf(false) }

    AsyncImage(
        model = rememberOpaqueImageRequest(url),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onState = { state -> if (state is AsyncImagePainter.State.Success) isLoaded = true },
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                val appliedScale = if (isLoaded) scale else 1f
                scaleX = appliedScale
                scaleY = appliedScale
            }
    )
}
