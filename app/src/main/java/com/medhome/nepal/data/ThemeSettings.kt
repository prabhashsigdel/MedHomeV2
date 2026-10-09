package com.medhome.nepal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The user's appearance choice. [key] is what's stored, so renaming an entry never breaks it. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    /** Whether this mode means dark, given the phone's own setting. */
    fun isDark(systemIsDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemIsDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        val DEFAULT = SYSTEM

        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

interface ThemeSettings {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}

/** The app's one settings file. Shared by every settings class: two delegates on one file crash. */
internal val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Stored with DataStore. Excluded from backup and device transfer by data_extraction_rules. */
class DataStoreThemeSettings(context: Context) : ThemeSettings {
    private val dataStore = context.applicationContext.settingsDataStore

    override val themeMode: Flow<ThemeMode> =
        dataStore.data.map { prefs -> ThemeMode.fromKey(prefs[KEY_THEME_MODE]) }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME_MODE] = mode.key }
    }

    /** The stored value, for applying before the first frame. */
    suspend fun current(): ThemeMode = themeMode.first()

    private companion object {
        val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
