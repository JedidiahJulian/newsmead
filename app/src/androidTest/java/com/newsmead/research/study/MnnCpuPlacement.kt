package com.newsmead.research.study

import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.math.BigInteger
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

/** Experimental test-only selection: executor parent plus exactly three same-comm native workers.
 * Cached native workers are accepted only with unchanged TID, comm and kernel starttime.
 */
internal class MnnCpuPlacement(private val requested: String, val metadata: JSONObject) {
    private data class Identity(val tid: Int, val comm: String, val startTicks: String) {
        fun json() = JSONObject().put("tid", tid).put("comm", comm).put("starttime_ticks", startTicks)
    }
    private data class Parent(val tid: Int, val name: String)
    private var targets = emptyList<Identity>()
    private var before = emptyMap<Int, BigInteger>()
    private var baselineMask: BigInteger? = null
    private val attempted = linkedSetOf<Int>()
    private var terminationBlocked = false
    private var nonTargetCleanupUnverified = false
    private val candidate = BigInteger("f0", 16)

    init {
        require(requested == "inherit" || requested == "f0")
        metadata.put("requested", requested).put("requested_worker_count", 4)
            .put("scope", "experimental MNN executor parent and three identified native workers only")
            .put("selection_limitation", "Exact comm grouping and stable cached identities; not native stack attribution")
    }

    fun apply(worker: ExecutorService) {
        val future = worker.submit(Callable { Parent(Process.myTid(), Thread.currentThread().name.take(15)) })
        val parent = try { future.get(5, TimeUnit.SECONDS) } finally { if (!future.isDone) future.cancel(false) }
        val parentIdentity = identity(parent.tid)
        check(parentIdentity.comm == parent.name) { "Executor name/comm mismatch; no change attempted" }
        val matching = File("/proc/self/task").listFiles().orEmpty().mapNotNull { entry ->
            val tid = entry.name.toIntOrNull() ?: return@mapNotNull null
            runCatching { identity(tid) }.getOrNull()?.takeIf { it.comm == parent.name }
        }
        check(matching.any { it == parentIdentity }) { "Executor parent missing from thread discovery" }
        targets = when (matching.size) {
            4 -> matching.also {
                cachedNative = it.filter { item -> item.tid != parent.tid }
                metadata.put("discovery", "four exact current comm matches")
            }
            1 -> {
                val cached = cachedNative
                check(cached.size == 3 && cached.none { it.tid == parent.tid } &&
                    cached.all { runCatching { identity(it.tid) == it }.getOrDefault(false) }) {
                    "No three unchanged cached native workers; no change attempted"
                }
                metadata.put("discovery", "current executor parent plus three unchanged cached native identities")
                listOf(parentIdentity) + cached
            }
            else -> error("Ambiguous executor/native comm group (${matching.size}); no change attempted")
        }
        check(targets.size == 4 && targets.map { it.tid }.toSet().size == 4)
        metadata.put("parent_tid", parent.tid).put("actual_worker_count", targets.size)
            .put("workers", JSONArray(targets.map { it.json() }))
        before = AppCpuPlacement.snapshot(metadata, "whole_app_before")
        check(before.values.toSet().size == 1) { "Nonuniform whole-app baseline; no change attempted" }
        val baseline = before.values.first()
        check(baseline != candidate && baseline.and(candidate) == candidate) {
            "f0 must be a proper subset of the uniform original baseline; no change attempted"
        }
        baselineMask = baseline
        metadata.put("original_uniform_baseline_mask", baseline.toString(16))
        targets.forEach { target ->
            checkIdentity(target)
            val original = checkNotNull(before[target.tid]) { "Target absent from original mask snapshot" }
            check(original.and(candidate) == candidate) { "f0 is not a subset of an original worker mask" }
        }
        val commands = JSONArray(); metadata.put("apply_commands", commands)
        if (requested == "f0") targets.forEach { target ->
            checkIdentity(target)
            attempted.add(target.tid) // A failed setter may still have changed the target.
            mutate(target.tid, "f0", commands)
            val readback = JSONObject(); commands.put(JSONObject().put("readback", readback))
            check(AppCpuPlacement.snapshot(readback, "target", target.tid)[target.tid] == candidate) {
                "Worker placement readback mismatch"
            }
        }
        verifySnapshot("whole_app_applied", requested == "f0")
    }

    /** Keep all provider/writer cleanup reachable; never race an unconfirmed mutating child. */
    fun restore(): Throwable? {
        var failure: Throwable? = null
        val commands = JSONArray(); metadata.put("restore_commands", commands)
        try {
            check(!terminationBlocked) { "Mutating child termination unconfirmed; no racing restoration" }
            targets.filter { it.tid in attempted }.forEach { target ->
                if (!terminationBlocked) try {
                    checkIdentity(target)
                    mutate(target.tid, checkNotNull(before[target.tid]).toString(16), commands)
                } catch (problem: Throwable) {
                    if (failure == null) failure = problem else failure?.addSuppressed(problem)
                }
            }
            check(!terminationBlocked) { "Restoration child termination unconfirmed" }
            if (baselineMask != null) try {
                verifySnapshot("whole_app_restored", false)
            } catch (problem: Throwable) {
                if (attempted.isNotEmpty() && !metadata.has("whole_app_restored_new_surviving_non_target_masks_at_baseline"))
                    nonTargetCleanupUnverified = true
                throw problem
            }
            if (failure != null) throw checkNotNull(failure)
            metadata.put("restoration_required", attempted.isNotEmpty()).put("restoration_successful", true)
            return null
        } catch (problem: Throwable) {
            metadata.put("restoration_successful", false).put("restoration_failure", problem.javaClass.simpleName)
                .put("blocking_cleanup_failure", terminationBlocked || nonTargetCleanupUnverified)
                .put("non_target_cleanup_unverified", nonTargetCleanupUnverified)
            if (terminationBlocked || nonTargetCleanupUnverified) metadata.put("blocking_cleanup_reason",
                if (terminationBlocked) "Mutating child termination unconfirmed; no further affinity commands; app force-stop required"
                else "Non-target affinity cleanup could not be verified against the original uniform baseline; unknown threads were not modified; app restart required")
            return problem
        }
    }

    private fun mutate(tid: Int, mask: String, receipts: JSONArray) {
        check(!terminationBlocked) { "Further affinity commands blocked" }
        val result = AppCpuPlacement.command(mask, tid)
        receipts.put(result.json)
        terminationBlocked = !result.json.getBoolean("child_terminated")
        check(result.success) { "Worker affinity command failed" }
    }

    private fun verifySnapshot(key: String, applied: Boolean) {
        val after = AppCpuPlacement.snapshot(metadata, key)
        val baseline = checkNotNull(baselineMask)
        val targetTids = targets.map { it.tid }.toSet()
        val targetsCorrect = targets.all { target ->
            runCatching { identity(target.tid) == target }.getOrDefault(false) &&
                after[target.tid] == if (applied) candidate else before[target.tid]
        }
        val commonOthers = before.keys.intersect(after.keys) - targetTids
        val commonUnchanged = commonOthers.all { before[it] == after[it] }
        val newTids = after.keys - before.keys - targetTids
        val newRecords = JSONArray()
        var newSurvivorsAtBaseline = true
        newTids.forEach { tid ->
            val current = runCatching { identity(tid) }.getOrNull()
            val alive = current != null || File("/proc/self/task/$tid").exists()
            val record = current?.json() ?: JSONObject().put("tid", tid)
            val matches = current != null && after[tid] == baseline
            record.put("snapshot_mask", after[tid]?.toString(16)).put("alive_when_inspected", alive)
                .put("identity_readable", current != null).put("matches_baseline", matches)
            newRecords.put(record)
            if (alive && !matches) newSurvivorsAtBaseline = false
        }
        // During restoration, a surviving unknown child cannot be restored by guessing its original mask.
        if (key == "whole_app_restored" && (!commonUnchanged || !newSurvivorsAtBaseline))
            nonTargetCleanupUnverified = true
        metadata.put("${key}_target_masks_correct", targetsCorrect)
            .put("${key}_common_non_target_masks_unchanged", commonUnchanged)
            .put("${key}_common_non_target_count", commonOthers.size)
            .put("${key}_new_non_target_count", newTids.size)
            .put("${key}_new_non_target_threads", newRecords)
            .put("${key}_new_surviving_non_target_masks_at_baseline", newSurvivorsAtBaseline)
            .put("${key}_exited_non_target_count", (before.keys - after.keys - targetTids).size)
        check(targetsCorrect) { "Worker snapshot/identity mismatch" }
        check(commonUnchanged) { "A common non-target thread mask changed" }
        check(newSurvivorsAtBaseline) { "A new surviving non-target thread is not verified at the uniform baseline" }
    }

    private fun checkIdentity(expected: Identity) {
        check(identity(expected.tid) == expected) { "Worker exited or identity changed" }
    }

    companion object {
        private var cachedNative = emptyList<Identity>()
        private fun identity(tid: Int): Identity {
            val root = File("/proc/self/task/$tid")
            val tgid = File(root, "status").useLines { lines ->
                lines.firstOrNull { it.startsWith("Tgid:") }?.substringAfter(':')?.trim()?.toIntOrNull()
            }
            check(tgid == Process.myPid()) { "Worker is not in this process" }
            val comm = File(root, "comm").readText().trimEnd('\n')
            val stat = File(root, "stat").readText()
            val fields = stat.substring(stat.lastIndexOf(')') + 1).trim().split(Regex("\\s+"))
            val start = fields[19] // stat field22; fields[0] is field3 (state).
            check(start.toLongOrNull() != null)
            return Identity(tid, comm, start)
        }
    }
}
