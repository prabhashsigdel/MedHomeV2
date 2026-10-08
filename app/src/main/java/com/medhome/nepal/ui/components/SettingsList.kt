package com.medhome.nepal.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.motion.feedbackTween
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassShapes
import com.medhome.nepal.ui.theme.GlassTheme

private val RowMinHeight = 56.dp

/**
 * A titled group: heading plus one glass card holding its rows. One card per section keeps the
 * blur to a few surfaces (rows themselves are never blurred individually).
 */
@Composable
fun SettingsSection(
    @StringRes title: Int,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(text = title)
        GlassCard(
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            content = content,
        )
    }
}

/** Thin separator between rows of a [SettingsSection]. */
@Composable
fun SettingsDivider() {
    HorizontalDivider(
        color = GlassTheme.colors.glassBorder,
        modifier = Modifier.padding(horizontal = GlassDimens.CardPadding),
    )
}

/** A tappable row with a title, optional supporting line and a chevron. */
@Composable
fun SettingsRow(
    @StringRes title: Int,
    onClick: () -> Unit,
    @StringRes subtitle: Int? = null,
    enabled: Boolean = true,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = RowMinHeight)
            .pressScale(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = GlassDimens.CardPadding, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            if (subtitle != null) {
                Text(
                    text = stringResource(subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = colors.link,
        )
    }
}

/** A labelled control inside a [SettingsSection] (e.g. Theme with its choices). */
@Composable
fun SettingsControl(
    @StringRes label: Int,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = GlassDimens.CardPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = stringResource(label), style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.textPrimary)
        content()
    }
}

/** One option of a [SegmentedChoice]. [label] is final text (some labels are never translated). */
class ChoiceOption<T>(val value: T, val label: String)

/**
 * Pill-shaped single choice (radio group semantics for TalkBack). The selected pill is filled
 * with the accent; selecting it again does nothing.
 */
@Composable
fun <T> SegmentedChoice(
    options: List<ChoiceOption<T>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoicePill(
                label = option.label,
                selected = option.value == selected,
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onSelect = { if (option.value != selected) onSelect(option.value) },
            )
        }
    }
}

@Composable
private fun ChoicePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier,
    onSelect: () -> Unit,
) {
    val colors = GlassTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val fill by animateColorAsState(
        targetValue = if (selected) colors.accent else colors.fieldFill,
        animationSpec = feedbackTween(),
        label = "choiceFill",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) colors.onAccent else colors.textPrimary,
        animationSpec = feedbackTween(),
        label = "choiceText",
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
                enabled = enabled,
                role = Role.RadioButton,
                onClick = onSelect,
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            textAlign = TextAlign.Center,
        )
    }
}
