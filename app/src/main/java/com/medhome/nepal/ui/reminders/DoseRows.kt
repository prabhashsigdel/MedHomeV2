package com.medhome.nepal.ui.reminders

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.domain.DoseState
import com.medhome.nepal.domain.TodayDose
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.StatusChip
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme

/** Test tag on every dose row. */
const val DOSE_ROW_TAG = "dose_row"

/**
 * One dose: time, medicine and dose, and its state as a chip (words, not just colour). Tapping
 * marks it taken, or not taken again; TalkBack reads it as a checkbox with the state.
 */
@Composable
fun DoseRow(dose: TodayDose, onToggle: (TodayDose) -> Unit) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val state = stringResource(dose.state.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GlassDimens.MinTouchTarget)
            .pressScale(interactionSource)
            .toggleable(
                value = dose.state == DoseState.TAKEN,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Checkbox,
                onValueChange = { onToggle(dose) },
            )
            .semantics { stateDescription = state }
            .testTag(DOSE_ROW_TAG)
            .padding(horizontal = GlassDimens.CardPadding, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = LocaleFormat.timeOfDay(dose.dose.time, currentLocale()),
            style = MaterialTheme.typography.labelLarge,
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.medicine_name_dose, dose.medicine.name, dose.medicine.dose),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        StatusChip(text = dose.state.label, emphasized = dose.state == DoseState.TAKEN)
    }
}

/**
 * Home's "Today's medicines": the dose that needs doing first (missed, then upcoming, then the
 * last taken) and how many more there are today; the bell opens them all. Kept to about the
 * other cards' height so Home still fits above the tab bar (HomeFitTest).
 */
@Composable
fun TodayMedicinesCard(
    state: TodayReminders,
    onToggle: (TodayDose) -> Unit,
    emptyCard: @Composable (title: Int, body: Int) -> Unit,
) {
    val first = (state.missed + state.upcoming).firstOrNull() ?: state.doses.lastOrNull { it.state == DoseState.TAKEN }
    when {
        state.loading || !state.hasMedicines -> emptyCard(R.string.home_no_medicines, R.string.home_no_medicines_body)
        first == null -> emptyCard(R.string.home_no_medicines, R.string.home_no_doses_today_body)
        else -> GlassCard(contentPadding = PaddingValues(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
            DoseRow(dose = first, onToggle = onToggle)
            val more = state.doses.size - 1
            if (more > 0) {
                Text(
                    text = pluralStringResource(R.plurals.home_more_doses, more, LocaleFormat.number(more, currentLocale())),
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTheme.colors.textSecondary,
                    modifier = Modifier.padding(start = GlassDimens.CardPadding, end = GlassDimens.CardPadding, bottom = 6.dp),
                )
            }
        }
    }
}
