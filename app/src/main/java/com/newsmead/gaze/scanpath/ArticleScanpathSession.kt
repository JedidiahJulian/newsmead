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
 * Raw observations are assigned against the current rendered article geometry.
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

    init { textView.addTextChangedListener(textWatcher) }

    private fun createEngine(captureFloor: Double? = null) = ScanpathEngine(words.ranges, config,
        onEvent = { event -> onEvent(generation, event) },
        onFixation = { fixation -> onFixation(generation, fixation) }, retainEvents = false,
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
        }
        val current = geometryReader.read(textView)
        if (current == null) {
            onGeometry(generation, null)
            if (geometry != null) dispatchEngine { engine.boundary("layout_unavailable", now) }
            geometry = null
            snapshotId = null
            geometryChangedMs = Double.POSITIVE_INFINITY
            return
        }
        if (current == geometry) return
        geometry = current
        snapshotId = "geometry:${++geometrySequence}"
        geometryChangedMs = now
        val snapshot = current.forEngine(snapshotId!!)
        onGeometry(generation, snapshot)
        dispatchEngine { engine.updateGeometry(snapshot, now) }
    }

    fun record(observation: MgazeNetGazeProvider.Observation) {
        checkMainThread()
        if (closed || !active) return
        checkGeometry()
        val x = observation.rawX?.toDouble()
        val y = observation.rawY?.toDouble()
        val capture = observation.captureMs
        val current = geometry
        val valid = observation.reason == "coordinate" && x != null && y != null &&
            x.isFinite() && y.isFinite() && capture != null && capture.isFinite()
        val settled = capture != null && capture >= geometryChangedMs + GEOMETRY_SETTLE_MS
        val consultationReason = consultation.exclusion(capture)
        val target = if (valid && settled && current != null && consultationReason == null)
            words.targetAt(textView, current, x!!, y!!) else TextTarget.INVALID
        val eligible = valid && settled && target.isValid
        val sample = ScanpathSample(
            capture, observation.outputMs, x, y,
            target.lineIndex.takeIf { target.isValid },
            target.wordStart.takeIf { target.hasWord }, target.wordEnd.takeIf { target.hasWord },
            snapshotId, eligible,
            when {
                consultationReason != null -> consultationReason
                !valid -> observation.reason
                current == null -> "layout_unavailable"
                !settled -> "geometry_unsettled"
                !target.isValid -> "outside_visible_text"
                else -> null
            },
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
                dispatchEngine { engine.updateGeometry(snapshot, now) }
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
        private fun elapsedMs() = SystemClock.elapsedRealtimeNanos() / 1e6
    }
}
