package com.medhome.nepal.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.SettingsRow
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Extra space above each Home section, on top of the screen's item spacing and the title's own padding. */
private val SectionGap = 12.dp

/**
 * Patient home: date, greeting and the profile avatar, then the next appointment, today's
 * medicines and the shortcuts, each a titled section with room around it.
 */
@Composable
fun PatientHomeScreen(
    profile: UserProfile,
    onOpenProfile: () -> Unit,
    onShortcut: (HomeShortcut) -> Unit,
) {
    GlassScreen(drawBackground = false) {
        HomeHeader(
            name = profile.name,
            firstName = profile.firstName,
            onOpenProfile = onOpenProfile,
            modifier = Modifier.entrance(0),
        )

        HomeSection(title = R.string.home_next_appointment, modifier = Modifier.padding(top = SectionGap).entrance(1)) {
            EmptyStateCard(title = R.string.home_no_appointments, body = R.string.home_no_appointments_body)
        }

        HomeSection(title = R.string.home_todays_medicines, modifier = Modifier.padding(top = SectionGap).entrance(2)) {
            EmptyStateCard(title = R.string.home_no_medicines, body = R.string.home_no_medicines_body)
        }

        HomeSection(title = R.string.home_shortcuts, modifier = Modifier.padding(top = SectionGap).entrance(3)) {
            ShortcutsCard(onShortcut = onShortcut)
        }
    }
}

/** A section title held close to its card (the gap between sections is larger). */
@Composable
private fun HomeSection(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = title)
        content()
    }
}

@Composable
private fun EmptyStateCard(
    @StringRes title: Int,
    @StringRes body: Int,
) {
    val colors = GlassTheme.colors
    GlassCard(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

/** One glass card for all shortcuts: rows are not blurred individually (cheap to scroll). */
@Composable
private fun ShortcutsCard(onShortcut: (HomeShortcut) -> Unit) {
    GlassCard(
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        HomeShortcut.entries.forEachIndexed { index, shortcut ->
            if (index > 0) SettingsDivider()
            SettingsRow(title = shortcut.title, icon = shortcut.icon, onClick = { onShortcut(shortcut) })
        }
    }
}
