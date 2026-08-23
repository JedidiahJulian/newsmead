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

    data class PipelineDiagnostics(
        val source: LocalRawGazeSource.Diagnostics?,
        val rawX: Float,
        val rawY: Float,
        val medianX: Float,
        val medianY: Float,
        val filteredX: Float,
        val filteredY: Float,
        val mappedX: Float,
        val mappedY: Float,
        val outputX: Float,
        val outputY: Float,
        val correctionActive: Boolean,
        val outputTimestampMs: Long,
    )

    fun interface OnDiagnostics {
        fun onDiagnostics(diagnostics: PipelineDiagnostics)
    }

    private val medianX = MedianFilter()
    private val medianY = MedianFilter()
    private val smoothX = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private val smoothY = OneEuroFilter(minCutoff = 0.7, beta = 0.005)
    private var onGaze: GazeProvider.OnGaze? = null
    private var onDiagnostics: OnDiagnostics? = null
    private var onSourceDiagnostics: LocalRawGazeSource.OnDiagnostics? = null
    @Volatile private var latestSourceDiagnostics: LocalRawGazeSource.Diagnostics? = null
    private var logCounter = 0

    override fun setOnGaze(listener: GazeProvider.OnGaze) {
        onGaze = listener
    }

    fun setOnDiagnostics(listener: OnDiagnostics) {
        onDiagnostics = listener
    }

    fun setOnSourceDiagnostics(listener: LocalRawGazeSource.OnDiagnostics) {
        onSourceDiagnostics = listener
    }

    override fun start(owner: LifecycleOwner) {
        rawSource.setOnDiagnostics { diagnostics ->
            latestSourceDiagnostics = diagnostics
            onSourceDiagnostics?.onDiagnostics(diagnostics)
        }
        rawSource.setOnRawGaze { gazeX, gazeY, timestampMs ->
            val mx = medianX.filter(gazeX)
            val my = medianY.filter(gazeY)
            val fx = smoothX.filter(mx, timestampMs)
            val fy = smoothY.filter(my, timestampMs)
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
            onDiagnostics?.onDiagnostics(
                PipelineDiagnostics(
                    source = latestSourceDiagnostics?.takeIf {
                        it.outcome == LocalRawGazeSource.DiagnosticOutcome.EMITTED &&
                            it.gazeX == gazeX && it.gazeY == gazeY
                    },
                    rawX = gazeX,
                    rawY = gazeY,
                    medianX = mx,
                    medianY = my,
                    filteredX = fx,
                    filteredY = fy,
                    mappedX = screen[0],
                    mappedY = screen[1],
                    outputX = corrected[0],
                    outputY = corrected[1],
                    correctionActive = correction != null,
                    outputTimestampMs = timestampMs,
                ),
            )
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
