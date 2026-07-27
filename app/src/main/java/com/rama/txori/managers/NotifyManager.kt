package com.rama.txori.managers

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Single entry point for "something finished" feedback. Reads the user's
 * notification settings and fires only the effects they've enabled.
 */
object NotifyManager {

    private val handler = Handler(Looper.getMainLooper())

    /** Quiet tick, e.g. the last-few-seconds countdown beep. Respects the sounds toggle. */
    fun tick(context: Context) {
        if (prefs(context).getBoolean(PrefsManager.FileKeys.NOTIFICATION_SOUNDS, true)) {
            SoundManager.beepTick()
        }
    }

    /**
     * Fire the "finished" notification according to the user's settings:
     * sound, vibration, screen flash, and/or camera flash.
     *
     * @param onFlashScreen invoked when the screen-flash setting is enabled;
     * the caller supplies how to actually flash its UI.
     */
    fun finish(context: Context, onFlashScreen: (() -> Unit)? = null) {
        val p = prefs(context)

        if (p.getBoolean(PrefsManager.FileKeys.NOTIFICATION_SOUNDS, true)) {
            SoundManager.beepFinish()
        }
        if (p.getBoolean(PrefsManager.FileKeys.NOTIFICATION_VIBRATE, false)) {
            vibrate(context)
        }
        if (p.getBoolean(PrefsManager.FileKeys.NOTIFICATION_FLASH, false)) {
            onFlashScreen?.invoke()
        }
        if (p.getBoolean(PrefsManager.FileKeys.NOTIFICATION_FLASH_CAMERA, false)) {
            blinkTorch(context)
        }
    }

    private fun prefs(context: Context) = PrefsManager.getInstance(context).prefs

    private fun vibrate(context: Context) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator == null || !vibrator.hasVibrator()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(400, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(400)
        }
    }

    /** Blinks the rear camera torch a few times, e.g. for use-while-face-down/silent scenarios. */
    private fun blinkTorch(context: Context) {
        val cameraManager =
            context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return

        val cameraId = try {
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        } ?: return

        val blinkCount = 3
        val onMs = 150L
        val offMs = 150L
        var t = 0L
        repeat(blinkCount) {
            handler.postDelayed({ setTorch(cameraManager, cameraId, true) }, t)
            t += onMs
            handler.postDelayed({ setTorch(cameraManager, cameraId, false) }, t)
            t += offMs
        }
    }

    private fun setTorch(cameraManager: CameraManager, cameraId: String, on: Boolean) {
        try {
            cameraManager.setTorchMode(cameraId, on)
        } catch (e: Exception) {
            // Torch may be unavailable (e.g. in use by another app); ignore.
        }
    }
}
