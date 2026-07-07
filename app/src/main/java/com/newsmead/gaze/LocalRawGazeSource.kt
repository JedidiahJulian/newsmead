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

    /** Optional monitor of the tracker's processing frame rate (results/sec). */
    fun interface OnFps {
        fun onFps(fps: Float)
    }

    fun setOnRawGaze(listener: OnRawGaze)
    fun setOnFps(listener: OnFps) {}
    fun start(owner: LifecycleOwner)
    fun stop()
}
