package com.newsmead.research.study

import com.newsmead.gaze.aid.ReadingAidAction
import com.newsmead.gaze.aid.ReadingAidAnchor
import com.newsmead.gaze.aid.ReadingAidContext
import com.newsmead.gaze.aid.ReadingAidContinuationEvidence
import com.newsmead.gaze.aid.ReadingAidDecision
import com.newsmead.gaze.aid.ReadingAidGuideEvidence
import com.newsmead.gaze.aid.ReadingGuideRange
import com.newsmead.gaze.aid.ReadingLocationUncertainty
import com.newsmead.gaze.aid.ReadingSupportedRowBand
import com.newsmead.gaze.aid.ReadingSupportedVisit
import com.newsmead.gaze.aid.ReadingThreeLineGuideEvidence
import com.newsmead.gaze.reading.ReadingGeometry
import com.newsmead.gaze.reading.ReadingLine
import com.newsmead.gaze.reading.ReadingMotionCompensation
import com.newsmead.gaze.reading.ReadingRect
import com.newsmead.gaze.reading.ReadingSample
import com.newsmead.gaze.scanpath.ScanpathCandidateEvidence
import com.newsmead.gaze.scanpath.ScanpathEvent
import com.newsmead.gaze.scanpath.ScanpathFixation
import com.newsmead.gaze.scanpath.ScanpathGeometry
import com.newsmead.gaze.scanpath.ScanpathLine
import com.newsmead.gaze.scanpath.ScanpathMotionCompensation
import com.newsmead.gaze.scanpath.ScanpathRect
import com.newsmead.gaze.scanpath.ScanpathSample
import org.json.JSONArray
import org.json.JSONObject
import java.io.Writer

/**
 * Direct streaming JSON encoder for StudyPackageWriter.
 * Emits the exact same numerical/text schema as StudyJson + JSONObject without allocating
 * intermediate JSONObject/JSONArray/String trees or using reflection on hot gaze structs.
 */
internal class StudyJsonWriter(private val out: Writer) {
    fun beginObject() { out.write('{'.code) }
    fun endObject() { out.write('}'.code) }
    fun beginArray() { out.write('['.code) }
    fun endArray() { out.write(']'.code) }
    fun comma() { out.write(','.code) }

    fun fieldName(name: String, first: Boolean = false) {
        if (!first) out.write(','.code)
        writeString(name)
        out.write(':'.code)
    }

    fun field(name: String, value: Any?, first: Boolean = false) {
        fieldName(name, first)
        writeValue(value)
    }

    fun fieldString(name: String, value: String?, first: Boolean = false) {
        fieldName(name, first)
        if (value == null) out.write("null") else writeString(value)
    }

    fun fieldInt(name: String, value: Int, first: Boolean = false) {
        fieldName(name, first)
        out.write(value.toString())
    }

    fun fieldIntOrNull(name: String, value: Int?, first: Boolean = false) {
        fieldName(name, first)
        if (value == null) out.write("null") else out.write(value.toString())
    }

    fun fieldLong(name: String, value: Long, first: Boolean = false) {
        fieldName(name, first)
        out.write(value.toString())
    }

    fun fieldLongOrNull(name: String, value: Long?, first: Boolean = false) {
        fieldName(name, first)
        if (value == null) out.write("null") else out.write(value.toString())
    }

    fun fieldBoolean(name: String, value: Boolean, first: Boolean = false) {
        fieldName(name, first)
        out.write(if (value) "true" else "false")
    }

    fun fieldDouble(name: String, value: Double, first: Boolean = false) {
        fieldName(name, first)
        writeDouble(value)
    }

    fun fieldDoubleOrNull(name: String, value: Double?, first: Boolean = false) {
        fieldName(name, first)
        if (value == null) out.write("null") else writeDouble(value)
    }

    fun fieldFloatOrNull(name: String, value: Float?, first: Boolean = false) {
        fieldName(name, first)
        if (value == null) out.write("null") else writeFloat(value)
    }

    fun writeString(value: String) {
        var simple = true
        for (i in 0 until value.length) {
            val c = value[i]
            if (c < ' ' || c == '"' || c == '\\' || c == '/' || c >= '\u007f') {
                simple = false
                break
            }
        }
        if (simple) {
            out.write('"'.code)
            out.write(value)
            out.write('"'.code)
        } else {
            out.write(JSONObject.quote(value))
        }
    }

    fun writeDouble(value: Double) {
        if (!value.isFinite()) {
            out.write("null")
        } else {
            out.write(JSONObject.numberToString(value))
        }
    }

    fun writeFloat(value: Float) {
        if (!value.isFinite()) {
            out.write("null")
        } else {
            out.write(JSONObject.numberToString(value.toDouble()))
        }
    }

    fun writeJsonObject(obj: JSONObject) {
        out.write('{'.code)
        val keys = obj.keys()
        var first = true
        while (keys.hasNext()) {
            val key = keys.next()
            if (!first) out.write(','.code)
            first = false
            writeString(key)
            out.write(':'.code)
            writeValue(obj.get(key))
        }
        out.write('}'.code)
    }

    fun writeJsonArray(array: JSONArray) {
        out.write('['.code)
        for (i in 0 until array.length()) {
            if (i > 0) out.write(','.code)
            writeValue(array.get(i))
        }
        out.write(']'.code)
    }

    fun writeValue(input: Any?) {
        when (input) {
            null, JSONObject.NULL -> out.write("null")
            is String -> writeString(input)
            is Boolean -> out.write(if (input) "true" else "false")
            is Int -> out.write(input.toString())
            is Long -> out.write(input.toString())
            is Short, is Byte -> out.write(input.toString())
            is Double -> writeDouble(input)
            is Float -> writeFloat(input)
            is Number -> out.write(JSONObject.numberToString(input))
            is Enum<*> -> writeString(input.name)
            is JSONObject -> writeJsonObject(input)
            is JSONArray -> writeJsonArray(input)
            is Map<*, *> -> {
                out.write('{'.code)
                var first = true
                for ((k, v) in input) {
                    if (!first) out.write(','.code)
                    first = false
                    writeString(k.toString())
                    out.write(':'.code)
                    writeValue(v)
                }
                out.write('}'.code)
            }
            is Pair<*, *> -> {
                out.write('['.code)
                writeValue(input.first)
                out.write(','.code)
                writeValue(input.second)
                out.write(']'.code)
            }
            is List<*> -> {
                out.write('['.code)
                for (i in 0 until input.size) {
                    if (i > 0) out.write(','.code)
                    writeValue(input[i])
                }
                out.write(']'.code)
            }
            is Iterable<*> -> {
                out.write('['.code)
                var first = true
                for (item in input) {
                    if (!first) out.write(','.code)
                    first = false
                    writeValue(item)
                }
                out.write(']'.code)
            }
            is Array<*> -> {
                out.write('['.code)
                for (i in input.indices) {
                    if (i > 0) out.write(','.code)
                    writeValue(input[i])
                }
                out.write(']'.code)
            }
            is DoubleArray -> {
                out.write('['.code)
                for (i in input.indices) {
                    if (i > 0) out.write(','.code)
                    writeDouble(input[i])
                }
                out.write(']'.code)
            }
            is FloatArray -> {
                out.write('['.code)
                for (i in input.indices) {
                    if (i > 0) out.write(','.code)
                    writeFloat(input[i])
                }
                out.write(']'.code)
            }
            is IntArray -> {
                out.write('['.code)
                for (i in input.indices) {
                    if (i > 0) out.write(','.code)
                    out.write(input[i].toString())
                }
                out.write(']'.code)
            }
            is LongArray -> {
                out.write('['.code)
                for (i in input.indices) {
                    if (i > 0) out.write(','.code)
                    out.write(input[i].toString())
                }
                out.write(']'.code)
            }
            is ScanpathLine -> writeScanpathLine(input)
            is ScanpathRect -> writeScanpathRect(input)
            is ScanpathMotionCompensation -> writeScanpathMotionCompensation(input)
            is ScanpathSample -> writeScanpathSample(input)
            is ScanpathGeometry -> writeScanpathGeometry(input)
            is ScanpathCandidateEvidence -> writeScanpathCandidateEvidence(input)
            is ScanpathFixation -> writeScanpathFixation(input)
            is ScanpathEvent -> writeScanpathEvent(input)
            is ReadingLine -> writeReadingLine(input)
            is ReadingRect -> writeReadingRect(input)
            is ReadingMotionCompensation -> writeReadingMotionCompensation(input)
            is ReadingSample -> writeReadingSample(input)
            is ReadingGeometry -> writeReadingGeometry(input)
            is ReadingAidContinuationEvidence -> writeContinuationEvidence(input)
            is ReadingSupportedRowBand -> writeSupportedRowBand(input)
            is ReadingSupportedVisit -> writeSupportedVisit(input)
            is ReadingAidAnchor -> writeAidAnchor(input)
            is ReadingGuideRange -> writeGuideRange(input)
            is ReadingThreeLineGuideEvidence -> writeThreeLineGuideEvidence(input)
            is ReadingAidGuideEvidence -> writeGuideEvidence(input)
            is ReadingLocationUncertainty -> writeLocationUncertainty(input)
            is ReadingAidContext -> writeAidContext(input)
            is ReadingAidAction -> writeAidAction(input)
            is ReadingAidDecision -> writeAidDecision(input)
            else -> {
                val type = input.javaClass
                require(type.name.startsWith("com.newsmead.gaze.")) { "Unsupported study value ${type.name}" }
                out.write('{'.code)
                val fields = StudyJson.fieldsFor(type)
                for (i in fields.indices) {
                    val member = fields[i]
                    if (i > 0) out.write(','.code)
                    writeString(member.name)
                    out.write(':'.code)
                    writeValue(member.field.get(input))
                }
                out.write('}'.code)
            }
        }
    }

    fun writeContextWithGeometryRef(context: ReadingAidContext, producer: String, revision: Long?) {
        out.write('{'.code)
        fieldLong("generation", context.generation, first = true)
        fieldString("geometry_snapshot_id", context.geometrySnapshotId)
        fieldInt("line_count", context.lineCount)
        fieldIntOrNull("target_line", context.targetLine)
        fieldIntOrNull("target_word_start_utf16", context.targetWordStartUtf16)
        fieldIntOrNull("target_word_end_utf16", context.targetWordEndUtf16)
        fieldDouble("now_ms", context.nowMs)
        fieldDoubleOrNull("capture_ms", context.captureMs)
        fieldDoubleOrNull("evidence_available_ms", context.evidenceAvailableMs)
        fieldBoolean("valid_live_input", context.validLiveInput)
        fieldDoubleOrNull("target_x", context.targetX)
        field("raw_sample", context.rawSample)
        fieldBoolean("bookmark_input_allowed", context.bookmarkInputAllowed)
        field("guide_range", context.guideRange)
        fieldBoolean("focus_input_allowed", context.focusInputAllowed)
        fieldDouble("focus_text_size_dp", context.focusTextSizeDp)
        field("location_uncertainty", context.locationUncertainty)
        field("target_word_bounds", context.targetWordBounds)
        fieldName("geometry_ref")
        val geometry = context.geometry
        if (geometry == null) {
            out.write("null")
        } else {
            out.write('{'.code)
            fieldString("producer", producer, first = true)
            fieldLong("generation", context.generation)
            fieldString("geometry_snapshot_id", geometry.snapshotId)
            fieldLongOrNull("layout_revision", revision)
            out.write('}'.code)
        }
        out.write('}'.code)
    }

    private fun writeScanpathRect(v: ScanpathRect) {
        out.write('{'.code)
        fieldDouble("left", v.left, first = true)
        fieldDouble("top", v.top)
        fieldDouble("right", v.right)
        fieldDouble("bottom", v.bottom)
        out.write('}'.code)
    }

    private fun writeScanpathLine(v: ScanpathLine) {
        out.write('{'.code)
        fieldInt("start_utf16", v.startUtf16, first = true)
        fieldInt("end_utf16", v.endUtf16)
        fieldDouble("left", v.left)
        fieldDouble("right", v.right)
        fieldDouble("top", v.top)
        fieldDouble("bottom", v.bottom)
        out.write('}'.code)
    }

    private fun writeScanpathMotionCompensation(v: ScanpathMotionCompensation) {
        out.write('{'.code)
        fieldDouble("geometry_time_ms", v.geometryTimeMs, first = true)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldDouble("text_origin_x", v.textOriginX)
        fieldDouble("text_origin_y", v.textOriginY)
        fieldDouble("content_x", v.contentX)
        fieldDouble("content_y", v.contentY)
        fieldString("mode_id", v.modeId)
        out.write('}'.code)
    }

    private fun writeScanpathSample(v: ScanpathSample) {
        out.write('{'.code)
        fieldDoubleOrNull("capture_ms", v.captureMs, first = true)
        fieldDoubleOrNull("delivery_ms", v.deliveryMs)
        fieldDoubleOrNull("raw_x", v.rawX)
        fieldDoubleOrNull("raw_y", v.rawY)
        fieldIntOrNull("line", v.line)
        fieldIntOrNull("word_start_utf16", v.wordStartUtf16)
        fieldIntOrNull("word_end_utf16", v.wordEndUtf16)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldBoolean("eligible", v.eligible)
        fieldString("exclusion", v.exclusion)
        fieldDoubleOrNull("display_x", v.displayX)
        fieldDoubleOrNull("display_y", v.displayY)
        fieldString("coordinate_stream", v.coordinateStream.name)
        fieldName("motion_compensation")
        v.motionCompensation?.let(::writeScanpathMotionCompensation) ?: out.write("null")
        out.write('}'.code)
    }

    private fun writeScanpathGeometry(v: ScanpathGeometry) {
        out.write('{'.code)
        fieldString("snapshot_id", v.snapshotId, first = true)
        fieldDouble("text_origin_x", v.textOriginX)
        fieldDouble("text_origin_y", v.textOriginY)
        fieldName("visible_rect_screen")
        writeScanpathRect(v.visibleRectScreen)
        fieldDouble("line_height_px", v.lineHeightPx)
        fieldName("lines")
        out.write('['.code)
        val lines = v.lines
        for (i in lines.indices) {
            if (i > 0) out.write(','.code)
            writeScanpathLine(lines[i])
        }
        out.write(']'.code)
        fieldString("text_direction", v.textDirection?.name)
        out.write('}'.code)
    }

    private fun writeScanpathCandidateEvidence(v: ScanpathCandidateEvidence) {
        out.write('{'.code)
        fieldString("candidate_id", v.candidateId, first = true)
        fieldInt("segment", v.segment)
        fieldDouble("start_ms", v.startMs)
        fieldDouble("end_ms", v.endMs)
        fieldDouble("duration_ms", v.durationMs)
        fieldInt("sample_count", v.sampleCount)
        fieldIntOrNull("word_start_utf16", v.wordStartUtf16)
        fieldIntOrNull("word_end_utf16", v.wordEndUtf16)
        fieldIntOrNull("line", v.line)
        fieldDouble("word_sample_agreement", v.wordSampleAgreement)
        fieldDouble("line_sample_agreement", v.lineSampleAgreement)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldDoubleOrNull("evidence_available_ms", v.evidenceAvailableMs)
        fieldString("evidence_time_basis", v.evidenceTimeBasis)
        out.write('}'.code)
    }

    private fun writeScanpathFixation(v: ScanpathFixation) {
        out.write('{'.code)
        fieldInt("index", v.index, first = true)
        fieldInt("segment", v.segment)
        fieldDouble("start_ms", v.startMs)
        fieldDouble("end_ms", v.endMs)
        fieldDouble("duration_ms", v.durationMs)
        fieldInt("sample_count", v.sampleCount)
        fieldDouble("x", v.x)
        fieldDouble("y", v.y)
        fieldDouble("dispersion_px", v.dispersionPx)
        fieldIntOrNull("word_start_utf16", v.wordStartUtf16)
        fieldIntOrNull("word_end_utf16", v.wordEndUtf16)
        fieldDouble("word_sample_agreement", v.wordSampleAgreement)
        fieldIntOrNull("line", v.line)
        fieldDouble("line_sample_agreement", v.lineSampleAgreement)
        fieldBoolean("left_censored", v.leftCensored)
        fieldBoolean("right_censored", v.rightCensored)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldDoubleOrNull("evidence_available_ms", v.evidenceAvailableMs)
        fieldString("evidence_time_basis", v.evidenceTimeBasis)
        out.write('}'.code)
    }

    private fun writeScanpathEvent(v: ScanpathEvent) {
        out.write('{'.code)
        fieldString("event_id", v.eventId, first = true)
        fieldString("pattern_id", v.patternId)
        fieldString("resolution", v.resolution)
        field("fixation_indices", v.fixationIndices)
        fieldDoubleOrNull("start_ms", v.startMs)
        fieldDoubleOrNull("end_ms", v.endMs)
        fieldDoubleOrNull("evidence_available_ms", v.evidenceAvailableMs)
        fieldString("evidence_time_basis", v.evidenceTimeBasis)
        field("metrics", v.metrics)
        field("limitations", v.limitations)
        out.write('}'.code)
    }

    private fun writeReadingRect(v: ReadingRect) {
        out.write('{'.code)
        fieldDouble("left", v.left, first = true)
        fieldDouble("top", v.top)
        fieldDouble("right", v.right)
        fieldDouble("bottom", v.bottom)
        out.write('}'.code)
    }

    private fun writeReadingLine(v: ReadingLine) {
        out.write('{'.code)
        fieldInt("start_utf16", v.startUtf16, first = true)
        fieldInt("end_utf16", v.endUtf16)
        fieldDouble("left", v.left)
        fieldDouble("right", v.right)
        fieldDouble("top", v.top)
        fieldDouble("bottom", v.bottom)
        out.write('}'.code)
    }

    private fun writeReadingMotionCompensation(v: ReadingMotionCompensation) {
        out.write('{'.code)
        fieldDouble("geometry_time_ms", v.geometryTimeMs, first = true)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldDouble("text_origin_x", v.textOriginX)
        fieldDouble("text_origin_y", v.textOriginY)
        fieldDouble("content_x", v.contentX)
        fieldDouble("content_y", v.contentY)
        fieldString("mode_id", v.modeId)
        out.write('}'.code)
    }

    private fun writeReadingSample(v: ReadingSample) {
        out.write('{'.code)
        fieldDoubleOrNull("capture_ms", v.captureMs, first = true)
        fieldDoubleOrNull("delivery_ms", v.deliveryMs)
        fieldDoubleOrNull("raw_x", v.rawX)
        fieldDoubleOrNull("raw_y", v.rawY)
        fieldIntOrNull("line", v.line)
        fieldIntOrNull("word_start_utf16", v.wordStartUtf16)
        fieldIntOrNull("word_end_utf16", v.wordEndUtf16)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldBoolean("eligible", v.eligible)
        fieldString("exclusion", v.exclusion)
        fieldDoubleOrNull("display_x", v.displayX)
        fieldDoubleOrNull("display_y", v.displayY)
        fieldString("coordinate_stream", v.coordinateStream.name)
        fieldName("motion_compensation")
        v.motionCompensation?.let(::writeReadingMotionCompensation) ?: out.write("null")
        out.write('}'.code)
    }

    private fun writeReadingGeometry(v: ReadingGeometry) {
        out.write('{'.code)
        fieldString("snapshot_id", v.snapshotId, first = true)
        fieldDouble("text_origin_x", v.textOriginX)
        fieldDouble("text_origin_y", v.textOriginY)
        fieldName("visible_rect_screen")
        writeReadingRect(v.visibleRectScreen)
        fieldDouble("line_height_px", v.lineHeightPx)
        fieldName("lines")
        out.write('['.code)
        val lines = v.lines
        for (i in lines.indices) {
            if (i > 0) out.write(','.code)
            writeReadingLine(lines[i])
        }
        out.write(']'.code)
        fieldString("text_direction", v.textDirection?.name)
        out.write('}'.code)
    }

    private fun writeContinuationEvidence(v: ReadingAidContinuationEvidence) {
        out.write('{'.code)
        fieldString("evidence_id", v.evidenceId, first = true)
        fieldInt("segment", v.segment)
        fieldInt("line", v.line)
        fieldDouble("first_capture_ms", v.firstCaptureMs)
        fieldDouble("last_capture_ms", v.lastCaptureMs)
        fieldDouble("evidence_available_ms", v.evidenceAvailableMs)
        fieldDoubleOrNull("first_confirmed_capture_ms", v.firstConfirmedCaptureMs)
        fieldDoubleOrNull("first_confirmed_delivery_ms", v.firstConfirmedDeliveryMs)
        out.write('}'.code)
    }

    private fun writeSupportedRowBand(v: ReadingSupportedRowBand) {
        out.write('{'.code)
        fieldInt("first_line", v.firstLine, first = true)
        fieldInt("last_line", v.lastLine)
        fieldInt("representative_line", v.representativeLine)
        fieldBoolean("measured", v.measured)
        fieldString("provenance", v.provenance)
        fieldDoubleOrNull("interval_top_px", v.intervalTopPx)
        fieldDoubleOrNull("interval_bottom_px", v.intervalBottomPx)
        out.write('}'.code)
    }

    private fun writeSupportedVisit(v: ReadingSupportedVisit) {
        out.write('{'.code)
        fieldString("id", v.id, first = true)
        fieldInt("segment", v.segment)
        fieldLong("generation", v.generation)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldName("band")
        writeSupportedRowBand(v.band)
        fieldDouble("first_capture_ms", v.firstCaptureMs)
        fieldDouble("last_capture_ms", v.lastCaptureMs)
        fieldDouble("evidence_available_ms", v.evidenceAvailableMs)
        fieldDouble("first_confirmed_capture_ms", v.firstConfirmedCaptureMs)
        fieldDouble("first_confirmed_delivery_ms", v.firstConfirmedDeliveryMs)
        out.write('}'.code)
    }

    private fun writeAidAnchor(v: ReadingAidAnchor) {
        out.write('{'.code)
        fieldLong("generation", v.generation, first = true)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldInt("line", v.line)
        fieldIntOrNull("word_start_utf16", v.wordStartUtf16)
        fieldIntOrNull("word_end_utf16", v.wordEndUtf16)
        out.write('}'.code)
    }

    private fun writeGuideRange(v: ReadingGuideRange) {
        out.write('{'.code)
        fieldInt("start_utf16", v.startUtf16, first = true)
        fieldInt("end_utf16", v.endUtf16)
        fieldString("unit", v.unit.name)
        out.write('}'.code)
    }

    private fun writeThreeLineGuideEvidence(v: ReadingThreeLineGuideEvidence) {
        out.write('{'.code)
        fieldString("direction", v.direction.name, first = true)
        fieldDouble("above_opacity", v.aboveOpacity)
        fieldDouble("below_opacity", v.belowOpacity)
        fieldDouble("transition_ms", v.transitionMs)
        field("direction_support", v.directionSupport)
        out.write('}'.code)
    }

    private fun writeGuideEvidence(v: ReadingAidGuideEvidence) {
        out.write('{'.code)
        fieldInt("first_line", v.firstLine, first = true)
        fieldInt("last_line", v.lastLine)
        fieldString("uncertainty_provenance", v.uncertaintyProvenance)
        fieldBoolean("measured_uncertainty", v.measuredUncertainty)
        fieldDouble("last_supported_delivery_ms", v.lastSupportedDeliveryMs)
        fieldDouble("presentation_started_ms", v.presentationStartedMs)
        fieldDouble("opacity", v.opacity)
        field("search_evidence_ids", v.searchEvidenceIds)
        fieldDouble("entry_ms", v.entryMs)
        fieldDouble("loss_grace_ms", v.lossGraceMs)
        fieldDouble("exit_ms", v.exitMs)
        fieldName("three_line")
        v.threeLine?.let(::writeThreeLineGuideEvidence) ?: out.write("null")
        out.write('}'.code)
    }

    private fun writeLocationUncertainty(v: ReadingLocationUncertainty) {
        out.write('{'.code)
        fieldDouble("horizontal_radius_px", v.horizontalRadiusPx, first = true)
        fieldDouble("vertical_radius_px", v.verticalRadiusPx)
        fieldString("provenance", v.provenance)
        out.write('}'.code)
    }

    private fun writeAidContext(v: ReadingAidContext) {
        out.write('{'.code)
        fieldLong("generation", v.generation, first = true)
        fieldString("geometry_snapshot_id", v.geometrySnapshotId)
        fieldInt("line_count", v.lineCount)
        fieldIntOrNull("target_line", v.targetLine)
        fieldIntOrNull("target_word_start_utf16", v.targetWordStartUtf16)
        fieldIntOrNull("target_word_end_utf16", v.targetWordEndUtf16)
        fieldDouble("now_ms", v.nowMs)
        fieldDoubleOrNull("capture_ms", v.captureMs)
        fieldDoubleOrNull("evidence_available_ms", v.evidenceAvailableMs)
        fieldBoolean("valid_live_input", v.validLiveInput)
        fieldName("geometry")
        v.geometry?.let(::writeScanpathGeometry) ?: out.write("null")
        fieldDoubleOrNull("target_x", v.targetX)
        fieldName("raw_sample")
        v.rawSample?.let(::writeScanpathSample) ?: out.write("null")
        fieldBoolean("bookmark_input_allowed", v.bookmarkInputAllowed)
        fieldName("guide_range")
        v.guideRange?.let(::writeGuideRange) ?: out.write("null")
        fieldBoolean("focus_input_allowed", v.focusInputAllowed)
        fieldDouble("focus_text_size_dp", v.focusTextSizeDp)
        fieldName("location_uncertainty")
        v.locationUncertainty?.let(::writeLocationUncertainty) ?: out.write("null")
        fieldName("target_word_bounds")
        v.targetWordBounds?.let(::writeScanpathRect) ?: out.write("null")
        out.write('}'.code)
    }

    private fun writeAidAction(v: ReadingAidAction) {
        out.write('{'.code)
        fieldString("id", v.id, first = true)
        fieldString("aid_type", v.aidType.name)
        fieldString("aid_form_id", v.aidFormId)
        fieldString("condition_id", v.conditionId)
        fieldName("anchor")
        writeAidAnchor(v.anchor)
        fieldDouble("strength", v.strength)
        fieldDoubleOrNull("magnification_scale", v.magnificationScale)
        fieldIntOrNull("context_radius_lines", v.contextRadiusLines)
        fieldDouble("onset_ms", v.onsetMs)
        fieldDouble("expires_ms", v.expiresMs)
        field("event_ids", v.eventIds)
        field("evidence_ids", v.evidenceIds)
        fieldDouble("first_capture_ms", v.firstCaptureMs)
        fieldDouble("last_capture_ms", v.lastCaptureMs)
        fieldDouble("evidence_available_ms", v.evidenceAvailableMs)
        fieldString("rule_id", v.ruleId)
        field("source_ids", v.sourceIds)
        fieldString("evidence_relationship", v.evidenceRelationship)
        fieldBoolean("experimental", v.experimental)
        fieldString("config_id", v.configId)
        field("continuation_evidence", v.continuationEvidence)
        field("bookmark_evidence", v.bookmarkEvidence)
        fieldName("guide_range")
        v.guideRange?.let(::writeGuideRange) ?: out.write("null")
        field("focus_evidence", v.focusEvidence)
        fieldString("focus_style", v.focusStyle?.name)
        fieldName("guide_evidence")
        v.guideEvidence?.let(::writeGuideEvidence) ?: out.write("null")
        fieldString("word_confidence", v.wordConfidence)
        out.write('}'.code)
    }

    private fun writeAidDecision(v: ReadingAidDecision) {
        out.write('{'.code)
        fieldString("status", v.status.name, first = true)
        fieldName("action")
        v.action?.let(::writeAidAction) ?: out.write("null")
        fieldString("reason", v.reason)
        fieldDouble("now_ms", v.nowMs)
        field("event_ids", v.eventIds)
        fieldString("rule_id", v.ruleId)
        field("source_ids", v.sourceIds)
        out.write('}'.code)
    }
}
