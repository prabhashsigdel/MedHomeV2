package com.medhome.nepal.ui.admin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import com.medhome.nepal.R
import com.medhome.nepal.domain.CancelReason
import com.medhome.nepal.domain.ClinicCancelReason
import com.medhome.nepal.ui.booking.label
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.GlassTextField
import com.medhome.nepal.ui.components.RadioOptionRow
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * Why the clinic is cancelling: one preset reason (required) and an optional note for the
 * patient, with its length out of [ClinicCancelReason.MAX_NOTE_LENGTH]. Used by both cancel
 * dialogs (one booking, and hiding a doctor with their bookings).
 */
@Composable
internal fun CancelReasonPicker(
    reason: CancelReason?,
    note: String,
    onReason: (CancelReason) -> Unit,
    onNote: (String) -> Unit,
    enabled: Boolean,
) {
    val colors = GlassTheme.colors
    Text(
        text = stringResource(R.string.cancel_reason_title),
        style = MaterialTheme.typography.titleSmall,
        color = colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        CancelReason.entries.forEach { option ->
            RadioOptionRow(
                label = stringResource(option.label),
                selected = option == reason,
                onClick = { if (enabled) onReason(option) },
            )
        }
    }
    GlassTextField(
        value = note,
        onValueChange = onNote,
        label = R.string.cancel_note_label,
        error = null,
        enabled = enabled,
        imeAction = ImeAction.Done,
        capitalization = KeyboardCapitalization.Sentences,
    )
    val locale = currentLocale()
    Text(
        text = stringResource(
            R.string.cancel_note_counter,
            LocaleFormat.number(note.length, locale),
            LocaleFormat.number(ClinicCancelReason.MAX_NOTE_LENGTH, locale),
        ),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textSecondary,
        textAlign = TextAlign.End,
        modifier = Modifier.fillMaxWidth(),
    )
}
