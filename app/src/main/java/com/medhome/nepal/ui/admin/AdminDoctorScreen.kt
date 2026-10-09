package com.medhome.nepal.ui.admin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.AdminError
import com.medhome.nepal.domain.DoctorAppointment
import com.medhome.nepal.domain.ManagedDoctor
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.common.dateTimeText
import com.medhome.nepal.ui.common.feeText
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassButtonStyle
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassDialog
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusChip
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.doctors.FactRow
import com.medhome.nepal.ui.doctors.LoadingCard
import com.medhome.nepal.ui.doctors.MessageCard
import com.medhome.nepal.ui.doctors.WeeklyHours
import com.medhome.nepal.ui.doctors.label
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on each upcoming booking, for tests. */
const val ADMIN_APPOINTMENT_TAG = "admin_appointment"

/**
 * One doctor for the admin: details, shown to or hidden from patients (with a confirm dialog
 * that counts their upcoming bookings and, when hiding, can cancel them too), Edit details, and
 * the upcoming bookings (date, time and the patient's first name), each of which can be
 * cancelled for the clinic.
 */
@Composable
fun AdminDoctorScreen(viewModel: AdminDoctorViewModel, onEdit: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val appointments by viewModel.appointments.collectAsStateWithLifecycle()
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val cancelDialog by viewModel.cancelDialog.collectAsStateWithLifecycle()

    GlassScreen(showBack = true, drawBackground = false) {
        when (val current = state) {
            AdminDoctorUiState.Loading -> LoadingCard()
            is AdminDoctorUiState.Failed -> StatusMessage(
                message = R.string.admin_doctor_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            is AdminDoctorUiState.Missing -> MessageCard(
                title = if (current.offline) R.string.doctors_offline_title else R.string.admin_doctor_missing_title,
                body = if (current.offline) R.string.admin_doctor_offline_body else R.string.admin_doctor_missing_body,
            )
            is AdminDoctorUiState.Ready -> {
                DoctorSummary(managed = current.doctor, showingSaved = current.showingSaved)
                VisibilityCard(active = current.doctor.active, onToggle = viewModel::requestToggle)
                GlassButton(
                    text = R.string.admin_edit_details,
                    onClick = { onEdit(current.doctor.doctor.id) },
                    style = GlassButtonStyle.Secondary,
                    modifier = Modifier.entrance(2),
                )
                SectionTitle(text = R.string.admin_upcoming_title, modifier = Modifier.entrance(3))
                Appointments(state = appointments, onRetry = viewModel::retryAppointments, onCancel = viewModel::requestCancel)
                SectionTitle(text = R.string.doctor_hours, modifier = Modifier.entrance(4))
                WeeklyHours(schedule = current.doctor.doctor.weeklySchedule, modifier = Modifier.entrance(4))
            }
        }
    }

    val doctorName = (state as? AdminDoctorUiState.Ready)?.doctor?.doctor?.name
    // The doctor went away (gone, or a reload) under an open dialog: close it rather than keep it hidden.
    val orphanedDialog = dialog != null && doctorName == null
    LaunchedEffect(orphanedDialog) { if (orphanedDialog) viewModel.dismissDialog() }
    dialog?.let { current ->
        if (doctorName != null) {
            ActiveDialog(
                state = current,
                doctorName = doctorName,
                onConfirm = viewModel::confirmToggle,
                onRetryCancel = viewModel::retryCancelBookings,
                onDismiss = viewModel::dismissDialog,
            )
        }
    }
    // Only over a loaded doctor (the screen may have gone to an error under it).
    if (doctorName != null) cancelDialog?.let { current ->
        CancelBookingDialog(state = current, onConfirm = viewModel::confirmCancel, onDismiss = viewModel::dismissCancel)
    }
}

@Composable
private fun DoctorSummary(managed: ManagedDoctor, showingSaved: Boolean) {
    val colors = GlassTheme.colors
    val doctor = managed.doctor
    val locale = currentLocale()
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
        InitialsAvatar(name = doctor.name, size = 64.dp, textStyle = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = doctor.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(doctor.specialty.label), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            StatusChip(
                text = if (managed.active) R.string.admin_status_active else R.string.admin_status_inactive,
                emphasized = managed.active,
            )
        }
    }
    if (showingSaved) StatusMessage(message = R.string.admin_doctor_saved_notice, kind = MessageKind.Info)
    GlassCard(
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
        modifier = Modifier.entrance(1),
    ) {
        FactRow(label = R.string.doctor_fee, value = feeText(doctor.feeNpr))
        SettingsDivider()
        FactRow(label = R.string.doctor_hospital, value = doctor.hospital)
        doctor.experienceYears?.let { years ->
            SettingsDivider()
            FactRow(
                label = R.string.doctor_experience,
                value = pluralStringResource(R.plurals.doctor_experience_years, years, LocaleFormat.number(years, locale)),
            )
        }
        SettingsDivider()
        FactRow(
            label = R.string.admin_slot_minutes,
            value = pluralStringResource(R.plurals.admin_minutes, doctor.slotMinutes, LocaleFormat.number(doctor.slotMinutes, locale)),
        )
    }
}

/** Whether patients see the doctor, and the button that changes it (through a confirm dialog). */
@Composable
private fun VisibilityCard(active: Boolean, onToggle: () -> Unit) {
    GlassCard(modifier = Modifier.entrance(2)) {
        Text(
            text = stringResource(if (active) R.string.admin_visibility_active else R.string.admin_visibility_inactive),
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTheme.colors.textPrimary,
        )
        GlassButton(
            text = if (active) R.string.admin_hide_doctor else R.string.admin_show_doctor,
            onClick = onToggle,
            style = if (active) GlassButtonStyle.Danger else GlassButtonStyle.Primary,
        )
    }
}

@Composable
private fun Appointments(state: AppointmentsUiState, onRetry: () -> Unit, onCancel: (DoctorAppointment) -> Unit) {
    when (state) {
        AppointmentsUiState.Loading -> LoadingCard()
        is AppointmentsUiState.Failed -> StatusMessage(message = R.string.admin_upcoming_failed, kind = MessageKind.Error, onRetry = onRetry)
        is AppointmentsUiState.Ready -> if (state.appointments.isEmpty()) {
            GlassCard {
                Text(
                    text = stringResource(R.string.admin_upcoming_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.textSecondary,
                )
            }
        } else {
            GlassCard(
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                state.appointments.forEachIndexed { index, appointment ->
                    key(appointment.bookingId) {
                        if (index > 0) SettingsDivider()
                        AppointmentRow(appointment, onCancel = { onCancel(appointment) })
                    }
                }
            }
        }
    }
}

/** Date, time and the patient's first name (nothing else about them), and Cancel. */
@Composable
private fun AppointmentRow(appointment: DoctorAppointment, onCancel: () -> Unit) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ADMIN_APPOINTMENT_TAG)
            .padding(start = 22.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = dateTimeText(appointment.date, appointment.start),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
            )
            Text(
                text = appointment.patientFirstName ?: stringResource(R.string.admin_patient_unknown),
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
            )
        }
        Spacer(Modifier.width(12.dp))
        val description = stringResource(
            R.string.admin_cancel_booking_of,
            dateTimeText(appointment.date, appointment.start),
            appointment.patientFirstName ?: stringResource(R.string.admin_patient_unknown),
        )
        GlassButton(
            text = R.string.admin_cancel_booking,
            onClick = onCancel,
            style = GlassButtonStyle.Secondary,
            compact = true,
            // Every row's button says "Cancel": TalkBack hears which booking.
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun ActiveDialog(
    state: ActiveDialogState,
    doctorName: String,
    onConfirm: (cancelBookings: Boolean) -> Unit,
    onRetryCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Back and tapping outside go through onDismiss, which the ViewModel ignores while saving.
    GlassDialog(onDismissRequest = onDismiss) {
        val result = state.cancelResult
        if (result != null) {
            BulkCancelReport(state = state, result = result, onRetry = onRetryCancel, onClose = onDismiss)
        } else {
            ConfirmActive(state = state, doctorName = doctorName, onConfirm = onConfirm, onDismiss = onDismiss)
        }
    }
}

@Composable
private fun ColumnScope.ConfirmActive(
    state: ActiveDialogState,
    doctorName: String,
    onConfirm: (cancelBookings: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    DialogTitle(if (state.activate) R.string.admin_show_title else R.string.admin_hide_title)
    Text(
        text = stringResource(if (state.activate) R.string.admin_show_body else R.string.admin_hide_body, doctorName),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.textSecondary,
    )
    Text(
        text = when {
            state.upcomingCount != null -> pluralStringResource(
                R.plurals.admin_upcoming_count,
                state.upcomingCount,
                LocaleFormat.number(state.upcomingCount, locale),
            )
            state.countFailed -> stringResource(R.string.admin_count_failed)
            else -> stringResource(R.string.admin_counting)
        },
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
    )
    state.error?.let { StatusMessage(message = adminErrorText(it), kind = MessageKind.Error) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val count = state.upcomingCount
        if (state.canCancelBookings && count != null) {
            GlassButton(
                text = pluralStringResource(R.plurals.admin_hide_and_cancel, count, LocaleFormat.number(count, locale)),
                onClick = { onConfirm(true) },
                style = GlassButtonStyle.Danger,
                loading = state.isSaving && state.cancellingBookings,
                enabled = state.canConfirm,
            )
        }
        GlassButton(
            text = when {
                state.activate -> R.string.admin_show_confirm
                state.canCancelBookings -> R.string.admin_hide_only
                else -> R.string.admin_hide_confirm
            },
            onClick = { onConfirm(false) },
            style = when {
                state.activate -> GlassButtonStyle.Primary
                state.canCancelBookings -> GlassButtonStyle.Secondary
                else -> GlassButtonStyle.Danger
            },
            loading = state.isSaving && !state.cancellingBookings,
            enabled = state.canConfirm,
        )
        GlassButton(text = R.string.action_cancel, onClick = onDismiss, style = GlassButtonStyle.Secondary, enabled = !state.isSaving)
    }
}

/** The doctor is hidden, but some bookings may still be booked: how many, why, and Retry. */
@Composable
private fun ColumnScope.BulkCancelReport(
    state: ActiveDialogState,
    result: BulkCancelResult,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    DialogTitle(R.string.admin_hidden_title)
    if (result.cancelled > 0) {
        Text(
            text = pluralStringResource(R.plurals.admin_bulk_cancelled, result.cancelled, LocaleFormat.number(result.cancelled, locale)),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
        )
    }
    Text(
        text = if (result.failed > 0) {
            pluralStringResource(R.plurals.admin_bulk_failed, result.failed, LocaleFormat.number(result.failed, locale))
        } else {
            stringResource(R.string.admin_bulk_check_failed)
        },
        style = MaterialTheme.typography.titleMedium,
        color = colors.textPrimary,
    )
    result.error?.let { StatusMessage(message = adminCancelErrorText(it), kind = MessageKind.Error) }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassButton(
            text = R.string.action_retry,
            onClick = onRetry,
            style = GlassButtonStyle.Danger,
            loading = state.isSaving,
            enabled = !state.isSaving,
        )
        GlassButton(text = R.string.action_done, onClick = onClose, style = GlassButtonStyle.Secondary, enabled = !state.isSaving)
    }
}

/** "Cancel this booking?" with when and whose. Back and tapping outside keep it, except while cancelling. */
@Composable
private fun CancelBookingDialog(state: CancelBookingDialogState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val appointment = state.appointment
    GlassDialog(onDismissRequest = onDismiss) {
        DialogTitle(R.string.admin_cancel_booking_title)
        Text(
            text = stringResource(
                R.string.admin_cancel_booking_body,
                dateTimeText(appointment.date, appointment.start),
                appointment.patientFirstName ?: stringResource(R.string.admin_patient_unknown),
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = GlassTheme.colors.textSecondary,
        )
        state.error?.let { StatusMessage(message = adminCancelErrorText(it), kind = MessageKind.Error) }
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(
                text = R.string.admin_cancel_booking_confirm,
                onClick = onConfirm,
                style = GlassButtonStyle.Danger,
                loading = state.isCancelling,
                enabled = !state.isCancelling,
            )
            GlassButton(
                text = R.string.admin_keep_booking,
                onClick = onDismiss,
                style = GlassButtonStyle.Secondary,
                enabled = !state.isCancelling,
            )
        }
    }
}

@Composable
private fun DialogTitle(@StringRes text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.headlineSmall,
        color = GlassTheme.colors.textPrimary,
        modifier = Modifier.semantics { heading() },
    )
}

/** Why cancelling a booking failed, in this feature's words. */
@StringRes
internal fun adminCancelErrorText(error: AdminError): Int = when (error) {
    AdminError.BOOKING_STARTED -> R.string.admin_cancel_error_started
    AdminError.NOT_FOUND -> R.string.admin_cancel_error_not_found
    AdminError.NETWORK,
    AdminError.PERMISSION_DENIED,
    AdminError.ALREADY_EXISTS,
    AdminError.UNKNOWN -> adminErrorText(error)
}

/** This feature's own messages for a failed admin write. */
internal fun adminErrorText(error: AdminError): Int = when (error) {
    AdminError.NETWORK -> R.string.admin_error_network
    AdminError.PERMISSION_DENIED -> R.string.admin_error_denied
    AdminError.NOT_FOUND -> R.string.admin_error_not_found
    AdminError.BOOKING_STARTED -> R.string.admin_cancel_error_started
    AdminError.ALREADY_EXISTS -> R.string.admin_error_exists
    AdminError.UNKNOWN -> R.string.admin_error_unknown
}
