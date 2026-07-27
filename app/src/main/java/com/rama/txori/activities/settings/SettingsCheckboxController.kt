package com.rama.txori.activities.settings

import android.view.View
import com.rama.bohio.objects.PrefKeys
import com.rama.txori.R
import com.rama.txori.activities.SettingsActivity
import com.rama.bohio.widgets.WdCheckbox
import com.rama.txori.managers.PrefsManager

class SettingsCheckboxController(private val activity: SettingsActivity) {

    private val prefs get() = activity.prefs

    fun setup() {
        bindWdCheckbox(R.id.show_system_bar, PrefKeys.SYSTEM_BAR_VISIBLE, false)
        bindWdCheckbox(R.id.keep_screen_awake, PrefKeys.SYSTEM_PREVENT_SLEEP, false)
        bindWdCheckbox(R.id.notification_sound, PrefsManager.FileKeys.NOTIFICATION_SOUNDS, true)
        bindWdCheckbox(
            R.id.notification_vibration,
            PrefsManager.FileKeys.NOTIFICATION_VIBRATE,
            false
        )
        bindWdCheckbox(
            R.id.notification_flash,
            PrefsManager.FileKeys.NOTIFICATION_FLASH,
            false
        )
        bindWdCheckbox(
            R.id.notification_camera_flash,
            PrefsManager.FileKeys.NOTIFICATION_FLASH_CAMERA,
            false
        )
    }

    private fun bindWdCheckbox(
        wdCheckboxId: Int,
        key: String,
        defaultValue: Boolean,
        dependentViewIds: List<Int>? = null,
        onChange: ((Boolean) -> Unit)? = null
    ) {
        val checkbox = activity.findViewById<WdCheckbox>(wdCheckboxId)
        val dependents = dependentViewIds?.map { activity.findViewById<View>(it) }

        val isChecked = prefs.getBoolean(key, defaultValue)
        checkbox.setChecked(isChecked)

        dependents?.forEach {
            it.visibility = if (isChecked) View.VISIBLE else View.GONE
        }

        checkbox.setOnCheckedChangeListener { checked ->
            prefs.setBoolean(key, checked)
            dependents?.forEach {
                it.visibility = if (checked) View.VISIBLE else View.GONE
            }
            onChange?.invoke(checked)
        }
    }
}