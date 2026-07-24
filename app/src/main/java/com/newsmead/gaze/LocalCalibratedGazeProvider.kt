package com.newsmead.gaze

import androidx.lifecycle.LifecycleOwner

/**
 * Local Stage 3 provider: maps phone-side raw gaze features through the saved
 * 16-point calibration and emits calibrated full-screen phone pixels. An
 * optional affine [correction] (fitted from the gaze accuracy test) is applied
 * after the mapper to compensate mid-session drift without re-calibrating.
 */
class LocalCalibratedGazeProvider(
    private val mapper: GazeMapper,
    private val rawSource: LocalRawGazeSource,
    private val correction: DriftCorrection? = null,
) : GazeProvider {

    private val medianX = MedianFilter()
    private val medianY = MedianFilter()
    private val smoothX = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private val smoothY = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private var onGaze: GazeProvider.OnGaze? = null

    override fun setOnGaze(listener: GazeProvider.OnGaze) {
        onGaze = listener
    }

    override fun start(owner: LifecycleOwner) {
        rawSource.setOnRawGaze { gazeX, gazeY, timestampMs ->
            val fx = smoothX.filter(medianX.filter(gazeX), timestampMs)
            val fy = smoothY.filter(medianY.filter(gazeY), timestampMs)
            val screen = mapper.map(fx, fy)
            val corrected = correction?.apply(screen[0], screen[1]) ?: screen
            onGaze?.onGaze(corrected[0], corrected[1])
        }
        rawSource.start(owner)
    }

    override fun stop() {
        rawSource.stop()
    }
}
