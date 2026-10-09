package com.medhome.nepal.ui.doctors

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.medhome.nepal.R
import com.medhome.nepal.domain.Doctor
import com.medhome.nepal.domain.TimeRange
import com.medhome.nepal.domain.Weekday
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.common.feeText
import com.medhome.nepal.ui.components.GlassButton
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.InitialsAvatar
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * A doctor's details (pushed from Find a doctor): header, fee, hospital, experience, bio and
 * weekly hours, and the button that opens booking for them.
 */
@Composable
fun DoctorDetailScreen(viewModel: DoctorDetailViewModel, onBook: (String) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    GlassScreen(showBack = true, drawBackground = false) {
        when (val current = state) {
            DoctorDetailUiState.Loading -> LoadingCard()
            is DoctorDetailUiState.Failed -> StatusMessage(
                // This screen's own message: a generic error's text may be about something else.
                message = R.string.doctor_load_failed,
                kind = MessageKind.Error,
                onRetry = viewModel::retry,
            )
            is DoctorDetailUiState.Unavailable -> MessageCard(
                title = R.string.doctor_unavailable_title,
                body = if (current.offline) R.string.doctor_unavailable_offline else R.string.doctor_unavailable_body,
            )
            is DoctorDetailUiState.Ready -> DoctorDetails(current.doctor, current.showingSaved, onBook = { onBook(current.doctor.id) })
        }
    }
}

@Composable
private fun DoctorDetails(doctor: Doctor, showingSaved: Boolean, onBook: () -> Unit) {
    val colors = GlassTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.entrance(0)) {
        InitialsAvatar(name = doctor.name, size = 64.dp, textStyle = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = doctor.name,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(doctor.specialty.label), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
    if (showingSaved) StatusMessage(message = R.string.doctors_saved_notice, kind = MessageKind.Info)

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
                value = pluralStringResource(R.plurals.doctor_experience_years, years, LocaleFormat.number(years, currentLocale())),
            )
        }
    }

    if (doctor.bio.isNotBlank()) {
        SectionTitle(text = R.string.doctor_about, modifier = Modifier.entrance(2))
        GlassCard(modifier = Modifier.entrance(2)) {
            Text(text = doctor.bio, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
        }
    }

    SectionTitle(text = R.string.doctor_hours, modifier = Modifier.entrance(3))
    WeeklyHours(schedule = doctor.weeklySchedule, modifier = Modifier.entrance(3))

    GlassButton(text = R.string.doctor_book, onClick = onBook, modifier = Modifier.entrance(4))
}

/** A label on the left and its value on the right; TalkBack reads them together. */
@Composable
internal fun FactRow(@StringRes label: Int, value: String) {
    FactRow(label = stringResource(label), value = value)
}

/** [FactRow] with its label already resolved (a date, say). */
@Composable
internal fun FactRow(label: String, value: String) {
    val colors = GlassTheme.colors
    Row(
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 22.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(text = value, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary, textAlign = TextAlign.End)
    }
}

/** Sunday to Saturday, each day's hours or "Closed", and a note that times are Nepal time. */
@Composable
internal fun WeeklyHours(schedule: Map<Weekday, List<TimeRange>>, modifier: Modifier = Modifier) {
    val colors = GlassTheme.colors
    val locale = currentLocale()
    val rangeTemplate = stringResource(R.string.doctor_hours_range)
    val closed = stringResource(R.string.doctor_hours_closed)
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Weekday.entries.forEach { day ->
            val ranges = schedule[day].orEmpty()
            val hours = if (ranges.isEmpty()) {
                closed
            } else {
                ranges.joinToString("\n") { range ->
                    rangeTemplate.format(LocaleFormat.timeOfDay(range.start, locale), LocaleFormat.timeOfDay(range.end, locale))
                }
            }
            Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    text = LocaleFormat.weekdayShort(day, locale),
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    modifier = Modifier.width(WeekdayColumnWidth),
                )
                Text(
                    text = hours,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ranges.isEmpty()) colors.textSecondary else colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(text = stringResource(R.string.doctor_hours_note), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

private val WeekdayColumnWidth = 64.dp
