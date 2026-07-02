package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * The single boundary all gaze output flows through. Downstream stages (line
 * mapping, RSI) depend only on this interface and its [onGaze] stream, so the
 * tracker implementation can be swapped without touching anything downstream.
 * Current implementation: [WiFiGazeProvider] (GazeFollower over WiFi).
 *
 * Ported from the gaze-thesis-prototype repo (Stage 3, validated). Unchanged
 * except package — swappability is why the port required no interface changes.
 */
interface GazeProvider {

    /** Receives smoothed on-screen gaze estimates in full-screen pixels. */
    fun interface OnGaze {
        fun onGaze(x: Float, y: Float)
    }

    fun setOnGaze(listener: OnGaze)

    /** Begin producing gaze, tied to [owner]'s lifecycle. */
    fun start(owner: LifecycleOwner)

    /** Release tracker resources. */
    fun stop()
}
