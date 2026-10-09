package com.medhome.nepal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max

object GlassShapes {
    val Card = RoundedCornerShape(GlassDimens.CardRadius)
    val Input = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(18.dp)
    val Chip = RoundedCornerShape(percent = 50)
}

object GlassDimens {
    val CardRadius = 26.dp
    val ScreenPadding = 24.dp
    val ItemSpacing = 16.dp
    /** Tighter rhythm for the sign-in forms, so they fit a 360x740dp phone without scrolling. */
    val CompactItemSpacing = 12.dp
    val CompactCardPadding = 20.dp
    val CardPadding = 22.dp
    val FloatingInset = 16.dp
    val ButtonHeight = 54.dp
    val FieldHeight = 52.dp
    val MinTouchTarget = 48.dp
    val BorderWidth = 1.dp
    val FormMaxWidth = 480.dp
    /** Blur of the floating bar, the only blurred surface (content scrolls under it). */
    val FloatingBlur = 30.dp
}

/** Smallest corner a nested shape keeps, so a deep inset never turns it square. */
private val MinNestedRadius = 4.dp

/**
 * Corner radius for a shape inset by [inset] inside one with [outerRadius], so both curves stay
 * concentric (inner = outer minus padding).
 */
fun nestedCornerRadius(outerRadius: Dp, inset: Dp): Dp = max(outerRadius - inset, MinNestedRadius)

val MedHomeShapes = Shapes(
    extraSmall = GlassShapes.Input,
    small = GlassShapes.Input,
    medium = GlassShapes.Button,
    large = GlassShapes.Card,
    extraLarge = GlassShapes.Card,
)
