package com.medhome.nepal.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The background of the dark theme. [key] is what's stored, so renaming an entry never breaks it. */
enum class DarkPalette(val key: String) {
    WARM_DUSK("warm_dusk"),
    MIDNIGHT_AURORA("midnight_aurora"),
    ;

    companion object {
        val DEFAULT = WARM_DUSK

        fun fromKey(key: String?): DarkPalette = entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}

/** Options for comparing looks on a device. Only builds with BuildConfig.DEVELOPER_OPTIONS read them. */
interface DeveloperSettings {
    val darkPalette: Flow<DarkPalette>

    suspend fun setDarkPalette(palette: DarkPalette)
}

/** Stored next to the theme in the app's settings DataStore. */
class DataStoreDeveloperSettings(context: Context) : DeveloperSettings {
    private val dataStore = context.applicationContext.settingsDataStore

    override val darkPalette: Flow<DarkPalette> =
        dataStore.data.map { prefs -> DarkPalette.fromKey(prefs[KEY_DARK_PALETTE]) }

    override suspend fun setDarkPalette(palette: DarkPalette) {
        dataStore.edit { it[KEY_DARK_PALETTE] = palette.key }
    }

    /** The stored value, for applying before the first frame. */
    suspend fun currentDarkPalette(): DarkPalette = darkPalette.first()

    private companion object {
        val KEY_DARK_PALETTE = stringPreferencesKey("developer_dark_palette")
    }
}
