package com.medhome.nepal.ui.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.medhome.nepal.R
import com.medhome.nepal.ui.components.GlassCard
import com.medhome.nepal.ui.components.GlassScreen
import com.medhome.nepal.ui.components.MessageKind
import com.medhome.nepal.ui.components.ScreenTitle
import com.medhome.nepal.ui.components.SettingsDivider
import com.medhome.nepal.ui.components.StatusMessage
import com.medhome.nepal.ui.motion.MotionTokens
import com.medhome.nepal.ui.motion.entrance
import com.medhome.nepal.ui.motion.motionSpec
import com.medhome.nepal.ui.theme.GlassDimens
import com.medhome.nepal.ui.theme.GlassTheme

private class TextSection(@param:StringRes val title: Int, @param:StringRes val body: Int)

/** Only questions that are true of the app today; features not built yet are not described. */
private val Faqs = listOf(
    TextSection(R.string.faq_profile_q, R.string.faq_profile_a),
    TextSection(R.string.faq_password_q, R.string.faq_password_a),
    TextSection(R.string.faq_appearance_q, R.string.faq_appearance_a),
    TextSection(R.string.faq_data_q, R.string.faq_data_a),
    TextSection(R.string.faq_delete_q, R.string.faq_delete_a),
    TextSection(R.string.faq_coming_q, R.string.faq_coming_a),
)

/** Frequently asked questions as an accordion in one card (rows are not blurred one by one). */
@Composable
fun HelpCenterScreen() {
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.settings_help_center, subtitle = R.string.help_faq_title, modifier = Modifier.entrance(0))
        GlassCard(
            modifier = Modifier.entrance(1),
            contentPadding = PaddingValues(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Faqs.forEachIndexed { index, faq ->
                if (index > 0) SettingsDivider()
                FaqItem(faq)
            }
        }
    }
}

@Composable
private fun FaqItem(faq: TextSection) {
    val colors = GlassTheme.colors
    var expanded by rememberSaveable { mutableStateOf(false) }
    val expandedLabel = stringResource(R.string.state_expanded)
    val collapsedLabel = stringResource(R.string.state_collapsed)
    val spec = motionSpec(tween<Float>(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = GlassDimens.MinTouchTarget)
            .clickable(role = Role.Button) { expanded = !expanded }
            .semantics { stateDescription = if (expanded) expandedLabel else collapsedLabel }
            .padding(horizontal = GlassDimens.CardPadding, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = stringResource(faq.title), style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(spec) + expandVertically(motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))),
            exit = fadeOut(spec) + shrinkVertically(motionSpec(tween(MotionTokens.FEEDBACK_MS, easing = MotionTokens.EaseOut))),
        ) {
            Text(text = stringResource(faq.body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        }
    }
}

private val PrivacySections = listOf(
    TextSection(R.string.privacy_what_title, R.string.privacy_what_body),
    TextSection(R.string.privacy_where_title, R.string.privacy_where_body),
    TextSection(R.string.privacy_passwords_title, R.string.privacy_passwords_body),
    TextSection(R.string.privacy_sharing_title, R.string.privacy_sharing_body),
    TextSection(R.string.privacy_delete_title, R.string.privacy_delete_body),
)

private val TermsSections = listOf(
    TextSection(R.string.terms_project_title, R.string.terms_project_body),
    TextSection(R.string.terms_medical_title, R.string.terms_medical_body),
    TextSection(R.string.terms_account_title, R.string.terms_account_body),
    TextSection(R.string.terms_changes_title, R.string.terms_changes_body),
)

@Composable
fun PrivacyPolicyScreen() = LegalScreen(title = R.string.settings_privacy_policy, sections = PrivacySections)

@Composable
fun TermsOfServiceScreen() = LegalScreen(title = R.string.settings_terms, sections = TermsSections)

/** Placeholder legal text, clearly marked as a draft until it's replaced before release. */
@Composable
private fun LegalScreen(@StringRes title: Int, sections: List<TextSection>) {
    val colors = GlassTheme.colors
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = title, modifier = Modifier.entrance(0))
        StatusMessage(message = R.string.legal_draft_banner, kind = MessageKind.Warning, modifier = Modifier.entrance(1))
        GlassCard(modifier = Modifier.entrance(2)) {
            sections.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(section.title),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(text = stringResource(section.body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
fun AboutScreen() {
    val colors = GlassTheme.colors
    val context = LocalContext.current
    val version = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }
    GlassScreen(showBack = true, drawBackground = false) {
        ScreenTitle(title = R.string.settings_about, modifier = Modifier.entrance(0))
        GlassCard(modifier = Modifier.entrance(1), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = colors.textPrimary)
            Text(text = stringResource(R.string.about_version, version), style = MaterialTheme.typography.labelLarge, color = colors.link)
            Text(text = stringResource(R.string.about_description), style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary)
            Text(text = stringResource(R.string.about_student_project), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(text = stringResource(R.string.about_fonts), style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}
