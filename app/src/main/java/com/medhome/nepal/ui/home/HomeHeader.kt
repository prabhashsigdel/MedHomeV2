package com.medhome.nepal.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.medhome.nepal.R
import com.medhome.nepal.ui.common.LocaleFormat
import com.medhome.nepal.ui.common.currentLocale
import com.medhome.nepal.ui.components.glassBorder
import com.medhome.nepal.ui.motion.pressScale
import com.medhome.nepal.ui.theme.GlassTheme
import java.util.Date

private val AvatarSize = 48.dp
private val BadgeSize = 8.dp

/** Test tag on the Home avatar (the way into Profile). */
const val HOME_AVATAR_TAG = "home_avatar"

/** Test tag on the avatar's person icon, shown when the name has no initials. */
const val HOME_AVATAR_ICON_TAG = "home_avatar_icon"

/** Test tag on the Home bell (the way into today's reminders). */
const val HOME_BELL_TAG = "home_bell"

/**
 * Today's date over the greeting on the left; on the right the reminders bell (when
 * [onOpenReminders] is given) and the profile avatar.
 */
@Composable
fun HomeHeader(
    name: String,
    firstName: String?,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenReminders: (() -> Unit)? = null,
    /** Doses missed today: a dot on the bell, and said by TalkBack. */
    missedCount: Int = 0,
) {
    val colors = GlassTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val today = rememberTodayMillis()
    val dateText = remember(today, locale) { homeDateText(Date(today), locale) }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = dateText, style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
            Text(
                text = if (firstName != null) {
                    stringResource(R.string.home_greeting, firstName)
                } else {
                    stringResource(R.string.home_greeting_no_name)
                },
                style = MaterialTheme.typography.headlineLarge,
                color = colors.textPrimary,
                modifier = Modifier.semantics { heading() },
            )
        }
        Spacer(Modifier.width(16.dp))
        if (onOpenReminders != null) {
            RemindersBell(missedCount = missedCount, onClick = onOpenReminders)
            Spacer(Modifier.width(8.dp))
        }
        ProfileAvatar(name = name, onClick = onOpenProfile)
    }
}

/** Glass circle with a bell, like the avatar; a dot (and the count, for TalkBack) when doses were missed. */
@Composable
private fun RemindersBell(missedCount: Int, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val description = if (missedCount > 0) {
        pluralStringResource(R.plurals.home_bell_missed, missedCount, LocaleFormat.number(missedCount, currentLocale()))
    } else {
        stringResource(R.string.home_bell)
    }
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .pressScale(interactionSource)
            .clip(CircleShape)
            .background(colors.glassFill)
            .glassBorder(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .testTag(HOME_BELL_TAG),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painter = painterResource(R.drawable.ic_sym_notifications), contentDescription = null, tint = colors.textPrimary)
        if (missedCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 11.dp, end = 12.dp)
                    .size(BadgeSize)
                    .clip(CircleShape)
                    .background(colors.error),
            )
        }
    }
}

/** Circular glass initials badge (primary text, no accent); a person icon when there are none. */
@Composable
private fun ProfileAvatar(name: String, onClick: () -> Unit) {
    val colors = GlassTheme.colors
    val initials = remember(name) { initialsOf(name) }
    val description = stringResource(R.string.profile_title)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .pressScale(interactionSource)
            .clip(CircleShape)
            .background(colors.glassFill)
            .glassBorder(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .semantics { contentDescription = description }
            .testTag(HOME_AVATAR_TAG),
        contentAlignment = Alignment.Center,
    ) {
        if (initials != null) {
            // Decorative: the button is announced as "Profile".
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                maxLines = 1,
                modifier = Modifier.clearAndSetSemantics {},
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_sym_person),
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.testTag(HOME_AVATAR_ICON_TAG),
            )
        }
    }
}

/**
 * Now, refreshed when the day can have changed: on resume, at midnight, and when the clock or
 * time zone is changed, so the date line never shows yesterday.
 */
@Composable
private fun rememberTodayMillis(): Long {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LifecycleResumeEffect(Unit) {
        now = System.currentTimeMillis()
        onPauseOrDispose {}
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                now = System.currentTimeMillis()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        // System broadcasts are delivered whatever the export flag; nothing else can send these.
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }
    return now
}
