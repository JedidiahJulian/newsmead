package com.newsmead.activities

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewTreeObserver
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.doOnPreDraw
import com.newsmead.R
import com.newsmead.data.StudyConfig
import com.newsmead.databinding.ActivityScanpathReplayBinding
import com.newsmead.gaze.aid.*
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.scanpath.*
import com.newsmead.gaze.scanpath.replay.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import kotlin.math.ceil

data class ScanpathReplayResult(val scenarioId: String, val termination: String,
    val samples: Int, val patterns: Set<String>, val chosenAids: Set<String>, val drawnAids: Set<String>,
    val expectedPatterns: Set<String>, val enabledExpectedAids: Set<String>, val trace: File?, val traceFailure: String?)

/** Explicit-start scripted observations through the same article engine, policy and drawing views. */
class ScanpathReplayActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScanpathReplayBinding
    private lateinit var overlay: ReadingAidOverlayView
    private lateinit var cursor: ReplayCursor
    private val handler = Handler(Looper.getMainLooper())
    private var runtime: ArticleReadingAidRuntime? = null
    private var session: ArticleScanpathSession? = null
    private var log: ScanpathReplayLog? = null
    private var readerPreferences = ReaderAidPreferences()
    private var points = emptyList<ReplayPoint>()
    private var scenario = ScanpathReplayScenarios.scenarios.first()
    private var plannedFrame: ScanpathGeometry? = null
    private var pendingStop: String? = null
    private var originElapsedMs = 0.0
    private var ordinal = 0
    private var token = 0
    private var preparing = false
    private var running = false
    private val patternCounts = linkedMapOf<String, Int>()
    private val chosenAids = linkedSetOf<String>()
    private val drawnAids = linkedSetOf<String>()
    private var lastChoice = ""
    private var latestReason = ""
    var lastResult: ScanpathReplayResult? = null
        private set
    val isReplaying get() = running || preparing
    private val geometryListener = ViewTreeObserver.OnPreDrawListener {
        session?.checkGeometry()
        runtime?.tick()
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanpathReplayBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.replayBody.text = REPLAY_TEXT
        binding.replayBody.textLocale = Locale.ENGLISH
        binding.replayBody.setTextSize(android.util.TypedValue.COMPLEX_UNIT_DIP, StudyConfig.ARTICLE_FONT_SIZE_DP)
        overlay = ReadingAidOverlayView(this)
        cursor = ReplayCursor(this)
        binding.replayViewport.addView(overlay, android.widget.FrameLayout.LayoutParams(-1, -1))
        binding.replayViewport.addView(cursor, android.widget.FrameLayout.LayoutParams(-1, -1))
        binding.replayScenario.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item,
            ScanpathReplayScenarios.scenarios.map { it.label })
        binding.replayScenario.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (isReplaying) stopReplay("scenario_changed")
                scenario = ScanpathReplayScenarios.scenarios[position]
                if (!isReplaying) binding.replayStatus.text = scenario.description
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        binding.replayBack.setOnClickListener { finish() }
        binding.replayRun.setOnClickListener { startReplay() }
        binding.replayStop.setOnClickListener { stopReplay("reader_stop") }
        binding.replayControls.setOnClickListener { showControls() }
        binding.replaySave.setOnClickListener { showTrace() }
        binding.replayStop.isEnabled = false
        binding.replaySave.isEnabled = false
    }

    fun setReaderPreferences(value: ReaderAidPreferences) {
        stopReplay("reader_controls_changed")
        readerPreferences = value
    }

    fun startReplay() {
        stopReplay("replaced")
        val startToken = ++token
        preparing = true
        lastResult = null
        binding.replayStatus.text = getString(R.string.scanpath_replay_preparing)
        binding.replayStop.isEnabled = true
        binding.replayWordDock.visibility = if (readerPreferences.enabled && readerPreferences.wordMagnifier) View.VISIBLE else View.GONE
        binding.root.doOnPreDraw {
            if (startToken != token || !preparing || isFinishing) return@doOnPreDraw
            try { prepare(startToken) } catch (e: Exception) {
                stopReplay("setup_failed")
                binding.replayStatus.text = e.message ?: getString(R.string.scanpath_replay_failed)
            }
        }
    }

    private fun prepare(startToken: Int) {
        val frame = checkNotNull(ScanpathTextGeometryReader().read(binding.replayBody)) { "Text layout is unavailable." }
        val geometry = frame.forEngine("planning")
        val mapper = ScanpathWordMapper(binding.replayBody.text.toString(), binding.replayBody)
        val layout = binding.replayBody.layout
        val words = mapper.ranges.mapNotNull { word ->
            val line = layout.getLineForOffset(word.startUtf16)
            if (layout.getLineForOffset(word.endUtf16 - 1) != line) return@mapNotNull null
            val row = frame.lines[line]
            val left = frame.originX + layout.getPrimaryHorizontal(word.startUtf16)
            val last = layout.getPrimaryHorizontal(word.endUtf16 - 1).toDouble()
            // Stay on the last letter's nearest-offset side; an insertion point after it may be whitespace.
            val lastAdvance = binding.replayBody.paint.measureText(binding.replayBody.text.subSequence(word.endUtf16 - 1, word.endUtf16).toString())
            val right = minOf(frame.originX + last + lastAdvance * 0.4, frame.originX + row.right)
            val rect = ScanpathRect(left, frame.originY + row.top + 1, right, frame.originY + row.bottom - 1)
            if (rect.right <= rect.left || rect.top < frame.visible.top || rect.bottom > frame.visible.bottom ||
                rect.left < frame.visible.left || rect.right > frame.visible.right) null
            else ReplayWord(word, line, rect)
        }
        val script = ScanpathReplayScenarios.build(scenario.id, geometry, words)
        // Continue actual raw observations for one second so the final aid can be inspected.
        val last = script.last()
        points = script + if (last.eligible) (1..20).map { last.copy(offsetMs = last.offsetMs + it * 50) } else emptyList()
        ordinal = 0
        patternCounts.clear(); chosenAids.clear(); drawnAids.clear()
        lastChoice = ""; latestReason = ""; pendingStop = null
        plannedFrame = geometry
        originElapsedMs = ceil(now())
        runtime = ArticleReadingAidRuntime(binding.replayBody, binding.replayWordDock, overlay,
            ReadingAidPolicyConfig.experimentalPrototype("REPLAY_AIDS_V1"), readerPreferences,
            ReadingAidInputSource.SYNTHETIC_REPLAY,
            onDecision = { d ->
                log?.decision(d)
                if (d.status == ReadingAidDecisionStatus.CHOSEN && d.action != null) {
                    chosenAids.add(d.action.aidType.name)
                    lastChoice = "${d.action.aidType.name} ${(d.action.strength * 100).toInt()}%"
                }
                latestReason = d.reason
            }, onRenderReceipt = { a, drawn, reason, at ->
                log?.render(a, drawn, reason, at)
                if (drawn) drawnAids.add(a.aidType.name)
            })
        log = ScanpathReplayLog(this, scenario, binding.replayBody.text.toString(),
            runtime!!.configurationSnapshot(), points, originElapsedMs)
        session = ArticleScanpathSession(binding.replayBody, runtime!!.scanpathConfig,
            onGeometry = { generation, value ->
                log?.geometry(generation, value)
                runtime?.geometry(generation, value)
                if (running && (value == null || value.copy(snapshotId = "planning") != plannedFrame)) pendingStop = "geometry_changed"
            }, onInput = { generation, sample ->
                log?.sample(generation, ordinal, sample)
                runtime?.input(generation, sample)
            }, onFixation = { generation, fixation ->
                log?.fixation(generation, fixation); runtime?.fixation(generation, fixation)
            }, onEvent = { generation, event ->
                log?.event(generation, event)
                patternCounts[event.patternId] = (patternCounts[event.patternId] ?: 0) + 1
                runtime?.event(generation, event)
            }, onCandidate = { generation, candidate ->
                log?.candidate(generation, candidate); runtime?.candidate(generation, candidate)
            })
        binding.root.viewTreeObserver.addOnPreDrawListener(geometryListener)
        session!!.checkGeometry()
        preparing = false
        running = true
        binding.root.keepScreenOn = true
        postNext(startToken)
    }

    private fun postNext(startToken: Int) {
        val point = points.getOrNull(ordinal)
        if (point == null) { stopReplay("completed"); return }
        val capture = originElapsedMs + point.offsetMs
        val delay = ceil((capture - now()).coerceAtLeast(0.0)).toLong()
        handler.postDelayed({
            if (!running || startToken != token) return@postDelayed
            session?.checkGeometry()
            val reason = pendingStop ?: log?.failure?.let { "trace_failure:$it" }
            if (reason != null) { stopReplay(reason); return@postDelayed }
            val deliveryNs = SystemClock.elapsedRealtimeNanos()
            val lag = deliveryNs / 1e6 - capture
            if (lag !in 0.0..100.0) {
                log?.append("scheduler_interruption", JSONObject().put("lag_ms", lag).put("plan_ordinal", ordinal))
                stopReplay("scheduler_late"); return@postDelayed
            }
            cursor.point(point.x, point.y)
            session?.record(MgazeNetGazeProvider.Observation(capture, deliveryNs, ordinal.toLong(),
                if (point.eligible) "coordinate" else "synthetic_loss", point.x?.toFloat(), point.y?.toFloat(),
                point.x?.toFloat(), point.y?.toFloat()))
            ordinal++
            binding.replayStatus.text = "${scenario.label}\nSamples $ordinal/${points.size} · ${patternCounts.keys.joinToString(" ")}\n" +
                "Aid: ${lastChoice.ifBlank { "none" }}\n${latestReason.ifBlank { "collecting candidate evidence" }}"
            postNext(startToken)
        }, delay)
    }

    fun stopReplay(reason: String) {
        val hadRun = runtime != null || preparing
        token++
        preparing = false; running = false
        handler.removeCallbacksAndMessages(null)
        if (::binding.isInitialized) {
            binding.root.keepScreenOn = false
            binding.root.viewTreeObserver.removeOnPreDrawListener(geometryListener)
        }
        runtime?.suspend(reason)
        session?.close(reason)
        runtime?.close(reason)
        session = null; runtime = null
        if (::cursor.isInitialized) cursor.point(null, null)
        val recorder = log
        if (recorder != null) {
            val enabledExpected = scenario.expectedAidTypes.filterTo(linkedSetOf()) { type ->
                readerPreferences.enabled && readerPreferences.maxStrength > 0 &&
                    (readerPreferences.adaptiveStrength || readerPreferences.manualStrength > 0) && when (type) {
                    "CURRENT_LINE" -> readerPreferences.lineGuide
                    "NEXT_LINE_START" -> readerPreferences.lineStartCue
                    "WORD_MAGNIFIER" -> readerPreferences.wordMagnifier
                    "FOCUS_REGION" -> readerPreferences.focusRegion
                    else -> false
                }
            }
            recorder.close(reason, JSONObject().put("samples_sent", ordinal).put("samples_planned", points.size)
                .put("observed_patterns", JSONObject().apply { patternCounts.forEach { (id, count) -> put(id, count) } })
                .put("chosen_aids", JSONArray(chosenAids.toList()))
                .put("drawn_proxy_aids", JSONArray(drawnAids.toList())).put("enabled_expected_aids", JSONArray(enabledExpected.toList()))
                .put("software_expectations_met", reason == "completed" && patternCounts.keys.containsAll(scenario.expectedPatternIds) &&
                    chosenAids.containsAll(enabledExpected) && drawnAids.containsAll(enabledExpected) && recorder.failure == null))
            lastResult = ScanpathReplayResult(scenario.id, reason, ordinal, patternCounts.keys.toSet(), chosenAids.toSet(),
                drawnAids.toSet(), scenario.expectedPatternIds, enabledExpected, recorder.file, recorder.failure)
            binding.replayStatus.text = "${scenario.label}: $reason\nPatterns ${patternCounts.keys.joinToString(" ")}\n" +
                "Chosen ${chosenAids.joinToString(" ")}\nDraw receipts ${drawnAids.joinToString(" ")}"
        } else if (hadRun) binding.replayStatus.text = getString(R.string.scanpath_replay_stopped)
        log = null
        if (::binding.isInitialized) {
            binding.replayStop.isEnabled = false
            binding.replaySave.isEnabled = lastResult?.trace != null
        }
    }

    private fun showTrace() {
        if (isReplaying) stopReplay("trace_requested")
        val file = lastResult?.trace ?: return
        AlertDialog.Builder(this).setTitle(R.string.scanpath_replay_trace)
            .setMessage("Private synthetic replay trace:\n${file.absolutePath}\n\n" +
                "Trace status: ${lastResult?.traceFailure ?: "written"}")
            .setPositiveButton(R.string.scanpath_replay_copy) { _, _ ->
                (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Replay trace", file.absolutePath))
            }.setNegativeButton(android.R.string.ok, null).show()
    }

    private fun showControls() {
        stopReplay("reader_controls")
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 8, 24, 8) }
        fun check(label: Int, selected: Boolean) = CheckBox(this).apply { setText(label); isChecked = selected; panel.addView(this) }
        val p = readerPreferences
        val enabled = check(R.string.reading_aids_enabled, p.enabled)
        val line = check(R.string.reading_aids_line, p.lineGuide)
        val start = check(R.string.reading_aids_start, p.lineStartCue)
        val word = check(R.string.reading_aids_word, p.wordMagnifier)
        val focus = check(R.string.reading_aids_focus, p.focusRegion)
        val adaptive = check(R.string.reading_aids_adaptive, p.adaptiveStrength)
        val label = TextView(this).apply { panel.addView(this) }
        val slider = SeekBar(this).apply {
            max = 100; progress = (p.maxStrength * 100).toInt(); panel.addView(this)
            label.text = getString(R.string.reading_aids_strength, progress)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { label.text = getString(R.string.reading_aids_strength, progress) }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        AlertDialog.Builder(this).setTitle(R.string.scanpath_replay_controls).setView(panel)
            .setPositiveButton(R.string.reading_aids_apply) { _, _ ->
                setReaderPreferences(ReaderAidPreferences(enabled.isChecked, line.isChecked, start.isChecked, word.isChecked,
                    focus.isChecked, adaptive.isChecked, slider.progress / 100.0, slider.progress / 100.0))
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    override fun onPause() { stopReplay("activity_paused"); super.onPause() }
    override fun onStop() { stopReplay("activity_stopped"); super.onStop() }
    override fun onDestroy() { stopReplay("activity_destroyed"); super.onDestroy() }

    private class ReplayCursor(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val origin = IntArray(2)
        private var x: Double? = null
        private var y: Double? = null
        init { isClickable = false; isFocusable = false }
        fun point(px: Double?, py: Double?) { x = px; y = py; invalidate() }
        override fun onDraw(canvas: Canvas) {
            val px = x ?: return
            val py = y ?: return
            getLocationOnScreen(origin)
            paint.color = Color.rgb(0, 110, 180); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2 * resources.displayMetrics.density
            canvas.drawCircle((px - origin[0]).toFloat(), (py - origin[1]).toFloat(), 5 * resources.displayMetrics.density, paint)
        }
    }

    companion object {
        private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6
        // Synthetic content written for software inspection, not a comprehension stimulus or published news.
        const val REPLAY_TEXT = "Extraordinarily resourceful neighbours organised an internationalisation workshop in the community library. " +
            "Readers explored reliable navigation between paragraphs and discussed straightforward examples together. " +
            "The coordinator prepared comprehensive information about reading activities and welcomed questions from everyone.\n\n" +
            "Internationalisation encourages participants to consider familiar words alongside extraordinarily detailed descriptions. " +
            "This practice paragraph provides visible words for forward movement, backward returns and transitions to another line. " +
            "Every demonstration follows the rendered layout and preserves earlier sentences for inspection.\n\n" +
            "Extraordinarily clear communication supports the organisation of neighbourhood activities. " +
            "Participants shared comprehensive explanations and compared different approaches at a comfortable pace. " +
            "The library prepared additional reading material so that the page can scroll during software checks.\n\n" +
            "Internationalisation and straightforward communication appear again in this synthetic paragraph. " +
            "Readers may revisit any sentence. The demonstration shows software observations without assigning a diagnosis. " +
            "Extraordinarily resourceful neighbours continue their discussion as the final paragraph reaches the lower page."
    }
}
