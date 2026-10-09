package com.medhome.nepal.ui.booking

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.common.feeText
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassDialog
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.FactRow
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.MessageCard
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * One booking (pushed from the Bookings tab): doctor, date, time, hospital and fee, its status,
 * and Cancel while it is upcoming, behind a confirm dialog. The screen stays after cancelling
 * and shows the booking as cancelled (live).
 */
@Composable
fun BookingDetailScreen(viewModel: BookingDetailViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GlassScreen(showBack = true, drawBackground = false) {
        when (val current = state) {
            BookingDetailUiState.Loading -> LoadingCard()
            BookingDetailUiState.Failed -> StatusMessage(
                message = R.string.bookings_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            BookingDetailUiState.NotFound -> MessageCard(R.string.booking_not_found_title, R.string.booking_not_found_body)
            is BookingDetailUiState.Ready -> BookingDetails(
                state = current,
                onCancel = viewModel::askToCancel,
                onDismissError = viewModel::dismissError,
            )
        }
    }
    val ready = state as? BookingDetailUiState.Ready
    if (ready != null && ready.confirmingCancel) {
        CancelDialog(
            cancelling = ready.cancelling,
            onConfirm = viewModel::confirmCancel,
            onDismiss = viewModel::dismissCancel,
        )
    }
}

@Composable
private fun BookingDetails(state: BookingDetailUiState.Ready, onCancel: () -> Unit, onDismissError: () -> Unit) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    val booking = state.booking
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
        InitialsAvatar(name = booking.doctor.name, size = 64.dp, textStyle = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = booking.doctor.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(booking.doctor.specialty.label),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
        }
    }
    when {
        booking.status == BookingStatus.CANCELLED ->
            StatusMessage(message = cancelledNoticeText(booking.cancelledBy), kind = MessageKind.Warning)
        !state.canCancel -> StatusMessage(message = R.string.booking_past_notice, kind = MessageKind.Info)
    }

    GlassCard(
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.entrance(1),
    ) {
        FactRow(label = R.string.label_date, value = LocaleFormat.date(booking.date, DateStyle.FULL, locale))
        SettingsDivider()
        FactRow(label = R.string.label_time, value = LocaleFormat.timeOfDay(booking.start, locale))
        SettingsDivider()
        FactRow(label = R.string.doctor_hospital, value = booking.doctor.hospital)
        SettingsDivider()
        FactRow(label = R.string.doctor_fee, value = feeText(booking.doctor.feeNpr))
    }

    state.cancelError?.let { StatusMessage(message = cancelErrorText(it), kind = MessageKind.Error, onDismiss = onDismissError) }

    if (state.canCancel) {
        StatusMessage(message = R.string.book_pay_at_clinic, kind = MessageKind.Info, modifier = Modifier.entrance(2))
        GlassButton(
            text = R.string.booking_cancel,
            onClick = onCancel,
            style = GlassButtonStyle.Danger,
            loading = state.cancelling,
            enabled = !state.cancelling,
            modifier = Modifier.entrance(3),
        )
        Text(
            text = stringResource(R.string.booking_cancel_note),
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            modifier = Modifier.entrance(3),
        )
    }
}

/** "Cancel this appointment?" Back and tapping outside keep it, except while cancelling. */
@Composable
private fun CancelDialog(cancelling: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val colors = GlassTheme.colors
    GlassDialog(onDismissRequest = { if (!cancelling) onDismiss() }) {
        Text(
            text = stringResource(R.string.booking_cancel_confirm_title),
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.booking_cancel_confirm_body),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        GlassButton(
            text = R.string.booking_cancel,
            onClick = onConfirm,
            style = GlassButtonStyle.Danger,
            loading = cancelling,
            enabled = !cancelling,
        )
        GlassButton(
            text = R.string.booking_keep,
            onClick = onDismiss,
            style = GlassButtonStyle.Secondary,
            enabled = !cancelling,
        )
    }
}
