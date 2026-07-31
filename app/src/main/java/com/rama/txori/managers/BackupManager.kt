package com.rama.txori.managers

import android.content.Context
import android.net.Uri
import com.rama.txori.DatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Serializes and restores all user data (activity sessions and app settings)
 * to/from a single portable JSON file chosen through the Storage Access
 * Framework. This powers the "backup / move to a new device" flow.
 *
 * File format (schema 1):
 * ```
 * {
 *   "app": "txori",
 *   "schema": 1,
 *   "exportedAt": "2025-07-31T12:00:00Z",
 *   "sessions": [
 *     {
 *       "name": "Morning Reset",
 *       "order": 1,
 *       "steps": [
 *         { "label": "Drink Water", "duration": "000500", "restDuration": "000000", "order": 1 }
 *       ]
 *     }
 *   ],
 *   "settings": { ...SharedPreferences export... }
 * }
 * ```
 *
 * Design notes:
 * - Sessions/steps are exported by value (label + durations + order), never by
 *   primary key. On import they are recreated through the same helpers the UI
 *   uses ([DatabaseHelper.createSession] / [DatabaseHelper.addTaskToSession]),
 *   which de-duplicates tasks and assigns fresh ids. This sidesteps id
 *   collisions and merge conflicts entirely.
 * - Restore runs inside a single DB transaction and the file is fully validated
 *   before anything is deleted, so a bad file can never half-wipe the database.
 */
object BackupManager {

    private const val APP_ID = "txori"
    const val SCHEMA = 1
    private val SUPPORTED_SCHEMAS = setOf(1)

    /** Outcome of a successful export or import. */
    data class Summary(val sessions: Int, val steps: Int)

    /** User-facing failure. [message] is safe to show in a toast. */
    sealed class BackupError(message: String) : Exception(message) {
        class Io(message: String) : BackupError(message)
        class Parse(message: String) : BackupError(message)
    }

    //region Export

    fun buildBackupJson(context: Context): JSONObject {
        val dbHelper = DatabaseHelper(context)
        val db = dbHelper.readableDatabase
        try {
            val sessionsJson = JSONArray()
            // getSessions() is ordered by session_order, so the array index is the order.
            dbHelper.getSessions(db).forEachIndexed { index, (sessionId, name) ->
                val stepsJson = JSONArray()
                // getSessionTasks() is ordered by step_order.
                dbHelper.getSessionTasks(db, sessionId).forEachIndexed { stepIndex, task ->
                    stepsJson.put(
                        JSONObject()
                            .put("label", task.label)
                            .put("duration", task.duration)
                            .put("restDuration", task.restDuration)
                            .put("order", stepIndex + 1)
                    )
                }
                sessionsJson.put(
                    JSONObject()
                        .put("name", name)
                        .put("order", index + 1)
                        .put("steps", stepsJson)
                )
            }

            return JSONObject()
                .put("app", APP_ID)
                .put("schema", SCHEMA)
                .put("exportedAt", iso8601Now())
                .put("sessions", sessionsJson)
                .put("settings", PrefsManager.getInstance(context).buildExportJson())
        } finally {
            db.close()
        }
    }

    fun exportToUri(context: Context, uri: Uri): Result<Summary> = runCatching {
        val json = buildBackupJson(context)

        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(json.toString(2).toByteArray(Charsets.UTF_8))
        } ?: throw BackupError.Io("Could not open the selected file for writing")

        val sessions = json.getJSONArray("sessions")
        var steps = 0
        for (i in 0 until sessions.length()) {
            steps += sessions.getJSONObject(i).optJSONArray("steps")?.length() ?: 0
        }
        Summary(sessions.length(), steps)
    }

    //endregion

    //region Import

    fun importFromUri(context: Context, uri: Uri): Result<Summary> = runCatching {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader().readText()
        } ?: throw BackupError.Io("Could not open the selected file for reading")

        val json = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupError.Parse("That file is not a valid backup")
        }

        // Validate everything BEFORE touching any local data.
        if (json.optString("app") != APP_ID) {
            throw BackupError.Parse("That file is not a Txori backup")
        }
        val schema = json.optInt("schema", -1)
        if (schema !in SUPPORTED_SCHEMAS) {
            throw BackupError.Parse("Unsupported backup version ($schema)")
        }
        val sessions = json.optJSONArray("sessions")
            ?: throw BackupError.Parse("That backup contains no lists")

        val summary = restoreSessions(context, sessions)

        // Settings are best-effort: a malformed settings block should not fail
        // the (already committed) list restore.
        json.optJSONObject("settings")?.let {
            PrefsManager.getInstance(context).importFromJson(it)
        }

        // Session ids are recreated on restore, so any saved "selected roulette
        // list" id now points nowhere. Reset it so the roulette screen picks a
        // sensible default instead of selecting nothing.
        PrefsManager.getInstance(context).prefs.edit()
            .putInt(PrefsManager.FileKeys.ROULETTE_LIST, -1)
            .apply()

        summary
    }

    /** Wipe and rebuild all sessions/steps/tasks in one transaction. */
    private fun restoreSessions(context: Context, sessions: JSONArray): Summary {
        val dbHelper = DatabaseHelper(context)
        val db = dbHelper.writableDatabase
        var sessionCount = 0
        var stepCount = 0

        db.beginTransaction()
        try {
            dbHelper.clearAllUserData(db)

            for (i in 0 until sessions.length()) {
                val session = sessions.getJSONObject(i)
                val name = session.optString("name", "").ifBlank { "Imported" }
                val sessionId = dbHelper.createSession(db, name)
                sessionCount++

                val steps = session.optJSONArray("steps") ?: continue
                for (j in 0 until steps.length()) {
                    val step = steps.getJSONObject(j)
                    val label = step.optString("label", "").trim()
                    if (label.isEmpty()) continue

                    dbHelper.addTaskToSession(
                        db,
                        sessionId,
                        label,
                        step.optString("duration", "000000"),
                        step.optString("restDuration", "000000")
                    )
                    stepCount++
                }
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
            db.close()
        }

        return Summary(sessionCount, stepCount)
    }

    //endregion

    private fun iso8601Now(): String {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date())
    }
}
