package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * Local phone-side source of uncalibrated gaze features. A MediaPipe/iris
 * implementation should emit the same feature pair saved by 16-point
 * calibration: gaze_x,gaze_y before screen mapping.
 */
interface LocalRawGazeSource {
    fun interface OnRawGaze {
        fun onRawGaze(gazeX: Float, gazeY: Float, timestampMs: Long)
    }

    fun setOnRawGaze(listener: OnRawGaze)
    fun start(owner: LifecycleOwner)
    fun stop()
}
