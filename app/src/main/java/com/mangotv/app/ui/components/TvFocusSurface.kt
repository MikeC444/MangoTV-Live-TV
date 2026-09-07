package com.mangotv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.mangotv.app.ui.theme.FocusBorder
import com.mangotv.app.ui.theme.MangoMotion

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
    shape: Shape = RoundedCornerShape(10.dp),
    focusedScale: Float = MangoMotion.FocusScale,
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
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

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
        targetValue = if (isFocused) 18f else 0f,
        animationSpec = MangoMotion.focusTween,
        label = "focusElevation"
    )
    val borderAlpha by animateFloatAsState(
        targetValue = if (isFocused || alwaysShowBorder) 1f else 0f,
        animationSpec = MangoMotion.focusTween,
        label = "focusBorder"
    )

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
    boxModifier = boxModifier
        .border(BorderStroke(2.dp, borderColor.copy(alpha = borderAlpha)), shape)
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick
        )

    Box(modifier = boxModifier, content = content)
}
