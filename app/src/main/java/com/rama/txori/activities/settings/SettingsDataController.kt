package com.rama.txori.activities.settings

import android.app.AlertDialog
import android.content.Intent
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.rama.bohio.util.UiActions
import com.rama.txori.R
import com.rama.txori.activities.SettingsActivity
import com.rama.txori.managers.BackupManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings section for backing up and restoring all Txori data.
 *
 * Export and import use the Storage Access Framework via the modern
 * [ActivityResultLauncher] API. Import is destructive (it replaces every list
 * and setting), so it is guarded by a confirmation dialog and followed by an
 * app restart so the retained home/roulette fragments reload the new data.
 */
class SettingsDataController(private val activity: SettingsActivity) {

    private lateinit var exportLauncher: ActivityResultLauncher<String>
    private lateinit var importLauncher: ActivityResultLauncher<Array<String>>

    fun setup() {
        exportLauncher = activity.registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { uri ->
            if (uri == null) return@registerForActivityResult // user cancelled
            BackupManager.exportToUri(activity, uri)
                .onSuccess {
                    toast(activity.getString(R.string.toast_backup_exported, it.sessions, it.steps))
                }
                .onFailure { toast(it.message ?: defaultError()) }
        }

        importLauncher = activity.registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri == null) return@registerForActivityResult // user cancelled
            BackupManager.importFromUri(activity, uri)
                .onSuccess {
                    toast(activity.getString(R.string.toast_backup_imported, it.sessions, it.steps))
                    // Lists are loaded in fragment onViewCreated and fragments are
                    // retained across navigation, so restart to rebuild them cleanly.
                    restartApp()
                }
                .onFailure { toast(it.message ?: defaultError()) }
        }

        UiActions.setupButton(activity, R.id.export_data_button) {
            exportLauncher.launch(defaultExportFileName())
        }

        UiActions.setupButton(activity, R.id.import_data_button) {
            confirmImport()
        }
    }

    private fun confirmImport() {
        AlertDialog.Builder(activity)
            .setTitle(R.string.dialog_import_title)
            .setMessage(R.string.dialog_import_message)
            .setPositiveButton(R.string.dialog_import_confirm) { _, _ ->
                // JSON files are labelled inconsistently across file providers, so
                // allow any type and validate the contents ourselves.
                importLauncher.launch(arrayOf("*/*"))
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun defaultExportFileName(): String {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        return "txori_backup_$date.json"
    }

    private fun restartApp() {
        val intent = activity.packageManager
            .getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            ?: return
        activity.startActivity(intent)
        activity.finish()
    }

    private fun defaultError(): String = activity.getString(R.string.toast_backup_failed)

    private fun toast(message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
