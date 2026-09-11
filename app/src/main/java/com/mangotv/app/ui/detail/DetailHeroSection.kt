package com.mangotv.app.ui.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mangotv.app.data.history.ContinueWatchingEntry
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.model.ContentType
import com.mangotv.app.data.model.Episode
import com.mangotv.app.ui.components.HeroIconButton
import com.mangotv.app.ui.components.MangoButton
import com.mangotv.app.ui.components.MangoButtonStyle
import com.mangotv.app.ui.theme.MangoAmber
import com.mangotv.app.ui.theme.MangoDimens
import com.mangotv.app.ui.theme.TextPrimary
import com.mangotv.app.ui.theme.TextSecondary
import com.mangotv.app.ui.theme.TextTertiary

/**
 * Detail screen's hero content — title/meta/description/actions bottom-left,
 * an IMDb-style rating badge bottom-right — layered transparently over
 * DetailScreen's fixed, full-screen backdrop (see KenBurnsBackdrop there).
 * This composable only reserves the vertical space and renders the text/
 * buttons; it no longer owns any backdrop image of its own. Focus handling
 * at the nav<->hero seam (imperative scroll-then-focus on UP, non-consuming
 * notify-only on DOWN) mirrors HomeScreen's proven setup.
 */
private val HeroTextShadow = Shadow(
    color = Color.Black.copy(alpha = 0.85f),
    offset = Offset(0f, 2f),
    blurRadius = 10f
)

@Composable
fun DetailHeroSection(
    content: Content,
    playFocusRequester: FocusRequester,
    onPlay: (Episode?) -> Unit,
    onWatched: () -> Unit,
    onWatchlist: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    navUpFocusRequester: FocusRequester? = null,
    onNavigateUpPastHero: () -> Unit = {},
    onNavigateDownFromHero: () -> Unit = {},
    isInMyList: Boolean = false,
    // Movie detail only, for now: shrinks everything except the title so
    // the whole page (hero + Cast + You May Also Like) fits on one screen
    // without scrolling. TV shows don't pass this and are unaffected.
    compact: Boolean = false,
    // This exact title's Continue Watching entry, if any -- null for a
    // title that's never been played, or one that's since been finished.
    // Swaps "Play" for "Resume" and, for a TV show, retargets which
    // episode the button plays. See its own doc further down for why the
    // TV show episode target matters beyond just the button label.
    resumeEntry: ContinueWatchingEntry? = null
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val heroMinHeight = if (compact) {
        // Reserve just enough fixed space below the hero for the Cast /
        // You May Also Like row (~130dp) plus the bottom spacer (16dp),
        // and give the hero everything else — rather than a fixed
        // fraction of the screen, this scales with the actual screen
        // height so the hero (and therefore where description/buttons/
        // Cast end up) stretches to fill exactly what's left, with Cast /
        // You May Also Like landing right at the bottom instead of
        // sitting high up with dead space beneath it.
        (screenHeightDp - 176.dp).coerceAtLeast(320.dp)
    } else {
        screenHeightDp * 0.82f
    }
    val bottomPadding = if (compact) 24.dp else 56.dp

    // Watched/Watchlist stay hidden until the user opens them via the
    // three-dot button, then pop out next to Play instead of always
    // being on screen.
    var actionsExpanded by remember { mutableStateOf(false) }

    // TV shows show which episode Play/Resume will start. resumeEntry (a
    // Continue Watching hit for this exact show) takes priority over the
    // plain "first episode of the first season" default -- this isn't just
    // cosmetic: SourcesViewModel only auto-continues on the same source
    // the user was watching when the season/episode it's asked to play
    // exactly matches the stored resume point (see its own doc), so
    // defaulting back to episode 1 here would silently break auto-resume
    // for every show beyond its first episode, even though the button
    // itself would still say "Resume". Falls back to the first episode if
    // the resume entry's season/episode can't be found in this show's own
    // season data (e.g. stale data after a re-fetch) rather than pointing
    // Play at nothing. Movies need no episode at all -- resumeEntry alone
    // (matched on providerId/contentId/contentType, with no season/episode
    // to compare) already drives their own Play -> Resume swap below.
    val resumeEpisode = resumeEntry?.takeIf { it.seasonNumber != null && it.episodeNumber != null }?.let { entry ->
        content.seasons.find { it.seasonNumber == entry.seasonNumber }
            ?.episodes?.find { it.episodeNumber == entry.episodeNumber }
    }
    val firstEpisode = if (content.type == ContentType.TV_SHOW) {
        resumeEpisode ?: content.seasons.firstOrNull()?.episodes?.firstOrNull()
    } else {
        null
    }
    val actionLabel = if (resumeEntry != null) "Resume" else "Play"
    val playButtonText = if (content.type == ContentType.TV_SHOW) {
        if (firstEpisode != null) {
            "$actionLabel S${firstEpisode.seasonNumber}E${firstEpisode.episodeNumber}"
        } else {
            actionLabel
        }
    } else {
        actionLabel
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = heroMinHeight)
    ) {
        content.rating?.let { rating ->
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = MangoDimens.ScreenPaddingHorizontal, bottom = bottomPadding)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(
                        horizontal = if (compact) 14.dp else 20.dp,
                        vertical = if (compact) 8.dp else 14.dp
                    )
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MangoAmber,
                        modifier = Modifier.height(if (compact) 16.dp else 22.dp)
                    )
                    Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
                    Column {
                        Text(
                            text = "%.1f".format(rating),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge
                        )
                        Text(
                            text = "IMDb Rating",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .let { if (compact) it.fillMaxSize() else it }
                .padding(
                    start = MangoDimens.ScreenPaddingHorizontal,
                    end = MangoDimens.ScreenPaddingHorizontal,
                    // Compact: title/meta sit at a fixed distance from the
                    // top of the (now much taller) hero, clearing the nav
                    // bar overlay above it, and stay put regardless of how
                    // tall the hero grows — the flexible spacer further
                    // down absorbs the change, so moving this doesn't shift
                    // description/buttons/Cast below. Non-compact:
                    // unchanged — the whole block is still bottom-anchored
                    // as one piece.
                    top = if (compact) 95.dp else 0.dp,
                    bottom = bottomPadding
                )
                .widthIn(max = 780.dp),
            verticalArrangement = if (compact) Arrangement.Top else Arrangement.Bottom
        ) {
            // Prefer the addon-supplied clearlogo (a stylized title
            // graphic, the way Stremio/Nuvio render it) over plain text
            // when the full meta fetch provided one. A fully fixed box
            // (not height-plus-max-width) so the rendered logo is always
            // the same footprint regardless of the source image's own
            // aspect ratio — a height-plus-widthIn(max) combination let a
            // wide logo render far beyond the intended cap, spanning
            // almost the full screen width. On compact, a small negative
            // x-offset compensates for the transparent margin most logo
            // PNGs carry around their visible artwork, so it reads as
            // flush with the left edge like the other text below it.
            if (content.logoUrl != null) {
                AsyncImage(
                    model = content.logoUrl,
                    contentDescription = content.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .size(
                            width = if (compact) 420.dp else 380.dp,
                            height = if (compact) 116.dp else 100.dp
                        )
                        .let { if (compact) it.offset(x = (-16).dp) else it }
                )
            } else {
                Text(
                    text = content.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.displayLarge.copy(shadow = HeroTextShadow),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(if (compact) 8.dp else 14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val metaStyle = (if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium)
                    .copy(shadow = HeroTextShadow)
                val leadingParts = buildList {
                    content.year?.let { add(it.toString()) }
                    content.runtimeMinutes?.let { add("${it / 60}h ${it % 60}m") }
                }
                if (leadingParts.isNotEmpty()) {
                    Text(
                        text = leadingParts.joinToString("   "),
                        color = Color.White,
                        style = metaStyle,
                        fontWeight = FontWeight.Medium
                    )
                }
                content.ageRating?.let { rating ->
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .border(BorderStroke(1.dp, TextTertiary), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(text = rating, color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (content.genres.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = content.genres.joinToString("  •  ") { it.name },
                        color = Color.White,
                        style = metaStyle,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (compact) {
                // Flexible gap: absorbs whatever room is left between the
                // meta row and the description/buttons below, so those
                // stay pinned to the bottom of the (now taller) hero
                // instead of trailing right behind the meta row — title
                // and meta above are unaffected since they're above this
                // spacer, not part of what it pushes down.
                Spacer(Modifier.weight(1f))
            }

            if (content.description.isNotBlank()) {
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
                Text(
                    text = content.description,
                    color = Color.White,
                    style = (if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge)
                        .copy(shadow = HeroTextShadow),
                    maxLines = if (compact) 2 else 4,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(if (compact) 16.dp else 28.dp))

            // Same imperative UP/DOWN handling as HeroSection: UP is fully
            // consumed (both KeyDown/KeyUp) and drives an explicit
            // scroll-then-focus back to the nav bar; DOWN is left
            // unconsumed (just notifies) so default focus-move handling
            // still carries focus into the content below.
            Row(
                modifier = Modifier.onPreviewKeyEvent { event ->
                    when {
                        event.key == Key.DirectionUp -> {
                            if (event.type == KeyEventType.KeyDown) {
                                onNavigateUpPastHero()
                            }
                            true
                        }
                        event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown -> {
                            onNavigateDownFromHero()
                            false
                        }
                        else -> false
                    }
                },
                verticalAlignment = Alignment.CenterVertically
            ) {
                MangoButton(
                    text = playButtonText,
                    icon = Icons.Filled.PlayArrow,
                    onClick = { onPlay(firstEpisode) },
                    style = MangoButtonStyle.LIGHT,
                    focusRequester = playFocusRequester,
                    focusUp = navUpFocusRequester,
                    bringIntoViewOnFocus = false,
                    compact = compact
                )
                Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
                AnimatedVisibility(
                    visible = actionsExpanded,
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HeroIconButton(
                            icon = Icons.Outlined.CheckCircle,
                            contentDescription = "Mark as watched",
                            onClick = onWatched,
                            focusUp = navUpFocusRequester,
                            compact = compact
                        )
                        Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
                        HeroIconButton(
                            icon = if (isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = if (isInMyList) "Remove from Watchlist" else "Add to Watchlist",
                            onClick = onWatchlist,
                            focusUp = navUpFocusRequester,
                            compact = compact
                        )
                        Spacer(Modifier.width(if (compact) 10.dp else 16.dp))
                    }
                }
                HeroIconButton(
                    icon = Icons.Filled.MoreVert,
                    contentDescription = "More options",
                    onClick = {
                        actionsExpanded = !actionsExpanded
                        onMore()
                    },
                    focusUp = navUpFocusRequester,
                    compact = compact
                )
            }
        }
    }
}
