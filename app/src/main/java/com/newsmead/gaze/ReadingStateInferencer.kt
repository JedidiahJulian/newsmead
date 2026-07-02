package com.newsmead.gaze

/**
 * Stage 5 RSI feed: converts the Stage 4 line-index stream into the events the
 * reading-state layer can consume. It intentionally uses only line dwell,
 * fixation, and regression signals; consumer camera sampling is not reliable
 * enough for saccade-velocity or microsaccade features.
 */
class ReadingStateInferencer(
    private val listener: Listener,
    private val fixationThresholdMs: Long = DEFAULT_FIXATION_THRESHOLD_MS,
    private val minDwellMs: Long = DEFAULT_MIN_DWELL_MS,
) {
    private var activeLine: Int? = null
    private var activeLineCount = 0
    private var activeStartedAtMs = 0L
    private var fixationEmitted = false

    fun onLine(lineIndex: Int, lineCount: Int, timestampMs: Long = System.currentTimeMillis()) {
        if (lineIndex < 0 || lineCount <= 0) return

        listener.onLineSample(LineSample(lineIndex, lineCount, timestampMs))

        val currentLine = activeLine
        if (currentLine == null) {
            startLine(lineIndex, lineCount, timestampMs)
            return
        }

        if (lineIndex == currentLine) {
            maybeEmitFixation(timestampMs)
            return
        }

        val dwellMs = timestampMs - activeStartedAtMs
        if (!fixationEmitted && dwellMs >= fixationThresholdMs) {
            listener.onFixation(FixationEvent(currentLine, activeLineCount, activeStartedAtMs, dwellMs))
        }
        if (dwellMs >= minDwellMs) {
            listener.onDwell(DwellEvent(currentLine, activeLineCount, activeStartedAtMs, dwellMs))
        }
        if (lineIndex < currentLine) {
            listener.onRegression(RegressionEvent(currentLine, lineIndex, timestampMs))
        }

        startLine(lineIndex, lineCount, timestampMs)
    }

    fun flush(timestampMs: Long = System.currentTimeMillis()) {
        val currentLine = activeLine ?: return
        val dwellMs = timestampMs - activeStartedAtMs
        if (!fixationEmitted && dwellMs >= fixationThresholdMs) {
            listener.onFixation(FixationEvent(currentLine, activeLineCount, activeStartedAtMs, dwellMs))
            fixationEmitted = true
        }
        if (dwellMs >= minDwellMs) {
            listener.onDwell(DwellEvent(currentLine, activeLineCount, activeStartedAtMs, dwellMs))
        }
    }

    private fun startLine(lineIndex: Int, lineCount: Int, timestampMs: Long) {
        activeLine = lineIndex
        activeLineCount = lineCount
        activeStartedAtMs = timestampMs
        fixationEmitted = false
    }

    private fun maybeEmitFixation(timestampMs: Long) {
        if (fixationEmitted) return
        val currentLine = activeLine ?: return
        val dwellMs = timestampMs - activeStartedAtMs
        if (dwellMs >= fixationThresholdMs) {
            listener.onFixation(FixationEvent(currentLine, activeLineCount, activeStartedAtMs, dwellMs))
            fixationEmitted = true
        }
    }

    interface Listener {
        fun onLineSample(sample: LineSample)
        fun onFixation(event: FixationEvent)
        fun onDwell(event: DwellEvent)
        fun onRegression(event: RegressionEvent)
    }

    data class LineSample(val lineIndex: Int, val lineCount: Int, val timestampMs: Long)
    data class FixationEvent(
        val lineIndex: Int,
        val lineCount: Int,
        val startedAtMs: Long,
        val durationMs: Long,
    )
    data class DwellEvent(
        val lineIndex: Int,
        val lineCount: Int,
        val startedAtMs: Long,
        val durationMs: Long,
    )
    data class RegressionEvent(val fromLine: Int, val toLine: Int, val timestampMs: Long)

    companion object {
        const val DEFAULT_FIXATION_THRESHOLD_MS = 300L
        const val DEFAULT_MIN_DWELL_MS = 120L
    }
}