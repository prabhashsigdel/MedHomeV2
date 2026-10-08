package com.medhome.nepal.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.security.MessageDigest

/** Remembers which accounts were already offered "save password", so we ask only once. */
interface SavePromptHistory {
    fun wasOffered(email: String): Boolean
    fun markOffered(email: String)
}

/**
 * Stores a one-way SHA-256 hash of the lowercased email, never the email or the password.
 * Excluded from backup and device transfer by data_extraction_rules.
 */
class SharedPrefsSavePromptHistory(context: Context) : SavePromptHistory {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun wasOffered(email: String): Boolean =
        prefs.getStringSet(KEY_OFFERED, emptySet()).orEmpty().contains(hashOf(email))

    override fun markOffered(email: String) {
        val updated = prefs.getStringSet(KEY_OFFERED, emptySet()).orEmpty() + hashOf(email)
        prefs.edit { putStringSet(KEY_OFFERED, updated) }
    }

    private companion object {
        const val PREFS_NAME = "password_save_prompts"
        const val KEY_OFFERED = "offered_account_hashes"
    }
}

internal fun hashOf(email: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(email.trim().lowercase().toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

/** One pending "save this password?" offer. The password lives in memory only, briefly. */
class PendingPasswordSave(val email: String, val password: String) {
    override fun toString(): String = "PendingPasswordSave(email=██, password=██)"
}

/**
 * Hand-off between a screen that just signed someone in with a typed password and the
 * activity-level prompter that shows the system "save password" sheet. The sign-in screen goes
 * away on success, so it can't show the sheet itself.
 */
class PasswordSaveOffers(private val history: SavePromptHistory) {
    private val _pending = MutableStateFlow<PendingPasswordSave?>(null)
    val pending: StateFlow<PendingPasswordSave?> = _pending.asStateFlow()

    /** Queues an offer unless this account was already offered (saved or declined). */
    fun offer(email: String, password: String) {
        if (history.wasOffered(email)) return
        _pending.value = PendingPasswordSave(email, password)
    }

    /**
     * After a password change: always offer, even if this account was asked before, because the
     * saved password (if any) is now out of date. Saving replaces it in the password manager.
     */
    fun offerUpdate(email: String, password: String) {
        _pending.value = PendingPasswordSave(email, password)
    }

    /** Takes the pending offer (at most once) so the password doesn't linger in memory. */
    fun take(): PendingPasswordSave? = _pending.getAndUpdate { null }

    /** Drops any pending offer without showing it (e.g. the user signed out first). */
    fun discard() {
        _pending.value = null
    }

    /** Saved or declined both mean "don't ask again"; an unavailable password manager doesn't. */
    fun record(email: String, outcome: SaveOutcome) {
        if (outcome != SaveOutcome.UNAVAILABLE) history.markOffered(email)
    }
}
