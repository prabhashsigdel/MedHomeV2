package com.medhome.nepal.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassColors
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Cards sit in the content; floating surfaces (bottom bars) blur what scrolls under them. */
enum class GlassLevel { Card, Floating }

/**
 * The glass card: a neutral translucent white with a 1px border, bright at the top and faint at
 * the bottom. No blur and no shadow: the background is already soft, and a shadow would show
 * through the fill. Fills the available width. Use it for cards and floating bars only, never
 * for list items.
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
            .fillMaxWidth()
            .then(clickModifier)
            .glassSurface(level, shape)
            .padding(contentPadding),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * The glass look as a modifier, for components that lay out their own content. Only
 * [GlassLevel.Floating] blurs (with Haze, Android 12+; a denser fill below), so content
 * scrolling under the bar stays soft and its labels stay readable.
 */
@Composable
fun Modifier.glassSurface(
    level: GlassLevel = GlassLevel.Card,
    shape: Shape = GlassShapes.Card,
): Modifier {
    val colors = GlassTheme.colors
    val hazeState = LocalHazeState.current
    val surface = when {
        level == GlassLevel.Card -> Modifier.background(colors.glassFill)
        hazeState != null -> Modifier.hazeEffect(state = hazeState, style = floatingStyle(colors))
        else -> Modifier.background(colors.glassFallback)
    }
    return this
        .clip(shape)
        .then(surface)
        .glassBorder(shape)
}

/** Fields, secondary buttons and chips: the fainter control fill with the glass border. */
@Composable
fun Modifier.glassControl(shape: Shape): Modifier = this
    .clip(shape)
    .background(GlassTheme.colors.controlFill)
    .glassBorder(shape)

/**
 * Sheets and dialogs live in their own window, with nothing of the app to show through, so
 * they draw the mesh themselves under a card: the same glass, and fully opaque.
 */
@Composable
fun Modifier.glassPanel(shape: Shape): Modifier {
    val colors = GlassTheme.colors
    return this
        .clip(shape)
        .meshGradient(colors.background)
        .background(colors.glassFill)
        .glassBorder(shape)
}

/** The 1px glass border: a vertical gradient from bright at the top to faint at the bottom. */
@Composable
fun Modifier.glassBorder(shape: Shape): Modifier {
    val colors = GlassTheme.colors
    val brush = remember(colors.glassBorderTop, colors.glassBorderBottom) {
        Brush.verticalGradient(listOf(colors.glassBorderTop, colors.glassBorderBottom))
    }
    return border(BorderStroke(GlassDimens.BorderWidth, brush), shape)
}

private fun floatingStyle(colors: GlassColors) = HazeStyle(
    backgroundColor = colors.background.base,
    tints = listOf(HazeTint(colors.floatingScrim), HazeTint(colors.glassFill)),
    blurRadius = GlassDimens.FloatingBlur,
    noiseFactor = 0f,
    fallbackTint = HazeTint(colors.glassFallback),
)
