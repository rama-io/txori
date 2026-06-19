package com.rama.txori.managers

import android.content.Context
import android.content.SharedPreferences
import com.rama.bohio.objects.PrefTheme
import com.rama.bohio.managers.PrefsManager as BohioPrefsManager

class PrefsManager private constructor(context: Context) : BohioPrefsManager(context) {

    override val defaultTheme: String = PrefTheme.DRACULA

    // Local preference keys
    object FileKeys {
        const val APP_TIMER = "app:timer"
        const val ROULETTE_TIMER = "roulette:timer"
        const val ROULETTE_LIST = "roulette:list"
        const val SESSION_COLLAPSED_IDS = "session:collapsed_ids"
    }

    // Local InitPrefs
    override fun applyAppDefaults(editor: SharedPreferences.Editor) {
        editor.putString(FileKeys.APP_TIMER, "3000")
        editor.putString(FileKeys.ROULETTE_TIMER, "3000")
        editor.putInt(FileKeys.ROULETTE_LIST, -1)
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
