package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * Option 1 [GazeProvider]: gaze comes from GazeFollower on the laptop, streamed
 * over UDP in laptop-screen pixels. A per-user calibration ([GazeMapper], fit
 * from the WiFi calibration) maps laptop-px -> phone-px. No camera on the phone.
 *
 * Ported verbatim (except package) from gaze-thesis-prototype.
 */
class WiFiGazeProvider(
    private val mapper: GazeMapper,
    port: Int = GazeStream.DEFAULT_PORT,
) : GazeProvider {

    private val medianX = MedianFilter()
    private val medianY = MedianFilter()
    private val smoothX = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private val smoothY = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private val stream = GazeStream(port) { x, y, ts -> onSample(x, y, ts) }
    private var onGaze: GazeProvider.OnGaze? = null

    override fun setOnGaze(listener: GazeProvider.OnGaze) {
        onGaze = listener
    }

    override fun start(owner: LifecycleOwner) {
        stream.start()
    }

    override fun stop() {
        stream.stop()
    }

    private fun onSample(x: Float, y: Float, timestampMs: Long) {
        val fx = smoothX.filter(medianX.filter(x), timestampMs)
        val fy = smoothY.filter(medianY.filter(y), timestampMs)
        val screen = mapper.map(fx, fy)
        onGaze?.onGaze(screen[0], screen[1])
    }
}
