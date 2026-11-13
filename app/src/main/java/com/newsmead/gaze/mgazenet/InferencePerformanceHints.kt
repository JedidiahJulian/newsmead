package com.newsmead.gaze.mgazenet

import android.content.Context
import android.os.Build
import android.os.PerformanceHintManager
import android.os.Process
import androidx.annotation.RequiresApi
import java.io.File

/** Optional scheduler experiment. Never selects cores or changes model math or research clocks. */
internal class InferencePerformanceHints(private val context: Context) {
    private val ownerTid = Process.myTid()
    private var membership = MnnWorkerThreads.Membership(emptyList(), "not_discovered")
    private var session: HintSession? = null
    @Volatile private var active = false
    private var requestedSessionThreads: Int? = null
    private var requested = false
    private var closed = false
    private var status = "disabled_by_default"
    private var closeVerified = true
    private var lastError: String? = null
    private var apiErrors = 0L
    private var sessionsCreated = 0L
    private var sessionsClosed = 0L
    private var attempts = 0L
    private var reports = 0L
    private var durationTotal = 0L
    private var durationMax = 0L
    private var durationLast = 0L

    fun <T> construct(threads: Int, create: () -> T): T = MnnWorkerThreads.construct(threads, create) {
        requestedSessionThreads = threads
        membership = it
    }

    @Synchronized fun setEnabled(enabled: Boolean) {
        checkOwner()
        check(!closed) { "Performance hint model already closed" }
        requested = enabled
        if (!enabled) { closeCurrent("disabled_explicitly"); return }
        if (active) return
        if (!closeCurrent("starting")) return
        if (Build.VERSION.SDK_INT < 31) { status = "unsupported_api_below_31"; return }
        if (requestedSessionThreads != 4 || membership.workers.size != 4 || !MnnWorkerThreads.stillValid(membership)) {
            status = "unsupported_worker_membership:${membership.status}"; return
        }
        try {
            val created = Api31.create(context, membership.workers.map { it.tid }.toIntArray())
            session = created.session
            active = session != null
            if (active) sessionsCreated++
            status = created.status
        } catch (problem: Exception) { status = errorStatus("unsupported_api_error", problem) }
        catch (problem: LinkageError) { status = errorStatus("unsupported_api_linkage", problem) }
    }

    fun beginTask(): Long = if (active) System.nanoTime() else 0L

    @Synchronized fun endTask(started: Long) {
        if (started == 0L || !active) return
        val elapsed = (System.nanoTime() - started).coerceAtLeast(1L)
        attempts++; durationLast = elapsed; durationTotal += elapsed; durationMax = maxOf(durationMax, elapsed)
        try { session?.report(elapsed); reports++ }
        catch (problem: Exception) { closeCurrent(errorStatus("report_error", problem)) }
        catch (problem: LinkageError) { closeCurrent(errorStatus("report_linkage", problem)) }
    }

    @Synchronized fun snapshot(): Map<String, Any?> = linkedMapOf(
        "requested" to requested, "active" to active, "status" to status, "closed" to closed,
        "target_duration_ns" to TARGET_NS, "thread_count" to membership.workers.size,
        "requested_session_threads" to requestedSessionThreads,
        "initial_process_pool_thread_count" to membership.initialPoolThreads,
        "pool_size_interpretation" to "First observed native pool persists for process lifetime; requested session count does not resize it",
        "membership_status" to membership.status, "owner_tid" to ownerTid,
        "thread_ids" to membership.workers.map { it.tid }, "report_attempts" to attempts,
        "reports_succeeded" to reports, "last_actual_duration_ns" to durationLast,
        "max_actual_duration_ns" to durationMax, "total_actual_duration_ns" to durationTotal,
        "session_close_verified" to closeVerified,
        "sessions_created" to sessionsCreated, "sessions_closed" to sessionsClosed,
        "api_errors" to apiErrors, "last_api_error" to lastError,
        "duration_clock" to "System.nanoTime; Android CLOCK_MONOTONIC, uptime-compatible duration",
        "duration_scope" to "One infer checks/JNI/output copy, group elapsed time; excludes queue and callback age",
    )

    @Synchronized fun close() {
        checkOwner()
        if (closed) return
        requested = false
        closeCurrent("closed")
        closed = true
    }

    private fun closeCurrent(nextStatus: String): Boolean {
        active = false
        try {
            if (session != null) { session?.close(); sessionsClosed++ }
            session = null; closeVerified = true; status = nextStatus
        }
        catch (problem: Exception) { closeVerified = false; status = errorStatus("close_error", problem) }
        catch (problem: LinkageError) { closeVerified = false; status = errorStatus("close_linkage", problem) }
        return closeVerified
    }

    private fun errorStatus(prefix: String, problem: Throwable): String {
        apiErrors++
        return "$prefix:${problem.javaClass.simpleName}".also { lastError = it }
    }

    private fun checkOwner() { check(Process.myTid() == ownerTid) { "Performance hint calls require the model owner thread" } }
    private interface HintSession { fun report(duration: Long); fun close() }
    private data class Created(val session: HintSession?, val status: String)
    @RequiresApi(31) private object Api31 {
        fun create(context: Context, tids: IntArray): Created {
            val manager = context.getSystemService(PerformanceHintManager::class.java)
                ?: return Created(null, "unsupported_missing_service")
            val native = manager.createHintSession(tids, TARGET_NS)
                ?: return Created(null, "unsupported_null_session")
            val session = object : HintSession {
                override fun report(duration: Long) = native.reportActualWorkDuration(duration)
                override fun close() = native.close()
            }
            return Created(session, "active")
        }
    }
    companion object { const val TARGET_NS = 33_333_333L }
}

/** Passive first native construction observation; the process pool is never resized here. */
private object MnnWorkerThreads {
    data class Worker(val tid: Int, val comm: String, val start: String)
    data class Membership(val workers: List<Worker>, val status: String, val initialPoolThreads: Int? = null)
    private var attempted = false
    private var cached = emptyList<Worker>()
    private var initialPoolThreads: Int? = null

    @Synchronized fun <T> construct(threads: Int, create: () -> T, discovered: (Membership) -> Unit): T {
        val first = threads in 1..8 && !attempted
        if (first) attempted = true
        val before = if (first) runCatching { ownTids() }.getOrNull() else null
        val result = create() // Preserve native initialization errors; optional discovery never replaces them.
        val membership = try {
            val owner = identity(Process.myTid())
            when {
                threads !in 1..8 -> Membership(emptyList(), "unsupported_session_thread_count")
                first && before != null -> {
                    val native = (ownTids() - before).map { identity(it) }.filter { it.comm == owner.comm }
                    if (native.size == threads - 1) {
                        cached = native
                        initialPoolThreads = threads
                        Membership(listOf(owner) + native, "first_native_creation_exact_${native.size}_new_same_comm", initialPoolThreads)
                    } else Membership(emptyList(), "ambiguous_first_native_creation_count_${native.size}")
                }
                initialPoolThreads != null && cached.none { it.tid == owner.tid } && cached.all { identity(it.tid) == it } ->
                    Membership(listOf(owner) + cached, "unchanged_cached_native_identities", initialPoolThreads)
                else -> Membership(emptyList(), "unresolved_native_membership")
            }
        } catch (_: Exception) { Membership(emptyList(), "worker_identity_read_failed") }
        discovered(membership)
        return result
    }

    fun stillValid(membership: Membership): Boolean = runCatching {
        membership.workers.all { identity(it.tid) == it }
    }.getOrDefault(false)

    private fun ownTids(): Set<Int> = checkNotNull(File("/proc/self/task").list())
        .mapNotNull { it.toIntOrNull() }.toSet()
    private fun identity(tid: Int): Worker {
        val root = File("/proc/self/task/$tid")
        val tgid = File(root, "status").useLines { lines ->
            lines.firstOrNull { it.startsWith("Tgid:") }?.substringAfter(':')?.trim()?.toIntOrNull()
        }
        check(tgid == Process.myPid())
        val comm = File(root, "comm").readText().trimEnd('\n')
        val stat = File(root, "stat").readText()
        val start = stat.substring(stat.lastIndexOf(')') + 1).trim().split(Regex("\\s+"))[19]
        check(start.toLongOrNull() != null)
        return Worker(tid, comm, start)
    }
}
