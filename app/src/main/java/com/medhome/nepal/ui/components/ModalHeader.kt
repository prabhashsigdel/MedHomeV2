package com.medhome.nepal.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on a sheet's or dialog's close button. */
const val MODAL_CLOSE_TAG = "modal_close"

/** Test tag on a sheet's or dialog's title. */
const val MODAL_TITLE_TAG = "modal_title"

/** The close button's touch target. */
val ModalCloseSize = 48.dp

/**
 * A sheet's or dialog's title with a close button (Material Symbols "close", 48dp, "Close")
 * at the top-right. The title takes the width left and wraps rather than running under the
 * button. [closeShift] pulls the button into the container's padding so its icon lines up with
 * the content's edge (the touch target stays whole). Disabled while a request is being sent,
 * like the container's own Cancel and Back.
 */
@Composable
internal fun ModalHeader(
    title: String,
    style: TextStyle,
    onClose: () -> Unit,
    closeEnabled: Boolean,
    modifier: Modifier = Modifier,
    closeShift: Dp = 12.dp,
) {
    val colors = GlassTheme.colors
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = title,
            style = style,
            color = colors.textPrimary,
            modifier = Modifier
                .weight(1f)
                // Centres a one-line title on the button.
                .padding(top = 8.dp)
                .semantics { heading() }
                .testTag(MODAL_TITLE_TAG),
        )
        Spacer(Modifier.width(8.dp))
        IconButton(
            onClick = onClose,
            enabled = closeEnabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = colors.textPrimary,
                disabledContentColor = colors.textSecondary,
            ),
            modifier = Modifier
                .offset(x = closeShift)
                .size(ModalCloseSize)
                .testTag(MODAL_CLOSE_TAG),
        ) {
            Icon(painter = painterResource(R.drawable.ic_sym_close), contentDescription = stringResource(R.string.action_close))
        }
    }
}
