package com.mangotv.app.ui.components

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.mangotv.app.data.audio.LocalUiSoundPlayer
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Which sound (if any) a TvFocusSurface's click plays -- see TvFocusSurface's own doc. */
enum class ClickSound { DEFAULT, BACK, NONE }

// How long DPAD_CENTER/Enter/NumPadEnter must be held before it counts as a
// long click -- see the onLongClick param's own doc for why this needs its
// own key-based detection, separate from combinedClickable's.
private const val LongPressTimeoutMs = 500L

/**
 * The single building block behind every focusable tile in Mango TV (cards,
 * buttons, nav items). It owns the focus -> scale/glow/border animation so
 * every part of the app reacts to the D-pad the same way — at several
 * metres' viewing distance the focused element must always be obvious.
 *
 * Note: [Modifier.clickable] already makes its target focusable and reacts
 * to DPAD_CENTER/Enter when focused, so no separate `.focusable()` call is
 * needed here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TvFocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Held DPAD_CENTER/Enter/NumPadEnter, or a touch long-press -- null (the
    // default) leaves this surface exactly as before, a plain click target.
    // combinedClickable's own onLongClick below only ever fires for the
    // touch case (it's built on detectTapGestures, a pointer-input gesture
    // detector with no concept of a held key) -- on a real TV remote, with
    // no touchscreen, that alone never fires. The onPreviewKeyEvent block
    // further down implements the equivalent "held past a threshold"
    // detection for keys, so both input modes reach this callback.
    onLongClick: (() -> Unit)? = null,
    // Which click sound to play -- DEFAULT for virtually every caller (cards,
    // buttons, nav items); BACK for anything whose whole purpose is leaving
    // the current screen (an on-screen Back button); NONE for a caller that
    // handles its own feedback (none currently do, kept for completeness).
    // Threaded straight through by HeroIconButton/MangoButton so a caller
    // several layers up (e.g. PlayerTopBar's Back button) can still pick it.
    clickSound: ClickSound = ClickSound.DEFAULT,
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = MangoMotion.FocusScale,
    // RenderNode-level ambient/spot shadow (both default to black) cast by
    // the focus scale-up below. Fine, even desirable, on the cards/buttons
    // this was designed for -- it reads as a lift off a dark background.
    // A caller whose own border is near-white (e.g. the nav bar) can zero
    // this out: at that size/elevation the blurred black shadow sits right
    // at the border's inner edge and reads as an unwanted dark ring inside
    // an otherwise clean white outline.
    focusedElevation: Float = 18f,
    backgroundColor: Color = Color.Transparent,
    backgroundBrush: Brush? = null,
    focusRequester: FocusRequester? = null,
    focusUp: FocusRequester? = null,
    focusDown: FocusRequester? = null,
    focusLeft: FocusRequester? = null,
    focusRight: FocusRequester? = null,
    bringIntoViewOnFocus: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
    // Keeps a persistent border (e.g. the "Recommended" outline on the
    // Sources screen) visible even when unfocused, drawn by this same
    // border() call rather than a second one layered on by the caller —
    // that second border used to sit on the modifier passed in from
    // outside, i.e. before the graphicsLayer scale below, so it stayed a
    // fixed size while the focused card scaled up around it.
    alwaysShowBorder: Boolean = false,
    borderColor: Color = FocusBorder,
    // Defaults to the same shared timing as scale/elevation. A caller with
    // several adjacent focusable siblings whose borders fade independently
    // (e.g. the top nav bar) can override this to something near-instant --
    // otherwise the outgoing item's fade-out and the incoming item's
    // fade-in both take the full duration and visibly overlap, reading as
    // the border "lagging behind" on the previously-focused item instead of
    // a clean handoff.
    borderAnimationSpec: AnimationSpec<Float> = MangoMotion.focusTween,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    // Every focusable card/button/nav item in the app is built on this one
    // composable, so hooking the nav/click sounds in here is what makes
    // them play everywhere automatically instead of every screen having to
    // wire sound playback into its own click handlers. Null outside the
    // real app tree (previews, tests) -- see LocalUiSoundPlayer's own doc.
    val uiSoundPlayer = LocalUiSoundPlayer.current

    // Held for the onLongClick key-detection block further down. Declared
    // unconditionally (not just `if (onLongClick != null)`) so every
    // composition of this composable calls remember/rememberCoroutineScope
    // in the same order regardless of which caller this instance is --
    // onLongClick's null-ness never actually changes across recompositions
    // of one given call site in this app, but there's no reason to rely on
    // that when hoisting these costs nothing.
    val longPressScope = rememberCoroutineScope()
    var longPressJob by remember { mutableStateOf<Job?>(null) }
    var longClickFired by remember { mutableStateOf(false) }

    // scale/elevation are read via .value INSIDE the graphicsLayer block
    // below rather than through `by` at composable scope, so an animation
    // frame only invalidates that layer's draw instead of recomposing this
    // whole composable — important here since every focus-adjacent card in
    // a scrolling row carries its own instance of these animators.
    val scale = animateFloatAsState(
        targetValue = if (isFocused) focusedScale else 1f,
        animationSpec = MangoMotion.focusTween,
        label = "focusScale"
    )
    val elevation = animateFloatAsState(
        targetValue = if (isFocused) focusedElevation else 0f,
        animationSpec = MangoMotion.focusTween,
        label = "focusElevation"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused || alwaysShowBorder) 1f else 0f,
        animationSpec = borderAnimationSpec,
        label = "focusBorder"
    )

    // Deliberately NOT where the nav sound plays, despite this being where
    // every element's focus-gained transition is already visible: this
    // fires for EVERY cause of a focus change, including the app's own
    // programmatic requestFocus() calls (landing on a screen's first item
    // when it opens, restoring focus after returning from Detail, etc.) --
    // none of which are the user "physically" moving around. Playing here
    // meant every screen navigation played an extra, unearned tick the
    // instant its content appeared. The nav sound instead lives on a single
    // global D-pad-direction key listener in MangoNavHost, which only ever
    // sees REAL key presses, never a bare requestFocus() call.
    LaunchedEffect(isFocused) {
        onFocusChanged(isFocused)
        if (isFocused && bringIntoViewOnFocus) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    // The focus shadow used to be a separate `.shadow(...)` modifier added
    // only `if (isFocused)` — during a held D-pad scroll, focus moves
    // card-to-card continuously, so that structurally added/removed the
    // modifier on nearly every frame. Folding shadowElevation into the
    // graphicsLayer that already unconditionally sits here instead means
    // there's always exactly one RenderNode, and only its parameters
    // animate — same visual result, no structural churn.
    var boxModifier = modifier
        .bringIntoViewRequester(bringIntoViewRequester)
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
            shadowElevation = elevation.value.dp.toPx()
            this.shape = shape
            clip = false
        }
    if (focusRequester != null) {
        boxModifier = boxModifier.focusRequester(focusRequester)
    }
    if (focusUp != null || focusDown != null || focusLeft != null || focusRight != null) {
        // The default D-pad focus search is a geometric heuristic and can fail
        // to find a target across large gaps or overlaid layouts (e.g. a nav
        // bar sitting above a tall hero). Pinning specific directions here
        // makes those seams deterministic instead of "getting stuck".
        boxModifier = boxModifier.focusProperties {
            focusUp?.let { up = it }
            focusDown?.let { down = it }
            focusLeft?.let { left = it }
            focusRight?.let { right = it }
        }
    }
    boxModifier = boxModifier.clip(shape)
    boxModifier = if (backgroundBrush != null) {
        boxModifier.background(backgroundBrush)
    } else {
        boxModifier.background(backgroundColor)
    }
    boxModifier = boxModifier.border(BorderStroke(2.dp, borderColor.copy(alpha = borderAlpha)), shape)

    // Shared by both the touch path (combinedClickable's own onClick below)
    // and the key path (this composable's own onPreviewKeyEvent handler,
    // when onLongClick is set) so the sound-playing logic isn't duplicated.
    val handleClick = {
        when (clickSound) {
            ClickSound.DEFAULT -> uiSoundPlayer?.playClick()
            ClickSound.BACK -> uiSoundPlayer?.playBack()
            ClickSound.NONE -> Unit
        }
        onClick()
    }

    if (onLongClick != null) {
        // Held-key long-press detection for D-pad/remote input -- see the
        // onLongClick param's own doc for why combinedClickable's built-in
        // onLongClick alone isn't enough here. This handler takes complete
        // ownership of DirectionCenter/Enter/NumPadEnter -- both KeyDown
        // and KeyUp are always consumed (never forwarded to
        // combinedClickable's own key handling below) rather than only
        // suppressing the one release that followed a long click: letting
        // combinedClickable see the KeyDown half of a sequence but not its
        // KeyUp would leave whatever internal press state it tracks for
        // that key dangling, an easy way to end up with a stuck/confused
        // state after the very first long press. Fully separating the two
        // paths avoids that regardless of combinedClickable's own
        // internals. Touch input is entirely unaffected -- key and pointer
        // events are separate pipelines, so combinedClickable's own
        // touch-driven onClick/onLongClick (still wired below) keep
        // working exactly as before.
        boxModifier = boxModifier.onPreviewKeyEvent { event ->
            if (event.key != Key.DirectionCenter && event.key != Key.Enter && event.key != Key.NumPadEnter) {
                return@onPreviewKeyEvent false
            }
            when (event.type) {
                KeyEventType.KeyDown -> {
                    // Holding the key sends KeyDown again and again via
                    // Android's own key-repeat -- only the very first one
                    // (repeatCount == 0) should (re)start the timer, or
                    // every repeat would restart it and the threshold would
                    // never actually be reached.
                    if (event.nativeKeyEvent.repeatCount == 0) {
                        longPressJob?.cancel()
                        longClickFired = false
                        longPressJob = longPressScope.launch {
                            delay(LongPressTimeoutMs)
                            longClickFired = true
                            onLongClick()
                        }
                    }
                }
                KeyEventType.KeyUp -> {
                    longPressJob?.cancel()
                    longPressJob = null
                    if (longClickFired) {
                        longClickFired = false
                    } else {
                        handleClick()
                    }
                }
                else -> Unit
            }
            true
        }
    }

    boxModifier = boxModifier.combinedClickable(
        interactionSource = interactionSource,
        indication = null,
        onLongClick = onLongClick,
        onClick = handleClick
    )

    Box(modifier = boxModifier, content = content)
}
