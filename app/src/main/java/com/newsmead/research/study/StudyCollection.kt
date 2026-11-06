package com.newsmead.research.study

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.widget.TextView
import android.graphics.Rect
import androidx.core.content.FileProvider
import com.newsmead.BuildConfig
import com.newsmead.gaze.ParticipantReadingBaseline
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import com.newsmead.gaze.getVisibleRectOnScreen
import com.newsmead.gaze.aid.ReadingAidAction
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Explicit researcher-controlled run. No network, automatic capture, or active-model restoration. */
object StudyCollection {
    data class Run(val participant: String, val condition: String, val phase: String, val id: String,
        val installation: String, val clockDomain: String)
    private val processClockDomain = UUID.randomUUID().toString()
    private val visits = mutableListOf<StudyVisit>()
    private val io = Executors.newSingleThreadExecutor()
    @Volatile private var maintenance = false
    private val maintenanceGate = StudyMaintenanceGate()
    private var isolatedTestStorage: File? = null
    var run: Run? = null
        private set
    private fun storage(context: Context) = isolatedTestStorage ?: context.noBackupFilesDir
    fun root(context: Context) = File(storage(context), "study-packages-v1")
    /** Instrumentation can rehearse the real exporter without enumerating participant packages. */
    @Synchronized internal fun useIsolatedStorageForTest(context: Context, directory: File?) {
        check(BuildConfig.DEBUG && run == null && !maintenance)
        check(visits.all { it.awaitClosed(0) })
        if (directory != null) {
            val path = directory.canonicalFile
            check(path.parentFile == context.cacheDir.canonicalFile && path.name.startsWith("study-device-test-"))
            check(path.mkdirs() || path.isDirectory)
        }
        visits.clear(); isolatedTestStorage = directory
    }
    @Synchronized fun start(context: Context, participant: String, condition: String, phase: String) {
        check(!maintenance) { "Wait for recovery or transfer preparation to finish." }
        require(participant.isNotBlank() && condition.isNotBlank())
        require(phase in listOf("reading", "coordinate_reference"))
        stop("run_replaced")
        val file = File(storage(context), "study-installation-id")
        val installation = if (file.isFile) file.readText().trim() else UUID.randomUUID().toString().also { file.writeText(it) }
        maintenanceGate.startRun()
        run = Run(participant.trim(), condition.trim(), phase, UUID.randomUUID().toString(), installation, processClockDomain)
    }
    @Synchronized fun stop(reason: String = "researcher_stopped") {
        visits.toList().forEach { it.finish(reason) }; run = null
        maintenanceGate.endRun()
    }
    @Synchronized fun open(context: Context, phase: String? = null, metadata: JSONObject = JSONObject()): StudyVisit? {
        check(!maintenance)
        val active = run ?: return null
        return StudyVisit(context.applicationContext, active, phase ?: active.phase, metadata).also { visits.add(it) }
    }
    fun background(work: () -> Unit) { io.execute(work) }
    @Synchronized fun recentSessions(): List<String> = visits.takeLast(4).map { "${it.run.participant} • ${it.phase} • ${it.id}" }
    @Synchronized private fun claimMaintenance(): List<StudyVisit> {
        check(run == null && !maintenance) { "Stop study recording before recovery or transfer." }
        maintenanceGate.claim()
        maintenance = true
        return visits.toList()
    }
    /** Call recovery only when no run/writer is active. */
    fun recover(context: Context): Int {
        val closing = claimMaintenance()
        try {
            check(closing.all { it.awaitClosed(10_000) }) { "Recording is still closing. Try recovery again shortly." }
            return StudyPackageWriter.recover(root(context)).size
        } finally { maintenanceGate.release(); maintenance = false }
    }
    /** Disk work only: callers dispatch through background(), never the UI thread. */
    fun catalog(context: Context): StudyRecordingCatalog.Catalog {
        val closing = claimMaintenance()
        try {
            check(closing.all { it.awaitClosed(10_000) }) { "Recording is still closing. Refresh shortly." }
            return StudyRecordingCatalog.load(root(context))
        } finally { maintenanceGate.release(); maintenance = false }
    }
    fun export(context: Context, recordingIds: Set<String>? = null): Intent? {
        val closing = claimMaintenance()
        try {
        check(closing.all { it.awaitClosed(10_000) }) { "Recording is still closing. Try export again shortly." }
        val root = root(context)
        StudyRecordingCatalog.validatePaths(root)
        StudyPackageWriter.recover(root)
        val selection = recordingIds?.let { StudyRecordingCatalog.select(StudyRecordingCatalog.load(root), it) }
        val packages = root.listFiles()?.filter { it.isDirectory && File(it, "manifest.json").isFile &&
            (selection == null || it.name in selection.packageIds) } ?: emptyList()
        if (packages.isEmpty()) return null
        val staging = File(context.cacheDir, "research-export").apply { mkdirs() }
        val zip = File(staging, "study_${UUID.randomUUID()}.zip")
        ZipOutputStream(zip.outputStream()).use { output ->
            val inventory = JSONArray()
            val receipts = JSONArray()
            val sessionIds = mutableSetOf<String>()
            packages.sortedBy { it.name }.forEach { directory ->
                StudyRecordingCatalog.directory(root, directory.name)
                val manifestFile = StudyRecordingCatalog.file(directory, "manifest.json")
                val manifest = JSONObject(manifestFile.readText(Charsets.UTF_8))
                require(manifest.getString("package_id") == directory.name)
                val session = manifest.getJSONObject("identity").getString("session_id")
                sessionIds.add(session)
                val entry = StudyJson.obj("package_id" to manifest.getString("package_id"),
                    "session_id" to session, "manifest_sha256" to sha(manifestFile))
                if (selection != null) entry.put("included_reference", directory.name in selection.includedReferenceIds)
                inventory.put(entry)
                val chunks = manifest.getJSONArray("chunks")
                val names = (0 until chunks.length()).map { chunks.getJSONObject(it).getString("file") } + "manifest.json"
                names.forEach { name ->
                    require(name.matches(Regex("[A-Za-z0-9_.-]+")))
                    val file = StudyRecordingCatalog.file(directory, name)
                    require(file.isFile && file.canonicalFile.parentFile == directory.canonicalFile)
                    if (name != "manifest.json") {
                        val entry = (0 until chunks.length()).map { chunks.getJSONObject(it) }.first { it.getString("file") == name }
                        require(file.length() == entry.getLong("byte_count") && sha(file) == entry.getString("sha256"))
                    }
                    output.putNextEntry(ZipEntry("${directory.name}/$name")); file.inputStream().use { it.copyTo(output) }; output.closeEntry()
                }
            }
            val receiptRoot = File(storage(context),"study-late-receipts")
            require(receiptRoot.canonicalFile.parentFile == storage(context).canonicalFile)
            receiptRoot.listFiles()?.filter { it.isFile && it.extension == "json" }
                ?.sortedBy { it.name }?.forEach { file ->
                    StudyRecordingCatalog.file(receiptRoot, file.name)
                    val receipt = JSONObject(file.readText(Charsets.UTF_8))
                    if (receipt.optString("session_id") in sessionIds) {
                        val name = "late_receipts/${file.name}"
                        receipts.put(StudyJson.obj("file" to name, "sha256" to sha(file), "byte_count" to file.length(),
                            "session_id" to receipt.getString("session_id"), "provider_epoch_id" to receipt.getString("provider_epoch_id")))
                        output.putNextEntry(ZipEntry(name)); file.inputStream().use { it.copyTo(output) }; output.closeEntry()
                    }
                }
            output.putNextEntry(ZipEntry("study_export_manifest.json"))
            val exportManifest = StudyJson.obj("schema_version" to "newsmead_study_export_v1", "packages" to inventory,
                "late_receipts" to receipts)
            if (selection != null) exportManifest.put("selection_warnings", JSONArray(selection.warnings))
            output.write(exportManifest.toString().toByteArray(Charsets.UTF_8)); output.closeEntry()
        }
        val uri = FileProvider.getUriForFile(context, context.packageName + ".research.fileprovider", zip)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Transfer study packages")
        } finally { maintenanceGate.release(); maintenance = false }
    }
    private fun sha(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(65536); while (true) {
            val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
        } }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    internal fun lateReceiptRoot(context: Context) = File(storage(context), "study-late-receipts")
}

/** One baseline or foreground article visit. Only immutable numerical/text snapshots reach the writer. */
class StudyVisit internal constructor(private val app: Context, val run: StudyCollection.Run,
    val phase: String, metadata: JSONObject) {
    val id = UUID.randomUUID().toString()
    private val handler = Handler(Looper.getMainLooper())
    private val started = now()
    private val pending = mutableSetOf<String>()
    private var logicalEnd: Double? = null
    private var sealed = false
    private var unknownTerminal = false
    private var pendingResults = 0
    private val timeline = StudyCaptureContext(started)
    private val excluded = mutableSetOf<String>()
    private var presentation = JSONObject()
    private var presentationId: String? = null
    private var calibration: String? = null
    private var currentBodyHash: String? = null
    private var lastPresentationSignature: List<Any?>? = null
    private var lastTextSource: CharSequence? = null
    private var aidSettings = JSONObject()
    private val providerMetadata = mutableMapOf<String, JSONObject>()
    private val acquisitionIds = mutableMapOf<String, String>()
    private data class Presentation(val id: String, val firstMs: Double, var lastMs: Double, var actionId: String,
        var target: String)
    private val presentations = mutableMapOf<String, Presentation>()
    private val auditEncoder = StudyAuditEncoder()
    // A terminal callback may outlive the active run and any test-storage override.
    private val lateReceiptDirectory = StudyCollection.lateReceiptRoot(app)
    private val writer = StudyPackageWriter(StudyCollection.root(app), StudyJson.obj(
        "study_id" to "newsmead_local_v1", "participant_id" to run.participant,
        "installation_id" to run.installation, "run_id" to run.id, "session_id" to id,
        "clock_domain_id" to run.clockDomain), ::now, onState = { state ->
            if (state == "failed") handler.post { android.widget.Toast.makeText(app,
                "Study recording failed. Reading remains available; inspect local records.", android.widget.Toast.LENGTH_LONG).show() }
        })
    init {
        record("session_start", StudyJson.obj("start_ms" to started, "phase" to phase,
            "collector_version" to "study_collection_v1.2", "audit_context_encoding" to StudyAuditEncoder.VERSION,
            "chunk_storage_encoding" to StudyChunkIO.GZIP_ENCODING,
            "coordinate_core_version" to com.newsmead.gaze.trajectory.CoordinateMahalanobis.VERSION,
            "rdf_definition" to "capture_time_line_stays_v1",
            "condition" to run.condition, "started_utc_ms" to System.currentTimeMillis(),
            "version_name" to BuildConfig.VERSION_NAME, "version_code" to BuildConfig.VERSION_CODE,
            "device_model" to Build.MODEL, "android_sdk" to Build.VERSION.SDK_INT,
            "metadata" to metadata), started)
    }
    fun record(kind: String, payload: JSONObject, atMs: Double? = null) {
        if (!sealed) runCatching { writer.record(kind, payload, atMs) }.onFailure { instrumentationFailure() }
    }
    fun recordValue(kind: String, value: Any?, atMs: Double? = null) {
        runCatching { record(kind, StudyJson.obj("value" to value), atMs) }.onFailure { instrumentationFailure() }
    }
    fun auditEvent(kind: String, atMs: Double, detail: Map<String, Any?>) {
        if (sealed) return
        runCatching {
            auditEncoder.record(kind, atMs, detail) { event, payload, sourceMs -> writer.record(event, payload, sourceMs) }
        }.onFailure { instrumentationFailure() }
    }
    private fun instrumentationFailure() {
        if (!sealed) { sealed = true; writer.finish("instrumentation_failure", now(), true) }
    }
    fun providerStarted(calibrationFingerprint: String?): String {
        val epoch = UUID.randomUUID().toString(); pending.add(epoch); calibration = calibrationFingerprint
        record("provider_start", StudyJson.obj("provider_epoch_id" to epoch, "calibration_id" to calibration), now())
        boundary("provider_start"); return epoch
    }
    fun metadata(epoch: String, values: Map<String, Any?>) {
        val json = StudyJson.value(values) as JSONObject; providerMetadata[epoch] = json
        acquisitionIds[epoch] = StudyJson.hash(json.toString())
        record("provider_metadata", StudyJson.obj("provider_epoch_id" to epoch, "metadata" to json), now())
    }
    fun presentation(body: TextView, extra: JSONObject = JSONObject()) {
        if (logicalEnd != null) return
        val visible = Rect(); body.getVisibleRectOnScreen(visible)
        val signature = listOf(body.layout, body.textSize, body.width, body.textScaleX, body.letterSpacing,
            body.lineSpacingExtra, body.lineSpacingMultiplier, body.typeface, body.currentTextColor,
            body.textLocale, body.resources.configuration.fontScale, visible.left, visible.top, visible.right,
            visible.bottom, extra.toString())
        if (signature == lastPresentationSignature && body.text === lastTextSource) return
        lastPresentationSignature = signature
        val value = StudyJson.obj("text_size_px" to body.textSize, "column_width_px" to (body.layout?.width ?: body.width),
            "signature_version" to "physical_viewport_typography_v1",
            "viewport_rect_screen_px" to listOf(visible.left,visible.top,visible.right,visible.bottom),
            "density" to body.resources.displayMetrics.density, "font_scale" to body.resources.configuration.fontScale,
            "line_spacing_extra_px" to body.lineSpacingExtra, "line_spacing_multiplier" to body.lineSpacingMultiplier,
            "text_scale_x" to body.textScaleX, "letter_spacing" to body.letterSpacing,
            "typeface_style" to body.typeface.style, "typeface_descriptor" to body.typeface.toString(),
            "language" to body.textLocale.toLanguageTag(),
            "text_color" to body.currentTextColor, "settings" to extra, "aid_preferences" to aidSettings)
        val hash = StudyJson.hash(value.toString())
        if (hash != presentationId) {
            presentation = value; presentationId = hash; boundary("presentation_changed")
            record("configuration", StudyJson.obj("producer" to "presentation", "presentation_id" to hash, "settings" to value), now())
        }
        lastTextSource = body.text
        val text = body.text.toString(); val textHash = StudyJson.hash(text)
        if (textHash != currentBodyHash) {
            currentBodyHash = textHash; boundary("text_changed")
            record("text_snapshot", StudyJson.obj("body_text" to text, "body_sha256" to textHash,
                "language" to body.textLocale.toLanguageTag()), now())
        }
    }
    fun boundary(reason: String, allowed: Boolean? = null, atMs: Double = now()) {
        if (sealed) return
        val effective = maxOf(atMs, now())
        val next = timeline.change(effective, allowed, presentationId, presentation)
        record("boundary", StudyJson.obj("at_ms" to effective, "source_at_ms" to atMs,
            "time_basis" to "collector_observed_transition_not_earlier_than_native_event",
            "reason" to reason, "context_epoch_id" to next.epoch, "context_allowed" to next.allowed), effective)
    }
    fun exclude(reason: String, active: Boolean) {
        val changed = if (active) excluded.add(reason) else excluded.remove(reason)
        if (changed) boundary(reason + if (active) "_start" else "_end", excluded.isEmpty() && logicalEnd == null)
    }
    fun observation(epoch: String, o: MgazeNetGazeProvider.Observation, screenWidth: Int, screenHeight: Int, rotationDegrees: Int) {
        if (sealed) { if (o.reason == "stopped") lateReceipt(epoch, o); return }
        val at = o.captureMs?.takeIf { it.isFinite() } ?: o.outputMs
        val state = timeline.at(at)
        val meta = providerMetadata[epoch]
        val coordinateContext = if (meta != null && state?.presentationId != null && calibration != null) StudyJson.obj(
            "participant_id" to run.participant, "installation_id" to run.installation, "calibration_id" to calibration,
            "model_id" to meta.optString("model_sha256", "unknown"),
            "acquisition_id" to acquisitionIds[epoch], "presentation_id" to state.presentationId,
            "clock_domain_id" to run.clockDomain, "screen_width_px" to screenWidth, "screen_height_px" to screenHeight,
            "rotation_degrees" to rotationDegrees, "coordinate_stream" to "calibrated_raw_v1") else null
        record("gaze_observation", StudyJson.obj("provider_epoch_id" to epoch, "delivery_id" to o.deliveryId,
            "capture_ms" to o.captureMs, "delivery_ms" to o.outputMs, "delivery_elapsed_ns" to o.deliveryElapsedNs,
            "reason" to o.reason, "raw_x_px" to o.rawX, "raw_y_px" to o.rawY,
            "selected_x_px" to o.x, "selected_y_px" to o.y, "timeout_watchdog" to o.timeoutWatchdog,
            "arrivals" to o.arrivals, "busy_drops" to o.busyDrops, "blink_filter_applied" to o.blinkFilterApplied,
            "left_eye_aspect" to o.leftEyeAspect, "right_eye_aspect" to o.rightEyeAspect,
            "coordinate_context" to coordinateContext, "context_epoch_id" to (state?.epoch ?: "context_unavailable"),
            "context_allowed" to (state?.allowed == true), "presentation_settings" to state?.settings), at)
        if (o.reason == "stopped") { pending.remove(epoch); if (logicalEnd != null && pending.isEmpty() && pendingResults == 0) seal() }
    }
    fun baseline(profile: ParticipantReadingBaseline.Entry?) = record("rdf_reference", StudyJson.obj(
        "reference" to profile, "participant_match" to (profile == null || profile.participantCode == run.participant)), now())
    fun runtimeEvent(kind: String, atMs: Double, detail: JSONObject) {
        record(if (kind == "support_revision") kind else "aid_runtime_$kind", detail, atMs)
        if (kind == "preferences" || kind == "started") {
            val settings = if (kind == "preferences") detail else detail.optJSONObject("preferences") ?: JSONObject()
            aidSettings = settings
            presentation = JSONObject(presentation.toString()).put("aid_preferences", settings)
            presentationId = StudyJson.hash(presentation.toString())
            lastPresentationSignature = null
            record("configuration", StudyJson.obj("producer" to "presentation", "presentation_id" to presentationId,
                "settings" to presentation), atMs)
            boundary("aid_preferences_changed", atMs = atMs)
        }
        when (kind) {
            "suspended" -> exclude("aid_runtime_suspended", true)
            "resumed" -> exclude("aid_runtime_suspended", false)
            "auto_scroll_start" -> exclude("auto_scroll", true)
            "auto_scroll_end" -> exclude("auto_scroll", false)
            "definition_consultation" -> exclude("definition_consultation", detail.optBoolean("active"))
            "reader_preferences", "preferences", "geometry", "focus_scroll_presentation" -> boundary("aid_$kind", atMs = atMs)
        }
    }
    fun aidRender(action: ReadingAidAction, drawn: Boolean, reason: String, atMs: Double) {
        val channel = action.aidType.name
        val target = "${action.anchor.generation}/${action.anchor.geometrySnapshotId}/${action.anchor.line}/${action.anchor.wordStartUtf16}/${action.anchor.wordEndUtf16}" +
            "/${action.strength}/${action.focusStyle}/${action.guideRange}/${action.guideEvidence?.opacity}/${action.guideEvidence?.threeLine}"
        val previous = presentations[channel]
        val value = if (drawn) previous ?: Presentation(UUID.randomUUID().toString(),atMs,atMs,action.id,target)
            .also { presentations[channel] = it } else previous
        record("aid_presentation", StudyJson.obj("presentation_id" to value?.id, "channel" to channel,
            "action_id" to action.id, "drawn_proxy" to drawn, "reason" to reason,
            "first_draw_ms" to value?.firstMs, "last_draw_ms" to if (drawn) atMs else value?.lastMs,
            "verified_visibility" to false, "action" to action), atMs)
        if (drawn && value != null) {
            if (previous == null || value.target != target) boundary("aid_presentation_changed:$channel", atMs = atMs)
            value.lastMs = atMs; value.actionId = action.id; value.target = target
        } else if (!drawn && previous != null) { presentations.remove(channel); boundary("aid_presentation_ended:$channel", atMs = atMs) }
    }
    fun resumed() { exclude("aid_runtime_suspended", false) }
    fun health() = writer.health()
    fun beginResult() { pendingResults++ }
    fun endResult() { pendingResults = (pendingResults - 1).coerceAtLeast(0)
        if (logicalEnd != null && pending.isEmpty() && pendingResults == 0) seal() }
    fun awaitClosed(timeoutMs: Long) = writer.awaitClosed(timeoutMs)
    fun finish(reason: String) {
        if (logicalEnd != null) return
        val end = now(); logicalEnd = end; boundary("session_end:$reason", false, end)
        presentations.forEach { (channel, value) -> record("aid_presentation", StudyJson.obj(
            "presentation_id" to value.id, "channel" to channel, "action_id" to value.actionId,
            "first_draw_ms" to value.firstMs, "last_draw_ms" to value.lastMs, "censored" to true,
            "reason" to reason, "verified_visibility" to false), end) }; presentations.clear()
        record("session_end", StudyJson.obj("end_ms" to end, "reason" to reason, "pending_provider_epochs" to pending.toList()), end)
        if (pending.isEmpty() && pendingResults == 0) seal() else handler.postDelayed({ if (!sealed) { unknownTerminal = true; seal() } }, CLOSE_TIMEOUT_MS)
    }
    private fun seal() {
        if (sealed) return
        record("provider_close_state", StudyJson.obj("unknown_terminal_counters" to unknownTerminal,
            "missing_provider_epochs" to pending.toList()), now())
        sealed = true; writer.finish("logical_session_closed", logicalEnd ?: now(), unknownTerminal)
    }
    private fun lateReceipt(epoch: String, observation: MgazeNetGazeProvider.Observation) {
        val data = StudyJson.obj("session_id" to id, "provider_epoch_id" to epoch, "received_elapsed_ms" to now(),
            "arrivals" to observation.arrivals, "busy_drops" to observation.busyDrops, "extends_reading_interval" to false)
        StudyCollection.background { val root = lateReceiptDirectory.apply { mkdirs() }
            runCatching {
                val partial = File(root,"$id-$epoch.json.partial"); partial.writeText(data.toString(),Charsets.UTF_8)
                check(partial.renameTo(File(root,"$id-$epoch.json")))
            } }
    }
    companion object {
        const val CLOSE_TIMEOUT_MS = 3000L
        fun now(): Double = SystemClock.elapsedRealtimeNanos() / 1e6
    }
}
