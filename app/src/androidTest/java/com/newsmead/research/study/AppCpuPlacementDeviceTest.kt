package com.newsmead.research.study

import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.math.BigInteger
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Opt-in, camera-free affinity query in the target app's own UID/SELinux context. */
@RunWith(AndroidJUnit4::class)
class AppCpuPlacementDeviceTest {
    @Test fun queryOwnProcessCpuPlacement() {
        assumeTrue(InstrumentationRegistry.getArguments()
            .getString("newsmeadCpuPlacementProbe") == "true")
        val pid = Process.myPid()
        val summary = JSONObject().put("schema", "newsmead_cpu_placement_probe_v1")
            .put("pid", pid).put("uid", Process.myUid())
        fun readMetadata(path: String, transform: (String) -> String): Any = try {
            transform(File(path).readText())
        } catch (_: Exception) { JSONObject.NULL }
        summary.put("self_status", readMetadata("/proc/self/status") { text ->
            text.lineSequence().filter { it.startsWith("Name:") || it.startsWith("Cpus_allowed_list:") }
                .joinToString("\n")
        }).put("selinux_context", readMetadata("/proc/self/attr/current") {
            it.trimEnd('\u0000', '\n', '\r').take(256)
        })
        val result = AppCpuPlacement.command(null)
        result.json.keys().forEach { key -> summary.put(key, result.json.get(key)) }
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "newsmead-cpu-placement-probe.json").writeText(summary.toString(2))
        assertFalse("Own-process affinity query timed out; see cache summary", result.json.getBoolean("timed_out"))
        assertEquals("Own-process affinity query failed; see cache summary", true, result.success)
    }
}

/** Test-only, self-process commands. A null mask is strictly a read-only query. */
internal object AppCpuPlacement {
    data class Result(val json: JSONObject, val success: Boolean)

    fun command(mask: String?, tid: Int? = null): Result {
        if (tid != null) {
            require(tid > 0)
            val tgid = File("/proc/self/task/$tid/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("Tgid:") }?.substringAfter(':')?.trim()?.toIntOrNull()
            }
            check(tgid == Process.myPid()) { "CPU placement target is not a current own-process thread" }
        }
        val command = listOf("/system/bin/taskset", if (tid == null) "-ap" else "-p") +
            listOfNotNull(mask) + (tid ?: Process.myPid()).toString()
        val summary = JSONObject().put("command", JSONArray(command))
            .put("pid", Process.myPid()).put("uid", Process.myUid())
        val output = ByteArrayOutputStream()
        var outputTruncated = false
        var readError: String? = null
        var process: java.lang.Process? = null
        var reader: Thread? = null
        var exitCode: Int? = null
        var timedOut = false
        val started = SystemClock.elapsedRealtime()
        fun confirmExit(): Boolean {
            val child = process ?: return true // No child was started.
            return try { exitCode = child.exitValue(); true }
            catch (_: IllegalThreadStateException) { false }
            catch (error: Exception) { summary.put("termination_error", error.javaClass.simpleName); false }
        }
        fun awaitExit(milliseconds: Long): Boolean {
            val deadline = SystemClock.elapsedRealtime() + milliseconds
            while (!confirmExit() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(10)
            return confirmExit()
        }
        fun joinBounded(thread: Thread?) {
            try { thread?.join(250) } catch (error: InterruptedException) {
                summary.put("query_error", error.javaClass.simpleName)
            }
        }
        try {
            process = ProcessBuilder(command).redirectErrorStream(true).start()
            val child = process
            reader = Thread({
                try {
                    child.inputStream.use { stream ->
                        val buffer = ByteArray(1024)
                        while (true) {
                            val count = stream.read(buffer)
                            if (count < 0) break
                            synchronized(output) {
                                val kept = minOf(count, 16_384 - output.size())
                                output.write(buffer, 0, kept)
                                if (kept < count) outputTruncated = true
                            }
                        }
                    }
                } catch (error: Exception) {
                    synchronized(output) { readError = error.javaClass.simpleName }
                }
            }, "cpu-placement-output").apply { isDaemon = true; start() }
            while (SystemClock.elapsedRealtime() - started < 5_000) {
                exitCode = try { child.exitValue() } catch (_: IllegalThreadStateException) { null }
                if (exitCode != null) break
                Thread.sleep(20)
            }
            timedOut = exitCode == null
        } catch (error: Exception) {
            summary.put("query_error", error.javaClass.simpleName)
        } finally {
            if (!confirmExit()) {
                try { process?.destroy() }
                catch (error: Exception) { summary.put("termination_error", error.javaClass.simpleName) }
                if (!awaitExit(250)) {
                    summary.put("force_stop_attempted", android.os.Build.VERSION.SDK_INT >= 26)
                    if (android.os.Build.VERSION.SDK_INT >= 26) try { process?.destroyForcibly() }
                    catch (error: Exception) { summary.put("termination_error", error.javaClass.simpleName) }
                    awaitExit(500)
                }
            }
            val childTerminated = confirmExit()
            if (!childTerminated) summary.put("termination_error", "ChildTerminationUnconfirmed")
            if (!summary.has("termination_error")) summary.put("termination_error", JSONObject.NULL)
            summary.put("child_started", process != null).put("child_terminated", childTerminated)
            // Let a terminated child's reader reach EOF before closing its pipes.
            joinBounded(reader)
            val pipesClosed = AtomicBoolean(process == null)
            val pipeError = AtomicReference<String?>(null)
            val closer = process?.let { child -> Thread({
                listOf(child.inputStream, child.errorStream, child.outputStream).forEach { stream ->
                    try { stream.close() } catch (error: Exception) { pipeError.set(error.javaClass.simpleName) }
                }
                pipesClosed.set(pipeError.get() == null)
            }, "cpu-placement-pipe-close").apply { isDaemon = true; start() } }
            joinBounded(closer)
            joinBounded(reader)
            summary.put("pipes_closed", pipesClosed.get() && closer?.isAlive != true)
                .put("pipe_close_error", pipeError.get() ?: JSONObject.NULL)
            summary.put("exit_code", exitCode ?: JSONObject.NULL).put("timed_out", timedOut)
                .put("elapsed_ms", SystemClock.elapsedRealtime() - started)
            synchronized(output) {
                summary.put("output", output.toString("UTF-8"))
                    .put("output_truncated", outputTruncated)
                    .put("output_read_error", readError ?: JSONObject.NULL)
                    .put("output_reader_complete", reader?.isAlive != true)
            }
        }
        return Result(summary, exitCode == 0 && !timedOut && !summary.has("query_error") &&
            !summary.getBoolean("output_truncated") && summary.isNull("output_read_error") &&
            summary.getBoolean("output_reader_complete") && summary.getBoolean("child_terminated") &&
            summary.getBoolean("pipes_closed"))
    }

    fun snapshot(destination: JSONObject, key: String, tid: Int? = null): Map<Int, BigInteger> {
        val result = command(null, tid)
        destination.put(key, result.json)
        check(result.success) { "Self-process CPU placement query failed: $key" }
        val pattern = Regex("^pid ([0-9]+)'s current affinity mask: ([0-9a-fA-F]+)$")
        val masks = linkedMapOf<Int, BigInteger>()
        result.json.getString("output").lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val match = checkNotNull(pattern.matchEntire(line.trim())) { "Unrecognized CPU placement query line" }
            check(masks.put(match.groupValues[1].toInt(), BigInteger(match.groupValues[2], 16)) == null)
        }
        check(masks.isNotEmpty() && masks.containsKey(tid ?: Process.myPid())) { "Missing own-process CPU placement" }
        result.json.put("tid_masks", JSONObject(masks.mapKeys { it.key.toString() }.mapValues { it.value.toString(16) }))
        return masks
    }

    class Lease(private val requested: String, val metadata: JSONObject) {
        private var original: BigInteger? = null
        private var changeAttempted = false
        private var mutationTerminationBlocked = false
        init {
            metadata.put("requested", requested).put("scope", "experimental whole-app process CPU placement; not MNN-only")
        }

        fun apply() {
            require(requested == "inherit" || requested == "f0")
            val before = snapshot(metadata, "before")
            check(before.values.toSet().size == 1) { "Heterogeneous original masks; no CPU placement change attempted" }
            val common = before.values.first()
            original = common
            val candidate = BigInteger("f0", 16)
            check(common.and(candidate) == candidate) { "Candidate f0 is not a subset of the observed original mask" }
            metadata.put("original_common_mask", common.toString(16))
            if (requested == "f0") {
                changeAttempted = true // A nonzero command exit can still mean a partial change.
                val result = command("f0")
                metadata.put("apply_command", result.json)
                mutationTerminationBlocked = !result.json.getBoolean("child_terminated")
                check(result.success) { "CPU placement command failed; restoration required" }
            }
            val applied = snapshot(metadata, "applied")
            check(applied.values.all { it == if (requested == "f0") candidate else common }) {
                "CPU placement readback mismatch"
            }
        }

        /** Returns failure rather than throwing, so provider/writer/raw cleanup always runs. */
        fun restore(): Throwable? = try {
            check(!mutationTerminationBlocked) {
                "Blocking CPU cleanup: mutating child termination unconfirmed; no racing restoration attempted; app force-stop required"
            }
            val common = original
            var commandSucceeded = true
            if (common != null) {
                if (changeAttempted) {
                    val result = command(common.toString(16))
                    metadata.put("restore_command", result.json)
                    mutationTerminationBlocked = !result.json.getBoolean("child_terminated")
                    check(!mutationTerminationBlocked) {
                        "Blocking CPU cleanup: restoration child termination unconfirmed; app force-stop required"
                    }
                    commandSucceeded = result.success
                }
                check(snapshot(metadata, "restored").values.all { it == common }) { "CPU placement restoration readback mismatch" }
                check(commandSucceeded) { "CPU placement restoration command failed" }
            }
            metadata.put("restoration_required", changeAttempted).put("restoration_successful", true)
            null
        } catch (failure: Throwable) {
            metadata.put("restoration_successful", false).put("restoration_failure", failure.javaClass.simpleName)
                .put("blocking_cleanup_failure", mutationTerminationBlocked)
            if (mutationTerminationBlocked) metadata.put("blocking_cleanup_reason",
                "Mutating child termination unconfirmed; no further affinity commands; app force-stop required")
            failure
        }
    }
}
