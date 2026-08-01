package com.rama.txori.activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import com.rama.txori.CsActivity
import com.rama.txori.R
import com.rama.txori.activities.settings.SettingsAppearanceController
import com.rama.txori.activities.settings.SettingsBasicController
import com.rama.txori.activities.settings.SettingsCheckboxController
import com.rama.txori.activities.settings.SettingsDataController
import com.rama.txori.activities.settings.SettingsLanguageController
import com.rama.txori.managers.PrefsManager

class SettingsActivity : CsActivity() {
    private lateinit var appearanceController: SettingsAppearanceController
    private lateinit var settingsRootView: View
    val FONT_PICK_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.view_settings)

        settingsRootView = findViewById(R.id.settings_root)
        applyEdgeToEdgePadding(settingsRootView)
        applyCurrentTheme(settingsRootView)

        SettingsBasicController(this).setup()
        appearanceController = SettingsAppearanceController(this).also { it.setup() }
        SettingsLanguageController(this).setup()
        SettingsCheckboxController(this).setup()
        SettingsDataController(this).setup()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        appearanceController.onActivityResult(requestCode, resultCode, data)
    }
}