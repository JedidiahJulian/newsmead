package com.newsmead.research.study

import android.os.Process
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Test-only one-step Linux niceness experiment; targets come from verified model membership. */
internal class MnnWorkerPriorityLease(private val requested: String, val metadata: JSONObject) {
    data class Identity(val tid: Int, val comm: String, val start: String) {
        fun json() = JSONObject().put("tid", tid).put("comm", comm).put("starttime_ticks", start)
    }
    private data class State(val identity: Identity, val priority: Int) {
        fun json() = identity.json().put("priority", priority)
    }
    private var before = emptyMap<Int, State>()
    private var targets = emptyMap<Int, State>()
    private val candidates = linkedMapOf<Int, Int>()
    private val attempted = linkedSetOf<Int>()
    private var cleanupUnverified = false

    init {
        require(requested == "inherit" || requested == "more_favorable")
        metadata.put("requested", requested).put("requested_worker_count", 4)
            .put("mutation_attempt_count", 0)
            .put("scope", "One Process.THREAD_PRIORITY_MORE_FAVORABLE increment on four model inference workers; Android selects CPU cores")
            .put("priority_increment", Process.THREAD_PRIORITY_MORE_FAVORABLE)
            .put("limitation", "Identity/priority snapshots are not atomic; a new thread at an attempted candidate priority is conservatively treated as possible inherited priority, not proof of parentage. No unknown non-target is modified")
            .put("common_non_target_variation", "Android may independently change existing non-target priorities; these differences are recorded, not attributed to test inheritance or used as a restoration gate")
    }

    /** Called on the model owner before handoff; returns copies of the original stored tuples. */
    fun validatedWorkerIdentities(expected: List<Identity>): List<Identity> {
        validateShape(expected)
        expected.forEach { check(identity(it.tid) == it) { "Stored model worker identity mismatch" } }
        return expected.map { it.copy() }
    }

    fun apply(expected: List<Identity>) {
        try {
            check(before.isEmpty()) { "Priority lease already used" }
            validateShape(expected)
            metadata.put("expected_original_workers", JSONArray(expected.map { it.json() }))
            before = snapshot("before")
            val originals = expected.associate { worker ->
                val baseline = checkNotNull(before[worker.tid]) { "Model worker absent from own-process snapshot" }
                check(baseline.identity == worker) { "Stored model worker identity mismatch" }
                worker.tid to baseline
            }
            targets = originals // Adopt priorities only after all four original identities match.
            targets.forEach { (tid, state) ->
                check(state.priority in -20..19)
                candidates[tid] = state.priority + Process.THREAD_PRIORITY_MORE_FAVORABLE
                if (requested == "more_favorable") check(candidates.getValue(tid) in -20..19) {
                    "One-step candidate outside Linux niceness range; no change attempted"
                }
                check(readState(tid) == state) { "Model worker identity/priority changed before apply" }
            }
            metadata.put("actual_worker_count", targets.size)
                .put("original_targets", JSONArray(targets.values.map { it.json() }))
                .put("candidate_priorities", JSONObject(candidates.mapKeys { it.key.toString() }))
            val receipts = JSONArray(); metadata.put("apply_receipts", receipts)
            if (requested == "more_favorable") targets.forEach { (tid, original) ->
                check(readState(tid) == original) { "Model worker changed before setter" }
                attempted.add(tid) // A failed setter is still included in restoration.
                metadata.put("mutation_attempt_count", attempted.size)
                setAndVerify(original.identity, candidates.getValue(tid), receipts)
            }
            verify("applied", requested == "more_favorable", false)
            metadata.put("apply_successful", true)
        } catch (failure: Throwable) {
            metadata.put("apply_successful", false).put("apply_failure", failure.javaClass.simpleName)
            throw failure
        }
    }

    private fun validateShape(expected: List<Identity>) {
        require(expected.size == 4 && expected.map { it.tid }.toSet().size == 4 && expected.all { it.tid > 0 }) {
            "Exactly four distinct stored model worker identities required"
        }
    }

    /** Returns errors so provider/writer cleanup can always run. No retries or unknown-thread setters. */
    fun restore(): Throwable? {
        var failure: Throwable? = null
        fun retain(problem: Throwable) {
            if (failure == null) failure = problem else failure?.addSuppressed(problem)
        }
        val receipts = JSONArray(); metadata.put("restore_receipts", receipts)
        attempted.forEach { tid ->
            val original = targets.getValue(tid)
            try { setAndVerify(original.identity, original.priority, receipts) }
            catch (problem: Throwable) { retain(problem) }
        }
        if (before.isNotEmpty()) try { verify("restored", false, true) }
        catch (problem: Throwable) {
            if (attempted.isNotEmpty() && !metadata.has("restored_cleanup_scope_verified")) cleanupUnverified = true
            retain(problem)
        }
        metadata.put("restoration_required", attempted.isNotEmpty())
            .put("restoration_successful", failure == null && !cleanupUnverified)
            .put("blocking_cleanup_failure", cleanupUnverified)
        if (cleanupUnverified) metadata.put("blocking_cleanup_reason",
            "Priority cleanup unverified for direct targets or new/replaced threads with possible inherited candidate priority; unknown threads untouched; app restart required")
        return failure ?: if (cleanupUnverified) IllegalStateException("Priority cleanup unverified") else null
    }

    private fun setAndVerify(expected: Identity, priority: Int, receipts: JSONArray) {
        val receipt = expected.json().put("requested_priority", priority); receipts.put(receipt)
        try {
            check(identity(expected.tid) == expected) { "Priority target identity changed; setter refused" }
            Process.setThreadPriority(expected.tid, priority)
            val after = readState(expected.tid)
            receipt.put("readback_priority", after.priority).put("identity_unchanged", after.identity == expected)
            check(after.identity == expected && after.priority == priority) { "Priority setter readback mismatch" }
            receipt.put("successful", true)
        } catch (failure: Throwable) {
            receipt.put("successful", false).put("failure", failure.javaClass.simpleName)
            throw failure
        }
    }

    private fun verify(key: String, candidate: Boolean, restoring: Boolean) {
        val after = snapshot(key)
        var targetsCorrect = true
        var survivingTargetsRestored = true
        targets.forEach { (tid, original) ->
            val now = after[tid]
            val expected = if (candidate) candidates[tid] else original.priority
            if (now == null || now.identity != original.identity || now.priority != expected)
                targetsCorrect = false
            if (now != null && now.identity == original.identity && now.priority != original.priority) survivingTargetsRestored = false
        }
        val commonOthers = before.values.filter { old -> old.identity.tid !in targets &&
            after[old.identity.tid]?.identity == old.identity }
        val commonChanges = commonOthers.filter { after[it.identity.tid]?.priority != it.priority }.map { old ->
            old.identity.json().put("before_priority", old.priority)
                .put("after_priority", after.getValue(old.identity.tid).priority)
        }
        val commonUnchanged = commonChanges.isEmpty()
        val unknown = after.values.filter { now -> before[now.identity.tid]?.identity != now.identity }
        val attemptedPriorities = attempted.mapNotNull { candidates[it] }.toSet()
        val suspect = unknown.filter { it.priority in attemptedPriorities }
        val scopeVerified = targetsCorrect && survivingTargetsRestored && suspect.isEmpty()
        if (restoring && !scopeVerified) cleanupUnverified = true
        metadata.put("${key}_target_priorities_correct", targetsCorrect)
            .put("${key}_common_non_target_count", commonOthers.size)
            .put("${key}_common_non_target_priorities_unchanged", commonUnchanged)
            .put("${key}_common_non_target_concurrent_priority_changes", JSONArray(commonChanges))
            .put("${key}_new_or_replaced_non_target_threads", JSONArray(unknown.map { it.json() }))
            .put("${key}_possible_inherited_priority_threads", JSONArray(suspect.map { it.json() }))
            .put("${key}_surviving_targets_at_original_priorities", survivingTargetsRestored)
        if (restoring) metadata.put("restored_cleanup_scope_verified", scopeVerified)
        check(targetsCorrect) { "A target exited, changed identity or has an unexpected priority" }
        check(suspect.isEmpty()) { "A new surviving thread may retain an inherited candidate priority" }
    }

    private fun snapshot(key: String): Map<Int, State> {
        val tids = checkNotNull(File("/proc/self/task").list()).mapNotNull { it.toIntOrNull() }
        check(tids.isNotEmpty() && tids.size <= 512) { "Own-process thread inventory unavailable or outside bounded diagnostic size" }
        val result = linkedMapOf<Int, State>()
        val exited = JSONArray()
        tids.forEach { tid ->
            try { result[tid] = readState(tid) }
            catch (failure: Exception) {
                if (File("/proc/self/task/$tid").exists()) throw failure
                exited.put(tid)
            }
        }
        metadata.put(key, JSONArray(result.values.map { it.json() })).put("${key}_exited_during_snapshot", exited)
        return result
    }

    private fun readState(tid: Int): State {
        val before = identity(tid)
        val priority = Process.getThreadPriority(tid)
        check(identity(tid) == before) { "Thread identity changed while reading priority" }
        check(priority in -20..19)
        return State(before, priority)
    }

    private fun identity(tid: Int): Identity {
        require(tid > 0)
        val root = File("/proc/self/task/$tid")
        val tgid = File(root, "status").useLines { lines ->
            lines.firstOrNull { it.startsWith("Tgid:") }?.substringAfter(':')?.trim()?.toIntOrNull()
        }
        check(tgid == Process.myPid()) { "Priority target is not an own-process thread" }
        val comm = File(root, "comm").readText().trimEnd('\n')
        val stat = File(root, "stat").readText()
        val start = stat.substring(stat.lastIndexOf(')') + 1).trim().split(Regex("\\s+"))[19]
        check(start.toLongOrNull() != null)
        return Identity(tid, comm, start)
    }

    companion object {
        /** Bounded negative check: a mismatched original tuple must fail before any priority setter. */
        fun verifyIdentityMismatchRejected(expected: List<Identity>): JSONObject {
            require(expected.isNotEmpty())
            val audit = JSONObject()
            val lease = MnnWorkerPriorityLease("more_favorable", audit)
            val mismatched = expected.mapIndexed { index, worker ->
                if (index == 0) worker.copy(comm = worker.comm + "#mismatch") else worker.copy()
            }
            var rejected = false
            try { lease.apply(mismatched) }
            catch (failure: IllegalStateException) {
                check(failure.message == "Stored model worker identity mismatch")
                rejected = true
            }
            check(rejected && lease.attempted.isEmpty() && audit.getInt("mutation_attempt_count") == 0) {
                "Stored identity mismatch did not refuse all priority changes"
            }
            return JSONObject().put("mismatched_original_identity_rejected", true).put("mutation_attempt_count", 0)
        }
    }
}
