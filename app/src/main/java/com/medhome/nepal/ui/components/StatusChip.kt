package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * A small status pill ("Active", "Inactive"). [emphasized] is a glass card pill, otherwise the
 * outlined control style, so two states differ in shape and wording, never by colour alone.
 * Text is always the primary ink, which GlassThemeTest checks on both surfaces.
 */
@Composable
fun StatusChip(@StringRes text: Int, emphasized: Boolean, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val shape = GlassShapes.Chip
    val surface = if (emphasized) {
        Modifier.background(colors.glassFill).glassBorder(shape)
    } else {
        Modifier.background(colors.controlFill).controlBorder(shape)
    }
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = colors.textPrimary,
        modifier = modifier
            .clip(shape)
            .then(surface)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
