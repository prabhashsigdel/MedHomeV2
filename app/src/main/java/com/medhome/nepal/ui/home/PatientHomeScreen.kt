package com.medhome.nepal.ui.home

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.domain.UserProfile
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.SectionTitle
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme

/** Patient home: first-name greeting, upcoming appointment, today's medicines, shortcuts. */
@Composable
fun PatientHomeScreen(
    profile: UserProfile,
    onOpenFeature: (ComingSoonFeature) -> Unit,
) {
    GlassScreen(drawBackground = false) {
        Greeting(firstName = profile.firstName, modifier = Modifier.entrance(0))

        SectionTitle(text = R.string.home_next_appointment, modifier = Modifier.entrance(1))
        EmptyStateCard(
            title = R.string.home_no_appointments,
            body = R.string.home_no_appointments_body,
            modifier = Modifier.entrance(1),
        )

        SectionTitle(text = R.string.home_todays_medicines, modifier = Modifier.entrance(2))
        EmptyStateCard(
            title = R.string.home_no_medicines,
            body = R.string.home_no_medicines_body,
            modifier = Modifier.entrance(2),
        )

        SectionTitle(text = R.string.home_shortcuts, modifier = Modifier.entrance(3))
        ShortcutsCard(onOpenFeature = onOpenFeature, modifier = Modifier.entrance(3))
    }
}

@Composable
private fun Greeting(firstName: String?, modifier: Modifier = Modifier) {
    val text = if (firstName != null) {
        stringResource(R.string.home_greeting, firstName)
    } else {
        stringResource(R.string.home_greeting_no_name)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.headlineLarge,
        color = GlassTheme.colors.textPrimary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun EmptyStateCard(
    @StringRes title: Int,
    @StringRes body: Int,
    modifier: Modifier = Modifier,
) {
    val colors = GlassTheme.colors
    GlassCard(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(text = stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
    }
}

/** One glass card for all shortcuts: rows are not blurred individually (cheap to scroll). */
@Composable
private fun ShortcutsCard(
    onOpenFeature: (ComingSoonFeature) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassCard(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        ComingSoonFeature.entries.forEachIndexed { index, feature ->
            if (index > 0) {
                HorizontalDivider(
                    color = GlassTheme.colors.glassBorder,
                    modifier = Modifier.padding(horizontal = GlassDimens.CardPadding),
                )
            }
            ShortcutRow(title = feature.title, onClick = { onOpenFeature(feature) })
        }
    }
}

@Composable
private fun ShortcutRow(
    @StringRes title: Int,
    onClick: () -> Unit,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = ShortcutRowHeight)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = GlassDimens.CardPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.link,
        )
    }
}

private val ShortcutRowHeight = 56.dp
