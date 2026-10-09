package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.motion.feedbackTween
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

private val TextAreaMinHeight = 120.dp
private val FocusedBorderWidth = 1.5.dp

/**
 * A multi-line glass field for paragraphs (a doctor's bio): the label above it, the text growing
 * with its content, and the same focus and error borders as [GlassTextField].
 */
@Composable
fun GlassTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    @StringRes error: Int?,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val borderColor by animateColorAsState(
        targetValue = when {
            error != null -> colors.error
            focused -> colors.accentEmphasis
            else -> Color.Transparent
        },
        animationSpec = feedbackTween(),
        label = "textAreaBorder",
    )
    val labelText = stringResource(label)
    val errorText = error?.let { stringResource(it) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.labelMedium,
            color = if (error != null) colors.error else colors.textSecondary,
            modifier = Modifier.padding(start = 4.dp),
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accentEmphasis),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TextAreaMinHeight)
                .glassControl(GlassShapes.Input)
                .border(if (focused || error != null) FocusedBorderWidth else GlassDimens.BorderWidth, borderColor, GlassShapes.Input)
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .semantics {
                    contentDescription = labelText
                    if (errorText != null) error(errorText)
                },
        )
        if (errorText != null) {
            Text(
                text = errorText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
