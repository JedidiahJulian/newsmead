package com.newsmead.activities

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.button.MaterialButton
import com.newsmead.BuildConfig
import com.newsmead.R
import com.newsmead.data.StudyConfig
import com.newsmead.gaze.*
import com.newsmead.gaze.experiment.*
import com.newsmead.gaze.mgazenet.*
import java.text.SimpleDateFormat
import java.util.*

/** Explicit-start experiment; it never corrects gaze or changes a saved calibration. */
class GazeExperimentRunActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var targetView: CalibrationView
    private lateinit var dot: GazeDotView
    private lateinit var status: TextView
    private lateinit var start: MaterialButton
    private lateinit var stopButton: MaterialButton
    private lateinit var reading: ScrollView
    private lateinit var body: TextView
    private lateinit var configuration: GazeExperimentConfiguration
    private lateinit var sessionId: String
    private lateinit var mode: String
    private val handler = Handler(Looper.getMainLooper())
    private var provider: MgazeNetGazeProvider? = null
    private var measurements: ExperimentMeasurements? = null
    private var frame: GazeCoordinateFrame? = null
    private var running = false
    private var closing = false
    private var attempted = false
    private var started = 0.0
    private var requested = 0.0
    private var startUtc = ""
    private var fingerprint = ""
    private var collectionId = ""
    private var targetIndex = -1
    private var permissionPending = false
    private var measurementPending = false
    private var preReadingDeliveries = 0
    private var geometry = emptyMap<String, Double>()
    private var physicalScale: PhysicalDisplayScale? = null
    private var confirmation: Map<String, String>? = null
    private var setupCheck: CheckBox? = null
    private val metadata = linkedMapOf<String, String>()
    private val scrollEvents = org.json.JSONArray()
    private var omittedScrollEvents = 0
    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val requested = permissionPending; permissionPending = false
        if (requested && granted && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) begin()
        else if (requested) status.text = "Camera permission is needed. Press Start when ready."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val config = GazeExperimentConfiguration.fromId(intent.getStringExtra(ExperimentFlow.EXTRA_CONFIGURATION) ?: "")
        val session = intent.getStringExtra(ExperimentFlow.EXTRA_SESSION) ?: ""
        val requestedMode = intent.getStringExtra(ExperimentFlow.EXTRA_MODE)
        if (!BuildConfig.GAZE_EXPERIMENT_ENABLED || config == null || !ExperimentFlow.validSession(session) ||
            requestedMode !in listOf(ExperimentFlow.VALIDATION, ExperimentFlow.READING)) { finish(); return }
        configuration = config; sessionId = session; mode = requestedMode!!
        confirmation = ACConfirmationLaunch.parse(intent)
        if (intent.hasExtra(ACConfirmationLaunch.EXTRA) && confirmation == null) { finish(); return }
        root = FrameLayout(this).apply { setBackgroundColor(ContextCompat.getColor(context, R.color.light)) }
        targetView = CalibrationView(this)
        root.addView(targetView, FrameLayout.LayoutParams(-1, -1))
        body = TextView(this).apply {
            text = PASSAGE
            setTextColor(ContextCompat.getColor(context, R.color.light_text))
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
            setLineSpacing(dp(4).toFloat(), 1f)
            setPadding(dp(20), dp(12), dp(20), dp(20))
        }
        reading = ScrollView(this).apply { addView(body); visibility = View.GONE }
        root.addView(reading, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(88); bottomMargin = dp(76) })
        reading.setOnScrollChangeListener { _, _, y, _, _ ->
            if (running && measurements != null) {
                if (scrollEvents.length() < 100) scrollEvents.put(org.json.JSONArray(listOf(MgazeNetCameraSource.now(), y)))
                else omittedScrollEvents++
            }
        }
        dot = GazeDotView(this).apply { visibility = View.GONE; isClickable = false }
        root.addView(dot, FrameLayout.LayoutParams(-1, -1))
        status = TextView(this).apply {
            textSize = 16f; gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.light_text))
            setPadding(dp(16), dp(12), dp(16), 0)
            text = if (mode == ExperimentFlow.VALIDATION)
                "${configuration.label}\nLook at each target. Nine positions, about one minute."
            else "${configuration.label}\nRead naturally for 60 seconds. Scroll by hand. The dot shows unsmoothed gaze."
        }
        root.addView(status, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        start = MaterialButton(this).apply { text = if (mode == ExperimentFlow.VALIDATION) "Start validation" else "Start reading test" }
        root.addView(start, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        confirmation?.let { fields ->
            status.text = "Round ${fields["confirmation_round"]} of 2 - ${config.label}\n" + status.text
            setupCheck = CheckBox(this).apply {
                text = "My phone/head position, lighting and glasses match the recorded setup."
                textSize = 16f; setPadding(dp(20), 0, dp(20), 0)
                setOnCheckedChangeListener { _, checked -> start.isEnabled = checked }
            }.also { check -> root.addView(check, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER).apply { bottomMargin = dp(150) }) }
            start.isEnabled = false
        }
        stopButton = MaterialButton(this).apply { text = "Back"; setOnClickListener {
            if (running) end("aborted", "Stopped by user") else if (!closing) finish()
        } }
        root.addView(stopButton, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(12) })
        setContentView(root)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).hide(WindowInsetsCompat.Type.systemBars())
        start.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) begin()
            else { permissionPending = true; permission.launch(Manifest.permission.CAMERA) }
        }
    }

    private fun begin() {
        if (running || closing || attempted || root.width == 0) return
        if (confirmation != null && setupCheck?.isChecked != true) return
        val store = MgazeNetCalibrationStore(this, configuration)
        val issue = store.compatibilityIssue(root)
        if (issue != null) { status.text = "Calibrate this configuration first."; return }
        physicalScale = ExperimentDisplayScale.snapshot(root)
        val scale = physicalScale ?: run { status.text = "Physical display dimensions are unavailable. Test not started."; return }
        fingerprint = store.fingerprint() ?: return
        collectionId = store.calibrationCollectionId() ?: return
        frame = root.gazeCoordinateFrame()
        val size = root.physicalDisplaySize()
        geometry = linkedMapOf("screenWidthPx" to size.x.toDouble(), "screenHeightPx" to size.y.toDouble(),
            "rotation" to root.display.rotation.toDouble(),
            "viewportX" to frame!!.originX.toDouble(), "viewportY" to frame!!.originY.toDouble(),
            "viewportWidth" to frame!!.width.toDouble(), "viewportHeight" to frame!!.height.toDouble(),
            "readingTextSizePx" to body.textSize.toDouble(), "readingLineHeightPx" to body.lineHeight.toDouble(),
            "pixelsPerCmX" to scale.pixelsPerCmX, "pixelsPerCmY" to scale.pixelsPerCmY)
        metadata["physical_scale_source"] = ExperimentDisplayScale.SOURCE
        confirmation?.let {
            metadata.putAll(it)
            metadata["confirmation_setup_attested_utc"] = utcNow()
            metadata["confirmation_boot_count"] = runCatching {
                Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT).takeIf { value -> value >= 0 }?.toString() ?: "unavailable"
            }.getOrDefault("unavailable")
        }
        metadata["configuration_order"] = intent.getStringExtra("configurationOrder") ?: "manual"
        metadata["age_scope"] = "all observed deliveries with valid camera clocks, including invalid gaze"
        startUtc = utcNow()
        running = true; attempted = true; requested = MgazeNetCameraSource.now()
        root.keepScreenOn = true; start.visibility = View.GONE; stopButton.text = "Stop test"
        setupCheck?.visibility = View.GONE
        status.text = "Preparing ${configuration.label}"
        val p = MgazeNetGazeProvider(this, root, configuration = configuration)
        p.onSourceMetadata = { values -> metadata.putAll(values.mapValues { it.value.toString() }) }
        p.onFailure = { if (running) end("failure", it) }
        p.onObservation = { o ->
            if (running && frame != root.gazeCoordinateFrame()) end("failure", "Screen geometry changed")
            if (running && measurements == null && !measurementPending && o.reason == "coordinate" && o.captureMs != null) {
                measurementPending = true
                metadata["initialization_ms"] = (o.outputMs - requested).toString()
                if (mode == ExperimentFlow.VALIDATION) {
                    started = o.captureMs; measurements = accumulator(started); targetIndex = 0; showTarget()
                } else revealReading()
            }
            val beforeReading = mode == ExperimentFlow.READING && o.reason != "stopped" &&
                (measurements == null || (o.captureMs != null && o.captureMs < started))
            if (running && beforeReading) preReadingDeliveries++
            if (!beforeReading && (running || (closing && o.reason == "stopped"))) {
                measurements?.observe(ExperimentObservation(o.captureMs, o.outputMs, o.reason,
                    o.rawX?.toDouble(), o.rawY?.toDouble(), o.x?.toDouble(), o.y?.toDouble(),
                    o.arrivals, o.busyDrops, o.timeoutWatchdog))
            }
            if (mode == ExperimentFlow.READING && o.reason != "coordinate") dot.visibility = View.INVISIBLE
        }
        p.setOnGaze { x, y -> if (running && measurements != null && mode == ExperimentFlow.READING) {
            dot.visibility = View.VISIBLE; dot.setGazeScreen(x, y)
        } }
        provider = p
        p.start(this)
        handler.postDelayed({ if (running && measurements == null) end("failure", "No reading-ready gaze within 20 seconds") }, 20_000)
    }

    private fun revealReading() {
        // Draw acknowledgement is a software display proxy, not photon timing.
        val listener = object : ViewTreeObserver.OnDrawListener {
            private var acknowledged = false
            override fun onDraw() {
                if (acknowledged) return
                acknowledged = true
                if (running) {
                    started = MgazeNetCameraSource.now()
                    measurements = accumulator(started)
                    metadata["reading_onset_clock"] = "view_tree_draw_cycle_elapsed_realtime_ms_proxy"
                    handler.postDelayed({ if (running) end("completed", null) }, 60_000)
                }
                body.post { if (body.viewTreeObserver.isAlive) body.viewTreeObserver.removeOnDrawListener(this) }
            }
        }
        body.viewTreeObserver.addOnDrawListener(listener)
        reading.visibility = View.VISIBLE
        status.text = "${configuration.label}\nRead naturally. The test ends after 60 seconds."
    }

    private fun accumulator(startMs: Double) = ExperimentMeasurements(startMs, mode,
        expectedHeldOutTargetIds = if (mode == ExperimentFlow.VALIDATION) (0..8).filter { it != 4 }.map { it.toString() }.toSet() else emptySet())

    private fun showTarget() {
        if (!running) return
        if (targetIndex >= 9) { end("completed", null); return }
        val point = MgazeNetAccuracyProtocol.target(frame!!, targetIndex)
        val local = frame!!.toLocal(point.x, point.y)
        val index = targetIndex
        status.text = "${configuration.label}\nTarget ${index + 1} of 9"
        targetView.onNextTargetDraw {
            if (running) {
                val onset = MgazeNetCameraSource.now()
                measurements!!.registerTarget(TargetWindow(index.toString(), point.x.toDouble(), point.y.toDouble(),
                    onset + 3000, onset + 5500, onset + 5750, index != 4))
                handler.postDelayed({ if (running) { targetIndex++; showTarget() } }, 5750)
            }
        }
        targetView.showTarget(local.x, local.y, 1800)
    }

    private fun end(outcome: String, reason: String?) {
        if (!running || closing) return
        running = false; closing = true
        val endMs = MgazeNetCameraSource.now()
        val endUtc = utcNow()
        handler.removeCallbacksAndMessages(null)
        root.keepScreenOn = false; targetView.hideTarget(); dot.visibility = View.GONE
        reading.visibility = View.GONE; stopButton.isEnabled = false
        status.text = "Saving result"
        val current = provider; provider = null
        val complete = {
            val measure = measurements ?: accumulator(requested)
            val result = measure.finish(endMs)
            metadata["reading_passage"] = if (mode == ExperimentFlow.READING) "newsmead_gf2_reading_v1" else "none"
            metadata["dot_feedback"] = if (mode == ExperimentFlow.READING) "visible_raw" else "hidden"
            metadata["scroll_events_elapsed_ms_y_px"] = scrollEvents.toString()
            metadata["scroll_events_omitted"] = omittedScrollEvents.toString()
            metadata["pre_reading_deliveries_excluded"] = preReadingDeliveries.toString()
            Thread {
                val centimeters = physicalScale?.let { runCatching { ExperimentCentimeters.fromResult(result, it) }.getOrNull() }
                val saved = runCatching { ExperimentReportStore(applicationContext).save(RunMetadata(
                    sessionId, collectionId, configuration.id, configuration.modelSha, fingerprint,
                    ExperimentFlow.PROTOCOL, ExperimentFlow.FILTER, startUtc, outcome, geometry, metadata.toMap(), reason, endUtc), result) }
                runOnUiThread {
                    closing = false
                    if (!isDestroyed) {
                        stopButton.isEnabled = true; stopButton.text = "Back to comparison"
                        status.text = saved.fold({
                            setResult(RESULT_OK)
                            val s = result.summary
                            val targets = if (mode == ExperimentFlow.VALIDATION)
                                "Held-out targets with samples: ${8 - s.missingHeldOutTargetIds.size}/8\n" else ""
                            "${configuration.label}\n${if (outcome == "completed") "Test finished" else "Test $outcome"}\n" +
                                (reason?.let { "$it\n" } ?: "") + targets +
                                "Fresh gaze: ${format(s.freshHz)} Hz\n95th-percentile sample age: ${format(s.p95AgeMs)} ms\n" +
                                "Usable / observed outputs: ${format(s.coverage?.times(100))}%\n" +
                                (centimeters?.optJSONObject("summary")?.optJSONObject("rawHeldOut")?.let {
                                    "Median target error: ${if (it.isNull("medianErrorCm")) "unavailable" else String.format(Locale.US, "%.3f", it.getDouble("medianErrorCm"))} cm\n"
                                } ?: "") +
                                "Result saved. Return to compare configurations."
                        }, { "Result could not be saved. Return and repeat this test." })
                    }
                }
            }.start()
        }
        if (current == null) complete() else current.stopAndThen(complete)
    }
    override fun onStop() {
        permissionPending = false
        if (running) end("aborted", "Activity left the foreground")
        super.onStop()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun utcNow() = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date())
    private fun format(value: Double?) = value?.let { String.format(Locale.US, "%.1f", it) } ?: "unavailable"
    companion object {
        private const val PASSAGE = "A quieter place to read\n\n" +
            "On a warm afternoon, a small community library opened its windows to the garden. " +
            "Readers chose their seats, set down their bags, and settled into the books they had brought. " +
            "Some preferred a place near the window. Others sat at a long table beneath a lamp.\n\n" +
            "Outside, volunteers were planting herbs in a row of reused pots. They wrote the names on small wooden labels " +
            "and watered the soil gently. A visitor asked how much sunlight the plants needed, and one volunteer pointed " +
            "toward the shaded end of the garden.\n\n" +
            "The library offered more than books. A notice board listed walking groups, local events, and classes for people " +
            "who wanted to learn a new skill. Before leaving, a reader copied the date of a workshop into a notebook.\n\n" +
            "By late afternoon the room was quiet again. The volunteers put away their tools, checked the windows, and left " +
            "a fresh stack of newspapers beside the door. Tomorrow, another group of readers would arrive. " +
            "If you reach the end before the test finishes, read the passage again at your normal pace."
    }
}
