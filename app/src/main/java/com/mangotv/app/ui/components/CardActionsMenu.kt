package com.mangotv.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mangotv.app.data.model.Content
import com.mangotv.app.data.provider.MyListRepository
import com.mangotv.app.data.sync.ContinueWatchingSyncRepository
import com.mangotv.app.navigation.MangoRoutes
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoBackgroundElevated
import com.mangotv.app.ui.theme.MangoCoral
import com.mangotv.app.ui.theme.MangoSurface
import com.mangotv.app.ui.theme.TextPrimary
import kotlinx.coroutines.launch

/**
 * Holds which card (if any) currently has its long-press actions menu open.
 * One instance lives for the whole app (provided by MangoNavHost via
 * [LocalCardActionsMenu]) so any ContentCard, however deeply nested inside
 * Home's rows or a browse grid, can open it with zero prop-threading --
 * the same reasoning LocalUiSoundPlayer already uses for a cross-cutting,
 * app-scoped concern. The menu itself renders once, at the NavHost root
 * (see CardActionsMenuOverlay), on top of whatever screen is showing.
 */
class CardActionsMenuState {
    var target: Content? by mutableStateOf(null)
        private set

    // Whether the overlay may move keyboard focus onto its own rows yet.
    // Starts false on every open() -- see TvFocusSurface's own
    // onLongClickKeyReleased doc for why stealing focus while the
    // triggering D-pad button is still physically held causes an unwanted
    // extra click. armFocus() is the signal that it's now safe, called
    // once the card that opened this menu observes that button's release.
    var canFocusActions: Boolean by mutableStateOf(false)
        private set

    // The FocusRequester of the card that opened this menu (handed back by
    // TvFocusSurface's onLongClickKeyReleased) -- not Compose state, since
    // it's only ever read imperatively from dismiss() below, never during
    // composition. Lets dismiss() return focus to that exact card instead
    // of wherever Compose's focus system falls back to once this overlay's
    // own focused row leaves composition (empirically, the top nav bar's
    // Home button, since that's this app's other default-focus target).
    private var originFocusRequester: FocusRequester? = null

    fun open(content: Content) {
        target = content
        canFocusActions = false
        originFocusRequester = null
    }

    fun armFocus(requester: FocusRequester) {
        canFocusActions = true
        originFocusRequester = requester
    }

    fun dismiss() {
        originFocusRequester?.let { runCatching { it.requestFocus() } }
        target = null
        canFocusActions = false
        originFocusRequester = null
    }
}

val LocalCardActionsMenu = staticCompositionLocalOf { CardActionsMenuState() }

private fun formatElapsed(positionMs: Long): String {
    val totalMinutes = (positionMs / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * The long-press quick-actions panel: a centered card showing the title's
 * own artwork alongside a short list of actions (Play/Resume, My List,
 * View Details, Remove from Continue Watching, Choose Source). Rendered
 * once at the NavHost root rather than per-row/per-grid, reading whichever
 * [Content] [state] currently points at -- see [CardActionsMenuState]'s own
 * doc for why. [onNavigate] is the same top-level nav callback MangoNavHost
 * already threads everywhere else.
 */
@Composable
fun CardActionsMenuOverlay(
    state: CardActionsMenuState,
    myListRepository: MyListRepository,
    continueWatchingSyncRepository: ContinueWatchingSyncRepository,
    onNavigate: (String) -> Unit,
    resolvePlayRoute: (Content) -> String,
    modifier: Modifier = Modifier
) {
    val content = state.target
    BackHandler(enabled = content != null) { state.dismiss() }
    if (content == null) return

    val coroutineScope = rememberCoroutineScope()
    val savedIds by myListRepository.items.collectAsStateWithLifecycle()
    val isInMyList = savedIds.any { it.id == content.id }
    val firstRowFocusRequester = remember(content.id) { FocusRequester() }

    // Gated on canFocusActions rather than firing as soon as content is set
    // -- see CardActionsMenuState's own doc for why taking focus early
    // (while the long-press button is still held) causes an unwanted click
    // on whichever row ends up focused.
    LaunchedEffect(content.id, state.canFocusActions) {
        if (state.canFocusActions) {
            runCatching { firstRowFocusRequester.requestFocus() }
        }
    }

    fun dismissAndNavigate(route: String) {
        state.dismiss()
        onNavigate(route)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .background(MangoBackgroundElevated, RoundedCornerShape(20.dp))
                .padding(20.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = rememberOpaqueImageRequest(content.posterUrl ?: content.backdropUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.widthIn(min = 260.dp)) {
                Text(
                    text = content.title,
                    color = TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(14.dp))

                val watchProgress = content.watchProgress
                val providerId = content.providerId
                if (watchProgress != null) {
                    CardActionRow(
                        icon = Icons.Filled.PlayArrow,
                        label = "Resume from ${formatElapsed(watchProgress.positionMs)}",
                        focusRequester = firstRowFocusRequester,
                        onClick = { if (providerId != null) dismissAndNavigate(resolvePlayRoute(content)) }
                    )
                } else {
                    CardActionRow(
                        icon = Icons.Filled.PlayArrow,
                        label = "Play",
                        focusRequester = firstRowFocusRequester,
                        onClick = { if (providerId != null) dismissAndNavigate(resolvePlayRoute(content)) }
                    )
                }
                CardActionRow(
                    icon = if (isInMyList) Icons.Filled.Check else Icons.Filled.Add,
                    label = if (isInMyList) "Remove from My List" else "Add to My List",
                    onClick = {
                        coroutineScope.launch { myListRepository.toggle(content) }
                        state.dismiss()
                    }
                )
                CardActionRow(
                    icon = Icons.Filled.Info,
                    label = "View Details",
                    onClick = {
                        if (providerId != null) {
                            dismissAndNavigate(MangoRoutes.detail(providerId, content.type, content.id))
                        }
                    }
                )
                if (watchProgress != null && providerId != null) {
                    CardActionRow(
                        icon = Icons.Filled.Delete,
                        label = "Remove from Continue Watching",
                        destructive = true,
                        onClick = {
                            continueWatchingSyncRepository.reportProgress(
                                providerId = providerId,
                                contentId = content.id,
                                contentType = content.type,
                                seasonNumber = watchProgress.seasonNumber,
                                episodeNumber = watchProgress.episodeNumber,
                                episodeTitle = watchProgress.episodeTitle,
                                title = content.title,
                                posterUrl = content.posterUrl,
                                backdropUrl = content.backdropUrl,
                                positionMs = watchProgress.positionMs,
                                durationMs = watchProgress.durationMs,
                                completed = true
                            )
                            state.dismiss()
                        }
                    )
                }
                if (providerId != null) {
                    CardActionRow(
                        icon = Icons.Filled.List,
                        label = "Choose Source",
                        onClick = {
                            dismissAndNavigate(
                                MangoRoutes.sources(
                                    providerId,
                                    content.type,
                                    content.id,
                                    watchProgress?.seasonNumber,
                                    watchProgress?.episodeNumber,
                                    skipAutoSelect = true
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    destructive: Boolean = false
) {
    TvFocusSurface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        backgroundColor = MangoSurface,
        focusRequester = focusRequester,
        borderColor = if (destructive) MangoCoral else FocusBorder,
        bringIntoViewOnFocus = false,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (destructive) MangoCoral else TextPrimary,
                modifier = Modifier.width(20.dp)
            )
            Spacer(Modifier.width(14.dp))
            Text(
                text = label,
                color = if (destructive) MangoCoral else TextPrimary,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
