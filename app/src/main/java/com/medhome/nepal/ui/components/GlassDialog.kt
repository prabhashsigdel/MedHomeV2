package com.medhome.nepal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.medhome.nepal.ui.motion.materializeIn
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes

private val DialogMaxWidth = 420.dp

/**
 * A platform Dialog (its own window: back and tapping outside call [onDismissRequest], and focus
 * stays inside while open) whose glass panel (over its own mesh background) materializes with a fade and a scale from 0.96.
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
        AnimatedVisibility(visibleState = visibleState, enter = materializeIn()) {
            Column(
                modifier = Modifier
                    .widthIn(max = DialogMaxWidth)
                    .fillMaxWidth()
                    .glassPanel(GlassShapes.Card)
                    // Scrolls when taller than the window (large text, a list of choices).
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(GlassDimens.ItemSpacing),
                content = content,
            )
        }
    }
}
