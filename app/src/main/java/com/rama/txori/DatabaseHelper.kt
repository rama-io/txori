package com.rama.txori

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues

class DatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, "tasks.db", null, 3) {

    override fun onCreate(db: SQLiteDatabase) {

        // TASKS
        db.execSQL(
            """
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                label TEXT,
                duration TEXT,
                rest_duration TEXT DEFAULT '000000'
            )
            """.trimIndent()
        )

        // SESSIONS (was groups)
        db.execSQL(
            """
            CREATE TABLE sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT,
                session_order INTEGER DEFAULT 0
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE session_steps (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER,
                task_id INTEGER,
                step_order INTEGER,
                FOREIGN KEY(session_id) REFERENCES sessions(id),
                FOREIGN KEY(task_id) REFERENCES tasks(id)
            )
            """.trimIndent()
        )

        insertInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Step 1 (v1→v2): add rest_duration column if missing
        if (oldVersion < 2) {
            try {
                db.execSQL("ALTER TABLE tasks ADD COLUMN rest_duration TEXT DEFAULT '000000'")
            } catch (_: Exception) {
            }
        }

        if (oldVersion < 3) {
            // Ensure rest_duration column exists (may have been added as INTEGER in v2)
            try {
                db.execSQL("ALTER TABLE tasks ADD COLUMN rest_duration TEXT DEFAULT '000000'")
            } catch (_: Exception) {
                // Column already exists — convert existing INTEGER values to hhmmss text
                db.execSQL(
                    """
                    UPDATE tasks SET rest_duration = printf('%02d%02d%02d',
                        CAST(rest_duration AS INTEGER) / 3600,
                        (CAST(rest_duration AS INTEGER) % 3600) / 60,
                        CAST(rest_duration AS INTEGER) % 60)
                    WHERE rest_duration GLOB '[0-9]*' AND length(rest_duration) <= 6
                      AND rest_duration NOT LIKE '__:__:__'
                """.trimIndent()
                )
            }

            // Migrate duration column: create new table, copy converted data, swap
            db.execSQL(
                """
                CREATE TABLE tasks_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    label TEXT,
                    duration TEXT,
                    rest_duration TEXT DEFAULT '000000'
                )
            """.trimIndent()
            )

            db.execSQL(
                """
                INSERT INTO tasks_new (id, label, duration, rest_duration)
                SELECT id, label,
                    printf('%02d%02d%02d',
                        CAST(duration AS INTEGER) / 3600,
                        (CAST(duration AS INTEGER) % 3600) / 60,
                        CAST(duration AS INTEGER) % 60),
                    CASE
                        WHEN rest_duration IS NULL OR rest_duration = '' THEN '000100'
                        WHEN rest_duration GLOB '[0-9][0-9][0-9][0-9][0-9][0-9]' THEN rest_duration
                        ELSE printf('%02d%02d%02d',
                            CAST(rest_duration AS INTEGER) / 3600,
                            (CAST(rest_duration AS INTEGER) % 3600) / 60,
                            CAST(rest_duration AS INTEGER) % 60)
                    END
                FROM tasks
            """.trimIndent()
            )

            db.execSQL("DROP TABLE tasks")
            db.execSQL("ALTER TABLE tasks_new RENAME TO tasks")
        }
    }

    // PUBLIC QUERY HELPERS

    fun getAllTasks(db: SQLiteDatabase): MutableList<Task> {
        val tasks = mutableListOf<Task>()
        val cursor = db.rawQuery(
            "SELECT id, label, duration, rest_duration FROM tasks ORDER BY label", null
        )
        while (cursor.moveToNext()) {
            tasks.add(
                Task(
                    id = cursor.getLong(0),
                    label = cursor.getString(1),
                    duration = cursor.getString(2) ?: "000000",
                    restDuration = cursor.getString(3) ?: "000000"
                )
            )
        }
        cursor.close()
        return tasks
    }

    /** Get tasks for a session, ss.id is carried as stepId so we can delete/reorder the exact row */
    fun getSessionTasks(db: SQLiteDatabase, sessionId: Long): MutableList<Task> {
        val tasks = mutableListOf<Task>()
        val cursor = db.rawQuery(
            """
            SELECT ss.id, t.id, t.label, t.duration, t.rest_duration
            FROM session_steps ss
            JOIN tasks t ON ss.task_id = t.id
            WHERE ss.session_id = ?
            ORDER BY ss.step_order
            """.trimIndent(),
            arrayOf(sessionId.toString())
        )
        while (cursor.moveToNext()) {
            tasks.add(
                Task(
                    stepId = cursor.getLong(0),
                    id = cursor.getLong(1),
                    label = cursor.getString(2),
                    duration = cursor.getString(3) ?: "000000",
                    restDuration = cursor.getString(4) ?: "000000"
                )
            )
        }
        cursor.close()
        return tasks
    }

    fun getSessions(db: SQLiteDatabase): List<Pair<Long, String>> {
        val result = mutableListOf<Pair<Long, String>>()
        val cursor = db.rawQuery("SELECT id, name FROM sessions ORDER BY session_order, id", null)
        while (cursor.moveToNext()) {
            result.add(Pair(cursor.getLong(0), cursor.getString(1)))
        }
        cursor.close()
        return result
    }

    // PUBLIC WRITE HELPERS

    fun createSession(db: SQLiteDatabase, name: String): Long {
        val cursor = db.rawQuery("SELECT COALESCE(MAX(session_order), 0) FROM sessions", null)
        val maxOrder = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        val values = ContentValues().apply {
            put("name", name)
            put("session_order", maxOrder + 1)
        }
        return db.insert("sessions", null, values)
    }

    fun deleteSession(db: SQLiteDatabase, sessionId: Long) {
        db.delete("session_steps", "session_id = ?", arrayOf(sessionId.toString()))
        db.delete("sessions", "id = ?", arrayOf(sessionId.toString()))
    }

    fun addTaskToSession(
        db: SQLiteDatabase,
        sessionId: Long,
        label: String,
        duration: String,
        restDuration: String
    ): Long {
        val taskId = getOrCreateTaskId(db, label, duration, restDuration)
        val cursor = db.rawQuery(
            "SELECT COALESCE(MAX(step_order), 0) FROM session_steps WHERE session_id = ?",
            arrayOf(sessionId.toString())
        )
        val maxOrder = if (cursor.moveToFirst()) cursor.getInt(0) else 0
        cursor.close()
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("task_id", taskId)
            put("step_order", maxOrder + 1)
        }
        db.insert("session_steps", null, values)
        return taskId
    }

    /** Delete a single step row by its own primary key. Avoids removing duplicate tasks across sessions. */
    fun removeStepFromSession(db: SQLiteDatabase, stepId: Long) {
        db.delete("session_steps", "id = ?", arrayOf(stepId.toString()))
    }

    /** Swap step_order of two adjacent tasks within a session. */
    fun swapStepOrder(db: SQLiteDatabase, stepIdA: Long, stepIdB: Long) {
        val cursorA = db.rawQuery(
            "SELECT step_order FROM session_steps WHERE id = ?",
            arrayOf(stepIdA.toString())
        )
        val orderA = if (cursorA.moveToFirst()) cursorA.getInt(0) else return
        cursorA.close()

        val cursorB = db.rawQuery(
            "SELECT step_order FROM session_steps WHERE id = ?",
            arrayOf(stepIdB.toString())
        )
        val orderB = if (cursorB.moveToFirst()) cursorB.getInt(0) else return
        cursorB.close()

        db.execSQL(
            "UPDATE session_steps SET step_order = ? WHERE id = ?",
            arrayOf<Any>(orderB, stepIdA)
        )
        db.execSQL(
            "UPDATE session_steps SET step_order = ? WHERE id = ?",
            arrayOf<Any>(orderA, stepIdB)
        )
    }

    /** Swap session_order of two adjacent sessions. */
    fun swapSessionOrder(db: SQLiteDatabase, sessionIdA: Long, sessionIdB: Long) {
        val cursorA = db.rawQuery(
            "SELECT session_order FROM sessions WHERE id = ?",
            arrayOf(sessionIdA.toString())
        )
        val orderA = if (cursorA.moveToFirst()) cursorA.getInt(0) else return
        cursorA.close()

        val cursorB = db.rawQuery(
            "SELECT session_order FROM sessions WHERE id = ?",
            arrayOf(sessionIdB.toString())
        )
        val orderB = if (cursorB.moveToFirst()) cursorB.getInt(0) else return
        cursorB.close()

        db.execSQL(
            "UPDATE sessions SET session_order = ? WHERE id = ?",
            arrayOf<Any>(orderB, sessionIdA)
        )
        db.execSQL(
            "UPDATE sessions SET session_order = ? WHERE id = ?",
            arrayOf<Any>(orderA, sessionIdB)
        )
    }

    // PRIVATE HELPERS

    private fun getOrCreateTaskId(
        db: SQLiteDatabase,
        label: String,
        duration: String,
        restDuration: String
    ): Long {
        val cursor = db.rawQuery(
            "SELECT id FROM tasks WHERE label = ? AND duration = ? AND rest_duration = ?",
            arrayOf(label, duration.toString(), restDuration.toString())
        )
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            cursor.close()
            return id
        }
        cursor.close()

        val values = ContentValues().apply {
            put("label", label)
            put("duration", duration)
            put("rest_duration", restDuration)
        }
        return db.insert("tasks", null, values)
    }

    private fun insertSession(db: SQLiteDatabase, name: String, order: Int): Long {
        val values = ContentValues().apply {
            put("name", name)
            put("session_order", order)
        }
        return db.insert("sessions", null, values)
    }

    private fun insertSessionStep(
        db: SQLiteDatabase,
        sessionId: Long,
        taskId: Long,
        order: Int
    ) {
        val values = ContentValues().apply {
            put("session_id", sessionId)
            put("task_id", taskId)
            put("step_order", order)
        }
        db.insert("session_steps", null, values)
    }

    fun getAllSessionTasks(db: SQLiteDatabase): MutableList<Task> {
        val tasks = mutableListOf<Task>()
        val cursor = db.rawQuery(
            """
            SELECT ss.id, t.id, t.label, t.duration, t.rest_duration
            FROM session_steps ss
            JOIN tasks t ON ss.task_id = t.id
            ORDER BY ss.session_id, ss.step_order
            """.trimIndent(),
            null
        )
        while (cursor.moveToNext()) {
            tasks.add(
                Task(
                    stepId = cursor.getLong(0),
                    id = cursor.getLong(1),
                    label = cursor.getString(2),
                    duration = cursor.getString(3) ?: "000000",
                    restDuration = cursor.getString(4) ?: "000000"
                )
            )
        }
        cursor.close()
        return tasks
    }

    private fun insertInitialData(db: SQLiteDatabase) {
        var n = 1
        val s0Id = insertSession(db, "Morning Reset", 1)
        fun s0(label: String, duration: String, restDuration: String = "000000") {
            insertSessionStep(db, s0Id, getOrCreateTaskId(db, label, duration, restDuration), n++)
        }

        s0("Drink Water", "000500")
        s0("Brush Teeth", "000500")
        s0("Shower", "001000")
        s0("Tidy Room", "000500")
        s0("Wash Dishes", "000500")

        val s1Id = insertSession(db, "Workout", 2)
        fun s1(label: String, duration: String, restDuration: String = "000000") {
            insertSessionStep(db, s1Id, getOrCreateTaskId(db, label, duration, restDuration), n++)
        }

        s1("Getting Ready", "000015", "000000")
        s1("Chest Opener", "000130", "000100")
        s1("Dead Hang", "000040", "000100")

        s1("Pull-Up x12", "000030", "000130")
        s1("Push-Up x40", "000100", "000100")

        s1("Pull-Up x12", "000030", "000130")
        s1("Push-Up x40", "000100", "000100")

        repeat(2) {
            s1("Chin-Up x12", "000030", "000130")
        }

        s1("Hip Thrust x30", "000100", "000100")
        s1("Hip Thrust x30", "000100", "000100")

        s1("Wall Sit", "000100", "000100")

        repeat(2) {
            s1("Crunches x12", "000100", "000100")
        }

        s1("Plank", "000100", "000100")
        s1("Dead Hang", "000100", "000015")
        s1("Deep Squat", "000100", "000015")
        s1("Split Stretch", "000100", "000000")

        val s2Id = insertSession(db, "Roulette", 1)
        fun s2(label: String, duration: String = "100", restDuration: String = "0") {
            insertSessionStep(db, s2Id, getOrCreateTaskId(db, label, duration, restDuration), n++)
        }

        s2("Drink Water")
        s2("Stand Up")
        s2("Walk Around")
        s2("Change Sitting Position")
        s2("Roll Shoulders")
        s2("Neck Stretch")
        s2("Wrist Stretch")
        s2("Goblin Posture Detected")
        s2("Look Away From Screen")
        s2("Touch Grass")
    }
}
