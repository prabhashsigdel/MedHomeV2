package com.medhome.nepal.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme

/** Between Home's sections: tight enough that all three fit above the tab bar (HomeFitTest). */
private val SectionGap = 14.dp

/** Between a section's title and its card. */
private val TitleGap = 6.dp

/** Empty-state cards keep the side padding of every card but less height. */
private val EmptyCardPadding = PaddingValues(horizontal = GlassDimens.CardPadding, vertical = 14.dp)

/**
 * Patient home: date, greeting and the profile avatar, then the next appointment, today's
 * medicines and the shortcuts, each a titled section. All three show above the tab bar on a
 * 360x740dp phone without scrolling (HomeFitTest).
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

        // One column with its own rhythm, instead of the screen's wider item spacing.
        Column(verticalArrangement = Arrangement.spacedBy(SectionGap)) {
            HomeSection(title = R.string.home_next_appointment, modifier = Modifier.entrance(1)) {
                EmptyStateCard(title = R.string.home_no_appointments, body = R.string.home_no_appointments_body)
            }

            HomeSection(title = R.string.home_todays_medicines, modifier = Modifier.entrance(2)) {
                EmptyStateCard(title = R.string.home_no_medicines, body = R.string.home_no_medicines_body)
            }

            HomeSection(title = R.string.home_shortcuts, modifier = Modifier.entrance(3)) {
                ShortcutsCard(onShortcut = onShortcut)
            }
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TitleGap)) {
        SectionTitle(text = title, topPadding = 0.dp)
        content()
    }
}

@Composable
private fun EmptyStateCard(
    @StringRes title: Int,
    @StringRes body: Int,
) {
    val colors = GlassTheme.colors
    GlassCard(contentPadding = EmptyCardPadding, verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
