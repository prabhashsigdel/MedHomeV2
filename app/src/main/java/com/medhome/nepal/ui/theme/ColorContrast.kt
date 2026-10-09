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

/**
 * WCAG 2.x contrast ratio (1 to 21) between [foreground] and an opaque [background]. A
 * translucent foreground (secondary text in the dark theme) is first composited over it.
 */
fun contrastRatio(foreground: Color, background: Color): Float {
    val shown = if (foreground.alpha < 1f) foreground.compositeOver(background) else foreground
    val a = shown.luminance()
    val b = background.luminance()
    return (max(a, b) + 0.05f) / (min(a, b) + 0.05f)
}

/**
 * The opaque colors text and marks can sit on: the background's darkest and brightest region,
 * bare (titles), under a glass card, and under a control on that card (a field or a secondary
 * button). Sheets and dialogs draw the same background and glass, so they are covered too.
 */
fun GlassColors.glassBackdrops(): List<Color> {
    val (darkest, brightest) = background.darkestAndBrightest()
    return listOf(darkest, brightest).flatMap { region ->
        val card = glassFill.compositeOver(region)
        listOf(region, card, controlFill.compositeOver(card))
    }
}
