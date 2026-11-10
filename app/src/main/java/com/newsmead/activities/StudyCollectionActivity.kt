package com.newsmead.activities

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.newsmead.research.study.StudyCollection
import com.newsmead.research.study.StudyRecordingCatalog
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Researcher controls. Opening this screen never starts the camera or recording. */
class StudyCollectionActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var recordingList: LinearLayout
    private lateinit var selectionStatus: TextView
    private var catalog: StudyRecordingCatalog.Catalog? = null
    private val selected = mutableSetOf<String>()
    private var refreshGeneration = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (16 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad,pad,pad,pad) }
        fun button(label: String, action: () -> Unit) = root.addView(MaterialButton(this).apply {
            text = label; setOnClickListener { action() }
        })
        button("Back") { finish() }
        root.addView(TextView(this).apply { text = "Local study recording"; textSize = 22f })
        root.addView(TextView(this).apply {
            text = "Researcher controls. Use a participant code, not a name. Start a Reading run, collect the person's Reading baseline, then open the assigned article. Records stay on this phone until you transfer them."
        })
        button("Session instructions") {
            AlertDialog.Builder(this).setTitle("Researcher session instructions")
                .setMessage("""
                    1. Consent and screening
                    Complete the approved consent and screening procedure before camera use. Interview audio is optional and needs separate agreement. Keep signed forms separate from coded gaze records.

                    2. Calibrate this person
                    Home → Calibrate eye tracker. Complete and save a fresh calibration for each new person. A saved calibration from the previous person is not suitable. The current app uses 13 targets and six checks, not the old colour-band instructions. Record its actual PASS/RETRY result and follow the approved retry procedure; PASS does not establish word/line accuracy.

                    3. Start the recording run
                    Enter the participant code and assigned condition label. Choose Reading and tap Start run. This prepares collection; opening this screen does not start the camera. A condition label is only a label.

                    4. Collect the reading baseline
                    Home → Reading baseline → Start. Ask the reader to read normally and scroll as needed. Use the displayed covered time and valid-window count; this is not an automatic 20-second warm-up. Tap Finish, then check the result. R/D/F describes the behaviour reference. The coordinate reference is recorded here and checked after transfer.

                    5. Read the assigned article
                    Open Reading aids in the article and set its main switch ON or OFF for the assigned condition before asking the participant to begin. Check this for every article. Keep font size, orientation and other settings consistent. Record when the participant begins/finishes; an article visit also includes setup time.

                    6. Between readings
                    Leave the article, administer its prepared comprehension questions and allow the planned break. To change the condition label, Stop run, enter the next label and Start run with the same participant. Keep the app process open; after an app restart, collect a new reading baseline.

                    7. Finish the session
                    Leave the article and tap Stop run. Follow the approved order: usability survey, interview, then debriefing. Record whether permission to use the data is maintained after debriefing. Honour pauses and withdrawal.

                    8. Save and transfer
                    Refresh saved recordings. Select this participant's readings and transfer selected recordings; available linked baselines are included. Retain originals until the received copy is verified. Stopping a run stops study records, not the article camera setting.

                    Use the approved participant-facing scripts. Do not describe MD as controlling the aids or an unavailable reference as ready.
                """.trimIndent())
                .setPositiveButton("Close", null).show()
        }
        val participant = EditText(this).apply { hint = "Participant code"; setSingleLine(); setText(StudyCollection.run?.participant ?: "") }
        val condition = EditText(this).apply { hint = "Condition label"; setSingleLine(); setText(StudyCollection.run?.condition ?: "pilot") }
        val phase = Spinner(this).apply { adapter = ArrayAdapter(this@StudyCollectionActivity,
            android.R.layout.simple_spinner_dropdown_item, listOf("Reading", "Coordinate reference")) }
        root.addView(participant); root.addView(condition); root.addView(phase)
        status = TextView(this).apply { setTextIsSelectable(true) }; root.addView(status)
        button("Start run") {
            runCatching { StudyCollection.start(this,participant.text.toString(),condition.text.toString(),
                if (phase.selectedItemPosition == 1) "coordinate_reference" else "reading") }
                .onSuccess { clearRecordings(); refresh() }.onFailure { status.text = it.message ?: "Could not start recording." }
        }
        button("Stop run") { StudyCollection.stop(); refresh(); loadRecordings() }
        button("Recover interrupted records") {
            status.text = "Checking local records…"
            StudyCollection.background {
                val result = runCatching { StudyCollection.recover(applicationContext) }
                runOnUiThread { result.onSuccess { status.text = "Recovered $it packages. Originals retained."; loadRecordings() }
                    .onFailure { status.text = it.message ?: "Recovery failed." } }
            }
        }
        button("Transfer all study packages") {
            status.text = "Preparing verified local copy…"
            StudyCollection.background {
                val result = runCatching { StudyCollection.export(applicationContext) }
                runOnUiThread { result.onSuccess { intent -> if (intent != null) {
                    status.text = "Original packages remain on the phone. Verify the received copy with the study importer."
                    startActivity(intent)
                } else status.text = "No completed or recovered packages to transfer." }
                    .onFailure { status.text = it.message ?: "Transfer preparation failed." } }
            }
        }
        root.addView(TextView(this).apply { text = "Saved recordings"; textSize = 20f })
        root.addView(TextView(this).apply {
            text = "Stop the run to browse. Select article readings or separate baseline/reference recordings. Linked shared baselines are included when available, independently of R/D/F fit success. Legacy coordinate-reference recordings require explicit selection. Saved status is declared by the package; transfer verifies file hashes."
        })
        button("Refresh saved recordings") { loadRecordings() }
        selectionStatus = TextView(this).apply { setTextIsSelectable(true) }; root.addView(selectionStatus)
        recordingList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(recordingList)
        button("Transfer selected recordings") {
            val ids = selected.toSet()
            if (StudyCollection.run != null) { status.text = "Stop recording before transfer." }
            else if (ids.isEmpty()) { selectionStatus.text = "Select at least one recording." }
            else {
                status.text = "Preparing selected recordings and available source baselines…"
                StudyCollection.background {
                    val result = runCatching { StudyCollection.export(applicationContext, ids) }
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed) result.onSuccess { intent ->
                            if (intent == null) status.text = "No selected packages remain. Refresh the list."
                            else { status.text = "Selected originals remain on the phone. Verify the received copy with the study importer."; startActivity(intent) }
                        }.onFailure { status.text = it.message ?: "Transfer preparation failed." }
                    }
                }
            }
        }
        setContentView(ScrollView(this).apply { fitsSystemWindows = true; addView(root) }); refresh()
        if (StudyCollection.run == null) loadRecordings() else clearRecordings()
    }
    private fun clearRecordings() {
        refreshGeneration++; selected.clear(); catalog = null
        recordingList.removeAllViews(); selectionStatus.text = "Stop recording, then refresh saved recordings."
    }
    private fun loadRecordings() {
        clearRecordings()
        if (StudyCollection.run != null) return
        val generation = refreshGeneration
        selectionStatus.text = "Loading saved recording metadata…"
        StudyCollection.background {
            val result = runCatching { StudyCollection.catalog(applicationContext) }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                result.onSuccess { loaded ->
                    if (StudyCollection.run != null) { clearRecordings(); return@onSuccess }
                    catalog = loaded
                    loaded.recordings.forEach { recording ->
                        recordingList.addView(MaterialCheckBox(this).apply {
                            text = describe(recording)
                            setOnCheckedChangeListener { _, checked ->
                                if (checked) selected.add(recording.id) else selected.remove(recording.id)
                                updateSelection()
                            }
                        })
                    }
                    updateSelection()
                }.onFailure { selectionStatus.text = it.message ?: "Could not load saved recordings." }
            }
        }
    }
    private fun describe(recording: StudyRecordingCatalog.Recording): String {
        val first = recording.first
        val label = when (first.phase) {
            "rdf_baseline" -> "R/D/F baseline"
            "reading_baseline" -> "Shared reading baseline (coordinate fit pending offline)"
            "coordinate_reference" -> "Coordinate reference (explicit selection)"
            "reading" -> "Article reading"
            else -> "Phase unavailable"
        }
        val article = recording.packages.map { it.articleId ?: "unavailable" }.distinct().joinToString(", ")
        val date = recording.startedUtcMs?.let { DateFormat.getDateTimeInstance().format(Date(it)) } ?: "Time unavailable"
        val duration = recording.durationMs?.let { String.format(Locale.ROOT, "%.1f s recorded segment span (includes exclusions)", it / 1000) } ?: "Duration unavailable"
        val references = recording.packages.map { p ->
            if (p.phase in listOf("rdf_baseline", "reading_baseline")) p.baselineAttempt ?: "unavailable"
            else if (!p.referenceKnown) "unavailable" else p.reference?.attempt ?: "none"
        }.distinct().joinToString(", ")
        val coordinate = recording.packages.map { it.coordinateReference?.packageId ?: "none" }.distinct().joinToString(", ")
        return "$label • ${first.participant}\nArticle ID: $article\nR/D/F reference ID: $references\nCoordinate source package: $coordinate\n$date • $duration\n" +
            "${String.format(Locale.ROOT, "%.2f MB", recording.bytes / 1_000_000.0)} • ${recording.packages.size} segment(s) • " +
            recording.packages.map { it.status }.distinct().joinToString(", ") +
            "\n" + if (recording.legacy) "Legacy transport session: ${first.session}"
            else "Recording ID: ${first.readingId ?: first.session}"
    }
    private fun updateSelection() {
        val loaded = catalog ?: return
        val plan = if (selected.isEmpty()) null else StudyRecordingCatalog.select(loaded, selected)
        selectionStatus.text = "${loaded.recordings.size} saved recording(s); ${selected.size} selected." +
            (plan?.let { "\n${it.packageIds.size} package(s) to transfer. Included source baseline package IDs: " +
                it.includedReferenceIds.joinToString(", ").ifEmpty { "none" } +
                if (it.warnings.isEmpty()) "" else "\n" + it.warnings.joinToString("\n") } ?: "") +
            if (loaded.warnings.isEmpty()) "" else "\n" + loaded.warnings.joinToString("\n")
    }
    private fun refresh() { status.text = (StudyCollection.run?.let {
        "Recording enabled: ${it.participant}, ${it.condition}, ${it.phase}. Return to Home to open a baseline or article."
    } ?: "Recording is stopped. Export never deletes original records.") +
        "\nRecent session IDs:\n" + StudyCollection.recentSessions().joinToString("\n") }
}
