package com.medhome.nepal.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.motion.feedbackTween
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * A single-choice row of glass chips that scrolls sideways when it doesn't fit. The selected
 * chip is filled with the accent. TalkBack reads each chip as a radio button in a group.
 * [contentPadding] lets the row scroll to the screen edge while its chips line up with the
 * content.
 */
@Composable
fun <T> FilterChipRow(
    options: List<ChoiceOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(contentPadding)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            FilterChip(label = option.label, selected = option.value == selected, onClick = { onSelect(option.value) })
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(if (selected) colors.accent else colors.controlFill, feedbackTween(), label = "chipFill")
    val textColor by animateColorAsState(if (selected) colors.onAccent else colors.textPrimary, feedbackTween(), label = "chipText")
    Box(
        modifier = Modifier
            .heightIn(min = GlassDimens.MinTouchTarget)
            .pressScale(interactionSource)
            .clip(GlassShapes.Chip)
            .background(fill)
            .controlBorder(GlassShapes.Chip)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = textColor, maxLines = 1)
    }
}
