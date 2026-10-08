package com.medhome.nepal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object GlassShapes {
    val Card = RoundedCornerShape(26.dp)
    val Input = RoundedCornerShape(16.dp)
    val Button = RoundedCornerShape(18.dp)
    val Chip = RoundedCornerShape(percent = 50)
}

object GlassDimens {
    val ScreenPadding = 22.dp
    val ItemSpacing = 16.dp
    val CardPadding = 22.dp
    val FloatingInset = 16.dp
    val ButtonHeight = 54.dp
    val FieldHeight = 52.dp
    val MinTouchTarget = 48.dp
    val BorderWidth = 1.dp
    val FormMaxWidth = 480.dp
    val CardBlur = 30.dp
    val BackgroundBlobBlur = 48.dp
}

val MedHomeShapes = Shapes(
    extraSmall = GlassShapes.Input,
    small = GlassShapes.Input,
    medium = GlassShapes.Button,
    large = GlassShapes.Card,
    extraLarge = GlassShapes.Card,
)
