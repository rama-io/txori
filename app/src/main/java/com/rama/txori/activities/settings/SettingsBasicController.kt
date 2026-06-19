package com.rama.txori.activities.settings

import android.content.Intent
import android.provider.Settings
import android.widget.*
import com.rama.txori.R
import com.rama.txori.activities.AboutActivity
import com.rama.txori.activities.MainActivity
import com.rama.txori.activities.SettingsActivity
import com.rama.bohio.util.UiActions

class SettingsBasicController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        UiActions.setupButton(activity, R.id.about_button) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }

        UiActions.setupButton(activity, R.id.close_button) {
            activity.finish()
        }
    }
}