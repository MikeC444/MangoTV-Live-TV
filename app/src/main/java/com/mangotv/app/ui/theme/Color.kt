package com.mangotv.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Core surfaces — near-black, cinematic
val MangoBackground = Color(0xFF08080A)
val MangoBackgroundElevated = Color(0xFF141417)
val MangoSurface = Color(0xFF1C1C20)
val MangoSurfaceHigh = Color(0xFF26262B)

// Brand gradient — amber gold to coral, used sparingly as an accent
val MangoAmber = Color(0xFFFFB020)
val MangoTangerine = Color(0xFFFF7A3D)
val MangoCoral = Color(0xFFFF3D68)

val MangoBrandGradient = Brush.linearGradient(
    colors = listOf(MangoAmber, MangoTangerine, MangoCoral)
)

// Per-tier accent colors for source/quality badges — same saturation and
// brightness family as the amber/tangerine/coral gradient above, extended
// into cool hues so 4K/1080p/720p read as distinct tiers rather than
// shades of the same orange.
val MangoAzure = Color(0xFF3D8BFF)
val MangoTeal = Color(0xFF2DD9A8)

fun mangoBrandGradient(angleColors: List<Color> = listOf(MangoAmber, MangoCoral)) =
    Brush.linearGradient(colors = angleColors)

// Text
val TextPrimary = Color(0xFFF6F6F8)
val TextSecondary = Color(0xFFAFAFB8)
val TextTertiary = Color(0xFF75757E)

// Structural
val DividerSubtle = Color(0x1FFFFFFF)
val ScrimColor = Color(0xFF08080A)

// Focus & interaction
val FocusGlow = MangoAmber
val FocusBorder = Color(0xFFFFC873)

// Ratings / progress
val ProgressTrack = Color(0x33FFFFFF)
val ProgressFill = MangoAmber
