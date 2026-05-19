package com.rama.txori.activities.settings

import android.content.Intent
import android.provider.Settings
import android.widget.*
import com.rama.txori.R
import com.rama.txori.activities.AboutActivity
import com.rama.txori.activities.MainActivity
import com.rama.txori.activities.SettingsActivity
import com.rama.txori.utils.SettingsUiUtils

class SettingsBasicController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        SettingsUiUtils.setupButton(activity, R.id.about_button) {
            activity.startActivity(Intent(activity, AboutActivity::class.java))
        }

        SettingsUiUtils.setupButton(activity, R.id.close_button) {
            activity.finish()
        }
    }
}