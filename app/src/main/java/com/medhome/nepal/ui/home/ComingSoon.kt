package com.medhome.nepal.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.theme.GlassTheme

/** Rows of the Home shortcuts card, in display order. The shell decides where each one goes. */
enum class HomeShortcut(@param:StringRes val title: Int, @param:DrawableRes val icon: Int) {
    FIND_DOCTOR(R.string.shortcut_find_doctor, R.drawable.ic_sym_search),
    HEALTH_RECORDS(R.string.shortcut_health_records, R.drawable.ic_sym_description),
    MEDICINE_REMINDERS(R.string.shortcut_medicine_reminders, R.drawable.ic_sym_alarm),
}

/** Placeholder screen: a title and a "coming soon" card. [showBack] for pushed screens. */
@Composable
fun ComingSoonScreen(
    @StringRes title: Int,
    showBack: Boolean,
) {
    GlassScreen(showBack = showBack, drawBackground = false) {
        ScreenTitle(title = title, modifier = Modifier.entrance(0))
        GlassCard(modifier = Modifier.entrance(1)) {
            Text(
                text = stringResource(R.string.coming_soon_body),
                style = MaterialTheme.typography.bodyLarge,
                color = GlassTheme.colors.textPrimary,
            )
        }
    }
}
