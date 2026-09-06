package com.rama.txori.adapters

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.rama.txori.DatabaseHelper
import com.rama.txori.R
import com.rama.bohio.R as BohioR
import com.rama.txori.SessionItem
import com.rama.txori.Task
import com.rama.txori.managers.PrefsManager
import com.rama.bohio.managers.ThemeManager

class SessionAdapter(
    private val context: Context,
    val items: MutableList<SessionItem>,
    private val db: SQLiteDatabase,
    private val dbHelper: DatabaseHelper,
    private val onStartGroup: (sessionId: Long, startIndex: Int) -> Unit,
    private val onResetGroup: (sessionId: Long) -> Unit,
    private val onDataChanged: () -> Unit
) : BaseAdapter() {

    companion object {
        private const val TYPE_HEADER_SHOW = 0
        private const val TYPE_HEADER_EDIT = 1
        private const val TYPE_TASK_SHOW = 2
        private const val TYPE_TASK_EDIT = 3
    }

    private var activeItemIndex: Int = -1
    private var activeProgress: Float = 0f
    private var activeRestProgress: Float = 0f
    private val liveHeaderRemainingSec: MutableMap<Long, Int> = mutableMapOf()
    private val collapsedSessions: MutableSet<Long> = loadCollapsedFromPrefs()
    private var isEditMode: Boolean = false
    private val playingSessions: MutableSet<Long> = mutableSetOf()

    private fun loadCollapsedFromPrefs(): MutableSet<Long> {
        val raw = PrefsManager.getInstance(context)
            .getString(PrefsManager.FileKeys.SESSION_COLLAPSED_IDS, "")
        if (raw.isBlank()) return mutableSetOf()
        return raw.split(",").mapNotNull { it.trim().toLongOrNull() }.toMutableSet()
    }

    private fun persistCollapsedToPrefs() {
        val raw = collapsedSessions.joinToString(",")
        PrefsManager.getInstance(context)
            .setString(PrefsManager.FileKeys.SESSION_COLLAPSED_IDS, raw)
    }

    fun setEditMode(editing: Boolean) {
        isEditMode = editing
        notifyDataSetChanged()
    }

    fun setActiveItemIndex(index: Int) {
        activeItemIndex = index
        activeProgress = 0f
        activeRestProgress = 0f
        notifyDataSetChanged()
    }

    fun setProgress(index: Int, progress: Float) {
        if (index != activeItemIndex) return
        activeProgress = progress

        val listView = (context as? android.app.Activity)
            ?.findViewById<ListView>(R.id.task_list) ?: return

        val visiblePosition = rawIndexToVisiblePosition(index)
        if (visiblePosition < 0) return

        val firstVisible = listView.firstVisiblePosition
        val localPosition = visiblePosition - firstVisible
        if (localPosition < 0 || localPosition >= listView.childCount) return
        val itemView = listView.getChildAt(localPosition) ?: return
        applyProgress(progress, itemView)
    }

    fun setRestProgress(index: Int, progress: Float) {
        if (index != activeItemIndex) return
        activeRestProgress = progress

        val listView = (context as? android.app.Activity)
            ?.findViewById<ListView>(R.id.task_list) ?: return

        val visiblePosition = rawIndexToVisiblePosition(index)
        if (visiblePosition < 0) return

        val firstVisible = listView.firstVisiblePosition
        val localPosition = visiblePosition - firstVisible
        if (localPosition < 0 || localPosition >= listView.childCount) return
        val itemView = listView.getChildAt(localPosition) ?: return
        applyRestProgress(progress, itemView)
    }

    fun rawIndexToVisiblePosition(rawIndex: Int): Int =
        computeVisiblePosition(items, collapsedSessions, rawIndex)

    override fun getCount(): Int {
        var count = 0
        for (item in items) {
            when (item) {
                is SessionItem.Header -> count++
                is SessionItem.Row -> if (!collapsedSessions.contains(item.sessionId)) count++
            }
        }
        return count
    }

    private fun getActualPosition(visiblePosition: Int): Int =
        computeRawIndex(items, collapsedSessions, visiblePosition)

    override fun getItem(position: Int) = items[getActualPosition(position)]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 4

    override fun getItemViewType(position: Int) = when (items[getActualPosition(position)]) {
        is SessionItem.Header -> if (isEditMode) TYPE_HEADER_EDIT else TYPE_HEADER_SHOW
        is SessionItem.Row -> if (isEditMode) TYPE_TASK_EDIT else TYPE_TASK_SHOW
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val actualPos = getActualPosition(position)
        return when (val item = items[actualPos]) {
            is SessionItem.Header -> getHeaderView(item, actualPos, convertView, parent)
            is SessionItem.Row -> getTaskView(item, actualPos, convertView, parent)
        }
    }

    fun updateActiveHeaderTimer(sessionId: Long, remainingMs: Long) {
        // Update the model first so a rebind (e.g. collapse/uncollapse) always
        // renders a fresh value, even if the list view is not currently reachable.
        val secs = setHeaderLiveTotal(sessionId, remainingMs)

        val listView = (context as? android.app.Activity)
            ?.findViewById<ListView>(R.id.task_list) ?: return

        for (i in items.indices) {
            val item = items[i]
            if (item is SessionItem.Header && item.sessionId == sessionId) {
                val visiblePosition = rawIndexToVisiblePosition(i)
                if (visiblePosition < 0) break
                val firstVisible = listView.firstVisiblePosition
                val local = visiblePosition - firstVisible
                if (local in 0 until listView.childCount) {
                    val headerView = listView.getChildAt(local) ?: break
                    val label = headerView.findViewById<TextView>(R.id.group_label) ?: break
                    val isCollapsed = collapsedSessions.contains(item.sessionId)
                    val indicator = if (isCollapsed) "[-]" else "[+]"
                    val timeStr = formatGroupTime(secs)
                    label.text = "$indicator ${item.name} :: $timeStr"
                }
                break
            }
        }
    }

    private fun getHeaderView(
        header: SessionItem.Header,
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val layoutRes =
            if (isEditMode) R.layout.list_item_header_edit else R.layout.list_item_header
        val view = convertView ?: LayoutInflater.from(context).inflate(layoutRes, parent, false)

        val totalSec = header.tasks.sumOf { hhmmssToSeconds(it.duration) }
        val timeStr = formatGroupTime(liveHeaderRemainingSec[header.sessionId] ?: totalSec)
        val isCollapsed = collapsedSessions.contains(header.sessionId)
        val collapseIndicator = if (isCollapsed) "[-]" else "[+]"

        val groupLabel = view.findViewById<TextView>(R.id.group_label)
        groupLabel.text = "$collapseIndicator ${header.name} :: $timeStr"

        groupLabel.setOnClickListener {
            if (collapsedSessions.contains(header.sessionId)) {
                collapsedSessions.remove(header.sessionId)
            } else {
                collapsedSessions.add(header.sessionId)
            }
            persistCollapsedToPrefs()
            notifyDataSetChanged()
        }

        if (isEditMode) {
            bindHeaderEditControls(view, header, position)
        } else {
            bindHeaderShowControls(view, header, position)
        }

        ThemeManager.applyTheme(context, view)
        return view
    }

    private fun bindHeaderShowControls(view: View, header: SessionItem.Header, position: Int) {
        val startGroupButton = view.findViewById<FrameLayout>(R.id.start_group)
        startGroupButton.setOnClickListener {
            onStartGroup(header.sessionId, position + 1)
        }

        view.findViewById<ImageView>(R.id.start_group_icon)
            .setImageResource(
                if (playingSessions.contains(header.sessionId))
                    BohioR.drawable.px_pause
                else
                    BohioR.drawable.px_play
            )

        val resetGroupButton = view.findViewById<FrameLayout>(R.id.reset_group)
        resetGroupButton.setOnClickListener { onResetGroup(header.sessionId) }
    }

    private fun bindHeaderEditControls(view: View, header: SessionItem.Header, position: Int) {
        // --- Ascend (move session up) ---
        val ascendButton = view.findViewById<FrameLayout>(R.id.ascend_button)
        ascendButton.setOnClickListener {
            val idx = items.indexOf(header)
            // Find the header immediately above this one
            val prevHeaderIdx = (idx - 1 downTo 0).firstOrNull { items[it] is SessionItem.Header }
            if (prevHeaderIdx != null) {
                val prevHeader = items[prevHeaderIdx] as SessionItem.Header
                dbHelper.swapSessionOrder(db, header.sessionId, prevHeader.sessionId)
                // Collect all items belonging to each session
                val thisGroup = items.filter {
                    (it is SessionItem.Header && it.sessionId == header.sessionId) ||
                            (it is SessionItem.Row && it.sessionId == header.sessionId)
                }
                val prevGroup = items.filter {
                    (it is SessionItem.Header && it.sessionId == prevHeader.sessionId) ||
                            (it is SessionItem.Row && it.sessionId == prevHeader.sessionId)
                }
                items.removeAll(thisGroup.toSet())
                items.removeAll(prevGroup.toSet())
                items.addAll(prevHeaderIdx, thisGroup + prevGroup)
                notifyDataSetChanged()
                onDataChanged()
            }
        }

        // --- Descend (move session down) ---
        val descendButton = view.findViewById<FrameLayout>(R.id.descend_button)
        descendButton.setOnClickListener {
            val idx = items.indexOf(header)
            // Find the header immediately below this one
            val nextHeaderIdx =
                (idx + 1 until items.size).firstOrNull { items[it] is SessionItem.Header }
            if (nextHeaderIdx != null) {
                val nextHeader = items[nextHeaderIdx] as SessionItem.Header
                dbHelper.swapSessionOrder(db, header.sessionId, nextHeader.sessionId)
                val thisBlock = items.filter {
                    (it is SessionItem.Header && it.sessionId == header.sessionId) ||
                            (it is SessionItem.Row && it.sessionId == header.sessionId)
                }
                val nextBlock = items.filter {
                    (it is SessionItem.Header && it.sessionId == nextHeader.sessionId) ||
                            (it is SessionItem.Row && it.sessionId == nextHeader.sessionId)
                }
                items.removeAll(thisBlock.toSet())
                items.removeAll(nextBlock.toSet())
                items.addAll(idx, nextBlock + thisBlock)
                notifyDataSetChanged()
                onDataChanged()
            }
        }

        val editSessionButton = view.findViewById<FrameLayout>(R.id.edit_session_button)
        editSessionButton.setOnClickListener {
            showEditSessionDialog(header, position)
            true
        }

        val addTaskButton = view.findViewById<FrameLayout>(R.id.add_task)
        addTaskButton.setOnClickListener {
            showAddTaskDialog(header, position)
        }
    }

    fun stopAllPlaying() {
        playingSessions.clear()
        notifyDataSetChanged()
    }

    private fun getTaskView(
        row: SessionItem.Row,
        position: Int,
        convertView: View?,
        parent: ViewGroup
    ): View {
        val layoutRes = if (isEditMode) R.layout.list_item_task_edit else R.layout.list_item_task
        val view = convertView ?: LayoutInflater.from(context).inflate(layoutRes, parent, false)

        view.findViewById<TextView>(R.id.task_label).text = row.task.label
        view.findViewById<TextView>(R.id.task_duration).text = hhmmssToDisplay(row.task.duration)

        var occurrence = 0
        var total = 0
        var currentIndex = -1
        var runningIndex = 0

        for (i in items.indices) {
            val item = items[i]
            if (item is SessionItem.Row &&
                item.sessionId == row.sessionId &&
                item.task.label == row.task.label
            ) {
                if (i == position) currentIndex = runningIndex
                runningIndex++
            }
        }

        total = runningIndex
        occurrence = currentIndex + 1

        val freqView = view.findViewById<TextView>(R.id.task_frequency)
        if (total > 1) {
            freqView.visibility = View.VISIBLE
            freqView.text = "$occurrence / $total"
        } else {
            freqView.visibility = View.GONE
        }

        if (isEditMode) {
            bindTaskEditControls(view, row, position)
        } else {
            bindTaskShowControls(view, position)
        }

        ThemeManager.applyTheme(context, view)
        return view
    }

    private fun bindTaskShowControls(view: View, position: Int) {
        val p = if (position == activeItemIndex) activeProgress else 0f
        applyProgress(p, view)
        val rp = if (position == activeItemIndex) activeRestProgress else 0f
        applyRestProgress(rp, view)
    }

    private fun bindTaskEditControls(view: View, row: SessionItem.Row, position: Int) {
        // --- Ascend (move task up within its session) ---
        val ascendButton = view.findViewById<FrameLayout>(R.id.ascend_button)
        ascendButton.setOnClickListener {
            val idx = items.indexOf(row)
            // Previous item must be a Row in the same session
            val prevIdx = idx - 1
            if (prevIdx >= 0 && items[prevIdx] is SessionItem.Row) {
                val prevRow = items[prevIdx] as SessionItem.Row
                if (prevRow.sessionId == row.sessionId) {
                    dbHelper.swapStepOrder(db, row.task.stepId, prevRow.task.stepId)
                    items[prevIdx] = row
                    items[idx] = prevRow
                    notifyDataSetChanged()
                    onDataChanged()
                }
            }
        }

        // --- Descend (move task down within its session) ---
        val descendButton = view.findViewById<FrameLayout>(R.id.descend_button)
        descendButton.setOnClickListener {
            val idx = items.indexOf(row)
            // Next item must be a Row in the same session
            val nextIdx = idx + 1
            if (nextIdx < items.size && items[nextIdx] is SessionItem.Row) {
                val nextRow = items[nextIdx] as SessionItem.Row
                if (nextRow.sessionId == row.sessionId) {
                    dbHelper.swapStepOrder(db, row.task.stepId, nextRow.task.stepId)
                    items[nextIdx] = row
                    items[idx] = nextRow
                    notifyDataSetChanged()
                    onDataChanged()
                }
            }
        }

        val editTaskButton = view.findViewById<FrameLayout>(R.id.edit_task_button)
        editTaskButton.setOnClickListener {
            showEditTaskDialog(row, position)
            true
        }
    }

    //  Progress

    private fun applyProgress(progress: Float, itemView: View) {
        val container = itemView.findViewById<View>(R.id.app_row_container) ?: return
        val progressView = itemView.findViewById<View>(R.id.progress_bg) ?: return

        container.post {
            val totalWidth = container.width
            if (totalWidth > 0) {
                progressView.layoutParams.width = (totalWidth * progress).toInt()
                progressView.requestLayout()
            }
        }
    }

    private fun applyRestProgress(progress: Float, itemView: View) {
        val container = itemView.findViewById<View>(R.id.app_row_container) ?: return
        val restProgressView = itemView.findViewById<View>(R.id.progress_rest_bg) ?: return

        container.post {
            val totalWidth = container.width
            if (totalWidth > 0) {
                restProgressView.layoutParams.width = (totalWidth * progress).toInt()
                restProgressView.requestLayout()
            }
        }
    }

    fun setHeaderLiveTotal(sessionId: Long, remainingMs: Long): Int {
        val secs = (remainingMs / 1000).toInt()
        liveHeaderRemainingSec[sessionId] = secs
        return secs
    }

    fun clearHeaderLiveTotal(sessionId: Long) {
        liveHeaderRemainingSec.remove(sessionId)
    }

    fun setGroupPlayingState(sessionId: Long, playing: Boolean) {
        if (playing) playingSessions.add(sessionId) else playingSessions.remove(sessionId)
        notifyDataSetChanged()
    }

    //  Dialogs

    private fun showEditSessionDialog(header: SessionItem.Header, position: Int) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_session_edit, null)

        ThemeManager.applyTheme(context, dialogView)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val title = dialogView.findViewById<TextView>(R.id.modal_title)
        title.text = context.getString(R.string.h2_edit_group)

        val input = dialogView.findViewById<EditText>(R.id.edit_text)
        input.setText(header.name)

        dialogView.findViewById<Button>(R.id.yes_button).apply {
            setText(context.getString(R.string.btn_save))
            setOnClickListener {
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) {
                    input.error = context.getString(R.string.toast_name_empty)
                    return@setOnClickListener
                }
                val values = ContentValues().apply { put("name", newName) }
                db.update("sessions", values, "id = ?", arrayOf(header.sessionId.toString()))

                val headerIdx = items.indexOfFirst {
                    it is SessionItem.Header && it.sessionId == header.sessionId
                }
                if (headerIdx >= 0) {
                    val old = items[headerIdx] as SessionItem.Header
                    items[headerIdx] = old.copy(name = newName)
                }
                notifyDataSetChanged()
                onDataChanged()
                dialog.dismiss()
            }
        }

        dialogView.findViewById<Button>(R.id.delete_group_button).apply {
            visibility = View.VISIBLE
            setText(context.getString(R.string.btn_delete_group))
            setOnClickListener {
                dbHelper.deleteSession(db, header.sessionId)

                items.removeAll { item ->
                    (item is SessionItem.Header && item.sessionId == header.sessionId) ||
                            (item is SessionItem.Row && item.sessionId == header.sessionId)
                }

                collapsedSessions.remove(header.sessionId)
                persistCollapsedToPrefs()
                notifyDataSetChanged()
                onDataChanged()
                dialog.dismiss()
            }
        }

        dialogView.findViewById<Button>(R.id.no_button).setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showAddTaskDialog(header: SessionItem.Header, headerPosition: Int) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_task_edit, null)

        ThemeManager.applyTheme(context, dialogView)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        dialogView.findViewById<TextView>(R.id.modal_title)
            .setText(context.getString(R.string.h2_add_new_task))
        dialogView.findViewById<Button>(R.id.add_button)
            .setText(context.getString(R.string.btn_create_task))

        val labelInput = dialogView.findViewById<AutoCompleteTextView>(R.id.label)
        val durationInput = dialogView.findViewById<EditText>(R.id.duration)
        durationInput.setText("000100")
        val restDurationInput = dialogView.findViewById<EditText>(R.id.rest_duration)
        restDurationInput.setText("000100")

        dialogView.findViewById<Button>(R.id.delete_button).visibility = View.GONE
        dialogView.findViewById<ListView>(R.id.existing_tasks_list).visibility = View.GONE

        // Unique labels across all tasks, for autocomplete suggestions
        val allLabels = dbHelper.getAllTasks(db)
            .map { it.label }
            .distinct()
        val suggestionAdapter =
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, allLabels)
        labelInput.setAdapter(suggestionAdapter)

        // Group autocomplete all sessions, pre-filled with current group
        val allSessions = dbHelper.getSessions(db)
        val groupInput = dialogView.findViewById<AutoCompleteTextView>(R.id.group_spinner)
        val groupLabel = dialogView.findViewById<TextView>(R.id.group_label)
        groupInput.visibility = View.VISIBLE
        groupLabel.visibility = View.VISIBLE
        val groupNames = allSessions.map { it.second }
        val groupSuggestionAdapter =
            ArrayAdapter(context, android.R.layout.simple_dropdown_item_1line, groupNames)
        groupInput.setAdapter(groupSuggestionAdapter)
        val currentGroup = allSessions.firstOrNull { it.first == header.sessionId }
        groupInput.setText(currentGroup?.second ?: "")

        dialogView.findViewById<Button>(R.id.add_button).setOnClickListener {
            val label = labelInput.text.toString().trim()
            val duration = sanitiseHhmmss(durationInput.text.toString())
            val restDuration = sanitiseHhmmss(restDurationInput.text.toString())

            if (label.isEmpty()) {
                labelInput.error = context.getString(R.string.toast_label_empty)
                return@setOnClickListener
            }

            val pickedName = groupInput.text.toString().trim()
            val targetSession = allSessions.firstOrNull { it.second == pickedName }
                ?: allSessions.firstOrNull { it.first == header.sessionId }
                ?: return@setOnClickListener
            val targetSessionId = targetSession.first

            dbHelper.addTaskToSession(db, targetSessionId, label, duration, restDuration)

            // Reload tasks from DB so the new task carries its real stepId.
            // Without it, swapStepOrder queries id=0 and silently fails.
            val freshTasks = dbHelper.getSessionTasks(db, targetSessionId)
            val newTask = freshTasks.lastOrNull() ?: Task(
                label = label,
                duration = duration,
                restDuration = restDuration
            )

            // Find the correct insert position for the target session
            val targetHeaderIdx = items.indexOfFirst {
                it is SessionItem.Header && it.sessionId == targetSessionId
            }
            var insertAt = targetHeaderIdx + 1
            while (insertAt < items.size && items[insertAt] is SessionItem.Row) insertAt++

            items.add(insertAt, SessionItem.Row(targetSessionId, newTask))

            notifyDataSetChanged()
            onDataChanged()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun showEditTaskDialog(row: SessionItem.Row, position: Int) {
        val dialogView = LayoutInflater.from(context)
            .inflate(R.layout.dialog_task_edit, null)

        ThemeManager.applyTheme(context, dialogView)
        val dialog = AlertDialog.Builder(context).setView(dialogView).create()

        val labelInput = dialogView.findViewById<EditText>(R.id.label)
        val durationInput = dialogView.findViewById<EditText>(R.id.duration)
        val restDurationInput = dialogView.findViewById<EditText>(R.id.rest_duration)

        labelInput.setText(row.task.label)
        durationInput.setText(row.task.duration)
        restDurationInput.setText(row.task.restDuration)

        dialogView.findViewById<Button>(R.id.delete_button).setOnClickListener {
            dbHelper.removeStepFromSession(db, row.task.stepId)
            items.removeAt(position)
            notifyDataSetChanged()
            onDataChanged()
            dialog.dismiss()
        }

        // Group autocomplete all sessions except the current one
        val allSessions = dbHelper.getSessions(db)
        val otherSessions = allSessions.filter { it.first != row.sessionId }
        val groupInput = dialogView.findViewById<AutoCompleteTextView>(R.id.group_spinner)
        val groupLabel = dialogView.findViewById<TextView>(R.id.group_label)
        if (otherSessions.isNotEmpty()) {
            groupInput.visibility = View.VISIBLE
            groupLabel.visibility = View.VISIBLE
            val groupSuggestionAdapter = ArrayAdapter(
                context,
                android.R.layout.simple_dropdown_item_1line,
                otherSessions.map { it.second })
            groupInput.setAdapter(groupSuggestionAdapter)
            val currentGroupName =
                allSessions.firstOrNull { it.first == row.sessionId }?.second ?: ""
            groupInput.setText(currentGroupName)
        }

        dialogView.findViewById<Button>(R.id.add_button).setOnClickListener {
            val newLabel = labelInput.text.toString().trim()
            val newDuration = sanitiseHhmmss(durationInput.text.toString())
            val newRestDuration = sanitiseHhmmss(restDurationInput.text.toString())

            if (newLabel.isEmpty()) {
                labelInput.error = context.getString(R.string.toast_label_empty)
                return@setOnClickListener
            }

            val values = ContentValues().apply {
                put("label", newLabel)
                put("duration", newDuration)
                put("rest_duration", newRestDuration)
            }
            db.update("tasks", values, "id = ?", arrayOf(row.task.id.toString()))
            row.task.label = newLabel
            row.task.duration = newDuration
            row.task.restDuration = newRestDuration

            // Move to another group if the spinner is visible and a group was selected
            val pickedName = groupInput.text.toString().trim()
            val targetSession = otherSessions.firstOrNull { it.second == pickedName }
            if (targetSession != null) {
                val targetSessionId = targetSession.first

                // Update the step's session_id in the DB
                val stepValues = ContentValues().apply { put("session_id", targetSessionId) }
                db.update(
                    "session_steps",
                    stepValues,
                    "id = ?",
                    arrayOf(row.task.stepId.toString())
                )

                // Move the item in memory: remove from current position, append to target group
                items.removeAt(position)
                val targetHeaderIdx = items.indexOfFirst {
                    it is SessionItem.Header && it.sessionId == targetSessionId
                }
                var insertAt = targetHeaderIdx + 1
                while (insertAt < items.size && items[insertAt] is SessionItem.Row) insertAt++
                items.add(insertAt, SessionItem.Row(targetSessionId, row.task))
            }

            notifyDataSetChanged()
            onDataChanged()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    //  Formatting

    // Parse "hhmmss" raw digits → total seconds
    private fun hhmmssToSeconds(raw: String): Int {
        val d = raw.filter { it.isDigit() }.padStart(6, '0')
        val hh = d.substring(0, 2).toInt()
        val mm = d.substring(2, 4).toInt()
        val ss = d.substring(4, 6).toInt()
        return hh * 3600 + mm * 60 + ss
    }

    // Display "hhmmss" stored string as "hh:mm:ss"
    private fun hhmmssToDisplay(raw: String): String {
        val d = raw.filter { it.isDigit() }.padStart(6, '0')
        return "${d.substring(0, 2)}:${d.substring(2, 4)}:${d.substring(4, 6)}"
    }

    // Sanitise user input: accept "hhmmss" digits (max 6), pad/trim as needed
    private fun sanitiseHhmmss(input: String): String {
        val digits = input.filter { it.isDigit() }.takeLast(6).padStart(6, '0')
        return digits
    }

    private fun formatGroupTime(totalSeconds: Int): String =
        String.format(
            "%02d:%02d:%02d",
            totalSeconds / 3600,
            (totalSeconds % 3600) / 60,
            totalSeconds % 60
        )
}