package com.rama.txori.activities

import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.rama.txori.R
import com.rama.txori.managers.PrefsManager
import com.rama.txori.managers.SoundManager

class TimerFragment : Fragment() {

    private lateinit var timerButton: Button
    private lateinit var editView: FrameLayout
    private lateinit var timerInput: EditText
    private lateinit var addTimer: Button
    private lateinit var startButton: Button
    private lateinit var resetButton: Button
    private lateinit var editModeButton: Button

    private var isRunning = false
    private var initialMs = 0L
    private var remainingMs = 0L
    private var isEditMode = false
    private var startTime = 0L
    private var wasRunningOnDestroy = false

    private val handler = Handler(Looper.getMainLooper())

    private val ticker = object : Runnable {
        override fun run() {
            if (!isRunning) return
            val elapsed = SystemClock.elapsedRealtime() - startTime
            val msLeft = remainingMs - elapsed
            if (msLeft <= 0) {
                timerButton.text = getString(R.string.h1_timer_default)
                isRunning = false
                remainingMs = 0L
                SoundManager.beepFinish()
                updateButtons()
                return
            }
            timerButton.text = formatMillis(msLeft)
            handler.postDelayed(this, 16)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.view_timer, container, false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        retainInstance = true
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val snapshotRemaining = if (isRunning) {
            remainingMs - (SystemClock.elapsedRealtime() - startTime)
        } else {
            remainingMs
        }
        outState.putLong(KEY_INITIAL_MS, initialMs)
        outState.putLong(KEY_REMAINING_MS, snapshotRemaining.coerceAtLeast(0L))
        outState.putBoolean(KEY_IS_RUNNING, isRunning || wasRunningOnDestroy)
        outState.putBoolean(KEY_IS_EDIT_MODE, isEditMode)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timerButton = view.findViewById(R.id.timer_button)
        editView = view.findViewById(R.id.edit_view)
        timerInput = view.findViewById(R.id.timer_input)
        addTimer = view.findViewById(R.id.add_timer)
        startButton = view.findViewById(R.id.start_timer)
        resetButton = view.findViewById(R.id.reset_timer)
        editModeButton = view.findViewById(R.id.edit_mode)

        timerButton.text = getString(R.string.h1_timer_default)

        editModeButton.setOnClickListener { setEditMode(!isEditMode) }
        timerButton.setOnClickListener { toggleTimer() }
        timerButton.setOnLongClickListener { resetTimer(); true }
        startButton.setOnClickListener { toggleTimer() }
        addTimer.setOnClickListener { applyInput() }
        resetButton.setOnClickListener { resetTimer() }

        loadPrefs()
        updateButtons()

        if (savedInstanceState != null) {
            // Process-death restore: fields were zeroed, read them from bundle.
            initialMs = savedInstanceState.getLong(KEY_INITIAL_MS, 0L)
            remainingMs = savedInstanceState.getLong(KEY_REMAINING_MS, 0L)
            isEditMode = savedInstanceState.getBoolean(KEY_IS_EDIT_MODE, false)
            wasRunningOnDestroy = savedInstanceState.getBoolean(KEY_IS_RUNNING, false)
        }

        if (initialMs > 0L) {
            timerButton.text = formatMillis(remainingMs)
        }
        updateEditModeUI()

        if (wasRunningOnDestroy) {
            wasRunningOnDestroy = false
            startTimer()
        } else {
            updateButtons()
        }
    }

    private fun loadPrefs() {
        val prefs = PrefsManager.getInstance(activity)

        val savedTimer = prefs.getString(
            PrefsManager.FileKeys.APP_TIMER,
            "00:30:00"
        ) ?: "00:30:00"

        val digits = savedTimer.filter { it.isDigit() }.takeLast(6)

        initialMs = digitsToMillis(digits)
        remainingMs = initialMs

        timerInput.setText(digits.trimStart('0'))
        timerButton.text = formatDigits(digits)
    }

    private fun toggleTimer() {
        if (isRunning) pauseTimer() else startTimer()
        startButton.setText(if (isRunning) getString(R.string.btn_timer_pause) else getString(R.string.btn_timer_start))
    }

    private fun updateButtons() {
        val hasTimer = initialMs > 0L
        val canStart = remainingMs > 0L
        val hasTimerActive = remainingMs > 0L
        val showNavbar = !isRunning || remainingMs <= 0L || isEditMode

        startButton.visibility =
            if (hasTimer && !isEditMode && hasTimerActive) View.VISIBLE else View.GONE
        resetButton.visibility =
            if (hasTimer && !isEditMode) View.VISIBLE else View.GONE
        startButton.setText(if (isRunning) getString(R.string.btn_timer_pause) else getString(R.string.btn_timer_start))
        startButton.isEnabled = canStart || isRunning
    }

    private fun updateEditModeUI() {
        if (isEditMode) {
            editModeButton.setText(getString(R.string.btn_switch_to_work_mode))
            timerButton.visibility = View.GONE
            editView.visibility = View.VISIBLE
            addTimer.visibility = View.VISIBLE

            val digits = timerButton.text.toString()
                .filter { it.isDigit() }.takeLast(6).trimStart('0')
            timerInput.setText(digits)
            timerInput.setSelection(timerInput.text.length)
            timerInput.requestFocus()
            showKeyboard()
        } else {
            editModeButton.setText(getString(R.string.btn_switch_to_edit_mode))
            editView.visibility = View.GONE
            addTimer.visibility = View.GONE
            timerButton.visibility = View.VISIBLE
            hideKeyboard()
        }
        updateButtons()
    }

    private fun setEditMode(enabled: Boolean) {
        isEditMode = enabled
        updateEditModeUI()
    }

    private fun applyInput() {
        val digits = timerInput.text.toString().filter { it.isDigit() }.takeLast(6)
        val formatted = formatDigits(digits)
        timerButton.text = formatted
        initialMs = digitsToMillis(digits)
        remainingMs = initialMs

        PrefsManager.getInstance(activity)
            .setString(PrefsManager.FileKeys.APP_TIMER, formatted)
        timerInput.clearFocus()
        Toast.makeText(activity, getString(R.string.toast_saved_timer), Toast.LENGTH_LONG).show()

        updateButtons()
        setEditMode(false)
    }

    private fun startTimer() {
        if (remainingMs <= 0L) return
        isRunning = true
        startTime = SystemClock.elapsedRealtime()
        handler.post(ticker)
        updateButtons()
    }

    private fun pauseTimer() {
        if (!isRunning) return
        remainingMs -= SystemClock.elapsedRealtime() - startTime
        isRunning = false
        handler.removeCallbacks(ticker)
        updateButtons()
    }

    private fun resetTimer() {
        handler.removeCallbacks(ticker)
        remainingMs = initialMs
        timerButton.text = formatMillis(initialMs)
        startTimer()
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

    private fun showKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(timerInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(timerInput.windowToken, 0)
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

    companion object {
        private const val KEY_INITIAL_MS = "initial_ms"
        private const val KEY_REMAINING_MS = "remaining_ms"
        private const val KEY_IS_RUNNING = "is_running"
        private const val KEY_IS_EDIT_MODE = "is_edit_mode"
    }
}