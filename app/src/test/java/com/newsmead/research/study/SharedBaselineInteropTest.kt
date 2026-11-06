package com.newsmead.research.study

import com.newsmead.gaze.SharedReadingBaseline
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Real writer + producer metadata, synthetic numeric points only. No Android or camera.
 * The Python checker consumes the resulting packages independently with default gates.
 */
class SharedBaselineInteropTest {
    @Test fun writePairedSharedPackagesUsingActualProducerMetadataAndReferenceLink() {
        val parent = File("build/shared-baseline-interop").apply { mkdirs() }
        val root = File(parent, UUID.randomUUID().toString()).apply { mkdirs() }
        val attempt = "synthetic-shared-attempt"
        fun identity(session: String) = StudyJson.obj("study_id" to "synthetic", "participant_id" to "synthetic",
            "installation_id" to "synthetic-phone", "run_id" to "synthetic-run", "session_id" to session,
            "clock_domain_id" to "synthetic-clock")
        val context = StudyJson.obj("participant_id" to "synthetic", "installation_id" to "synthetic-phone",
            "calibration_id" to "synthetic-cal", "model_id" to "synthetic-model", "acquisition_id" to "synthetic-650ms",
            "presentation_id" to "synthetic-article-surface", "clock_domain_id" to "synthetic-clock",
            "screen_width_px" to 1080, "screen_height_px" to 2400, "rotation_degrees" to 0,
            "coordinate_stream" to "calibrated_raw_v1")
        var clock = 1000.0
        fun gaze(writer: StudyPackageWriter, provider: String, delivery: Long, at: Double, x: Double, y: Double) {
            clock = at + 100
            writer.record("gaze_observation", StudyJson.obj("provider_epoch_id" to provider, "delivery_id" to delivery,
                "capture_ms" to at, "delivery_ms" to clock, "reason" to "coordinate", "raw_x_px" to x,
                "raw_y_px" to y, "selected_x_px" to -123.0, "selected_y_px" to -123.0,
                "timeout_watchdog" to false, "context_allowed" to true, "context_epoch_id" to provider,
                "coordinate_context" to context), at)
        }
        val reference = StudyPackageWriter(root, identity("source-session"), { clock }, packageId = "source",
            queueCapacity = 4096)
        reference.record("session_start", StudyJson.obj("phase" to "reading_baseline", "start_ms" to 1000.0,
            "metadata" to StudyBaselineProtocol.declaration(attempt, "synthetic", "synthetic-cal")), clock)
        for (i in 0..1950) {
            val phase = 2 * PI * i / 30.0
            gaze(reference, "source-provider", i + 1L, 1000.0 + i * 1000.0 / 30,
                400 + 100 * cos(phase), 800 + 200 * sin(phase))
        }
        reference.record("session_end", StudyJson.obj("end_ms" to 66000.0), 66000.0)
        reference.record("shared_baseline_result", StudyJson.obj("baseline_attempt_id" to attempt, "completed" to true,
            "coordinate_reference_status" to StudyBaselineProtocol.PENDING, "rdf_status" to "INSUFFICIENT_DURATION",
            "start_ms" to 1000.0, "end_ms" to 66000.0), clock)
        reference.finish("synthetic_reference", 66000.0, false)
        assertTrue(reference.awaitClosed(10_000))
        clock = 67000.0
        val evaluation = StudyPackageWriter(root, identity("evaluation-session"), { clock }, packageId = "evaluation")
        evaluation.record("session_start", StudyJson.obj("phase" to "reading", "start_ms" to 67000.0,
            "metadata" to StudyArticleReading().nextSegment("synthetic-article", "synthetic-cal", true)), clock)
        val link = SharedReadingBaseline.Entry("source", "source-session", attempt, "synthetic", "synthetic-phone",
            "synthetic-cal", "synthetic-clock", 1000.0, 66000.0)
        evaluation.record("coordinate_reference", StudyJson.obj("reference" to link.fields()), clock)
        evaluation.record("rdf_reference", StudyJson.obj("reference" to null), clock)
        listOf(400.0 to 800.0, 500.0 to 800.0, 500.0 to 1000.0).forEachIndexed { i, (x, y) ->
            gaze(evaluation, "evaluation-provider", i + 1L, 67000.0 + i * 100, x, y)
        }
        evaluation.record("rdf_tick", StudyJson.obj("feature_vector" to StudyJson.obj(
            "window_start_ms" to 67000.0, "window_end_ms" to 67200.0), "score" to null), clock)
        evaluation.record("session_end", StudyJson.obj("end_ms" to 67200.0), 67200.0)
        evaluation.finish("synthetic_evaluation", 67200.0, false)
        assertTrue(evaluation.awaitClosed(10_000))
        assertEquals(0L, reference.health().getLong("lost_records"))
        assertEquals(0L, evaluation.health().getLong("lost_records"))
        File(parent, "latest.json").writeText(JSONObject().put("root", root.absolutePath).toString())
    }
}
