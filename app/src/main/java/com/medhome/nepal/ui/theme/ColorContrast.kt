package com.medhome.nepal.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG minimum for normal-size text. */
const val MIN_TEXT_CONTRAST = 4.5f

/** WCAG minimum for borders, focus rings and other non-text UI. */
const val MIN_UI_CONTRAST = 3f

/** WCAG 2.x contrast ratio between two opaque colors (1 to 21). */
fun contrastRatio(foreground: Color, background: Color): Float {
    val a = foreground.luminance()
    val b = background.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/**
 * The opaque colors a glass card can actually show: each background blob over the base,
 * under the card fill (blur on) and under the fallback fill (Android 11 and lower).
 */
fun GlassColors.glassBackdrops(): List<Color> {
    val behindCard = listOf(backgroundBase, blobLavender, blobSky, blobPink)
        .map { it.compositeOver(backgroundBase) }
    return listOf(glassFill, glassFillFloating, glassFallback).flatMap { fill ->
        behindCard.map { fill.compositeOver(it) }
    }
}
