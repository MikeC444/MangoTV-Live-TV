package com.mangotv.app.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Central motion tokens so every focus/transition animation across the app
 * feels like part of the same system rather than ad-hoc per-screen tuning.
 */
object MangoMotion {
    const val FocusScale = 1.08f
    const val FocusScaleHero = 1.05f

    const val FastMillis = 150
    const val MediumMillis = 280
    const val SlowMillis = 550
    const val HeroCrossfadeMillis = 900
    const val HeroKenBurnsMillis = 9000

    val StandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    val focusTween = tween<Float>(durationMillis = FastMillis, easing = StandardEasing)
    val mediumTween = tween<Float>(durationMillis = MediumMillis, easing = StandardEasing)

    // Compose's default BringIntoViewSpec positioning ("scroll the minimum
    // amount needed to reveal the item") is kept as-is here — only the
    // animation itself speeds up, replacing Compose's slower default
    // spring with focusTween so a D-pad held down doesn't outrun it and
    // stutter (see HomeScreen.kt's own comment for the full story). Used
    // for horizontal card-to-card scrolling within a row, which shouldn't
    // re-center on every move.
    @OptIn(ExperimentalFoundationApi::class)
    val FastBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
        override val scrollAnimationSpec: AnimationSpec<Float> = focusTween
    }

    // Fully disables Compose's automatic focus-triggered bring-into-view
    // for whatever container this is provided to. Home's outer LazyColumn
    // uses this because it's now scrolled exclusively by an explicit
    // LaunchedEffect (see HomeScreen.kt) that centers whichever row has
    // focus -- without this, the automatic mechanism still reacts to any
    // focus rect bubbling up from inside a row (e.g. a card's own
    // scale-on-focus transform reporting a slightly shifted rect as it
    // grows) and nudges the whole outer list vertically on every purely
    // horizontal card-to-card move, which read as the entire page shaking
    // while just moving within a row.
    @OptIn(ExperimentalFoundationApi::class)
    val DisabledBringIntoViewSpec: BringIntoViewSpec = object : BringIntoViewSpec {
        override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
    }
}
