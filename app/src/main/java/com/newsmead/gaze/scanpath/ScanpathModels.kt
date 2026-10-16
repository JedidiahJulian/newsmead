package com.newsmead.gaze.scanpath

/** Offsets are Android UTF-16. Coordinates are pixels in the explicitly selected frame. */
data class WordRange(val startUtf16: Int, val endUtf16: Int)
data class ScanpathRect(val left: Double, val top: Double, val right: Double, val bottom: Double)
data class ScanpathLine(
    val startUtf16: Int, val endUtf16: Int,
    val left: Double, val right: Double, val top: Double, val bottom: Double
)
enum class ScanpathTextDirection { LTR, RTL, UNKNOWN }
data class ScanpathGeometry(
    val snapshotId: String,
    val textOriginX: Double, val textOriginY: Double,
    val visibleRectScreen: ScanpathRect,
    val lineHeightPx: Double,
    val lines: List<ScanpathLine>,
    val textDirection: ScanpathTextDirection? = null
)
/** Versioned coordinate source; neither selection performs calibration or filtering. */
enum class ScanpathCoordinateStream(val id: String) {
    CALIBRATED_RAW("calibrated_raw_v1"),
    ARTICLE_SMOOTHED("article_smoothed_v1")
}

data class ScanpathSample(
    val captureMs: Double?, val deliveryMs: Double?,
    val rawX: Double?, val rawY: Double?,
    val line: Int?, val wordStartUtf16: Int?, val wordEndUtf16: Int?,
    val geometrySnapshotId: String?,
    val eligible: Boolean = true, val exclusion: String? = null,
    val displayX: Double? = null, val displayY: Double? = null,
    val coordinateStream: ScanpathCoordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW,
    val motionCompensation: ScanpathMotionCompensation? = null
) {
    val processingX: Double? get() = when (coordinateStream) {
        ScanpathCoordinateStream.CALIBRATED_RAW -> rawX
        ScanpathCoordinateStream.ARTICLE_SMOOTHED -> displayX
    }
    val processingY: Double? get() = when (coordinateStream) {
        ScanpathCoordinateStream.CALIBRATED_RAW -> rawY
        ScanpathCoordinateStream.ARTICLE_SMOOTHED -> displayY
    }
    /** Body-local pixels only for validated opt-in metadata; processing/display stay on screen. */
    val analysisX: Double? get() {
        val motion = motionCompensation ?: return processingX
        return motion.contentX.takeIf { motion.validFor(this) }
    }
    val analysisY: Double? get() {
        val motion = motionCompensation ?: return processingY
        return motion.contentY.takeIf { motion.validFor(this) }
    }
}

/** Optional values are study settings, never inferred difficulty thresholds. */
data class ScanpathConfig(
    val dispersionLines: Double = 0.5,
    val minFixationMs: Double = 100.0,
    val minFixationSamples: Int = 3,
    /** Operational cap: a full unresolved buffer becomes a censored fixation and explicit break. */
    val maxPendingSamples: Int = 4096,
    val maxSampleGapMs: Double = 125.0,
    val maxTransitionGapMs: Double = 125.0,
    val assignmentAgreement: Double = 0.8,
    val returnSweepEndFraction: Double? = null,
    val returnSweepStartFraction: Double? = null,
    val textDirection: ScanpathTextDirection? = null,
    val cycleWindowMs: Double? = null,
    val wordResidenceMs: Double? = null,
    val lineResidenceMs: Double? = null,
    val progressionWindowMs: Double? = null,
    val wordProgressionMaxUnitsPerS: Double? = null,
    val lineProgressionMaxUnitsPerS: Double? = null,
    val progressionMinObservedFraction: Double? = null
) {
    init {
        require(dispersionLines.isFinite() && dispersionLines > 0)
        require(minFixationMs.isFinite() && minFixationMs > 0 && minFixationSamples >= 2)
        require(maxPendingSamples >= minFixationSamples)
        require(maxSampleGapMs.isFinite() && maxSampleGapMs > 0)
        require(maxTransitionGapMs.isFinite() && maxTransitionGapMs >= 0)
        require(assignmentAgreement.isFinite() && assignmentAgreement > 0.5 && assignmentAgreement <= 1)
        require((returnSweepStartFraction == null) == (returnSweepEndFraction == null))
        if (returnSweepStartFraction != null && returnSweepEndFraction != null) {
            require(returnSweepStartFraction.isFinite() && returnSweepEndFraction.isFinite())
            require(returnSweepStartFraction >= 0 && returnSweepStartFraction < returnSweepEndFraction && returnSweepEndFraction <= 1)
        }
        listOf(cycleWindowMs, wordResidenceMs, lineResidenceMs, progressionWindowMs).forEach {
            require(it == null || (it.isFinite() && it > 0))
        }
        listOf(wordProgressionMaxUnitsPerS, lineProgressionMaxUnitsPerS).forEach {
            require(it == null || (it.isFinite() && it >= 0))
        }
        require(progressionMinObservedFraction == null ||
            (progressionMinObservedFraction.isFinite() && progressionMinObservedFraction > 0 && progressionMinObservedFraction <= 1))
        if (wordProgressionMaxUnitsPerS != null || lineProgressionMaxUnitsPerS != null) {
            require(progressionWindowMs != null && progressionMinObservedFraction != null)
        }
    }
}

data class ScanpathFixation(
    val index: Int, val segment: Int, val startMs: Double, val endMs: Double,
    val durationMs: Double, val sampleCount: Int, val x: Double, val y: Double,
    val dispersionPx: Double, val wordStartUtf16: Int?, val wordEndUtf16: Int?,
    val wordSampleAgreement: Double, val line: Int?, val lineSampleAgreement: Double,
    val leftCensored: Boolean, val rightCensored: Boolean,
    val geometrySnapshotId: String?, val evidenceAvailableMs: Double?,
    val evidenceTimeBasis: String
)
data class ScanpathEvent(
    val eventId: String, val patternId: String, val resolution: String,
    val fixationIndices: List<Int>, val startMs: Double?, val endMs: Double?,
    val evidenceAvailableMs: Double?, val evidenceTimeBasis: String?,
    val metrics: Map<String, Any?> = emptyMap(), val limitations: List<String> = emptyList()
)

/** Established compact pending prefix. It is not a finalized physiological fixation. */
data class ScanpathCandidateEvidence(
    val candidateId: String, val segment: Int,
    val startMs: Double, val endMs: Double, val durationMs: Double, val sampleCount: Int,
    val wordStartUtf16: Int?, val wordEndUtf16: Int?, val line: Int?,
    val wordSampleAgreement: Double, val lineSampleAgreement: Double,
    val geometrySnapshotId: String?, val evidenceAvailableMs: Double?, val evidenceTimeBasis: String,
)
