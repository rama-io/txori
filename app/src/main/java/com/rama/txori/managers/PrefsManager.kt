package com.rama.txori.managers

import android.content.Context
import android.content.SharedPreferences
import com.rama.bohio.objects.PrefKeys
import com.rama.bohio.objects.PrefTheme
import org.json.JSONArray
import org.json.JSONObject
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

class PrefsManager private constructor(context: Context) : BohioPrefsManager(context) {

    override val defaultTheme: String = PrefTheme.DRACULA

    // Local preference keys
    object FileKeys {
        const val APP_TIMER = "app:timer"
        const val ROULETTE_TIMER = "roulette:timer"
        const val ROULETTE_LIST = "roulette:list"
        const val SESSION_COLLAPSED_IDS = "session:collapsed_ids"
        const val NOTIFICATION_VIBRATE = "notification:vibrate"
        const val NOTIFICATION_SOUNDS = "notification:sounds"
        const val NOTIFICATION_FLASH = "notification:flash"
        const val NOTIFICATION_FLASH_CAMERA = "notification:flash_camera"
    }

    // Local InitPrefs
    override fun applyAppDefaults(editor: SharedPreferences.Editor) {
        editor.putString(FileKeys.APP_TIMER, "3000")
        editor.putString(FileKeys.ROULETTE_TIMER, "3000")
        editor.putInt(FileKeys.ROULETTE_LIST, -1)
        editor.putBoolean(FileKeys.NOTIFICATION_SOUNDS, true)
        editor.putBoolean(FileKeys.NOTIFICATION_FLASH, false)
        editor.putBoolean(FileKeys.NOTIFICATION_FLASH_CAMERA, false)
        editor.putBoolean(FileKeys.NOTIFICATION_VIBRATE, false)
    }

    /**
     * Restore settings from a JSON object produced by [buildExportJson].
     *
     * bohio exposes [BohioPrefsManager.importFromUri] but only reads from a Uri;
     * Txori embeds the settings block inside a larger backup file, so this works
     * on an already-parsed object. It mirrors bohio's type coercion (including
     * re-coercing [PrefKeys.APP_UI_SCALE] back to a Float).
     */
    fun importFromJson(json: JSONObject): Boolean {
        return try {
            clearAllPrefs()

            val editor = prefs.edit()
            json.keys().forEach { key ->
                when (val value = json.get(key)) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int ->
                        if (key == PrefKeys.APP_UI_SCALE) editor.putFloat(key, value.toFloat())
                        else editor.putInt(key, value)

                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                    is String -> editor.putString(key, value)
                    is JSONArray -> {
                        val set = mutableSetOf<String>()
                        for (i in 0 until value.length()) set.add(value.getString(i))
                        editor.putStringSet(key, set)
                    }
                }
            }
            editor.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context.applicationContext).also {
                    INSTANCE = it
                    register(it)
                }
            }
    }
}
