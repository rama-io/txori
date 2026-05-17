package com.rama.txori.activities

import android.app.AlertDialog
import android.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import com.rama.txori.DatabaseHelper
import com.rama.txori.R
import com.rama.txori.SessionItem
import com.rama.txori.adapters.SessionAdapter
import com.rama.txori.managers.FontManager
import com.rama.txori.managers.SoundManager
import com.rama.txori.managers.WorkoutManager

class HomeFragment : Fragment(), WorkoutManager.Listener {

    private lateinit var listView: ListView
    private lateinit var adapter: SessionAdapter
    private val dbHelper by lazy { DatabaseHelper(activity) }
    private lateinit var db: android.database.sqlite.SQLiteDatabase

    private lateinit var taskNameView: TextView
    private lateinit var timerView: TextView
    private lateinit var nextTaskView: TextView
    private lateinit var globalControllers: LinearLayout
    private lateinit var editButton: Button
    private lateinit var playPauseIcon: ImageView

    private val items: MutableList<SessionItem> = mutableListOf()
    private lateinit var workout: WorkoutManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keep this fragment instance alive across rotation so WorkoutManager
        // (and its running timers) survive the config change.
        retainInstance = true
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.view_home, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView = view.findViewById(R.id.task_list)
        taskNameView = view.findViewById(R.id.current_task_name)
        timerView = view.findViewById(R.id.current_task_timer)
        nextTaskView = view.findViewById(R.id.next_task_name)
        globalControllers = view.findViewById(R.id.controllers)
        editButton = view.findViewById(R.id.edit_button)
        playPauseIcon = view.findViewById(R.id.play_pause_icon)

        SoundManager.init()

        // Only create WorkoutManager on first load; on rotation the retained
        // instance still holds the running workout, just reconnect the listener.
        if (!::workout.isInitialized) {
            workout = WorkoutManager(this)
        } else {
            workout.reconnectListener(this)
        }

        db = dbHelper.writableDatabase
        loadItems()
        workout.items = items

        adapter = SessionAdapter(
            context = activity,
            items = items,
            db = db,
            dbHelper = dbHelper,
            onStartGroup = { sessionId, startIndex -> workout.startGroup(sessionId, startIndex) },
            onResetGroup = { sessionId -> workout.resetGroup(sessionId) },
            onDataChanged = { FontManager.applyToListView(activity, listView) }
        )
        listView.adapter = adapter
        listView.post { FontManager.applyToListView(activity, listView) }

        view.findViewById<View>(R.id.repeat_task).setOnClickListener { workout.repeatCurrentTask() }
        view.findViewById<View>(R.id.increase_duration)
            .setOnClickListener { workout.addTime(30_000L) }
        view.findViewById<View>(R.id.start_task).setOnClickListener { workout.togglePlayPause() }
        view.findViewById<View>(R.id.skip_task).setOnClickListener { workout.skipTask() }

        val addGroupButton = view.findViewById<Button>(R.id.add_group_button)
        val workButton = view.findViewById<Button>(R.id.work_button)
        val timeContainer = view.findViewById<View>(R.id.time_container)

        addGroupButton.setOnClickListener { showAddGroupDialog() }

        editButton.setOnClickListener {
            addGroupButton.visibility = View.VISIBLE
            workButton.visibility = View.VISIBLE
            editButton.visibility = View.GONE
            timeContainer.visibility = View.GONE
            adapter.stopAllPlaying()
            adapter.setEditMode(true)
            workout.stopAndClear()
        }

        workButton.setOnClickListener {
            addGroupButton.visibility = View.GONE
            workButton.visibility = View.GONE
            editButton.visibility = View.VISIBLE
            timeContainer.visibility = View.VISIBLE
            adapter.setEditMode(false)
        }

        // After rotation the views are blank but the workout may still be running.
        // Re-drive the UI to match the current workout state.
        syncUiToWorkoutState()
    }

    private fun syncUiToWorkoutState() {
        val idx = workout.currentItemIndex
        if (idx < 0) return

        val row = items.getOrNull(idx) as? com.rama.txori.SessionItem.Row ?: return

        taskNameView.text = row.task.label
        updateTimerDisplay(workout.remainingMs)
        updateNextTaskDisplay(idx, workout.activeSessionId)
        adapter.setActiveItemIndex(idx)
        adapter.setGroupPlayingState(workout.activeSessionId, workout.isRunning)
        globalControllers.visibility = View.VISIBLE
        editButton.visibility = if (workout.isRunning) View.GONE else View.VISIBLE
        playPauseIcon.setImageResource(
            if (workout.isRunning) R.drawable.icon_pause else R.drawable.icon_play
        )
    }

    // WorkoutManager.Listener

    override fun onTaskStarted(index: Int, label: String, remainingMs: Long) {
        taskNameView.text = label
        updateTimerDisplay(remainingMs)
        adapter.setActiveItemIndex(index)
        updateNextTaskDisplay(index, workout.activeSessionId)
        globalControllers.visibility = View.VISIBLE
        listView.post {
            val visiblePos = adapter.rawIndexToVisiblePosition(index)
            if (visiblePos >= 0) listView.setSelectionFromTop(visiblePos, 0)
        }
    }

    override fun onTaskTick(index: Int, remainingMs: Long, progress: Float) {
        updateTimerDisplay(remainingMs)
        adapter.setProgress(index, progress)
    }

    override fun onTaskFinished(index: Int) {
        adapter.setProgress(index, 1f)
    }

    override fun onSessionTick(sessionId: Long, remainingMs: Long) {
        adapter.updateActiveHeaderTimer(sessionId, remainingMs)
    }

    override fun onPlayingStateChanged(sessionId: Long, playing: Boolean) {
        adapter.setGroupPlayingState(sessionId, playing)
        playPauseIcon.setImageResource(
            if (playing) R.drawable.icon_pause else R.drawable.icon_play
        )
        editButton.visibility = if (playing) View.GONE else View.VISIBLE
    }

    override fun onGroupFinished(sessionId: Long) {
        taskNameView.text = getString(R.string.home_done)
        timerView.text = getString(R.string.home_timer_zero)
        nextTaskView.text = getString(R.string.home_next_placeholder)
        adapter.setActiveItemIndex(-1)
        adapter.setGroupPlayingState(sessionId, false)
        playPauseIcon.setImageResource(R.drawable.icon_play)
        globalControllers.visibility = View.GONE
        editButton.visibility = View.VISIBLE
    }

    override fun onGroupReset(sessionId: Long) {
        taskNameView.text = getString(R.string.home_greeting)
        timerView.text = getString(R.string.home_timer_zero)
        nextTaskView.text = getString(R.string.home_next_placeholder)
        adapter.setActiveItemIndex(-1)
        adapter.setGroupPlayingState(sessionId, false)
        playPauseIcon.setImageResource(R.drawable.icon_play)
        globalControllers.visibility = View.GONE
    }

    // Helpers

    private fun loadItems() {
        items.clear()
        val sessions = dbHelper.getSessions(db)
        for ((id, name) in sessions) {
            val tasks = dbHelper.getSessionTasks(db, id)
            items.add(SessionItem.Header(id, name, tasks))
            for (task in tasks) items.add(SessionItem.Row(id, task))
        }
    }

    private fun updateNextTaskDisplay(currentIndex: Int, sessionId: Long) {
        val nextIndex = (currentIndex + 1 until items.size).firstOrNull {
            val item = items[it]
            item is SessionItem.Row && item.sessionId == sessionId
        }
        val nextRow = nextIndex?.let { items[it] as? SessionItem.Row }
        nextTaskView.text = if (nextRow != null) getString(
            R.string.home_next_task_prefix,
            nextRow.task.label
        ) else getString(R.string.home_next_placeholder)
    }

    private fun updateTimerDisplay(ms: Long) {
        val total = (ms / 1000).coerceAtLeast(0)
        timerView.text = String.format("%02d:%02d", total / 60, total % 60)
    }

    private fun showAddGroupDialog() {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_session_edit, null)

        FontManager.applyToView(activity, dialogView)
        val dialog = AlertDialog.Builder(activity).setView(dialogView).create()
        val input = dialogView.findViewById<EditText>(R.id.edit_text)

        dialogView.findViewById<Button>(R.id.yes_button).apply {
            setText(getString(R.string.dialog_create_group))
            setOnClickListener {
                val name = input.text.toString().trim()
                if (name.isEmpty()) {
                    input.error = getString(R.string.error_name_empty); return@setOnClickListener
                }
                val newId = dbHelper.createSession(db, name)
                items.add(SessionItem.Header(newId, name, emptyList()))
                workout.items = items
                adapter.notifyDataSetChanged()
                FontManager.applyToListView(activity, listView)
                dialog.dismiss()
            }
        }
        dialogView.findViewById<Button>(R.id.delete_group_button).visibility = View.GONE
        dialogView.findViewById<Button>(R.id.no_button).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun onDestroyView() {
        workout.release()
        SoundManager.release()
        dbHelper.close()
        super.onDestroyView()
    }
}