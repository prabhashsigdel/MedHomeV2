package com.medhome.nepal.ui.language

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.feedbackTween
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

/**
 * English / नेपाली choice. Each option is labelled in its own language so it's recognisable
 * whichever language is active. Choosing one recreates the activity in that language.
 */
@Composable
fun LanguageSwitcher(modifier: Modifier = Modifier) {
    val configuration = LocalConfiguration.current
    val selected = remember(configuration) { LanguageSettings.current(configuration) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LanguageOption(R.string.language_english, selected == AppLanguage.ENGLISH, Modifier.weight(1f)) {
            LanguageSettings.apply(AppLanguage.ENGLISH)
        }
        LanguageOption(R.string.language_nepali, selected == AppLanguage.NEPALI, Modifier.weight(1f)) {
            LanguageSettings.apply(AppLanguage.NEPALI)
        }
    }
}

@Composable
private fun LanguageOption(
    @StringRes label: Int,
    selected: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.fieldFill,
        animationSpec = feedbackTween(),
        label = "languageFill",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.onAccent else colors.textPrimary,
        animationSpec = feedbackTween(),
        label = "languageText",
    )
    Box(
        modifier = modifier
            .heightIn(min = GlassDimens.MinTouchTarget)
            .pressScale(interactionSource)
            .clip(GlassShapes.Chip)
            .background(fill)
            .border(GlassDimens.BorderWidth, colors.fieldBorder, GlassShapes.Chip)
            .selectable(
                selected = selected,
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.RadioButton,
                onClick = { if (!selected) onSelect() },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.labelLarge, color = textColor)
    }
}
