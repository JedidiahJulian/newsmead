package com.newsmead.gaze

import com.newsmead.gaze.mahalanobis.MahalanobisConfig
import com.newsmead.gaze.mahalanobis.RdfFeatureVector

/** Independent consumer of mapped gaze, never Scanpath classifications or aid decisions. */
class ReadingFeaturePipeline(val config: MahalanobisConfig = MahalanobisConfig()) {
    private val rolling = RollingReadingFeatures(config)
    private val stabilizer = GazeTargetStabilizer()
    private var lastValidAt: Long? = null
    private var lastCapture: Long? = null
    private var activeLine: Int? = null
    private var activeStartedAt: Long? = null
    private fun newInferencer() = ReadingStateInferencer(object : ReadingStateInferencer.Listener {
        override fun onLineSample(sample: ReadingStateInferencer.LineSample) = Unit
        override fun onFixation(event: ReadingStateInferencer.FixationEvent) = rolling.fixation()
        override fun onDwell(event: ReadingStateInferencer.DwellEvent) =
            rolling.dwell(event.startedAtMs, event.startedAtMs + event.durationMs)
        override fun onRegression(event: ReadingStateInferencer.RegressionEvent) = rolling.regression()
    })
    private var inferencer = newInferencer()

    fun sample(lineIndex: Int?, lineCount: Int?, eligible: Boolean, captureMs: Long) {
        val previous = lastCapture
        if (captureMs < 0 || (previous != null && captureMs <= previous)) return
        if (previous != null && captureMs - previous > config.maxGapMs) breakContinuity()
        lastCapture = captureMs
        val valid = eligible && lineIndex != null && lineCount != null && lineIndex in 0 until lineCount
        rolling.recordQuality(captureMs, valid)
        if (!valid) { breakContinuity(); return }
        // Match the existing reading-feature path: mapped-line jitter is confirmed
        // by the shared stabilizer before it creates line-event residence.
        val target = stabilizer.update(TextTarget(lineIndex!!, lineCount!!), captureMs)
        if (!target.isValid) { breakContinuity(); return }
        inferencer.onLine(target.lineIndex, target.lineCount, captureMs)
        if (activeLine != target.lineIndex) {
            activeLine = target.lineIndex
            activeStartedAt = captureMs
        }
        lastValidAt = captureMs
    }

    fun tick(nowMs: Long): RdfFeatureVector? {
        if (lastCapture?.let { nowMs - it > config.maxGapMs } == true) breakContinuity()
        return rolling.tick(nowMs, activeStartedAt, lastValidAt)
    }

    /** Finalize at the last observed capture, excluding missing time from residence. */
    fun breakContinuity() {
        lastValidAt?.let { inferencer.flush(it) }
        lastValidAt = null
        activeLine = null
        activeStartedAt = null
        stabilizer.reset()
        inferencer = newInferencer()
    }

    fun boundary() {
        breakContinuity(); rolling.reset(); lastCapture = null
    }
}
