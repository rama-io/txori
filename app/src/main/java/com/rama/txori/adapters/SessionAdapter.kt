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
import com.rama.txori.SessionItem
import com.rama.txori.Task
import com.rama.txori.managers.ThemeManager

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
        private const val TYPE_HEADER = 0
        private const val TYPE_TASK = 1
    }

    private var activeItemIndex: Int = -1
    private var activeProgress: Float = 0f
    private val collapsedSessions: MutableSet<Long> = mutableSetOf()
    private var isEditMode: Boolean = false
    private val playingSessions: MutableSet<Long> = mutableSetOf()

    fun setEditMode(editing: Boolean) {
        isEditMode = editing
        notifyDataSetChanged()
    }

    fun setActiveItemIndex(index: Int) {
        activeItemIndex = index
        activeProgress = 0f
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

    fun rawIndexToVisiblePosition(rawIndex: Int): Int {
        val item = items.getOrNull(rawIndex) ?: return -1
        if (item is SessionItem.Row && collapsedSessions.contains(item.sessionId)) return -1
        var visible = 0
        for (i in items.indices) {
            val current = items[i]
            val skip = current is SessionItem.Row && collapsedSessions.contains(current.sessionId)
            if (!skip) {
                if (i == rawIndex) return visible
                visible++
            }
        }
        return -1
    }

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

    private fun getActualPosition(visiblePosition: Int): Int {
        var visible = 0
        for (i in items.indices) {
            val item = items[i]
            val skip = item is SessionItem.Row && collapsedSessions.contains(item.sessionId)
            if (!skip) {
                if (visible == visiblePosition) return i
                visible++
            }
        }
        return visiblePosition
    }

    override fun getItem(position: Int) = items[getActualPosition(position)]
    override fun getItemId(position: Int) = position.toLong()
    override fun getViewTypeCount() = 2

    override fun getItemViewType(position: Int) = when (items[getActualPosition(position)]) {
        is SessionItem.Header -> TYPE_HEADER
        is SessionItem.Row -> TYPE_TASK
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val actualPos = getActualPosition(position)
        return when (val item = items[actualPos]) {
            is SessionItem.Header -> getHeaderView(item, actualPos, convertView, parent)
            is SessionItem.Row -> getTaskView(item, actualPos, convertView, parent)
        }
    }

    fun updateActiveHeaderTimer(sessionId: Long, remainingMs: Long) {
        val listView = (context as? android.app.Activity)
            ?.findViewById<ListView>(R.id.task_list) ?: return
        val firstVisible = listView.firstVisiblePosition

        for (i in items.indices) {
            val item = items[i]
            if (item is SessionItem.Header && item.sessionId == sessionId) {
                val local = i - firstVisible
                if (local >= 0 && local < listView.childCount) {
                    val headerView = listView.getChildAt(local) ?: break
                    val label = headerView.findViewById<TextView>(R.id.group_label) ?: break
                    val isCollapsed = collapsedSessions.contains(item.sessionId)
                    val indicator = if (isCollapsed) "[-]" else "[+]"
                    val timeStr = formatGroupTime((remainingMs / 1000).toInt())
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
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_header, parent, false)

        val totalSec = header.tasks.sumOf { it.duration }
        val timeStr = formatGroupTime(totalSec)
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
            notifyDataSetChanged()
        }

        // --- Ascend (move session up) ---
        val ascendButton = view.findViewById<FrameLayout>(R.id.ascend_button)
        ascendButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE
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
        descendButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE
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
        editSessionButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE
        editSessionButton.setOnClickListener {
            showEditSessionDialog(header, position)
            true
        }

        val addTaskButton = view.findViewById<FrameLayout>(R.id.add_task)
        addTaskButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE
        addTaskButton.setOnClickListener {
            showAddTaskDialog(header, position)
        }

        val startGroupButton = view.findViewById<FrameLayout>(R.id.start_group)
        startGroupButton.setOnClickListener {
            onStartGroup(header.sessionId, position + 1)
        }
        startGroupButton.visibility = if (isEditMode) View.GONE else View.VISIBLE

        view.findViewById<ImageView>(R.id.start_group_icon)
            .setImageResource(
                if (playingSessions.contains(header.sessionId))
                    R.drawable.icon_pause
                else
                    R.drawable.icon_play
            )

        val resetGroupButton = view.findViewById<FrameLayout>(R.id.reset_group)
        resetGroupButton.setOnClickListener { onResetGroup(header.sessionId) }
        resetGroupButton.visibility = if (isEditMode) View.GONE else View.VISIBLE

        ThemeManager.applyTheme(context, view)
        return view
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
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.list_item_task, parent, false)

        view.findViewById<TextView>(R.id.task_label).text = row.task.label
        view.findViewById<TextView>(R.id.task_duration).text = formatDuration(row.task.duration)

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

        val p = if (position == activeItemIndex) activeProgress else 0f
        applyProgress(p, view)

        // --- Ascend (move task up within its session) ---
        val ascendButton = view.findViewById<FrameLayout>(R.id.ascend_button)
        ascendButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE
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
        descendButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE
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
        editTaskButton.visibility = if (!isEditMode) View.GONE else View.VISIBLE

        ThemeManager.applyTheme(context, view)
        return view
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

        val labelInput = dialogView.findViewById<EditText>(R.id.label)
        val durationInput = dialogView.findViewById<EditText>(R.id.duration)
        durationInput.setText("60")

        dialogView.findViewById<Button>(R.id.delete_button).visibility = View.GONE

        dialogView.findViewById<Button>(R.id.add_button).setOnClickListener {
            val label = labelInput.text.toString().trim()
            val duration = durationInput.text.toString().toIntOrNull() ?: 60

            if (label.isEmpty()) {
                labelInput.error = context.getString(R.string.toast_label_empty)
                return@setOnClickListener
            }

            dbHelper.addTaskToSession(db, header.sessionId, label, duration)

            // Reload tasks from DB so the new task carries its real stepId.
            // Without it, swapStepOrder queries id=0 and silently fails.
            val freshTasks = dbHelper.getSessionTasks(db, header.sessionId)
            val newTask = freshTasks.lastOrNull() ?: Task(label = label, duration = duration)

            var insertAt = headerPosition + 1
            while (insertAt < items.size && items[insertAt] is SessionItem.Row) insertAt++

            items.add(insertAt, SessionItem.Row(header.sessionId, newTask))

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

        labelInput.setText(row.task.label)
        durationInput.setText(row.task.duration.toString())

        dialogView.findViewById<Button>(R.id.delete_button).setOnClickListener {
            dbHelper.removeStepFromSession(db, row.task.stepId)
            items.removeAt(position)
            notifyDataSetChanged()
            onDataChanged()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.add_button).setOnClickListener {
            val newLabel = labelInput.text.toString().trim()
            val newDuration = durationInput.text.toString().toIntOrNull() ?: row.task.duration

            if (newLabel.isEmpty()) {
                labelInput.error = context.getString(R.string.toast_label_empty)
                return@setOnClickListener
            }

            val values = ContentValues().apply {
                put("label", newLabel)
                put("duration", newDuration)
            }

            db.update("tasks", values, "id = ?", arrayOf(row.task.id.toString()))

            row.task.label = newLabel
            row.task.duration = newDuration

            notifyDataSetChanged()
            onDataChanged()
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.cancel_button)
            .setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    //  Formatting

    private fun formatDuration(seconds: Int): String {
        return if (seconds >= 60) {
            val m = seconds / 60
            val s = seconds % 60
            if (s == 0) "${m}m" else "${m}m ${s}s"
        } else "${seconds}s"
    }

    private fun formatGroupTime(totalSeconds: Int): String {
        return if (totalSeconds >= 3600) {
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            String.format("%02d:%02d:%02d", h, m, s)
        } else {
            val m = totalSeconds / 60
            val s = totalSeconds % 60
            String.format("%02d:%02d", m, s)
        }
    }
}