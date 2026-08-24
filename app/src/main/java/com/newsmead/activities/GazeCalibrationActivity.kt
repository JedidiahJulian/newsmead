package com.newsmead.activities

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.PointF
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextPaint
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.ActivityGazeCalibrationBinding
import com.newsmead.gaze.CalibrationPointCollector
import com.newsmead.gaze.CalibrationQuality
import com.newsmead.gaze.CalibrationSample
import com.newsmead.gaze.CalibrationSessionLog
import com.newsmead.gaze.CalibrationStore
import com.newsmead.gaze.CalibrationView
import com.newsmead.gaze.FixationWindowFilter
import com.newsmead.gaze.GazeMapper
import com.newsmead.gaze.LocalGazeSources
import com.newsmead.gaze.LocalRawGazeSource
import com.newsmead.gaze.ReadingSpatialMetrics
import java.util.Locale
import kotlin.math.hypot

/**
 * Full-screen 16-dot gaze calibration (docs/calibration-design.md).
 *
 * Per point: APPEAR -> HOLD_ATTENTION -> SETTLE -> SAMPLE (adaptive) -> CONFIRM,
 * with MAD/dispersion rejection (FixationWindowFilter), one automatic re-capture
 * and exclusion after a second failure. The sequence uses a predictable fixed
 * row-major order, preceded by a practice point, and a near-center point repeats at the end as a
 * drift check. After the fit, a leave-one-out report plus a 5-point held-out
 * validation pass feed a researcher quality gate (Accept / Redo worst / Redo
 * all); calibration_16point.csv is written only on Accept. Every point is also
 * logged incrementally to a per-session JSON for thesis data-quality reporting.
 */
class GazeCalibrationActivity : AppCompatActivity() {

    private enum class Kind { PRACTICE, FIT, DRIFT_REPEAT, VALIDATION }

    private data class Presentation(val kind: Kind, val gridIndex: Int, val point: PointF)

    private lateinit var binding: ActivityGazeCalibrationBinding
    private var rawGazeSource: LocalRawGazeSource? = null
    private val handler = Handler(Looper.getMainLooper())
    private var tone: ToneGenerator? = null
    private lateinit var collector: CalibrationPointCollector

    // Sequence state
    private var gridPoints: List<PointF> = emptyList()
    private var driftGridIndex = -1
    private val presentations = ArrayList<Presentation>()
    private var presIndex = 0
    private var attempts = 0
    private var presentationCounter = 0
    private var started = false

    // Results
    private val fitPairs = LinkedHashMap<Int, CalibrationSample>() // gridIndex -> pair
    private val excluded = LinkedHashSet<Int>()
    private var driftFirst: FloatArray? = null
    private var driftSecond: FloatArray? = null
    private var fittedMapper: GazeMapper? = null
    private var looReport: CalibrationQuality.Report? = null
    private var looGridOrder: List<Int> = emptyList()
    private val validationObservations = ArrayList<ReadingSpatialMetrics.Observation>()
    private var driftDeltaPx = Float.NaN
    private var driftDxPx = Float.NaN
    private var driftDyPx = Float.NaN
    private var lineHeightPx = 1f

    // Diagnostics / logging
    private var sessionLog: CalibrationSessionLog? = null
    private var logFinished = false
    @Volatile private var latestBlinkStats: LocalRawGazeSource.BlinkStats? = null
    private var presStartBlinkStats: LocalRawGazeSource.BlinkStats? = null
    private var sampleWindowActive = false
    private val pointSourceEvents = ArrayList<LocalRawGazeSource.Diagnostics>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGazeCalibrationBinding.inflate(layoutInflater)
        setContentView(binding.root)
        enableImmersiveMode()
        lineHeightPx = computeLineHeightPx()
        tone = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, TONE_VOLUME)
        } catch (e: RuntimeException) {
            Log.w(TAG, "ToneGenerator unavailable; calibration runs without audio cues", e)
            null
        }
        collector = CalibrationPointCollector(binding.calibrationView, tone)
        collector.setOnSampleWindowChanged { active ->
            sampleWindowActive = active
            if (active) pointSourceEvents.clear()
        }
        startRawGazeSource()

        // Start the dot sequence only when the researcher taps Start.
        binding.progressText.setText(com.newsmead.R.string.calib_ready_instruction)
        binding.startButton.setOnClickListener {
            binding.startButton.visibility = View.GONE
            beginSequenceWhenLaidOut()
        }
        binding.btnAccept.setOnClickListener { acceptCalibration() }
        binding.btnRedoWorst.setOnClickListener { redoWorstPoints() }
        binding.btnRedoAll.setOnClickListener { redoAll() }
    }

    private fun startRawGazeSource() {
        rawGazeSource?.stop()
        rawGazeSource = LocalGazeSources.create(this).also { source ->
            source.setOnRawGaze { x, y, ts -> runOnUiThread { collector.onRawSample(x, y, ts) } }
            source.setOnFps { fps -> runOnUiThread { showFps(fps) } }
            source.setOnBlinkStats { stats -> latestBlinkStats = stats }
            source.setOnDiagnostics { diagnostics ->
                runOnUiThread {
                    if (sampleWindowActive) pointSourceEvents.add(diagnostics)
                }
            }
            source.start(this)
        }
    }

    private fun showFps(fps: Float) {
        binding.fpsText.text = String.format(Locale.US, "%.0f fps", fps)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED
        ) {
            startRawGazeSource()
        }
    }

    // --- Sequence construction --------------------------------------------

    /** Start the dot sequence once the view has a size. */
    private fun beginSequenceWhenLaidOut() {
        if (started) return
        val view = binding.calibrationView
        if (view.width == 0 || view.height == 0) {
            view.post { beginSequenceWhenLaidOut() }
            return
        }
        started = true
        startSequence()
    }

    private fun startSequence() {
        val view = binding.calibrationView
        gridPoints = computePoints(view.width, view.height)
        driftGridIndex = nearestToCenter(gridPoints, view.width, view.height)

        // Fixed row-major order restores the original, predictable traversal for
        // older adults: top-left to top-right, then each following row.
        val order = gridPoints.indices.toList()

        presentations.clear()
        presentations.add(
            Presentation(Kind.PRACTICE, -1, PointF(view.width / 2f, view.height / 2f)),
        )
        order.forEach { presentations.add(Presentation(Kind.FIT, it, gridPoints[it])) }
        presentations.add(
            Presentation(Kind.DRIFT_REPEAT, driftGridIndex, gridPoints[driftGridIndex]),
        )

        sessionLog = CalibrationSessionLog(
            this,
            view.width,
            view.height,
            resources.displayMetrics.densityDpi,
            orderSeed = FIXED_ORDER_SEED,
            orderMode = "fixed_row_major",
        )
        logFinished = false
        Log.i(TAG, "Sequence started: mode=fixed_row_major drift=$driftGridIndex order=$order")
        runPresentation(0)
    }

    private fun computePoints(w: Int, h: Int): List<PointF> {
        // 4x4 grid evenly spaced across the inset region (16 points).
        val fractions = floatArrayOf(MARGIN_FRAC, 0.3667f, 0.6333f, 1f - MARGIN_FRAC)
        val xs = fractions.map { w * it }
        val ys = fractions.map { h * it }
        return buildList { for (y in ys) for (x in xs) add(PointF(x, y)) }
    }

    private fun nearestToCenter(points: List<PointF>, w: Int, h: Int): Int {
        val cx = w / 2f
        val cy = h / 2f
        return points.indices.minByOrNull { hypot(points[it].x - cx, points[it].y - cy) } ?: 0
    }

    // --- Per-point state machine -------------------------------------------
    // APPEAR -> HOLD_ATTENTION -> SETTLE -> SAMPLE (adaptive) -> CONFIRM

    private fun runPresentation(index: Int) {
        presIndex = index
        attempts = 0
        startAttempt()
    }

    private fun startAttempt() {
        val pres = presentations[presIndex]
        presStartBlinkStats = latestBlinkStats
        binding.statusText.text = ""
        updateProgressText(pres)
        updateMiniMap()
        // The shared collector runs APPEAR -> HOLD -> SETTLE -> SAMPLE -> CONFIRM
        // and filters the samples; we own only the per-point outcome policy.
        collector.capture(pres.point.x, pres.point.y) { result ->
            if (result.status == FixationWindowFilter.Status.ACCEPTED) {
                recordSuccess(result)
                handler.postDelayed({ advance() }, CONFIRM_MS)
            } else {
                failAttempt(result)
            }
        }
    }

    private fun failAttempt(result: FixationWindowFilter.Result) {
        attempts++
        val pres = presentations[presIndex]
        Log.w(
            TAG,
            "Presentation ${presIndex + 1} (${pres.kind}, grid ${pres.gridIndex}) attempt $attempts " +
                "failed: ${result.status} raw=${result.rawCount} retained=${result.retainedCount}",
        )
        if (attempts < MAX_ATTEMPTS) {
            logPresentation(pres, result, attempt = attempts, excludedFlag = false)
            binding.statusText.text = getString(com.newsmead.R.string.calib_retry)
            handler.postDelayed({ startAttempt() }, RETRY_PAUSE_MS)
            return
        }
        // Second failure: never feed a known-bad pair to the fit (design §4.2).
        when (pres.kind) {
            Kind.FIT -> {
                excluded.add(pres.gridIndex)
                binding.statusText.text = getString(com.newsmead.R.string.calib_excluded_notice)
                sessionLog?.addFlag("point_${pres.gridIndex}_excluded_${result.status}")
            }
            Kind.DRIFT_REPEAT -> sessionLog?.addFlag("drift_repeat_failed")
            Kind.VALIDATION -> sessionLog?.addFlag("validation_point_failed")
            Kind.PRACTICE -> Unit
        }
        logPresentation(
            pres,
            result,
            attempt = attempts,
            excludedFlag = pres.kind == Kind.FIT,
        )
        updateMiniMap()
        handler.postDelayed({ advance() }, RETRY_PAUSE_MS)
    }

    private fun recordSuccess(result: FixationWindowFilter.Result) {
        val pres = presentations[presIndex]
        when (pres.kind) {
            Kind.PRACTICE -> Unit
            Kind.FIT -> {
                fitPairs[pres.gridIndex] =
                    CalibrationSample(pres.point.x, pres.point.y, result.medianX, result.medianY)
                if (pres.gridIndex == driftGridIndex && driftFirst == null) {
                    driftFirst = floatArrayOf(result.medianX, result.medianY)
                }
            }
            Kind.DRIFT_REPEAT -> driftSecond = floatArrayOf(result.medianX, result.medianY)
            Kind.VALIDATION -> {
                val mapped = fittedMapper?.map(result.medianX, result.medianY) ?: return
                val errPx = hypot(mapped[0] - pres.point.x, mapped[1] - pres.point.y)
                val validationIndex = validationObservations.size
                validationObservations.add(
                    ReadingSpatialMetrics.Observation(
                        id = validationIndex,
                        label = validationLabel(validationIndex),
                        targetX = pres.point.x,
                        targetY = pres.point.y,
                        predictedX = mapped[0],
                        predictedY = mapped[1],
                    ),
                )
                sessionLog?.addValidationPoint(
                    pres.point.x,
                    pres.point.y,
                    mapped[0],
                    mapped[1],
                    errPx,
                    errPx / lineHeightPx,
                    kotlin.math.abs(mapped[1] - pres.point.y) / lineHeightPx,
                )
            }
        }
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "%s grid=%d screen=(%.0f, %.0f) gaze=(%.4f, %.4f) raw=%d retained=%d disp=(%.4f, %.4f)",
                pres.kind, pres.gridIndex, pres.point.x, pres.point.y,
                result.medianX, result.medianY, result.rawCount, result.retainedCount,
                result.dispersionX, result.dispersionY,
            ),
        )
        logPresentation(pres, result, attempt = attempts + 1, excludedFlag = false)
        updateMiniMap()
    }

    private fun advance() {
        presIndex++
        if (presIndex < presentations.size) {
            runPresentation(presIndex)
            return
        }
        if (fittedMapper == null) fitAndStartValidation() else showGate()
    }

    // --- Fit, validation, quality gate --------------------------------------

    private fun fitAndStartValidation() {
        val ordered = fitPairs.entries.sortedBy { it.key }
        val samples = ordered.map { it.value }
        if (samples.size < MIN_FIT_POINTS) {
            showAbort(samples.size)
            return
        }
        fittedMapper = try {
            GazeMapper(samples)
        } catch (e: Exception) {
            Log.e(TAG, "Calibration fit failed", e)
            showAbort(samples.size)
            return
        }
        looGridOrder = ordered.map { it.key }
        looReport = CalibrationQuality.leaveOneOut(samples)

        computeDriftResiduals()
        sessionLog?.logDrift(
            driftFirst, driftSecond, driftDxPx, driftDyPx, driftDeltaPx, driftFlagged(),
        )
        looReport?.let { rep ->
            val summary = looSummary(samples, rep) ?: return@let
            sessionLog?.logFit(
                pointsUsed = samples.size,
                summary = summary,
                lineHeightPx = lineHeightPx,
                worstPointIds = readingWorstIndices(rep).take(WORST_REDO_COUNT).map { looGridOrder[it] },
            )
        }

        // Held-out validation pass: positions deliberately off the 4x4 grid.
        validationObservations.clear()
        sessionLog?.resetValidation()
        val view = binding.calibrationView
        VALIDATION_FRACTIONS.forEach { (fx, fy) ->
            presentations.add(
                Presentation(Kind.VALIDATION, -1, PointF(view.width * fx, view.height * fy)),
            )
        }
        runPresentation(presIndex)
    }

    private fun computeDriftResiduals() {
        driftDxPx = Float.NaN
        driftDyPx = Float.NaN
        driftDeltaPx = Float.NaN
        val mapper = fittedMapper ?: return
        val f1 = driftFirst ?: return
        val f2 = driftSecond ?: return
        val a = mapper.map(f1[0], f1[1])
        val b = mapper.map(f2[0], f2[1])
        driftDxPx = b[0] - a[0]
        driftDyPx = b[1] - a[1]
        driftDeltaPx = hypot(driftDxPx, driftDyPx)
    }

    private fun driftFlagged(): Boolean =
        driftDeltaPx.isFinite() && driftDeltaPx > DRIFT_FLAG_LINE_FRACTION * lineHeightPx

    private fun showGate() {
        binding.calibrationView.hideTarget()
        val rep = looReport
        if (rep == null) {
            showAbort(fitPairs.size)
            return
        }
        val samples = fitPairs.entries.sortedBy { it.key }.map { it.value }
        val fitSummary = looSummary(samples, rep) ?: run { showAbort(fitPairs.size); return }
        val validationSummary = ReadingSpatialMetrics.summarize(validationObservations, lineHeightPx)
        validationSummary?.let { sessionLog?.logValidationSummary(it, lineHeightPx) }
        val provisional = fitSummary.meetsProvisionalReference &&
            validationSummary?.meetsProvisionalReference == true && !driftFlagged() && excluded.isEmpty()
        val color = if (provisional) GREEN_COLOR else AMBER_COLOR
        val summary = buildCalibrationSummary(fitSummary, validationSummary, provisional)
        Log.i(
            TAG,
            String.format(
                Locale.US,
                "GATE: LOO vertical median=%.2f lines p95=%.2f max=%.2f within1.2=%d/%d; " +
                    "validation within1.2=%d/%d; excluded=%d drift=(%.0f,%.0f) px",
                fitSummary.medianVerticalPx / lineHeightPx,
                fitSummary.p95VerticalPx / lineHeightPx,
                fitSummary.maxVerticalPx / lineHeightPx,
                fitSummary.withinReference, fitSummary.points.size,
                validationSummary?.withinReference ?: 0, validationSummary?.points?.size ?: 0,
                excluded.size, driftDxPx, driftDyPx,
            ),
        )
        binding.progressText.text = getString(com.newsmead.R.string.calib_gate_title)
        binding.statusText.text = ""
        binding.gateText.text = summary
        binding.gateText.setTextColor(color)
        binding.btnAccept.visibility = View.VISIBLE
        binding.btnRedoWorst.visibility = View.VISIBLE
        binding.btnRedoAll.visibility = View.VISIBLE
        binding.gatePanel.visibility = View.VISIBLE
    }

    private fun showAbort(usablePoints: Int) {
        binding.calibrationView.hideTarget()
        sessionLog?.addFlag("insufficient_points")
        binding.progressText.text = getString(com.newsmead.R.string.calib_gate_title)
        binding.gateText.text = getString(com.newsmead.R.string.calib_aborted, usablePoints, gridPoints.size)
        binding.gateText.setTextColor(RED_COLOR)
        binding.btnAccept.visibility = View.GONE
        binding.btnRedoWorst.visibility = View.GONE
        binding.btnRedoAll.visibility = View.VISIBLE
        binding.gatePanel.visibility = View.VISIBLE
    }

    private fun acceptCalibration() {
        val ordered = fitPairs.entries.sortedBy { it.key }.map { it.value }
        CalibrationStore.save(this, ordered)
        sessionLog?.finish("accepted")
        logFinished = true
        binding.gatePanel.visibility = View.GONE
        binding.progressText.text =
            getString(com.newsmead.R.string.calib_done, ordered.size, gridPoints.size)
        binding.statusText.text = getString(com.newsmead.R.string.calib_saved)
    }

    private fun redoWorstPoints() {
        val rep = looReport ?: return
        val worstGrid = readingWorstIndices(rep).take(WORST_REDO_COUNT).map { looGridOrder[it] }
        val redoSet = (worstGrid + excluded).toSet()
        Log.i(TAG, "Redoing points: $redoSet")
        redoSet.forEach { fitPairs.remove(it) }
        excluded.removeAll(redoSet)
        fittedMapper = null
        looReport = null
        binding.gatePanel.visibility = View.GONE

        presentations.clear()
        redoSet.sorted().forEach {
            presentations.add(Presentation(Kind.FIT, it, gridPoints[it]))
        }
        // After these, advance() refits and re-runs the validation pass.
        runPresentation(0)
    }

    private fun redoAll() {
        sessionLog?.finish("redo_all")
        logFinished = true
        fitPairs.clear()
        excluded.clear()
        driftFirst = null
        driftSecond = null
        fittedMapper = null
        looReport = null
        validationObservations.clear()
        driftDeltaPx = Float.NaN
        driftDxPx = Float.NaN
        driftDyPx = Float.NaN
        presentationCounter = 0
        binding.gatePanel.visibility = View.GONE
        startSequence()
    }

    // --- UI helpers ---------------------------------------------------------

    private fun updateProgressText(pres: Presentation) {
        binding.progressText.text = when (pres.kind) {
            Kind.PRACTICE -> getString(com.newsmead.R.string.calib_practice_label)
            Kind.FIT -> getString(
                com.newsmead.R.string.calib_progress,
                (fitPairs.size + excluded.size + 1).coerceAtMost(gridPoints.size),
                gridPoints.size,
            )
            Kind.DRIFT_REPEAT -> getString(com.newsmead.R.string.calib_drift_label)
            Kind.VALIDATION -> getString(
                com.newsmead.R.string.calib_validation_progress,
                presentations.subList(0, presIndex).count { it.kind == Kind.VALIDATION } + 1,
                VALIDATION_FRACTIONS.size,
            )
        }
    }

    private fun updateMiniMap() {
        val current = presentations.getOrNull(presIndex)
        val states = gridPoints.indices.map { i ->
            when {
                current?.kind == Kind.FIT && current.gridIndex == i -> CalibrationView.MiniDotState.CURRENT
                i in excluded -> CalibrationView.MiniDotState.EXCLUDED
                fitPairs.containsKey(i) -> CalibrationView.MiniDotState.DONE
                else -> CalibrationView.MiniDotState.PENDING
            }
        }
        binding.calibrationView.setMiniMap(states)
    }

    private fun logPresentation(
        pres: Presentation,
        result: FixationWindowFilter.Result,
        attempt: Int,
        excludedFlag: Boolean,
    ) {
        val start = presStartBlinkStats
        val end = latestBlinkStats
        val blinkDropped =
            if (start != null && end != null) end.blinkDroppedFrames - start.blinkDroppedFrames else 0L
        val noFace =
            if (start != null && end != null) end.noFaceFrames - start.noFaceFrames else 0L
        sessionLog?.logPoint(
            pointId = pres.gridIndex,
            kind = pres.kind.name,
            attempt = attempt,
            status = result.status.name,
            screenX = pres.point.x,
            screenY = pres.point.y,
            presentationIndex = presentationCounter++,
            practice = pres.kind == Kind.PRACTICE,
            driftRepeat = pres.kind == Kind.DRIFT_REPEAT,
            recaptured = attempts > 0,
            excluded = excludedFlag,
            rawSampleCount = result.rawCount,
            retainedSampleCount = result.retainedCount,
            blinkDropped = blinkDropped,
            noFace = noFace,
            featureX = result.medianX,
            featureY = result.medianY,
            dispersionX = result.dispersionX,
            dispersionY = result.dispersionY,
            sourceEvents = pointSourceEvents.toList(),
        )
    }

    /**
     * On-screen height of one article body line: the locked 22dp font's metrics
     * times the article's 1.6 line-spacing multiplier plus its 1sp extra —
     * derived from the same constants ArticleFragment applies, so gate errors
     * are expressed in the unit that matters for line-AOI mapping.
     */
    private fun computeLineHeightPx(): Float {
        val dm = resources.displayMetrics
        val paint = TextPaint()
        paint.textSize = StudyConfig.ARTICLE_FONT_SIZE_DP * dm.density
        val fm = paint.fontMetrics
        val extraPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 1f, dm)
        return (fm.descent - fm.ascent) * ARTICLE_LINE_SPACING_MULT + extraPx
    }

    private fun looSummary(
        samples: List<CalibrationSample>,
        report: CalibrationQuality.Report,
    ): ReadingSpatialMetrics.Summary? {
        val observations = samples.indices.map { index ->
            val sample = samples[index]
            val gridId = looGridOrder[index]
            ReadingSpatialMetrics.Observation(
                id = gridId,
                label = gridLabel(gridId),
                targetX = sample.screenX,
                targetY = sample.screenY,
                predictedX = sample.screenX + report.dxPx[index],
                predictedY = sample.screenY + report.dyPx[index],
            )
        }
        return ReadingSpatialMetrics.summarize(observations, lineHeightPx)
    }

    private fun buildCalibrationSummary(
        fit: ReadingSpatialMetrics.Summary,
        validation: ReadingSpatialMetrics.Summary?,
        provisional: Boolean,
    ): String = buildString {
        appendLine("16-point fit — leave-one-out (not training error)")
        appendLine(metricSummary(fit))
        appendLine("Individual fit targets (signed vertical; + is below target)")
        appendLine(pointDetails(fit))
        appendLine("Worst fit: P${fit.worstVertical.id + 1} ${fit.worstVertical.label} " +
            "(${signedLines(fit.worstVertical.dyPx)} vertical, ${fit.worstVertical.errorPx.toInt()} px total)")
        appendLine("Rows 1–4 (vertical median): ${gridAxisSummary(fit, true)}")
        appendLine("Columns 1–4 (vertical median): ${gridAxisSummary(fit, false)}")
        appendLine()
        appendLine("5 held-out checks")
        if (validation == null) {
            appendLine("No clean held-out targets recorded.")
        } else {
            appendLine(metricSummary(validation))
            appendLine("Individual held-out targets")
            appendLine(pointDetails(validation))
            appendLine("Worst held-out: ${validation.worstVertical.label} " +
                "(${signedLines(validation.worstVertical.dyPx)} vertical, " +
                "${validation.worstVertical.errorPx.toInt()} px total)")
        }
        appendLine()
        appendLine(String.format(Locale.US, "Drift: dx %+.0f px · dy %+.1f lines · total %.1f lines%s",
            driftDxPx, driftDyPx / lineHeightPx, driftDeltaPx / lineHeightPx,
            if (driftFlagged()) " — warning" else ""))
        appendLine("Coverage: ${fit.points.size}/16 fit · ${validation?.points?.size ?: 0}/5 held-out · ${excluded.size} excluded")
        appendLine()
        appendLine(
            if (provisional) {
                "Meets the provisional spatial reference: every measured target is within 1.2 lines."
            } else {
                "Does not meet the provisional spatial reference. Review the individual target(s) above."
            },
        )
        append("This is a spatial diagnostic, not proof of reading-line compatibility; direct reading validation is still required.")
    }

    private fun metricSummary(summary: ReadingSpatialMetrics.Summary): String = String.format(
        Locale.US,
        "Vertical median %.1f · P95 %.1f · max %.1f lines | within 0.5/1.0/1.2: %d/%d · %d/%d · %d/%d | 2-D median/P95/max: %.0f/%.0f/%.0f px",
        summary.medianVerticalPx / lineHeightPx,
        summary.p95VerticalPx / lineHeightPx,
        summary.maxVerticalPx / lineHeightPx,
        summary.withinHalfLine, summary.points.size,
        summary.withinOneLine, summary.points.size,
        summary.withinReference, summary.points.size,
        summary.medianErrorPx, summary.p95ErrorPx, summary.maxErrorPx,
    )

    private fun pointDetails(summary: ReadingSpatialMetrics.Summary): String =
        summary.points.sortedBy { it.id }.joinToString("\n") {
            String.format(
                Locale.US,
                "P%d %-13s dx %+.0f px · dy %+.1f lines · total %.0f px",
                it.id + 1, it.label, it.dxPx, it.dyPx / lineHeightPx, it.errorPx,
            )
        }

    private fun readingWorstIndices(report: CalibrationQuality.Report): List<Int> =
        report.dyPx.indices.sortedByDescending { kotlin.math.abs(report.dyPx[it]) }

    private fun gridAxisSummary(summary: ReadingSpatialMetrics.Summary, rows: Boolean): String =
        (0..3).joinToString(" / ") { axis ->
            val values = summary.points
                .filter { if (rows) it.id / 4 == axis else it.id % 4 == axis }
                .map { it.verticalLines }
                .sorted()
            if (values.isEmpty()) {
                "—"
            } else {
                val n = values.size
                val median = if (n % 2 == 1) values[n / 2] else (values[n / 2 - 1] + values[n / 2]) / 2f
                String.format(Locale.US, "%.1f", median)
            }
        }

    private fun signedLines(dyPx: Float): String =
        String.format(Locale.US, "%+.1f lines", dyPx / lineHeightPx)

    private fun gridLabel(index: Int): String =
        "row ${index / 4 + 1}, col ${index % 4 + 1}"

    private fun validationLabel(index: Int): String = when (index) {
        0 -> "center"
        1 -> "top-left"
        2 -> "top-right"
        3 -> "bottom-left"
        4 -> "bottom-right"
        else -> "held-out ${index + 1}"
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        collector.cancel()
        rawGazeSource?.stop()
        tone?.release()
        if (!logFinished) sessionLog?.finish("aborted")
    }

    companion object {
        private const val TAG = "GazeCalib"

        // Inter-point pacing owned by this host; per-point capture timings live
        // in CalibrationPointCollector.
        private const val CONFIRM_MS = 250L
        private const val RETRY_PAUSE_MS = 500L

        private const val MAX_ATTEMPTS = 2
        private const val MIN_FIT_POINTS = 12
        private const val WORST_REDO_COUNT = 3
        private const val MARGIN_FRAC = 0.1f
        private const val FIXED_ORDER_SEED = 0L

        private const val DRIFT_FLAG_LINE_FRACTION = 1.0f
        private const val ARTICLE_LINE_SPACING_MULT = 1.6f

        private val GREEN_COLOR = Color.parseColor("#2E7D32")
        private val AMBER_COLOR = Color.parseColor("#B26A00")
        private val RED_COLOR = Color.parseColor("#C62828")

        // Held-out validation positions: center + quadrant midpoints, none of
        // which coincide with the 4x4 calibration grid fractions.
        private val VALIDATION_FRACTIONS = listOf(
            0.5f to 0.5f, 0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f,
        )

        private const val TONE_VOLUME = 60
        private const val CAMERA_PERMISSION_REQUEST = 4104
    }
}
