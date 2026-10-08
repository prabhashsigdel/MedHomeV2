package com.medhome.nepal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medhome.nepal.ui.motion.materializeIn
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

private val DialogMaxWidth = 420.dp

/**
 * A platform Dialog (its own window: back and tapping outside call [onDismissRequest], and focus
 * stays inside while open) whose glass panel materializes with a fade and a scale from 0.96.
 * Callers that must not close mid-request ignore [onDismissRequest] while busy.
 */
@Composable
fun GlassDialog(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = true),
    ) {
        val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
        val colors = GlassTheme.colors
        AnimatedVisibility(visibleState = visibleState, enter = materializeIn()) {
            Column(
                modifier = Modifier
                    .widthIn(max = DialogMaxWidth)
                    .fillMaxWidth()
                    .clip(GlassShapes.Card)
                    .background(colors.dialogFill)
                    .border(GlassDimens.BorderWidth, colors.glassBorder, GlassShapes.Card)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(GlassDimens.ItemSpacing),
                content = content,
            )
        }
    }
}
