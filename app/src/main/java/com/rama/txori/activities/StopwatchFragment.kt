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
import com.rama.txori.R

class StopwatchFragment : Fragment() {

    private lateinit var counterView: Button
    private lateinit var counterStartButton: Button
    private lateinit var counterResetButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false
    private var startTime = 0L
    private var pausedElapsed = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (isRunning) {
                val elapsed = SystemClock.elapsedRealtime() - startTime
                counterView.text = formatTime(elapsed)
                handler.postDelayed(this, 100)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.view_stopwatch, container, false)

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Capture elapsed time up to this moment
        val elapsed = if (isRunning) SystemClock.elapsedRealtime() - startTime else pausedElapsed
        outState.putLong(KEY_PAUSED_ELAPSED, elapsed)
        outState.putBoolean(KEY_IS_RUNNING, isRunning)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        counterView = view.findViewById(R.id.counter_btn)
        counterStartButton = view.findViewById(R.id.start_stopwatch)
        counterResetButton = view.findViewById(R.id.reset_counter)

        counterView.setOnClickListener { toggleStopwatch() }
        counterView.setOnLongClickListener { resetStopwatch(); true }
        counterStartButton.setOnClickListener { toggleStopwatch() }
        counterResetButton.setOnClickListener { resetStopwatch() }

        // Restore state after rotation
        if (savedInstanceState != null) {
            pausedElapsed = savedInstanceState.getLong(KEY_PAUSED_ELAPSED, 0L)
            counterView.text = formatTime(pausedElapsed)
            if (savedInstanceState.getBoolean(KEY_IS_RUNNING, false)) {
                startStopwatch()
            } else {
                counterStartButton.setText(getString(R.string.stopwatch_start))
            }
        }
    }

    private fun toggleStopwatch() {
        if (isRunning) pauseStopwatch() else startStopwatch()
    }

    private fun startStopwatch() {
        startTime = SystemClock.elapsedRealtime() - pausedElapsed
        isRunning = true
        counterStartButton.setText(getString(R.string.stopwatch_pause))
        handler.post(ticker)
    }

    private fun pauseStopwatch() {
        pausedElapsed = SystemClock.elapsedRealtime() - startTime
        isRunning = false
        counterStartButton.setText(getString(R.string.stopwatch_start))
        handler.removeCallbacks(ticker)
    }

    private fun resetStopwatch() {
        isRunning = false
        pausedElapsed = 0L
        counterStartButton.setText(getString(R.string.stopwatch_start))
        handler.removeCallbacks(ticker)
        counterView.text = getString(R.string.stopwatch_counter_default)
    }

    private fun formatTime(ms: Long): String {
        val totalSeconds = ms / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        val tenths = (ms % 1000) / 100

        return when {
            hours > 0 -> "$hours:${minutes.toString().padStart(2, '0')}:${
                seconds.toString().padStart(2, '0')
            }.$tenths"

            minutes > 0 -> "$minutes:${seconds.toString().padStart(2, '0')}.$tenths"
            else -> "$seconds.$tenths"
        }
    }

    override fun onDestroyView() {
        handler.removeCallbacks(ticker)
        super.onDestroyView()
    }

    companion object {
        private const val KEY_PAUSED_ELAPSED = "paused_elapsed"
        private const val KEY_IS_RUNNING = "is_running"
    }
}