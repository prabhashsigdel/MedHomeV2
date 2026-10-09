package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = GlassDimens.CardRadius, topEnd = GlassDimens.CardRadius)

/** Test tag on the sheet's bordered body. */
const val GLASS_SHEET_TAG = "glass_sheet"

/**
 * Modal sheet in the glass style: like [GlassDialog], a glass panel over its own copy of the
 * mesh background, since it is its own window and cannot show the app behind it. Drag, scrim, back and
 * predictive back come from Material's sheet. While [locked] (a request is being sent) the
 * sheet is completely static: no drag, no predictive back, and neither Back nor the scrim
 * dismisses it.
 *
 * The border is drawn on our own body, inside the sheet, with our own drag handle and insets:
 * Material places the sheet with an offset inside its surface, so a border on the sheet's
 * modifier would be drawn where the sheet starts, not where it is (it would float at the top of
 * the screen and not follow drags).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    @StringRes title: Int,
    locked: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = GlassTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        // Disabling gestures (not refusing them in confirmValueChange) means a drag never
        // starts, so there is no rubber band.
        sheetGesturesEnabled = !locked,
        properties = ModalBottomSheetProperties(shouldDismissOnBackPress = !locked, shouldDismissOnClickOutside = !locked),
        shape = SheetShape,
        // Covered by the glass panel; opaque so nothing shows at its edges while it moves.
        containerColor = colors.background.base,
        contentColor = colors.textPrimary,
        tonalElevation = 0.dp,
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0) },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel(SheetShape)
                .windowInsetsPadding(BottomSheetDefaults.windowInsets)
                .padding(bottom = 16.dp)
                .testTag(GLASS_SHEET_TAG),
        ) {
            BottomSheetDefaults.DragHandle(
                color = colors.textSecondary.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                modifier = Modifier
                    .padding(horizontal = GlassDimens.CardPadding)
                    .padding(bottom = 8.dp)
                    .semantics { heading() },
            )
            content()
        }
    }
}

/**
 * A single-choice picker sheet: radio rows, the current one checked. Choosing an option closes
 * the sheet with its slide-down. The choice is applied straight away (so a theme crossfade runs
 * while the sheet leaves), or after the sheet has gone when [applyAfterClose] is set (for
 * changes that redraw the whole app, like the language).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> GlassChoiceSheet(
    @StringRes title: Int,
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    onDismissRequest: () -> Unit,
    applyAfterClose: Boolean = false,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }

    fun choose(value: T) {
        if (closing) return
        closing = true
        val changed = value != selected
        if (changed && !applyAfterClose) onSelect(value)
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismissRequest()
            if (changed && applyAfterClose) onSelect(value)
        }
    }

    GlassBottomSheet(onDismissRequest = onDismissRequest, sheetState = sheetState, title = title) {
        Column(modifier = Modifier.selectableGroup()) {
            options.forEach { option ->
                RadioOptionRow(
                    label = option.label,
                    selected = option.value == selected,
                    onClick = { choose(option.value) },
                )
            }
        }
    }
}
