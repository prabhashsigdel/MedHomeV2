package com.medhome.nepal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassColors
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Cards sit in the content; floating surfaces (bottom bars) are the most transparent. */
enum class GlassLevel { Card, Floating }

private val CardElevation = 10.dp
private val FloatingElevation = 14.dp

/**
 * Translucent white surface with a real backdrop blur (Android 12+), a white border, a top
 * highlight and a soft shadow. Below Android 12 it falls back to a denser white with no blur.
 * Use it for cards and floating bars only, never for individual list items.
 *
 * Haze 1.x has no saturation control on Android, so the boost from the design is not applied.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = GlassShapes.Card,
    contentPadding: PaddingValues = PaddingValues(GlassDimens.CardPadding),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(GlassDimens.ItemSpacing),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val clickModifier = if (onClick != null) {
        Modifier
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
    } else {
        Modifier
    }
    Column(
        modifier = modifier
            .then(clickModifier)
            .glassSurface(level, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/** The glass look as a modifier, for components that lay out their own content. */
@Composable
fun Modifier.glassSurface(
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = GlassShapes.Card,
): Modifier {
    val colors = GlassTheme.colors
    val hazeState = LocalHazeState.current
    val fill = if (level == GlassLevel.Floating) colors.glassFillFloating else colors.glassFill
    val elevation: Dp = if (level == GlassLevel.Floating) FloatingElevation else CardElevation
    val blur = if (hazeState != null) {
        Modifier.hazeEffect(state = hazeState, style = glassStyle(colors, fill))
    } else {
        Modifier.background(colors.glassFallback)
    }
    return this
        .shadow(elevation, shape, clip = false, ambientColor = colors.shadow, spotColor = colors.shadow)
        .clip(shape)
        .then(blur)
        .border(GlassDimens.BorderWidth, colors.glassBorder, shape)
        .topHighlight(colors.glassHighlight)
}

private fun glassStyle(colors: GlassColors, fill: Color) = HazeStyle(
    backgroundColor = colors.backgroundBase,
    tints = listOf(HazeTint(fill)),
    blurRadius = GlassDimens.CardBlur,
    noiseFactor = 0f,
    fallbackTint = HazeTint(colors.glassFallback),
)

/** A thin light line along the top edge, fading out toward the rounded corners. */
private fun Modifier.topHighlight(color: Color): Modifier = drawWithContent {
    drawContent()
    val stroke = 1.dp.toPx()
    val inset = 22.dp.toPx().coerceAtMost(size.width / 3f)
    drawLine(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, color, color, Color.Transparent),
            startX = inset,
            endX = size.width - inset,
        ),
        start = Offset(inset, stroke),
        end = Offset(size.width - inset, stroke),
        strokeWidth = stroke,
    )
}
