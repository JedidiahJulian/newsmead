package com.newsmead.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.newsmead.BuildConfig
import com.newsmead.gaze.experiment.*
import com.newsmead.gaze.mgazenet.*
import org.json.JSONObject
import java.text.DateFormat
import java.util.*

/** User-controlled experiment navigation. Opening/selecting never starts a camera. */
class GazeExperimentSetupActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("gaze-experiment-navigation-v1", MODE_PRIVATE) }
    private lateinit var content: LinearLayout
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var calibrate: MaterialButton
    private lateinit var validate: MaterialButton
    private lateinit var reading: MaterialButton
    private lateinit var next: MaterialButton
    private lateinit var radios: RadioGroup
    private lateinit var compare: MaterialButton
    private lateinit var compareAC: MaterialButton
    private lateinit var historySelector: Spinner
    private lateinit var reportWarning: TextView
    private var historyKeys = emptyList<String>()
    private var historySession = ""
    private var historyScope = ExperimentResultHistory.ALL
    private var updatingHistory = false
    private var showAttempts = false
    private var exportingSession: String? = null
    private var physicalScale: PhysicalDisplayScale? = null
    private var selected = GazeExperimentConfiguration.CURRENT_SVR
    private var reports = emptyList<JSONObject>()
    private val fingerprints = mutableMapOf<String, String?>()
    private val collections = mutableMapOf<String, String?>()
    private val compatible = mutableMapOf<String, Boolean>()
    private val savedTimes = mutableMapOf<String, Long?>()
    private var refreshing = false
    private var refreshGeneration = 0
    private var launchingConfiguration: String? = null
    private var launchingCalibration = false
    private var confirmationPlan: ACConfirmationPlan? = null
    private var confirmationError: String? = null
    private var launchingConfirmationStep: String? = null
    private var pendingConfirmationBinding: Triple<String, String, String>? = null
    private val launch = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (launchingCalibration && result.resultCode == RESULT_OK) {
            GazeExperimentConfiguration.fromId(launchingConfiguration ?: "")?.let { config ->
                val collection = MgazeNetCalibrationStore(this, config).calibrationCollectionId()
                prefs.edit().putString("collection_${ExperimentFlow.collectionGroup(config.id)}", collection).apply()
                val fingerprint = MgazeNetCalibrationStore(this, config).fingerprint()
                if (launchingConfirmationStep != null && collection != null && fingerprint != null)
                    pendingConfirmationBinding = Triple(launchingConfirmationStep!!, collection, fingerprint)
            }
        }
        launchingConfiguration = null; launchingCalibration = false
        launchingConfirmationStep = null
        refresh()
    }
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val id = exportingSession ?: sessionId()
            val scale = physicalScale
            val planSnapshot = confirmationPlan?.takeIf { it.sessionId == id }?.toJson()
            exportingSession = null
            Thread {
                val saved = runCatching {
                    val document = ExperimentReportStore(applicationContext).exportSession(id, scale)
                    planSnapshot?.let { document.put("confirmationPlan", it) }
                    if (id == sessionId()) document.put("configurationOrder", prefs.getString("order", "A,B,C"))
                    contentResolver.openOutputStream(uri)?.use { it.write(document.toString(2).toByteArray(Charsets.UTF_8)) }
                        ?: error("No export stream")
                }
                runOnUiThread { if (!isDestroyed) Toast.makeText(this,
                    if (saved.isSuccess) "Comparison exported" else "Export failed", Toast.LENGTH_LONG).show() }
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.GAZE_EXPERIMENT_ENABLED) { finish(); return }
        if (!ExperimentFlow.validSession(prefs.getString("session", "") ?: "")) newSession(false)
        confirmationPlan = prefs.getString("acPlan", null)?.let { value -> runCatching {
            ACConfirmationPlan.fromJson(JSONObject(value)).also { require(it.sessionId == sessionId()) }
        }.onFailure { confirmationError = "Saved A/C setup could not be restored. Start a new A/C comparison." }.getOrNull() }
        selected = GazeExperimentConfiguration.fromId(prefs.getString("selected", "A") ?: "A") ?: GazeExperimentConfiguration.CURRENT_SVR
        launchingConfiguration = savedInstanceState?.getString("launchingConfiguration")
        launchingCalibration = savedInstanceState?.getBoolean("launchingCalibration") ?: false
        launchingConfirmationStep = savedInstanceState?.getString("launchingConfirmationStep")
        savedInstanceState?.getStringArrayList("pendingConfirmationBinding")?.takeIf { it.size == 3 }?.let {
            pendingConfirmationBinding = Triple(it[0], it[1], it[2])
        }
        historyScope = savedInstanceState?.getString("historyScope") ?: ExperimentResultHistory.ALL
        showAttempts = savedInstanceState?.getBoolean("showAttempts") ?: false
        exportingSession = savedInstanceState?.getString("exportingSession")
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; fitsSystemWindows = true
            setPadding(dp(20), dp(12), dp(20), dp(24))
        }
        val scroll = ScrollView(this).apply { addView(content) }
        setContentView(scroll)
        button("Back") { finish() }
        label("Eye tracker comparison", 24f)
        label("Choose a configuration, calibrate, then validate and try a short reading test.", 16f)
        radios = RadioGroup(this)
        GazeExperimentConfiguration.entries.forEach { config ->
            radios.addView(RadioButton(this).apply {
                id = View.generateViewId(); tag = config.id; text = config.label; textSize = 17f
                isChecked = selected == config
                setPadding(0, dp(8), 0, dp(8))
            })
        }
        content.addView(radios)
        radios.setOnCheckedChangeListener { group, checked ->
            selected = GazeExperimentConfiguration.fromId(group.findViewById<RadioButton>(checked).tag as String)!!
            prefs.edit().putString("selected", selected.id).apply()
            render()
        }
        status = label("Checking saved calibrations", 16f)
        calibrate = button("Calibrate selected") { openCalibration(selected) }
        validate = button("Validate selected") { openRun(selected, ExperimentFlow.VALIDATION) }
        reading = button("Reading test") { openRun(selected, ExperimentFlow.READING) }
        compareAC = button("Start A/C confirmation") { createConfirmation() }
        compare = button("Compare all three") { newSession(true); refresh() }
        next = button("Continue comparison") { continueGuided() }
        label("Saved results", 21f)
        label("Earlier runs stay available after recalibration or a new comparison. B and C share one fit; calibrating either replaces that pair.", 14f)
        historySelector = Spinner(this).apply { contentDescription = "Results comparison" }
        content.addView(historySelector)
        historySelector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (!updatingHistory && position in historyKeys.indices && historyScope != historyKeys[position]) {
                    historyScope = historyKeys[position]; renderResults()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) { }
        }
        reportWarning = label("", 14f)
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(results)
        button("Show / hide saved attempts") { showAttempts = !showAttempts; renderResults() }
        button("Export comparison results") { chooseExport() }
        button("Calibrate normal reading tracker") { startActivity(Intent(this, GazeCalibrationActivity::class.java)) }
        label("These experiment calibrations are separate from normal reading. B and C share one calibration collection. " +
            "A finished test is not an accuracy pass. Validation and reading attempts remain in the export.", 14f)
    }
    override fun onResume() { super.onResume(); if (::content.isInitialized) content.post { refresh() } }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("launchingConfiguration", launchingConfiguration)
        outState.putBoolean("launchingCalibration", launchingCalibration)
        outState.putString("launchingConfirmationStep", launchingConfirmationStep)
        pendingConfirmationBinding?.let { outState.putStringArrayList("pendingConfirmationBinding", arrayListOf(it.first, it.second, it.third)) }
        outState.putString("historyScope", historyScope)
        outState.putBoolean("showAttempts", showAttempts)
        outState.putString("exportingSession", exportingSession)
        super.onSaveInstanceState(outState)
    }
    private fun sessionId() = prefs.getString("session", "")!!
    private fun newSession(guided: Boolean) {
        confirmationPlan = null; confirmationError = null; pendingConfirmationBinding = null
        val index = prefs.getInt("nextOrder", 0)
        prefs.edit().putString("session", UUID.randomUUID().toString()).putBoolean("guided", guided)
            .putString("order", ExperimentFlow.order(index).joinToString(","))
            .putInt("nextOrder", if (guided) (index + 1) % ExperimentFlow.orders.size else index)
            .remove("collection_current").remove("collection_next").remove("acPlan").apply()
    }
    private fun refresh() {
        if (!::content.isInitialized || isDestroyed) return
        val generation = ++refreshGeneration
        refreshing = true
        physicalScale = ExperimentDisplayScale.snapshot(content)
        GazeExperimentConfiguration.entries.forEach { config ->
            val store = MgazeNetCalibrationStore(this, config)
            compatible[config.id] = store.compatibilityIssue(content) == null
            fingerprints[config.id] = store.fingerprint()
            collections[config.id] = store.calibrationCollectionId()
            savedTimes[config.id] = store.savedAt()
        }
        render()
        val id = sessionId()
        val scale = physicalScale
        Thread {
            val store = ExperimentReportStore(applicationContext)
            val loaded = runCatching { store.list(summariesOnly = true, centimeterFallback = scale) }
            runOnUiThread {
                if (!isDestroyed && generation == refreshGeneration && sessionId() == id) {
                    if (loaded.isSuccess) {
                        reports = loaded.getOrThrow()
                        bindConfirmationResult()
                    }
                    refreshing = false
                    reportWarning.text = if (loaded.isFailure) "Saved results could not be refreshed. Previously loaded results remain visible."
                        else if (store.lastListCorruptCount > 0 || store.lastListTruncated)
                            "Some saved results could not be listed (${store.lastListCorruptCount} unreadable; limited listing: ${store.lastListTruncated}). Export retains omission counts."
                        else ""
                    render()
                }
            }
        }.start()
    }
    private fun currentReports(config: GazeExperimentConfiguration) = ExperimentResultHistory.current(reports,
        sessionId(), config.id, fingerprints[config.id], collections[config.id])
    private fun completed(config: GazeExperimentConfiguration, mode: String): JSONObject? = currentReports(config).firstOrNull {
        it.getJSONObject("result").getString("mode") == mode && ExperimentResultHistory.qualifies(it)
    }
    private fun freshForGuided(config: GazeExperimentConfiguration): Boolean = compatible[config.id] == true &&
        collections[config.id] != null && collections[config.id] == prefs.getString("collection_${ExperimentFlow.collectionGroup(config.id)}", null)
    private fun render() {
        if (!::status.isInitialized) return
        val available = compatible[selected.id] == true
        val time = savedTimes[selected.id]?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }
        status.text = "${selected.label}\n" + if (available) "Calibration fitted: $time\n" +
            if (completed(selected, ExperimentFlow.VALIDATION) != null) "Validation recorded for this calibration." else "Independent validation still needed."
            else "Calibration needed."
        calibrate.isEnabled = !refreshing
        validate.isEnabled = available && !refreshing
        reading.isEnabled = !refreshing && completed(selected, ExperimentFlow.VALIDATION) != null
        compareAC.isEnabled = !refreshing
        compareAC.text = if (confirmationPlan == null) "Start A/C confirmation" else "Start new A/C confirmation"
        compare.isEnabled = !refreshing
        compare.text = if (prefs.getBoolean("guided", false)) "Start new comparison" else "Compare all three"
        next.visibility = if (prefs.getBoolean("guided", false)) View.VISIBLE else View.GONE
        next.isEnabled = !refreshing
        next.text = "Continue comparison (${prefs.getString("order", "A,B,C")!!.replace(",", " → ")})"
        val plan = confirmationPlan
        if (plan != null || confirmationError != null) {
            calibrate.isEnabled = false; validate.isEnabled = false; reading.isEnabled = false
            for (i in 0 until radios.childCount) radios.getChildAt(i).isEnabled = false
            next.visibility = View.VISIBLE
            val action = plan?.action(reports, liveFits())
            next.isEnabled = !refreshing && confirmationError == null && action?.stage != ACConfirmationPlan.Stage.COMPLETE
            if (confirmationError != null) {
                status.text = confirmationError; next.text = "A/C setup unavailable"
            } else if (action?.stage == ACConfirmationPlan.Stage.COMPLETE) {
                status.text = "A/C confirmation complete: two fresh-calibration blocks per configuration. Review both rounds and export."
                next.text = "A/C confirmation complete"
            } else if (action?.index != null) {
                val step = requireNotNull(plan).steps[action.index]
                val task = when (action.stage) {
                    ACConfirmationPlan.Stage.NEED_CALIBRATION -> "Fresh calibration"
                    ACConfirmationPlan.Stage.VALIDATION -> "Validation"
                    ACConfirmationPlan.Stage.READING -> "Reading test"
                    else -> ""
                }
                status.text = "A/C confirmation - round ${step.round} of 2, ${step.conditionId}\n" +
                    "Next: $task. Keep phone/head position and lighting consistent. Take a break between blocks if needed."
                next.text = "Continue A/C - round ${step.round}, ${step.conditionId}: $task"
            }
        } else for (i in 0 until radios.childCount) radios.getChildAt(i).isEnabled = true
        updateHistorySelector()
        renderResults()
    }
    private fun updateHistorySelector() {
        val sessions = reports.map { it.getJSONObject("metadata").getString("sessionId") }.distinct().filter { it != sessionId() }
        val keys = listOf(ExperimentResultHistory.ALL, ExperimentResultHistory.CURRENT) + sessions
        if (historyScope !in keys) historyScope = ExperimentResultHistory.ALL
        if (keys == historyKeys && historySession == sessionId()) return
        updatingHistory = true; historyKeys = keys; historySession = sessionId()
        val labels = listOf("Latest saved results (all comparisons)", "Current comparison (${sessionId().take(8)})") +
            sessions.map { "Saved comparison ${it.take(8)}" }
        historySelector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        historySelector.setSelection(keys.indexOf(historyScope))
        updatingHistory = false
    }
    private fun displayedReports() = ExperimentResultHistory.scope(reports, historyScope, sessionId())
    private fun renderResults() {
        results.removeAllViews()
        val displayed = displayedReports()
        renderConfirmationResults()
        results.addView(TextView(this).apply {
            text = "The table shows the latest attempt for each configuration and task in this view, including incomplete or failed attempts. " +
                "Rows from different comparisons or calibration collections are exploratory. Saved attempts below retain earlier runs."
            textSize = 14f
        })
        val table = TableLayout(this)
        val horizontal = HorizontalScrollView(this).apply { addView(table) }
        results.addView(horizontal)
        fun row(values: List<String>) { table.addView(TableRow(this).apply { values.forEach { value ->
            addView(TextView(context).apply { text = value; textSize = 15f; setPadding(dp(8), dp(8), dp(14), dp(8)) })
        } }) }
        row(listOf("Run", "Saved attempt / calibration", "Error cm", "Jitter cm", "Fresh Hz", "Age p95 ms", "Usable %"))
        GazeExperimentConfiguration.entries.forEach { config ->
            val report = ExperimentResultHistory.latest(displayed, config.id, ExperimentFlow.VALIDATION)
            val summary = report?.getJSONObject("result")?.getJSONObject("summary")
            val spatial = centimeterSpatial(report)
            row(listOf("${config.id} targets", attemptLabel(report), cmNumber(spatial, "medianErrorCm"), cmNumber(spatial, "spatialSdCm"),
                number(summary, "freshHz"), number(summary, "p95AgeMs"), number(summary, "coverage", 100.0)))
            val readingReport = ExperimentResultHistory.latest(displayed, config.id, ExperimentFlow.READING)
            val readingSummary = readingReport?.getJSONObject("result")?.getJSONObject("summary")
            row(listOf("${config.id} reading", attemptLabel(readingReport), "—", "—", number(readingSummary, "freshHz"),
                number(readingSummary, "p95AgeMs"), number(readingSummary, "coverage", 100.0)))
            val attempts = displayed.filter { it.getJSONObject("metadata").getString("conditionId") == config.id }
            val incomplete = attempts.count { !ExperimentResultHistory.qualifies(it) }
            results.addView(TextView(this).apply {
                text = "${config.label}: ${attempts.size} saved attempts in this view, $incomplete incomplete/failed. " +
                    "Current-calibration validation: ${if (completed(config, ExperimentFlow.VALIDATION) == null) "needed" else "recorded"}."
                textSize = 14f; setPadding(0, dp(6), 0, dp(6))
            })
        }
        results.addView(TextView(this).apply {
            text = "Target results use unsmoothed gaze at eight held-out locations. The centre repeat is separate in the export. " +
                "Error and jitter are in cm using device-reported display DPI; jitter is pooled within-target SD. " +
                "Older local runs use the current display scale only when screen geometry matches. Pixel data remains in the export. " +
                "Usable % is valid / observed delivered outputs; unobserved camera frames are unknown. " +
                "Age includes invalid and stale outputs with valid clocks. Swipe the table sideways."
            textSize = 14f
        })
        if (showAttempts) displayed.forEach { report ->
            val metadata = report.getJSONObject("metadata")
            val summary = report.getJSONObject("result").getJSONObject("summary")
            results.addView(TextView(this).apply {
                text = "${metadata.getString("conditionId")} ${report.getJSONObject("result").getString("mode")}\n" +
                    attemptLabel(report) + "\n" + metadata.getString("startedAtUtc").take(19).replace('T', ' ') + " UTC\n" +
                    "Error: ${cmNumber(centimeterSpatial(report), "medianErrorCm")} cm; " +
                    "fresh: ${number(summary, "freshHz")} Hz; age p95: ${number(summary, "p95AgeMs")} ms"
                textSize = 14f; setPadding(0, dp(12), 0, dp(12))
            })
        }
    }
    private fun attemptLabel(report: JSONObject?): String {
        if (report == null) return "No saved run"
        val metadata = report.getJSONObject("metadata")
        val result = report.getJSONObject("result")
        val outcome = metadata.getString("outcome")
        val qualifiers = mutableListOf(outcome)
        if (result.getString("mode") == ExperimentFlow.VALIDATION) {
            val missing = result.getJSONObject("summary").getJSONArray("missingHeldOutTargetIds").length()
            qualifiers += "${8 - missing}/8 held-out targets"
        }
        if (result.getBoolean("rowsTruncated")) qualifiers += "partial log"
        val config = metadata.getString("conditionId")
        val matches = metadata.getString("calibrationFingerprint") == fingerprints[config] &&
            metadata.getString("collectionId") == collections[config]
        qualifiers += if (matches) "current calibration" else "previous calibration"
        qualifiers += "comparison ${metadata.getString("sessionId").take(8)}"
        qualifiers += "collection ${metadata.getString("collectionId").take(8)}"
        qualifiers += "run ${report.getString("reportId").take(8)}"
        val source = metadata.getJSONObject("sourceMetadata")
        if (source.optString("confirmation_plan") == ACConfirmationPlan.VERSION)
            qualifiers += "A/C round ${source.optString("confirmation_round")}, block ${source.optString("confirmation_step_index").toIntOrNull()?.plus(1)}"
        if (report.optJSONObject("centimeters") == null && result.getString("mode") == ExperimentFlow.VALIDATION)
            qualifiers += "cm scale unavailable"
        return qualifiers.joinToString("\n")
    }
    private fun renderConfirmationResults() {
        val plan = confirmationPlan ?: return
        if (historyScope != ExperimentResultHistory.ALL && historyScope != ExperimentResultHistory.CURRENT && historyScope != plan.sessionId) return
        val table = TableLayout(this)
        results.addView(TextView(this).apply { text = "A/C rounds: fresh calibration in every block"; textSize = 18f })
        results.addView(HorizontalScrollView(this).apply { addView(table) })
        fun row(values: List<String>) { table.addView(TableRow(this).apply { values.forEach { value ->
            addView(TextView(context).apply { text = value; textSize = 15f; setPadding(dp(8), dp(8), dp(14), dp(8)) })
        } }) }
        row(listOf("Block", "Validation", "Error cm", "Jitter cm", "Reading Hz", "Age p95 ms", "Collection"))
        plan.steps.forEach { step ->
            val attempts = reports.filter { r -> val m = r.getJSONObject("metadata")
                m.getString("sessionId") == plan.sessionId && m.getString("conditionId") == step.conditionId &&
                    m.getString("collectionId") == step.boundCollectionId && m.getString("calibrationFingerprint") == step.boundFingerprint &&
                    plan.sourceMetadata(step.index).all { (key, value) -> m.getJSONObject("sourceMetadata").optString(key) == value }
            }
            val validation = ExperimentResultHistory.latest(attempts, step.conditionId, ExperimentFlow.VALIDATION)
            val readingReport = ExperimentResultHistory.latest(attempts, step.conditionId, ExperimentFlow.READING)
            val spatial = centimeterSpatial(validation)
            val readingSummary = readingReport?.getJSONObject("result")?.getJSONObject("summary")
            val validationState = validation?.let { r ->
                "${r.getJSONObject("metadata").getString("outcome")}\n${8 - r.getJSONObject("result").getJSONObject("summary").getJSONArray("missingHeldOutTargetIds").length()}/8 targets"
            } ?: "pending"
            row(listOf("R${step.round} ${step.conditionId}", validationState, cmNumber(spatial, "medianErrorCm"),
                cmNumber(spatial, "spatialSdCm"), number(readingSummary, "freshHz"), number(readingSummary, "p95AgeMs"),
                step.boundCollectionId?.take(8) ?: "fresh fit needed"))
        }
    }
    private fun chooseExport() {
        fun exportSession(id: String) {
            exportingSession = id
            export.launch("newsmead-gaze-$id.json")
        }
        if (historyScope != ExperimentResultHistory.ALL) {
            exportSession(if (historyScope == ExperimentResultHistory.CURRENT) sessionId() else historyScope)
            return
        }
        val ids = (listOf(sessionId()) + reports.map { it.getJSONObject("metadata").getString("sessionId") }).distinct()
        if (ids.size == 1) { exportSession(ids.single()); return }
        androidx.appcompat.app.AlertDialog.Builder(this).setTitle("Export a comparison")
            .setItems(ids.map { "${if (it == sessionId()) "Current" else "Saved"} comparison ${it.take(8)}" }.toTypedArray()) { _, which ->
                exportSession(ids[which])
            }.setNegativeButton("Cancel", null).show()
    }
    private fun continueGuided() {
        if (confirmationPlan != null || confirmationError != null) { continueConfirmation(); return }
        val order = prefs.getString("order", "A,B,C")!!.split(",").map { GazeExperimentConfiguration.fromId(it)!! }
        for (config in order) {
            select(config)
            if (!freshForGuided(config)) { openCalibration(config); return }
            if (completed(config, ExperimentFlow.VALIDATION) == null) { openRun(config, ExperimentFlow.VALIDATION); return }
            if (completed(config, ExperimentFlow.READING) == null) { openRun(config, ExperimentFlow.READING); return }
        }
        status.text = "All three configurations have validation and reading results. Review and export the comparison."
    }
    private fun select(config: GazeExperimentConfiguration) {
        for (i in 0 until radios.childCount) {
            val radio = radios.getChildAt(i) as RadioButton
            if (radio.tag == config.id) radios.check(radio.id)
        }
    }
    private fun openCalibration(config: GazeExperimentConfiguration, confirmationIndex: Int? = null) {
        launchingConfiguration = config.id; launchingCalibration = true
        val intent = Intent(this, GazeExperimentCalibrationActivity::class.java)
            .putExtra(ExperimentFlow.EXTRA_CONFIGURATION, config.id).putExtra(ExperimentFlow.EXTRA_SESSION, sessionId())
        launchingConfirmationStep = confirmationIndex?.let { index ->
            val plan = requireNotNull(confirmationPlan); ACConfirmationLaunch.put(intent, plan, index); plan.steps[index].id
        }
        launch.launch(intent)
    }
    private fun openRun(config: GazeExperimentConfiguration, mode: String, confirmationIndex: Int? = null) {
        launchingConfiguration = config.id; launchingCalibration = false
        launchingConfirmationStep = null
        val intent = Intent(this, GazeExperimentRunActivity::class.java)
            .putExtra(ExperimentFlow.EXTRA_CONFIGURATION, config.id).putExtra(ExperimentFlow.EXTRA_SESSION, sessionId())
            .putExtra(ExperimentFlow.EXTRA_MODE, mode).putExtra("configurationOrder", prefs.getString("order", "A,B,C"))
        confirmationIndex?.let { ACConfirmationLaunch.put(intent, requireNotNull(confirmationPlan), it) }
        launch.launch(intent)
    }
    private fun liveFits() = GazeExperimentConfiguration.entries.mapNotNull { config ->
        val collection = collections[config.id]; val fingerprint = fingerprints[config.id]
        if (compatible[config.id] == true && collection != null && fingerprint != null)
            config.id to ACConfirmationPlan.Fit(collection, fingerprint) else null
    }.toMap()
    private fun saveConfirmation(plan: ACConfirmationPlan) {
        check(plan.sessionId == sessionId())
        check(prefs.edit().putString("acPlan", plan.toJson().toString()).commit())
        confirmationPlan = plan
    }
    private fun bindConfirmationResult() {
        val pending = pendingConfirmationBinding ?: return
        pendingConfirmationBinding = null
        val plan = confirmationPlan ?: return
        val index = plan.steps.indexOfFirst { it.id == pending.first }
        if (index < 0) return
        runCatching { saveConfirmation(plan.bind(index, pending.second, pending.third, reports)) }
            .onFailure { Toast.makeText(this, "Calibration saved, but this A/C block could not accept it. Continue to collect a fresh fit.", Toast.LENGTH_LONG).show() }
    }
    private fun continueConfirmation() {
        val plan = confirmationPlan ?: return
        val action = plan.action(reports, liveFits())
        val index = action.index ?: return
        val step = plan.steps[index]
        val config = requireNotNull(GazeExperimentConfiguration.fromId(step.conditionId))
        select(config)
        if (action.stage == ACConfirmationPlan.Stage.NEED_CALIBRATION) {
            saveConfirmation(plan.enter(index, collections[config.id], reports))
            openCalibration(config, index)
        } else openRun(config, if (action.stage == ACConfirmationPlan.Stage.VALIDATION) ExperimentFlow.VALIDATION else ExperimentFlow.READING, index)
    }
    private fun createConfirmation() {
        val form = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        form.addView(TextView(this).apply {
            text = "Two rounds: A then C, followed by C then A. Each block collects a fresh calibration, nine-target validation and 60-second reading test.\n\n" +
                "Support the phone and keep its position, your seated head position, lighting and glasses consistent. Take breaks between blocks."
            textSize = 16f
        })
        val distance = EditText(this).apply { hint = "Viewing distance (cm, optional)"; inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL }
        val notes = EditText(this).apply { hint = "Setup notes: support, room lighting, glasses"; maxLines = 4; filters = arrayOf(android.text.InputFilter.LengthFilter(800)) }
        form.addView(distance); form.addView(notes)
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this).setTitle("A/C confirmation setup")
            .setView(ScrollView(this).apply { addView(form) }).setPositiveButton("Create comparison", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val text = distance.text.toString().trim().replace(',', '.')
                val value = if (text.isEmpty()) null else text.toDoubleOrNull()
                if (text.isNotEmpty() && (value == null || !value.isFinite() || value <= 0)) { distance.error = "Enter a positive distance or leave blank"; return@setOnClickListener }
                val plan = ACConfirmationPlan.create(setupNotes = notes.text.toString().trim(), viewingDistanceCm = value)
                check(prefs.edit().putString("session", plan.sessionId).putString("order", "A,C,C,A").putBoolean("guided", false)
                    .putString("acPlan", plan.toJson().toString()).remove("collection_current").remove("collection_next").commit())
                confirmationPlan = plan; confirmationError = null; pendingConfirmationBinding = null
                historyScope = ExperimentResultHistory.ALL
                dialog.dismiss(); refresh()
            }
        }
        dialog.show()
    }
    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value; textSize = size; setPadding(0, dp(8), 0, dp(8)); content.addView(this)
    }
    private fun button(value: String, action: () -> Unit) = MaterialButton(this).apply {
        text = value; setOnClickListener { action() }; content.addView(this, LinearLayout.LayoutParams(-1, -2))
    }
    private fun number(obj: JSONObject?, key: String, scale: Double = 1.0): String =
        if (obj == null || obj.isNull(key)) "—" else String.format(Locale.US, "%.1f", obj.getDouble(key) * scale)
    private fun centimeterSpatial(report: JSONObject?) = report?.optJSONObject("centimeters")
        ?.optJSONObject("summary")?.optJSONObject("rawHeldOut")
    private fun cmNumber(obj: JSONObject?, key: String): String =
        if (obj == null || obj.isNull(key)) "—" else String.format(Locale.US, "%.3f", obj.getDouble(key))
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
