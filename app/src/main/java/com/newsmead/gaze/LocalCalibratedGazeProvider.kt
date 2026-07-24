package com.newsmead.gaze

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import java.util.Locale

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
    private var logCounter = 0

    override fun setOnGaze(listener: GazeProvider.OnGaze) {
        onGaze = listener
    }

    override fun start(owner: LifecycleOwner) {
        rawSource.setOnRawGaze { gazeX, gazeY, timestampMs ->
            val fx = smoothX.filter(medianX.filter(gazeX), timestampMs)
            val fy = smoothY.filter(medianY.filter(gazeY), timestampMs)
            val screen = mapper.map(fx, fy)
            val corrected = correction?.apply(screen[0], screen[1]) ?: screen
            // Diagnostic: pair the raw feature with the mapped px so a persistent
            // vertical "wall" can be attributed. If raw fy keeps changing while
            // mapped y stops, it is the mapping; if raw fy itself flatlines when
            // looking further up/down, the feature is saturating at the source
            // (appearance-based vertical limit) and no mapper change will help.
            if (++logCounter % LOG_INTERVAL == 0) {
                Log.d(
                    TAG,
                    String.format(
                        Locale.US,
                        "raw=(%.4f, %.4f) filt=(%.4f, %.4f) -> screen=(%.0f, %.0f)%s",
                        gazeX, gazeY, fx, fy, corrected[0], corrected[1],
                        if (correction != null) " [drift-corrected]" else "",
                    ),
                )
            }
            onGaze?.onGaze(corrected[0], corrected[1])
        }
        rawSource.start(owner)
    }

    override fun stop() {
        rawSource.stop()
    }

    companion object {
        private const val TAG = "GazeMap"
        private const val LOG_INTERVAL = 15
    }
}
