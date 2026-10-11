package com.medhome.nepal.ui.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.ui.booking.BackToBookingsButton
import com.medhome.nepal.R
import com.medhome.nepal.domain.AdminBooking
import com.medhome.nepal.domain.AdminBookingFilter
import com.medhome.nepal.domain.AdminCancelledBy
import com.medhome.nepal.domain.BookingStatus
import com.medhome.nepal.domain.NepalTime
import com.medhome.nepal.ui.common.DateStyle
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.common.dateTimeText
import com.medhome.nepal.ui.common.feeText
import com.medhome.nepal.ui.components.ChoiceOption
import com.medhome.nepal.ui.components.FactRow
import com.medhome.nepal.ui.components.FilterChipRow
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.MessageCard
import com.medhome.nepal.ui.booking.label as reasonLabel
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on each row of the admin's bookings list, for tests. */
const val ADMIN_BOOKING_ROW_TAG = "admin_booking_row"

/**
 * The admin's Bookings tab: every doctor's bookings under Upcoming, Past or Cancelled, a page
 * at a time (Show more). Each row: when, the doctor, the patient's first name and the status;
 * tapping one opens it.
 */
@Composable
fun AdminBookingsScreen(viewModel: AdminBookingsViewModel, onOpenBooking: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GlassScreen(drawBackground = false) {
        ScreenTitle(title = R.string.admin_bookings_title, modifier = Modifier.entrance(0))
        FilterChipRow(
            options = listOf(
                ChoiceOption(AdminBookingFilter.UPCOMING, stringResource(R.string.bookings_upcoming)),
                ChoiceOption(AdminBookingFilter.PAST, stringResource(R.string.bookings_past)),
                ChoiceOption(AdminBookingFilter.CANCELLED, stringResource(R.string.admin_bookings_cancelled)),
            ),
            selected = state.filter,
            onSelect = viewModel::selectFilter,
            modifier = Modifier.entrance(1),
        )
        if (state.showingSaved) StatusMessage(message = R.string.bookings_saved_notice, kind = MessageKind.Info)
        when (state.status) {
            AdminBookingsStatus.LOADING -> LoadingCard()
            AdminBookingsStatus.FAILED -> StatusMessage(
                message = R.string.admin_bookings_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            AdminBookingsStatus.EMPTY -> MessageCard(emptyTitle(state.filter), R.string.admin_bookings_none_body)
            AdminBookingsStatus.READY -> {
                state.bookings.forEach { booking ->
                    key(booking.bookingId) {
                        AdminBookingRow(
                            booking = booking,
                            upcoming = booking.isUpcoming(state.nowMillis),
                            onClick = { onOpenBooking(booking.bookingId) },
                        )
                    }
                }
                if (state.hasMore) {
                    GlassButton(
                        text = R.string.admin_bookings_show_more,
                        onClick = viewModel::showMore,
                        style = GlassButtonStyle.Secondary,
                        loading = state.loadingMore,
                        enabled = !state.loadingMore,
                    )
                }
            }
        }
    }
}

@StringRes
private fun emptyTitle(filter: AdminBookingFilter): Int = when (filter) {
    AdminBookingFilter.UPCOMING -> R.string.admin_bookings_none_upcoming
    AdminBookingFilter.PAST -> R.string.admin_bookings_none_past
    AdminBookingFilter.CANCELLED -> R.string.admin_bookings_none_cancelled
}

/** When, the doctor, the patient and the status (who cancelled, and when). One button for TalkBack. */
@Composable
private fun AdminBookingRow(booking: AdminBooking, upcoming: Boolean, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    GlassCard(
        onClick = onClick,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .testTag(ADMIN_BOOKING_ROW_TAG)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            text = dateTimeText(booking.date, booking.start),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )
        Text(text = booking.doctor.name, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(text = patientLabel(booking.toAppointment()), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        Text(
            text = statusText(booking, upcoming),
            style = MaterialTheme.typography.labelMedium,
            color = if (booking.status == BookingStatus.CANCELLED) colors.error else colors.textSecondary,
        )
    }
}

/**
 * "Booked", "Past", or who cancelled it, then when ("Cancelled by you · 8 Oct · 14:30"); cancels
 * from before the time was stored show no time.
 */
@Composable
private fun statusText(booking: AdminBooking, upcoming: Boolean): String {
    val status = stringResource(statusLabel(booking, upcoming))
    val cancelledAt = booking.cancelledAtMillis ?: return status
    return stringResource(R.string.admin_cancelled_when, status, dateTimeText(NepalTime.dateOf(cancelledAt), NepalTime.timeOf(cancelledAt)))
}

/** [upcoming]: booked and not started as of now; a booked one that has started reads Past. */
@StringRes
private fun statusLabel(booking: AdminBooking, upcoming: Boolean): Int = when (booking.status) {
    BookingStatus.BOOKED -> if (upcoming) R.string.admin_booking_booked else R.string.admin_booking_past
    BookingStatus.CANCELLED -> when (booking.cancelledBy) {
        AdminCancelledBy.PATIENT -> R.string.admin_cancelled_by_patient
        AdminCancelledBy.YOU -> R.string.admin_cancelled_by_you
        AdminCancelledBy.ANOTHER_ADMIN -> R.string.admin_cancelled_by_other_admin
        AdminCancelledBy.CLINIC -> R.string.admin_cancelled_by_clinic
        null -> R.string.booking_status_cancelled
    }
}

/**
 * One booking for the admin (pushed from the Bookings tab): who, which doctor, when, where, the
 * fee and the status, and Cancel while it is upcoming (the same confirm dialog as a doctor's list).
 */
@Composable
fun AdminBookingScreen(viewModel: AdminBookingViewModel, onBackToBookings: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val cancelDialog by viewModel.cancelDialog.collectAsStateWithLifecycle()
    // Cancelled (here or elsewhere) or past: nothing left to do but go back to the list.
    val settled = (state as? AdminBookingUiState.Ready)?.canCancel == false
    GlassScreen(
        showBack = true,
        drawBackground = false,
        bottomAction = if (settled) {
            { BackToBookingsButton(onClick = onBackToBookings) }
        } else {
            null
        },
    ) {
        ScreenTitle(title = R.string.admin_booking_title, modifier = Modifier.entrance(0))
        when (val current = state) {
            AdminBookingUiState.Loading -> LoadingCard()
            is AdminBookingUiState.Failed -> StatusMessage(
                message = R.string.admin_booking_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            AdminBookingUiState.Missing -> MessageCard(R.string.admin_booking_missing_title, R.string.admin_cancel_error_not_found)
            is AdminBookingUiState.Ready -> {
                BookingFacts(current.booking, upcoming = current.canCancel, modifier = Modifier.entrance(1))
                if (current.canCancel) {
                    GlassButton(
                        text = R.string.admin_cancel_booking_confirm,
                        onClick = viewModel::requestCancel,
                        style = GlassButtonStyle.Danger,
                        modifier = Modifier.entrance(2),
                    )
                }
            }
        }
    }
    // Only over a loaded booking (the screen may have gone to an error under it).
    if (state is AdminBookingUiState.Ready) cancelDialog?.let { current ->
        CancelBookingDialog(
            state = current,
            onReason = viewModel::selectCancelReason,
            onNote = viewModel::editCancelNote,
            onConfirm = viewModel::confirmCancel,
            onDismiss = viewModel::dismissCancel,
        )
    }
}

@Composable
private fun BookingFacts(booking: AdminBooking, upcoming: Boolean, modifier: Modifier = Modifier) {
    val locale = currentLocale()
    GlassCard(contentPadding = PaddingValues(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(0.dp), modifier = modifier) {
        FactRow(label = R.string.label_patient, value = patientLabel(booking.toAppointment()))
        SettingsDivider()
        FactRow(label = R.string.label_doctor, value = booking.doctor.name)
        SettingsDivider()
        FactRow(label = R.string.label_specialty, value = stringResource(booking.doctor.specialty.label))
        SettingsDivider()
        FactRow(label = R.string.label_date, value = LocaleFormat.date(booking.date, DateStyle.FULL, locale))
        SettingsDivider()
        FactRow(label = R.string.label_time, value = LocaleFormat.timeOfDay(booking.start, locale))
        SettingsDivider()
        FactRow(label = R.string.doctor_hospital, value = booking.doctor.hospital)
        SettingsDivider()
        FactRow(label = R.string.doctor_fee, value = feeText(booking.doctor.feeNpr))
        SettingsDivider()
        FactRow(label = R.string.label_status, value = stringResource(statusLabel(booking, upcoming)))
        booking.cancelledAtMillis?.let { at ->
            SettingsDivider()
            FactRow(label = R.string.label_cancelled_at, value = dateTimeText(NepalTime.dateOf(at), NepalTime.timeOf(at)))
        }
        booking.clinicReason?.let { why ->
            SettingsDivider()
            FactRow(label = R.string.label_reason, value = stringResource(why.reason.reasonLabel))
            why.note?.let { note ->
                SettingsDivider()
                // A plain Text: the note shows as typed, never as markup or links.
                FactRow(label = R.string.label_note, value = note)
            }
        }
    }
}
