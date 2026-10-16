package com.newsmead.gaze.scanpath

import android.os.Looper
import android.os.SystemClock
import android.text.Editable
import android.text.TextWatcher
import android.widget.TextView
import com.newsmead.gaze.TextTarget
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import java.util.Locale

/**
 * Main-thread, in-memory article adapter. No camera images, files or aid decisions.
 * The declared coordinate stream is assigned against current rendered article geometry.
 */
class ArticleScanpathSession(
    private val textView: TextView,
    private val config: ScanpathConfig = ScanpathConfig(),
    private val onEvent: (generation: Int, event: ScanpathEvent) -> Unit = { _, _ -> },
    private val onGeometry: (generation: Int, geometry: ScanpathGeometry?) -> Unit = { _, _ -> },
    private val onInput: (generation: Int, sample: ScanpathSample) -> Unit = { _, _ -> },
    private val onCandidate: (generation: Int, candidate: ScanpathCandidateEvidence?) -> Unit = { _, _ -> },
    private val onFixation: (generation: Int, fixation: ScanpathFixation) -> Unit = { _, _ -> },
    private val onAcceptance: (generation: Int, sample: ScanpathSample, accepted: Boolean) -> Unit = { _, _, _ -> },
    val coordinateStream: ScanpathCoordinateStream = ScanpathCoordinateStream.CALIBRATED_RAW,
    /** Live aid prototype only; frozen Pilot/Replay retain the default screen-frame adapter. */
    val motionCompensatedAids: Boolean = false,
) {
    var generation: Int = 0
        private set
    private var body = textView.text.toString()
    private var bodySource = textView.text
    private var bodyDirty = false
    private var locale: Locale = textView.textLocale
    private var words = ScanpathWordMapper(body, textView)
    private val geometryReader = ScanpathTextGeometryReader()
    var engine: ScanpathEngine = createEngine()
        private set
    private var geometry: ScanpathTextGeometry? = null
    private var snapshotId: String? = null
    private var geometrySequence = 0
    private var geometryChangedMs = Double.POSITIVE_INFINITY
    private val geometryTimeline = ScanpathGeometryTimeline()
    private var controlledAutoScroll = false
    private var layoutSignature: List<Any?>? = null
    private var active = true
    private var closed = false
    private val consultation = ConsultationCaptureGate()
    private var engineDispatchDepth = 0
    private val pendingConsultationBoundaries = ArrayDeque<Pair<String, Double>>()
    private val textWatcher = object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            bodyDirty = true
            geometryReader.invalidate()
        }
        override fun afterTextChanged(s: Editable?) = Unit
    }

    init {
        require(!motionCompensatedAids || coordinateStream == ScanpathCoordinateStream.ARTICLE_SMOOTHED)
        textView.addTextChangedListener(textWatcher)
    }

    private fun createEngine(captureFloor: Double? = null) = ScanpathEngine(words.ranges, config,
        onEvent = { event -> onEvent(generation, event) },
        onFixation = { fixation ->
            // Engine clusters are in content pixels; aid policy consumes their current
            // screen projection. Selected input coordinates remain untouched in onInput.
            val g = geometry
            onFixation(generation, if (motionCompensatedAids && g != null)
                fixation.copy(x = fixation.x + g.originX, y = fixation.y + g.originY) else fixation)
        }, retainEvents = false,
        initialCaptureMs = captureFloor)

    /** Also called before drawing, so a scroll breaks continuity without waiting for gaze. */
    fun checkGeometry() {
        checkMainThread()
        if (closed || !active) return
        val now = elapsedMs()
        val currentBody = if (bodyDirty || textView.text !== bodySource) textView.text.toString() else body
        bodySource = textView.text
        bodyDirty = false
        if (currentBody != body || textView.textLocale != locale) {
            onGeometry(generation, null)
            dispatchEngine { engine.boundary("text_changed", now) }
            generation++
            body = currentBody
            locale = textView.textLocale
            words = ScanpathWordMapper(body, textView)
            geometryReader.invalidate()
            engine = createEngine(engine.latestObservedCaptureMs)
            geometry = null
            snapshotId = null
            geometrySequence = 0
            geometryChangedMs = Double.POSITIVE_INFINITY
            geometryTimeline.clear()
        }
        val current = geometryReader.read(textView)
        if (current == null) {
            onGeometry(generation, null)
            if (geometry != null) dispatchEngine { engine.boundary("layout_unavailable", now) }
            geometry = null
            snapshotId = null
            geometryChangedMs = Double.POSITIVE_INFINITY
            geometryTimeline.clear()
            return
        }
        val signature = listOf(textView.width, textView.height, textView.textSize, textView.textScaleX,
            textView.letterSpacing, textView.typeface, textView.lineSpacingExtra, textView.lineSpacingMultiplier,
            textView.totalPaddingLeft, textView.totalPaddingTop, textView.totalPaddingRight, textView.totalPaddingBottom)
        val sameLayout = !motionCompensatedAids || signature == layoutSignature
        layoutSignature = signature
        if (current == geometry && sameLayout) {
            if (motionCompensatedAids) snapshotId?.let { geometryTimeline.record(now, current.forEngine(it)) }
            return
        }
        val previous = geometry
        val translation = motionCompensatedAids && controlledAutoScroll && sameLayout && previous != null &&
            current.originX == previous.originX && current.lines == previous.lines &&
            current.lineHeightPx == previous.lineHeightPx && current.direction == previous.direction &&
            current.visible.left == previous.visible.left && current.visible.right == previous.visible.right
        geometry = current
        if (translation && snapshotId != null) {
            val display = current.forEngine(snapshotId!!)
            geometryTimeline.record(now, display)
            onGeometry(generation, display)
            return
        }
        snapshotId = "geometry:${++geometrySequence}"
        geometryChangedMs = now
        val snapshot = current.forEngine(snapshotId!!)
        geometryTimeline.clear()
        if (motionCompensatedAids) geometryTimeline.record(now, snapshot)
        onGeometry(generation, snapshot)
        dispatchEngine { engine.updateGeometry(engineGeometry(snapshot), now) }
    }

    private fun engineGeometry(display: ScanpathGeometry): ScanpathGeometry = if (!motionCompensatedAids) display
        else display.copy(textOriginX = 0.0, textOriginY = 0.0, visibleRectScreen = ScanpathRect(
            0.0, 0.0, (textView.layout?.width ?: textView.width).toDouble(),
            (textView.layout?.height ?: textView.height).toDouble()))

    /** Only the explicit navigation controller may mark translations as controlled. */
    fun autoScrollMotion(moving: Boolean, reason: String? = null) {
        checkMainThread()
        if (closed || !active || !motionCompensatedAids) return
        if (moving) {
            checkGeometry()
            controlledAutoScroll = true
        } else {
            // An external move/reflow must never be smuggled through the final self-scroll snapshot.
            if (reason in listOf("external_scroll", "reading_layout_changed")) {
                controlledAutoScroll = false
                onGeometry(generation, null)
                dispatchEngine { engine.boundary("uncontrolled_scroll", elapsedMs()) }
                geometry = null
                snapshotId = null
                geometryTimeline.clear()
                geometryChangedMs = Double.POSITIVE_INFINITY
            }
            checkGeometry()
            controlledAutoScroll = false
        }
    }

    fun record(observation: MgazeNetGazeProvider.Observation) {
        checkMainThread()
        if (closed || !active) return
        checkGeometry()
        val rawX = observation.rawX?.toDouble()
        val rawY = observation.rawY?.toDouble()
        val displayX = observation.x?.toDouble()
        val displayY = observation.y?.toDouble()
        val x = if (coordinateStream == ScanpathCoordinateStream.CALIBRATED_RAW) rawX else displayX
        val y = if (coordinateStream == ScanpathCoordinateStream.CALIBRATED_RAW) rawY else displayY
        val capture = observation.captureMs
        val current = geometry
        val valid = observation.reason == "coordinate" && x != null && y != null &&
            x.isFinite() && y.isFinite() && capture != null && capture.isFinite()
        val settled = capture != null && capture >= geometryChangedMs + GEOMETRY_SETTLE_MS
        val historical = if (motionCompensatedAids && capture != null)
            geometryTimeline.at(capture, MAX_CAPTURE_GEOMETRY_AGE_MS) else null
        val captureGeometry = if (motionCompensatedAids) historical?.geometry?.let {
            ScanpathTextGeometry(it.textOriginX, it.textOriginY, it.visibleRectScreen,
                it.lineHeightPx, it.lines, it.textDirection ?: ScanpathTextDirection.UNKNOWN)
        } else current
        val freshMotionInput = !motionCompensatedAids || (capture != null &&
            observation.outputMs >= capture && observation.outputMs <= elapsedMs() &&
            elapsedMs() - capture <= MAX_MOTION_INPUT_AGE_MS)
        val consultationReason = consultation.exclusion(capture)
        val target = if (valid && settled && captureGeometry != null && freshMotionInput && consultationReason == null)
            words.targetAt(textView, captureGeometry, x!!, y!!) else TextTarget.INVALID
        val eligible = valid && settled && freshMotionInput && target.isValid
        val sample = ScanpathSample(
            capture, observation.outputMs, rawX, rawY,
            target.lineIndex.takeIf { target.isValid },
            target.wordStart.takeIf { target.hasWord }, target.wordEnd.takeIf { target.hasWord },
            snapshotId, eligible,
            when {
                consultationReason != null -> consultationReason
                !valid -> if (observation.reason != "coordinate" ||
                    coordinateStream == ScanpathCoordinateStream.CALIBRATED_RAW)
                    observation.reason else "coordinate_unavailable"
                current == null -> "layout_unavailable"
                !settled -> "geometry_unsettled"
                motionCompensatedAids && historical == null -> "scroll_geometry_unavailable"
                !freshMotionInput -> "scroll_input_stale"
                !target.isValid -> "outside_visible_text"
                else -> null
            },
            displayX, displayY, coordinateStream,
            motionCompensation = if (motionCompensatedAids && historical != null && valid)
                ScanpathMotionCompensation(historical.atMs, historical.geometry.snapshotId,
                    historical.geometry.textOriginX, historical.geometry.textOriginY,
                    x!! - historical.geometry.textOriginX, y!! - historical.geometry.textOriginY) else null,
        )
        onInput(generation, sample)
        val accepted = if (consultationReason == null) dispatchEngine { engine.onSample(sample) } else false
        onAcceptance(generation, sample, accepted)
        onCandidate(generation, if (accepted) engine.currentCandidateEvidence() else null)
        if (engine.fixationCount >= MAX_HISTORY_FIXATIONS) {
            // An explicit operational boundary bounds full P12 support work and retained history.
            val now = elapsedMs()
            onGeometry(generation, null)
            dispatchEngine { engine.boundary("history_limit", now) }
            generation++
            engine = createEngine(engine.latestObservedCaptureMs)
            geometry?.let { state -> snapshotId?.let { id ->
                val snapshot = state.forEngine(id)
                onGeometry(generation, snapshot)
                dispatchEngine { engine.updateGeometry(engineGeometry(snapshot), now) }
            } }
        }
    }

    fun boundary(reason: String) {
        checkMainThread()
        if (!closed && active) dispatchEngine { engine.boundary(reason, elapsedMs()) }
    }

    fun setConsultation(value: Boolean, atMs: Double, reason: String) {
        checkMainThread()
        if (closed) return
        consultation.change(value, atMs)
        pendingConsultationBoundaries.addLast("definition_${if (value) "opened" else "closed"}:$reason" to atMs)
        if (engineDispatchDepth == 0) dispatchEngine { Unit }
    }

    /** Callbacks may open help inside classification; never mutate pending state recursively. */
    private fun <T> dispatchEngine(block: () -> T): T {
        engineDispatchDepth++
        try { return block() }
        finally {
            engineDispatchDepth--
            if (engineDispatchDepth == 0) {
                engineDispatchDepth++
                try {
                    while (pendingConsultationBoundaries.isNotEmpty()) {
                        val (reason, at) = pendingConsultationBoundaries.removeFirst()
                        engine.boundary(reason, at)
                    }
                } finally { engineDispatchDepth-- }
            }
        }
    }

    fun pause(reason: String) {
        boundary(reason)
        controlledAutoScroll = false
        geometryTimeline.clear()
        active = false
    }

    fun resume() {
        checkMainThread()
        if (closed) return
        active = true
        checkGeometry()
    }

    fun close(reason: String) {
        checkMainThread()
        if (closed) return
        boundary(reason)
        active = false
        closed = true
        textView.removeTextChangedListener(textWatcher)
    }

    private fun checkMainThread() = check(Looper.myLooper() == Looper.getMainLooper())

    companion object {
        // Geometry eligibility guard, not a fixation or difficulty threshold.
        const val GEOMETRY_SETTLE_MS = 150.0
        // Engineering resource guard, not a reading or difficulty threshold.
        const val MAX_HISTORY_FIXATIONS = 512
        const val MAX_CAPTURE_GEOMETRY_AGE_MS = 100.0
        const val MAX_MOTION_INPUT_AGE_MS = 250.0
        private fun elapsedMs() = SystemClock.elapsedRealtimeNanos() / 1e6
    }
}
