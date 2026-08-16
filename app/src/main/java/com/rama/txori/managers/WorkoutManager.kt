package com.rama.txori.managers

import android.os.CountDownTimer
import com.rama.txori.SessionItem

class WorkoutManager(
    private var listener: Listener,
    private val onTick: () -> Unit = {},
    private val onFinishNotify: () -> Unit = {}
) {

    interface Listener {
        fun onTaskStarted(index: Int, label: String, remainingMs: Long)
        fun onTaskTick(index: Int, remainingMs: Long, progress: Float)
        fun onTaskFinished(index: Int)
        fun onRestStarted(index: Int, restDurationMs: Long)
        fun onRestTick(index: Int, remainingMs: Long, progress: Float)
        fun onRestFinished(index: Int)
        fun onSessionTick(sessionId: Long, remainingMs: Long)
        // headerRemainingMs: value the group header total should show, or null
        // to revert it to the static (full) sum. Pause freezes a value; a real
        // stop/switch passes null. Only the manager knows which, so it declares it.
        fun onPlayingStateChanged(sessionId: Long, playing: Boolean, headerRemainingMs: Long?)
        fun onGroupFinished(sessionId: Long)
        fun onGroupReset(sessionId: Long)
    }

    // Immutable snapshot of all list items — set once from MainActivity
    var items: List<SessionItem> = emptyList()

    var activeSessionId: Long = -1
        private set
    var currentItemIndex: Int = -1
        private set
    var isRunning: Boolean = false
        private set
    var remainingMs: Long = 0
        private set
    var globalRemainingMs: Long = 0
        private set

    private var taskTimer: CountDownTimer? = null
    private var globalTimer: CountDownTimer? = null
    private var taskGeneration: Int = 0
    private var lastBeepSecond: Long = -1
    private var taskDurationMs: Long = 0L
    private var restTimer: CountDownTimer? = null
    private var restDurationMs: Long = 0L
    private var restRemainingMs: Long = 0L
    private var isResting: Boolean = false

    //  Public actions

    /** Call after rotation to point callbacks at the new fragment view. */
    fun reconnectListener(newListener: Listener) {
        listener = newListener
    }

    fun startGroup(sessionId: Long, startIndex: Int) {
        when {
            activeSessionId == sessionId && isRunning -> pause()

            activeSessionId == sessionId && !isRunning && currentItemIndex >= 0 -> resume()

            else -> {
                stopTask()
                cancelGlobalTimer()
                if (activeSessionId != -1L && activeSessionId != sessionId) {
                    listener.onPlayingStateChanged(activeSessionId, false, null)
                }
                activeSessionId = sessionId
                globalRemainingMs = calcSessionMs(sessionId, startIndex)
                startFromIndex(startIndex)
                launchGlobalTimer(globalRemainingMs)
            }
        }
    }

    fun resetGroup(sessionId: Long) {
        if (activeSessionId != sessionId) return
        stopTask()
        cancelGlobalTimer()
        globalRemainingMs = 0
        activeSessionId = -1
        currentItemIndex = -1
        isRunning = false
        listener.onGroupReset(sessionId)
    }

    fun togglePlayPause() {
        if (currentItemIndex < 0) return
        if (isRunning) pause() else resume()
    }

    fun addTime(ms: Long) {
        if (!isRunning) return
        cancelGlobalTimer()
        globalRemainingMs += ms
        launchGlobalTimer(globalRemainingMs)
        if (isResting) {
            restRemainingMs += ms
            restDurationMs += ms
            launchRestTimer(currentItemIndex, restRemainingMs, isResume = true)
        } else {
            cancelTaskTimer()
            remainingMs += ms
            taskDurationMs += ms
            launchTaskTimer(remainingMs)
        }
    }

    fun repeatCurrentTask() {
        if (currentItemIndex < 0) return
        val row = items.getOrNull(currentItemIndex) as? SessionItem.Row ?: return
        if (isResting) {
            val restMs = hhmmssToMs(row.task.restDuration)
            if (restRemainingMs > restMs) return
            if (restMs > 0) launchRestTimer(currentItemIndex, restMs)
            return
        }
        val originalMs = hhmmssToMs(row.task.duration)
        if (remainingMs > originalMs) return
        loadTask(currentItemIndex)
    }

    fun skipTask() {
        cancelTaskTimer()
        interruptRest()
        startFromIndex(currentItemIndex + 1)
    }

    fun stopAndClear() {
        stopTask()
        cancelGlobalTimer()
        val prevSession = activeSessionId
        activeSessionId = -1
        currentItemIndex = -1
        globalRemainingMs = 0
        if (prevSession != -1L) listener.onPlayingStateChanged(prevSession, false, null)
    }

    fun release() {
        stopTask()
        cancelGlobalTimer()
    }

    //  Private helpers 

    private fun pause() {
        cancelTaskTimer()
        cancelRestTimer()
        cancelGlobalTimer()
        isRunning = false
        listener.onPlayingStateChanged(activeSessionId, false, globalRemainingMs)
    }

    private fun resume() {
        isRunning = true
        listener.onPlayingStateChanged(activeSessionId, true, globalRemainingMs)
        if (isResting) {
            launchRestTimer(currentItemIndex, restRemainingMs, isResume = true)
        } else {
            launchTaskTimer(remainingMs)
        }
        if (globalRemainingMs > 0) launchGlobalTimer(globalRemainingMs)
    }

    private fun startFromIndex(index: Int) {
        val target = (index until items.size).firstOrNull {
            val item = items[it]
            item is SessionItem.Row && item.sessionId == activeSessionId
        }
        if (target == null) {
            finishGroup()
        } else {
            loadTask(target)
        }
    }

    private fun loadTask(index: Int) {
        val row = items.getOrNull(index) as? SessionItem.Row ?: return
        cancelTaskTimer()
        isResting = false
        restRemainingMs = 0L
        currentItemIndex = index
        remainingMs = hhmmssToMs(row.task.duration)
        taskDurationMs = remainingMs
        lastBeepSecond = -1
        isRunning = true
        listener.onTaskStarted(index, row.task.label, remainingMs)
        listener.onPlayingStateChanged(activeSessionId, true, globalRemainingMs)
        launchTaskTimer(remainingMs)
    }

    private fun finishGroup() {
        isRunning = false
        cancelGlobalTimer()
        globalRemainingMs = 0
        val doneId = activeSessionId
        activeSessionId = -1
        currentItemIndex = -1
        listener.onGroupFinished(doneId)
    }

    private fun stopTask() {
        cancelTaskTimer()
        cancelRestTimer()
        isResting = false
        restRemainingMs = 0L
        isRunning = false
    }

    private fun launchTaskTimer(durationMs: Long) {
        cancelTaskTimer()
        val generation = ++taskGeneration
        taskTimer = object : CountDownTimer(durationMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                if (generation != taskGeneration) return
                remainingMs = millisUntilFinished
                val progress =
                    (1f - millisUntilFinished.toFloat() / taskDurationMs).coerceIn(0f, 1f)
                listener.onTaskTick(currentItemIndex, millisUntilFinished, progress)

                val secondsLeft = millisUntilFinished / 1000
                if (secondsLeft in 0..5 && secondsLeft != lastBeepSecond) {
                    lastBeepSecond = secondsLeft
                    onTick()
                }
            }

            override fun onFinish() {
                if (generation != taskGeneration) return
                onFinishNotify()
                listener.onTaskFinished(currentItemIndex)
                val row = items.getOrNull(currentItemIndex) as? com.rama.txori.SessionItem.Row
                val restMs = hhmmssToMs(row?.task?.restDuration ?: "000000")
                if (restMs > 0) {
                    launchRestTimer(currentItemIndex, restMs)
                } else {
                    startFromIndex(currentItemIndex + 1)
                }
            }
        }.start()
    }

    private fun launchGlobalTimer(durationMs: Long) {
        cancelGlobalTimer()
        globalTimer = object : CountDownTimer(durationMs, 1000) {
            override fun onTick(ms: Long) {
                globalRemainingMs = ms
                listener.onSessionTick(activeSessionId, ms)
            }

            override fun onFinish() {
                globalRemainingMs = 0
                listener.onSessionTick(activeSessionId, 0)
            }
        }.start()
    }

    private fun cancelTaskTimer() {
        taskTimer?.cancel(); taskTimer = null
    }

    private fun launchRestTimer(index: Int, durationMs: Long, isResume: Boolean = false) {
        cancelRestTimer()
        isResting = true
        restRemainingMs = durationMs
        if (!isResume) {
            restDurationMs = durationMs
            listener.onRestStarted(index, durationMs)
        }
        restTimer = object : CountDownTimer(durationMs, 100) {
            override fun onTick(millisUntilFinished: Long) {
                restRemainingMs = millisUntilFinished
                val progress =
                    (1f - millisUntilFinished.toFloat() / restDurationMs).coerceIn(0f, 1f)
                listener.onRestTick(index, millisUntilFinished, progress)
            }

            override fun onFinish() {
                isResting = false
                restRemainingMs = 0L
                onFinishNotify()
                listener.onRestFinished(index)
                startFromIndex(index + 1)
            }
        }.start()
    }

    private fun cancelRestTimer() {
        restTimer?.cancel(); restTimer = null
    }

    private fun interruptRest() {
        if (!isResting) return
        cancelRestTimer()
        isResting = false
        restRemainingMs = 0L
        listener.onRestFinished(currentItemIndex)
    }

    private fun cancelGlobalTimer() {
        globalTimer?.cancel(); globalTimer = null
    }

    private fun calcSessionMs(sessionId: Long, fromIndex: Int): Long {
        var total = 0L
        for (i in fromIndex until items.size) {
            val item = items[i]
            if (item is SessionItem.Row && item.sessionId == sessionId) {
                total += hhmmssToMs(item.task.duration)
                total += hhmmssToMs(item.task.restDuration)
            }
        }
        return total
    }

    private fun hhmmssToMs(raw: String): Long {
        val d = raw.filter { it.isDigit() }.padStart(6, '0')
        val hh = d.substring(0, 2).toLong()
        val mm = d.substring(2, 4).toLong()
        val ss = d.substring(4, 6).toLong()
        return (hh * 3600 + mm * 60 + ss) * 1_000L
    }
}