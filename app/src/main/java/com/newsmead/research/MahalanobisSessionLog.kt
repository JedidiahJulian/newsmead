package com.newsmead.research

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.newsmead.gaze.mahalanobis.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Private feature-only journal. No images, raw gaze, uploads or invented labels. */
class MahalanobisSessionLog(context: Context, kind: String) {
    val file = File(context.noBackupFilesDir, "mahalanobis_${kind}_${UUID.randomUUID()}.jsonl")
    private val writer = file.bufferedWriter()
    private val queue = Executors.newSingleThreadExecutor()
    @Volatile private var failure: Exception? = null
    private var closed = false
    init { write(JSONObject().put("type", "session_start").put("schemaVersion", 1)
        .put("timeBase", "elapsed_realtime_ms").put("researchSessionId", ResearchSession.sessionId)) }
    fun write(record: JSONObject) {
        if (closed) return
        val line = record.toString()
        queue.execute { try { writer.appendLine(line); writer.flush() } catch (e: Exception) { failure = e } }
    }
    fun close() {
        if (closed) return
        closed = true
        queue.execute { writer.close() }
        queue.shutdown()
    }
    fun share(context: Context): Intent {
        if (!closed) queue.submit {}.get(5, TimeUnit.SECONDS)
        else check(queue.awaitTermination(5, TimeUnit.SECONDS)) { "Log is still closing" }
        failure?.let { throw it }
        val copy = File(context.cacheDir, "research-export/${file.name}")
        copy.parentFile!!.mkdirs(); file.copyTo(copy, overwrite = true)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".research.fileprovider", copy)
        return Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/x-ndjson")
            .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Export detector log")
    }
}

object MahalanobisJson {
    fun config(c: MahalanobisConfig) = JSONObject()
        .put("windowMs", c.windowMs).put("updateIntervalMs", c.updateIntervalMs)
        .put("maxWindowOverhangMs", c.maxWindowOverhangMs)
        .put("minimumBaselineDurationMs", c.minimumBaselineDurationMs).put("minimumRetainedWindows", c.minimumRetainedWindows)
        .put("lambda", c.lambda).put("sampling", c.sampling.name).put("thinningIntervalMs", c.thinningIntervalMs)
        .put("percentile", c.percentile.name).put("customThresholdMd2", c.customThresholdMd2 ?: JSONObject.NULL)
        .put("persistenceMs", c.persistenceMs).put("recoveryMs", c.recoveryMs)
        .put("recoveryThresholdRatio", c.recoveryThresholdRatio).put("maxGapMs", c.maxGapMs)
        .put("nearTolerance", c.nearTolerance).put("minimumValidFraction", c.minimumValidFraction)
    fun matrix(m: Matrix3) = JSONArray((0..2).map { row -> (0..2).map { col -> m[row, col] } })
    fun values(v: RdfValues) = JSONArray((0..2).map { v[it] })
    fun vector(v: RdfFeatureVector, type: String, c: MahalanobisConfig) = JSONObject()
        .put("type", type).put("timestamp", v.timestampMs).put("windowStart", v.windowStartMs).put("windowEnd", v.windowEndMs)
        .put("R", finite(v.values.regressionRatePerMinute)).put("D", finite(v.values.dwellFraction))
        .put("F", finite(v.values.fixationRatePerMinute)).put("qualityValid", v.isUsable(c))
        .put("qualityStatus", v.quality.name).put("validFraction", finite(v.validFraction))
        .put("validSampleCount", v.validSampleCount ?: JSONObject.NULL).put("totalSampleCount", v.totalSampleCount ?: JSONObject.NULL)
    fun profile(p: FrozenMahalanobisProfile) = JSONObject().put("type", "baseline_profile")
        .put("config", config(p.config)).put("baselineValid", true).put("mu", values(p.mean))
        .put("covariance", matrix(p.covariance)).put("regularizedCovariance", matrix(p.regularizedCovariance))
        .put("lambda", p.config.lambda).put("regularizationApplied", p.config.lambda > 0)
        .put("matrixDiagnostic", JSONObject().put("kind", "cholesky_diagonal_ratio").put("value", p.stability.choleskyDiagonalRatio))
        .put("baselineMD2", JSONArray(p.baselineMd2)).put("P90", p.p90Md2).put("P95", p.p95Md2).put("P99", p.p99Md2)
        .put("activeThreshold", p.thresholdMd2).put("thresholdMode", p.config.percentile.name)
        .put("totalBaselineWindows", p.sampling.receivedCount).put("validBaselineWindows", p.sampling.validCount)
        .put("retainedBaselineWindows", p.sampling.retainedCount).put("baselineSamplingMode", p.sampling.policy.name)
        .put("independentObservations", false).put("referenceDistribution", "in_sample")
    fun update(score: MahalanobisScore, state: TemporalResult, p: FrozenMahalanobisProfile?, scaffold: String,
        c: MahalanobisConfig) = vector(score.vector, "scoring_update", c)
        .put("mu_R", p?.mean?.regressionRatePerMinute ?: JSONObject.NULL)
        .put("mu_D", p?.mean?.dwellFraction ?: JSONObject.NULL).put("mu_F", p?.mean?.fixationRatePerMinute ?: JSONObject.NULL)
        .put("delta_R", score.delta?.regressionRatePerMinute ?: JSONObject.NULL)
        .put("delta_D", score.delta?.dwellFraction ?: JSONObject.NULL).put("delta_F", score.delta?.fixationRatePerMinute ?: JSONObject.NULL)
        .put("directions", score.directions?.let { JSONArray(it.map(Direction::name)) } ?: JSONObject.NULL)
        .put("MD2", score.md2 ?: JSONObject.NULL).put("MD", score.md ?: JSONObject.NULL)
        .put("scoreStatus", score.status.name).put("activeThreshold", score.thresholdMd2 ?: JSONObject.NULL)
        .put("thresholdMode", c.percentile.name).put("candidateDeviation", state.high ?: JSONObject.NULL)
        .put("confirmedInstability", state.active).put("baselineValid", p != null)
        .put("persistenceState", state.status.name).put("scaffoldState", scaffold)
    fun episode(e: HighEpisode) = JSONObject().put("type", "candidate_episode").put("episodeStart", e.startMs)
        .put("episodeEnd", e.endMs).put("duration", e.durationMs).put("peakMD2", e.peakMd2)
        .put("meanMD2", e.meanMd2).put("numberOfUpdates", e.sampleCount).put("thresholdUsed", e.thresholdMd2)
    private fun finite(value: Double): Any = if (value.isFinite()) value else JSONObject.NULL
}
