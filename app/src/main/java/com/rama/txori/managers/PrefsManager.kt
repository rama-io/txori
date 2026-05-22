package com.rama.txori.managers

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import org.json.JSONObject

class PrefsManager private constructor(context: Context) {

    val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    object PrefKeys {
        const val FONT_STYLE = "font:style"
        const val FONT_CUSTOM_PATH = "font:custom_path"
        const val APP_LANGUAGE = "app:language"
        const val SYSTEM_BAR_VISIBLE = "system:bar:visible"
        const val SYSTEM_PREVENT_SLEEP = "system:prevent_sleeping"

        const val APP_THEME_NAME = "app:theme:name"
        const val APP_THEME_H1 = "app:theme:h1"
        const val APP_THEME_FOREGROUND = "app:theme:foreground"
        const val APP_THEME_BG_1 = "app:theme:bg_1"
        const val APP_THEME_BG_2 = "app:theme:bg_2"
        const val APP_THEME_BG_3 = "app:theme:bg_3"
        const val APP_THEME_ACCENT_1 = "app:theme:accent_1"
        const val APP_THEME_ACCENT_2 = "app:theme:accent_2"
        const val APP_THEME_ACCENT_3 = "app:theme:accent_3"
        const val APP_THEME_DISABLED = "app:theme:disabled"
        const val APP_THEME_PROGRESS_BAR = "app:theme:progress"
        const val APP_THEME_TASK_FREQUENCY = "app:theme:task_frequency"
        const val APP_THEME_INPUT = "app:theme:input"
        const val APP_THEME_BUTTON_1 = "app:theme:button_1"
        const val APP_THEME_BUTTON_1_SELECTED = "app:theme:button_1_selected"
        const val APP_THEME_BUTTON_2 = "app:theme:button_2"
        const val APP_THEME_DANGER = "app:theme:danger"
        const val APP_THEME_COLLAPSIBLE_HEADER = "app:theme:collapsible_header"

        const val APP_TIMER = "app:timer"
        const val ROULETTE_TIMER = "roulette:timer"
        const val ROULETTE_LIST = "roulette:list"

        const val SETTINGS_SECTION_FONTS = "settings:section:fonts"
        const val SETTINGS_SECTION_SYSTEM = "settings:section:system"
        const val SETTINGS_SECTION_LANGUAGE = "settings:section:language"
        const val SETTINGS_SECTION_THEMES = "settings:section:themes"
    }

    object FontStyle {
        const val DEFAULT = "default"
        const val JERSEY_25 = "jersey-25"
        const val CUSTOM = "custom"
    }

    object Language {
        const val SYSTEM = "system"
        const val FALLBACK = "en"
    }

    object Theme {
        const val RAMA = "rama"
        const val MAKO = "mako"
        const val CATPPUCCIN_MOCHA = "catppuccin_mocha"
        const val DRACULA = "dracula"
        const val MELANGE = "melange"
        const val TOKYO_NIGHT = "tokyo_night"
        const val CUSTOM = "custom"
    }

    fun initPrefs() {
        if (prefs.getString(PrefKeys.FONT_STYLE, null).isNullOrEmpty()) {
            prefs.edit()
                .putString(PrefKeys.FONT_STYLE, FontStyle.JERSEY_25)
                .putString(PrefKeys.APP_LANGUAGE, Language.SYSTEM)

                .putBoolean(PrefKeys.SYSTEM_BAR_VISIBLE, false)

                .putString(PrefKeys.APP_THEME_NAME, Theme.TOKYO_NIGHT)

                .putString(PrefKeys.APP_TIMER, "000000")
                .putString(PrefKeys.ROULETTE_TIMER, "000000")
                .putInt(PrefKeys.ROULETTE_LIST, 0)

                .putBoolean(PrefKeys.SETTINGS_SECTION_FONTS, true)
                .putBoolean(PrefKeys.SETTINGS_SECTION_SYSTEM, true)
                .putBoolean(PrefKeys.SETTINGS_SECTION_LANGUAGE, true)
                .putBoolean(PrefKeys.SETTINGS_SECTION_THEMES, true)

                .apply()
        }
    }

    fun isSystemBarVisible(): Boolean =
        prefs.getBoolean(PrefKeys.SYSTEM_BAR_VISIBLE, false)

    // SETTINGS - FONT

    fun getFontStyle(): String =
        prefs.getString(PrefKeys.FONT_STYLE, "") ?: ""

    fun setFontStyle(style: String) =
        prefs.edit().putString(PrefKeys.FONT_STYLE, style).apply()

    fun getCustomFontPath(): String =
        prefs.getString(PrefKeys.FONT_CUSTOM_PATH, "") ?: ""

    fun setCustomFontPath(path: String) =
        prefs.edit().putString(PrefKeys.FONT_CUSTOM_PATH, path).apply()

    fun getTheme(): String =
        prefs.getString(PrefKeys.APP_THEME_NAME, "") ?: ""

    fun setTheme(style: String) =
        prefs.edit().putString(PrefKeys.APP_THEME_NAME, style).apply()

    fun getCustomThemeColor(key: String, fallback: Int): Int =
        prefs.getInt(key, fallback)

    fun setCustomThemeColor(key: String, color: Int) =
        prefs.edit().putInt(key, color).apply()

    fun getAppLanguage(): String {
        return prefs.getString(PrefKeys.APP_LANGUAGE, Language.SYSTEM) ?: Language.SYSTEM
    }

    fun setAppLanguage(language: String) {
        prefs.edit().putString(PrefKeys.APP_LANGUAGE, language).apply()
    }


    // GENERIC HELPERS

    fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        prefs.getBoolean(key, defaultValue)

    fun setBoolean(key: String, value: Boolean) =
        prefs.edit().putBoolean(key, value).apply()

    fun getString(key: String, defaultValue: String = ""): String =
        prefs.getString(key, defaultValue) ?: defaultValue

    fun setString(key: String, value: String) =
        prefs.edit().putString(key, value).apply()

    // Core builder

    private fun buildExportJson(): JSONObject {
        val json = JSONObject()

        val sortedEntries = prefs.all.entries
            .sortedBy { it.key }

        sortedEntries.forEach { (key, value) ->
            Log.d("mako-export", "$key = $value")

            when (value) {
                is Boolean -> json.put(key, value)
                is Int -> json.put(key, value)
                is Long -> json.put(key, value)
                is Float -> json.put(key, value)
                is String -> json.put(key, value)

                is Set<*> -> {
                    val array = org.json.JSONArray()
                    value.forEach { item ->
                        array.put(item)
                    }
                    json.put(key, array)
                }

                else -> json.put(key, value.toString())
            }
        }

        return json
    }

    // Export to SAF (user picked location)

    fun exportToUri(context: Context, uri: Uri): Boolean {
        return try {
            val json = buildExportJson()

            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toString(2).toByteArray())
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun clearAllPrefs(): Result<Unit> {
        return try {
            prefs.edit().clear().commit()
            initPrefs()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}