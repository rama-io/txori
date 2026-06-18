package com.rama.txori.activities

import android.app.Fragment
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.rama.txori.DatabaseHelper
import com.rama.txori.R
import com.rama.txori.Task
import com.rama.txori.managers.PrefsManager
import com.rama.txori.managers.SoundManager

class RouletteFragment : Fragment() {

    // Views
    private lateinit var chooseListSection: ScrollView
    private lateinit var runningSection: LinearLayout
    private lateinit var timerInput: EditText
    private lateinit var saveTimerButton: FrameLayout
    private lateinit var listsGroup: RadioGroup
    private lateinit var counterBtn: Button
    private lateinit var currentTaskName: TextView
    private lateinit var startRouletteBtn: Button
    private lateinit var playPauseBtn: Button
    private lateinit var skipTaskBtn: Button
    private lateinit var finishBtn: Button

    // State
    private var isRunning = false
    private var isStarted = false
    private var timerMs = 0L
    private var remainingMs = 0L
    private var startTime = 0L
    private var shuffledTasks = mutableListOf<Task>()
    private var currentIndex = 0
    private var waitingForComplete = false
    private var wasRunningOnDestroy = false

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val msLeft = remainingMs - elapsed
            if (msLeft <= 0) {
                remainingMs = 0L
                isRunning = false
                waitingForComplete = true
                counterBtn.text = getString(R.string.h1_timer_default)
                SoundManager.beepFinish()
                updateRunningUI()
                return
            }
            counterBtn.text = formatMillis(msLeft)
            handler.postDelayed(this, 16)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.view_roulette, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        chooseListSection = view.findViewById(R.id.edit_view)
        runningSection = view.findViewById(R.id.running_roulette)
        timerInput = view.findViewById(R.id.timer_input)
        saveTimerButton = view.findViewById(R.id.save_timer_button)
        listsGroup = view.findViewById(R.id.lists)
        counterBtn = view.findViewById(R.id.counter_btn)
        currentTaskName = view.findViewById(R.id.current_roulette_task_name)
        startRouletteBtn = view.findViewById(R.id.start_roulette)
        playPauseBtn = view.findViewById(R.id.play_pause_btn)
        skipTaskBtn = view.findViewById(R.id.next_task)
        finishBtn = view.findViewById(R.id.finish_roulette)

        loadPrefs()
        populateSessionList()

        saveTimerButton.setOnClickListener { saveTimer() }
        startRouletteBtn.setOnClickListener { startRoulette() }
        playPauseBtn.setOnClickListener { togglePlayPause() }
        skipTaskBtn.setOnClickListener { nextTask() }
        finishBtn.setOnClickListener { finishRoulette() }

        counterBtn.setOnClickListener {
            if (waitingForComplete) completeTask()
        }

        syncUiAfterRotation()
    }

    private fun syncUiAfterRotation() {
        if (!isStarted) return

        showRunningSection()

        if (currentIndex < shuffledTasks.size) {
            currentTaskName.text = shuffledTasks[currentIndex].label
        }

        if (wasRunningOnDestroy) {
            wasRunningOnDestroy = false
            startTimer()
        } else if (waitingForComplete) {
            counterBtn.text = getString(R.string.h2_go)
        } else {
            counterBtn.text = formatMillis(remainingMs)
        }
        updateRunningUI()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) populateSessionList()
    }

    override fun onDestroyView() {
        wasRunningOnDestroy = isRunning
        if (isRunning) {
            remainingMs =
                (remainingMs - (SystemClock.elapsedRealtime() - startTime)).coerceAtLeast(0L)
            isRunning = false
        }
        handler.removeCallbacks(ticker)
        super.onDestroyView()
    }

    private fun loadPrefs() {
        val prefs = PrefsManager.getInstance(activity)
        val savedTimer = prefs.getString(PrefsManager.FileKeys.ROULETTE_TIMER, "00:00:00")
        timerInput.setText(savedTimer.filter { it.isDigit() }.trimStart('0'))
        timerMs = digitsToMillis(savedTimer.filter { it.isDigit() }.takeLast(6))
    }

    private fun saveTimer() {
        val digits = timerInput.text.toString().filter { it.isDigit() }.takeLast(6)
        val formatted = formatDigits(digits)
        timerMs = digitsToMillis(digits)
        PrefsManager.getInstance(activity)
            .setString(PrefsManager.FileKeys.ROULETTE_TIMER, formatted)
        timerInput.clearFocus()
        Toast.makeText(activity, getString(R.string.toast_saved_timer), Toast.LENGTH_LONG).show()
    }

    private fun populateSessionList() {
        val db = DatabaseHelper(activity).readableDatabase
        val sessions = DatabaseHelper(activity).getSessions(db)
        db.close()

        val savedId = PrefsManager.getInstance(activity)
            .prefs.getInt(PrefsManager.FileKeys.ROULETTE_LIST, -1).toLong()

        val defaultId = when {
            savedId != -1L -> savedId
            else -> sessions.firstOrNull { (_, name) -> name == "Roulette" }?.first
                ?: sessions.firstOrNull()?.first
                ?: -1L
        }

        listsGroup.removeAllViews()
        sessions.forEach { (session_id, name) ->
            val rb = RadioButton(activity).apply {
                text = name
                tag = session_id
                id = View.generateViewId()
                if (session_id == defaultId) isChecked = true
            }
            listsGroup.addView(rb)
        }

        listsGroup.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId) ?: return@setOnCheckedChangeListener
            val sessionId = rb.tag as Long
            PrefsManager.getInstance(activity).prefs.edit()
                .putInt(PrefsManager.FileKeys.ROULETTE_LIST, sessionId.toInt())
                .apply()
        }
    }

    private fun startRoulette() {
        val checkedId = listsGroup.checkedRadioButtonId
        if (checkedId == -1) return

        val rb = listsGroup.findViewById<RadioButton>(checkedId)
        val sessionId = rb.tag as Long

        val db = DatabaseHelper(activity).readableDatabase
        val tasks = DatabaseHelper(activity).getSessionTasks(db, sessionId)
        db.close()

        if (tasks.isEmpty()) return

        shuffledTasks = tasks.shuffled().toMutableList()
        currentIndex = 0
        isStarted = true
        waitingForComplete = false

        showRunningSection()
        showCurrentTask()
        beginTimer()
    }

    private fun showCurrentTask() {
        if (currentIndex >= shuffledTasks.size) {
            finishRoulette()
            return
        }
        val task = shuffledTasks[currentIndex]
        counterBtn.text = formatMillis(timerMs)
        currentTaskName.text = task.label
        waitingForComplete = false
    }

    private fun beginTimer() {
        remainingMs = timerMs
        startTimer()
    }

    private fun startTimer() {
        if (remainingMs <= 0L) return
        isRunning = true
        startTime = SystemClock.elapsedRealtime()
        handler.post(ticker)
        updateRunningUI()
    }

    private fun togglePlayPause() {
        if (waitingForComplete) {
            waitingForComplete = false
            remainingMs = timerMs
            startTimer()
            return
        }
        if (isRunning) pauseTimer() else startTimer()
    }

    private fun pauseTimer() {
        if (!isRunning) return
        remainingMs -= SystemClock.elapsedRealtime() - startTime
        isRunning = false
        handler.removeCallbacks(ticker)
        updateRunningUI()
    }

    private fun completeTask() {
        nextTask()
    }

    private fun nextTask() {
        handler.removeCallbacks(ticker)
        isRunning = false
        currentIndex++
        if (currentIndex >= shuffledTasks.size) {
            finishRoulette()
            return
        }
        showCurrentTask()
        beginTimer()
    }

    private fun finishRoulette() {
        isStarted = false
        isRunning = false
        waitingForComplete = false
        handler.removeCallbacks(ticker)
        showEditSection()
    }

    private fun showRunningSection() {
        chooseListSection.visibility = View.GONE
        runningSection.visibility = View.VISIBLE
        finishBtn.visibility = View.VISIBLE
        updateRunningUI()
    }

    private fun showEditSection() {
        runningSection.visibility = View.GONE
        chooseListSection.visibility = View.VISIBLE
        startRouletteBtn.visibility = View.VISIBLE
        playPauseBtn.visibility = View.GONE
        skipTaskBtn.visibility = View.GONE
        finishBtn.visibility = View.GONE
    }

    private fun updateRunningUI() {
        startRouletteBtn.visibility = View.GONE
        playPauseBtn.visibility = View.VISIBLE
        skipTaskBtn.visibility = View.VISIBLE

        if (waitingForComplete) {
            playPauseBtn.text = getString(R.string.btn_timer_start)
            counterBtn.text = getString(R.string.h2_go)
            playPauseBtn.visibility = View.GONE
        } else {
            playPauseBtn.visibility = View.VISIBLE
            playPauseBtn.text = if (isRunning)
                getString(R.string.btn_timer_pause)
            else
                getString(R.string.btn_timer_start)
        }
    }

    private fun digitsToMillis(digits: String): Long {
        val padded = digits.padStart(6, '0')
        val hh = padded.substring(0, 2).toLong()
        val mm = padded.substring(2, 4).toLong()
        val ss = padded.substring(4, 6).toLong()
        return ((hh * 3600) + (mm * 60) + ss) * 1000
    }

    private fun formatDigits(digits: String): String {
        val padded = digits.padStart(6, '0')
        return "${padded.substring(0, 2)}:${padded.substring(2, 4)}:${padded.substring(4, 6)}"
    }

    private fun formatMillis(ms: Long): String {
        val total = ms / 1000
        return "%02d:%02d:%02d".format(total / 3600, (total % 3600) / 60, total % 60)
    }
}