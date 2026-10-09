package com.medhome.nepal.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * Looks like a text field, but opens a picker (a time, a date). Its label sits above the value;
 * TalkBack reads "label, value" as one button. [error] draws the error border and text below.
 */
@Composable
fun PickerField(
    label: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
) {
    val colors = GlassTheme.colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = GlassDimens.FieldHeight)
                .glassControl(GlassShapes.Input)
                .then(if (error != null) Modifier.border(GlassDimens.BorderWidth, colors.error, GlassShapes.Input) else Modifier)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
                .clearAndSetSemantics {
                    contentDescription = if (error != null) "$label, $value, $error" else "$label, $value"
                    role = Role.Button
                    if (!enabled) disabled()
                    onClick { onClick(); true }
                }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = if (error != null) colors.error else colors.textSecondary)
            Text(text = value, style = MaterialTheme.typography.bodyLarge, color = colors.textPrimary)
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
