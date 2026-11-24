package com.filesecuritytool.android.data.settings

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

private val Context.settingsDataStore by preferencesDataStore("app_settings_v2")

enum class AppLanguage(val tag: String) { ENGLISH("en"), CHINESE("zh") }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppSettings(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
)

class AppSettingsRepository(private val context: Context) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { values ->
        AppSettings(
            language = AppLanguage.entries.firstOrNull {
                it.tag == values[LANGUAGE]
            } ?: AppLanguage.ENGLISH,
            themeMode = runCatching {
                ThemeMode.valueOf(values[THEME] ?: ThemeMode.SYSTEM.name)
            }.getOrDefault(ThemeMode.SYSTEM)
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.settingsDataStore.edit { it[LANGUAGE] = language.tag }
        context.getSharedPreferences(BOOT_PREFS, Context.MODE_PRIVATE)
            .edit().putString(BOOT_LANGUAGE, language.tag).apply()
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
    }

    suspend fun setTheme(mode: ThemeMode) {
        context.settingsDataStore.edit { it[THEME] = mode.name }
    }

    companion object {
        private val LANGUAGE = stringPreferencesKey("language")
        private val THEME = stringPreferencesKey("theme")
        const val BOOT_PREFS = "boot_settings"
        const val BOOT_LANGUAGE = "language"
    }
}
