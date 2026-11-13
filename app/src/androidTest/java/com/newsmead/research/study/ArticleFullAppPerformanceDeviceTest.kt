package com.newsmead.research.study

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.PowerManager
import android.view.FrameMetrics
import android.view.Window
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.newsmead.activities.ArticleActivity
import com.newsmead.gaze.mgazenet.SameModelExecutionTrial
import com.newsmead.gaze.mgazenet.MnnEstimator
import com.newsmead.data.StudyArticleCatalog
import com.newsmead.data.StudyConfig
import com.newsmead.fragments.article.ArticleFragment
import com.newsmead.gaze.GazeProvider
import com.newsmead.gaze.SharedReadingBaseline
import com.newsmead.gaze.mgazenet.MgazeNetCalibrationStore
import com.newsmead.gaze.mgazenet.MgazeNetGazeProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Callable
import java.util.concurrent.ExecutorService
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

/** Explicit live-camera diagnostic; OFF/ON refers only to numerical study recording.
 * Uses saved calibration and current reader/aid preferences without modifying either.
 * Only aggregate timing/reason/transport evidence survives a successful run.
 */
@RunWith(AndroidJUnit4::class)
class ArticleFullAppPerformanceDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val handler = Handler(Looper.getMainLooper())
    private fun main(block: () -> Unit) = instrumentation.runOnMainSync { block() }
    private fun now() = SystemClock.elapsedRealtimeNanos() / 1e6

    @Test fun fullArticleRecordingComparison() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue("Explicit live-camera opt-in required", args.getString("newsmeadLivePerf") == "true")
        val sameModelTrial = args.getString(SameModelExecutionTrial.OPT_IN) == "true"
        val threadArgument = args.getString(SameModelExecutionTrial.THREADS)
        require(sameModelTrial == (threadArgument != null)) { "Execution trial requires opt-in and a single invocation-wide thread count" }
        val configuredThreads = if (sameModelTrial) {
            require(threadArgument in listOf("1", "2", "3", "4")) { "Execution trial threads must be 1, 2, 3 or 4" }
            checkNotNull(threadArgument).toInt()
        } else MnnEstimator.DEFAULT_CPU_THREADS
        val requestedLocalizer = args.getString(SameModelExecutionTrial.LOCALIZER)
        require(!args.containsKey(SameModelExecutionTrial.LOCALIZER) ||
            (requestedLocalizer in listOf("CPU", "GPU") && sameModelTrial && configuredThreads == 2)) {
            "Explicit localizer requires CPU or GPU and the isolated CPU2 execution trial"
        }
        val requestedBackend = args.getString(SameModelExecutionTrial.BACKEND)
        require(!args.containsKey(SameModelExecutionTrial.BACKEND) ||
            (requestedBackend in listOf("CPU", "OPENCL") && sameModelTrial && configuredThreads == 2)) {
            "Explicit gaze backend requires CPU or OPENCL and the isolated two-thread reference configuration"
        }
        val warmup = (args.getString("newsmeadPerfWarmupSeconds")?.toLongOrNull() ?: 5).coerceIn(1, 30)
        val duration = (args.getString("newsmeadPerfDurationSeconds")?.toLongOrNull() ?: 20).coerceIn(5, 60)
        val order = (args.getString("newsmeadPerfOrder") ?: "OFF,ON,ON,OFF")
            .split(',').map { it.trim().uppercase(java.util.Locale.ROOT) }
        require(order.size in 2..8 && order.all { it == "OFF" || it == "ON" } && order.toSet().size == 2)
        val cpuMasks = args.getString("newsmeadPerfCpuMasks")?.split(',')?.map {
            it.trim().lowercase(java.util.Locale.ROOT)
        }
        require(cpuMasks == null || (cpuMasks.size == order.size && cpuMasks.all { it == "inherit" || it == "f0" })) {
            "newsmeadPerfCpuMasks must match phase count and contain only inherit or f0"
        }
        val mnnCpuMasks = args.getString("newsmeadPerfMnnCpuMasks")?.split(',')?.map {
            it.trim().lowercase(java.util.Locale.ROOT)
        }
        require(mnnCpuMasks == null || (mnnCpuMasks.size == order.size && mnnCpuMasks.all { it == "inherit" || it == "f0" })) {
            "newsmeadPerfMnnCpuMasks must match phase count and contain only inherit or f0"
        }
        require(cpuMasks == null || mnnCpuMasks == null) { "Whole-app and MNN-worker CPU placement arguments are mutually exclusive" }
        val hintModes = args.getString("newsmeadPerfHintModes")?.split(',')?.map {
            it.trim().uppercase(java.util.Locale.ROOT)
        }
        require(hintModes == null || (hintModes.size == order.size && hintModes.all { it == "OFF" || it == "ON" })) {
            "newsmeadPerfHintModes must match phase count and contain only OFF or ON"
        }
        require(hintModes == null || (cpuMasks == null && mnnCpuMasks == null)) {
            "Performance hints and CPU placement arguments are mutually exclusive"
        }
        val priorityModes = args.getString("newsmeadPerfPriorityModes")?.split(',')?.map {
            it.trim().lowercase(java.util.Locale.ROOT)
        }
        require(priorityModes == null || (priorityModes.size == order.size && priorityModes.all {
            it == "inherit" || it == "more_favorable"
        })) { "newsmeadPerfPriorityModes must match phase count and contain only inherit or more_favorable" }
        require(priorityModes == null || (hintModes == null && cpuMasks == null && mnnCpuMasks == null)) {
            "Worker priority, performance hints and both CPU placement arguments are mutually exclusive"
        }
        require(!sameModelTrial || (cpuMasks == null && mnnCpuMasks == null && hintModes == null && priorityModes == null)) {
            "Same-model execution trial cannot mix affinity, priority or hint-mode interventions"
        }
        if (mnnCpuMasks != null || priorityModes != null || hintModes != null) {
            require(MnnEstimator.DEFAULT_CPU_THREADS == 4) { "Historical four-worker diagnostics require the four-thread default; unavailable with the current default" }
        }
        val placementScope = when {
            sameModelTrial -> "exploratory same-model ${requestedBackend ?: "CPU"} execution; CPU reference threads=$configuredThreads; invocation-wide constant, fresh process required"
            priorityModes != null -> "experimental one-step OS niceness increment for four MNN workers; Android selects CPU cores"
            hintModes != null -> "experimental platform Performance Hint API for MNN inference"
            mnnCpuMasks != null -> "experimental MNN executor parent and three identified native workers only"
            cpuMasks != null -> "experimental whole-app process CPU placement; not MNN-only"
            else -> null
        }
        main { check(StudyCollection.run == null) { "Stop the existing researcher run before this diagnostic." } }
        val summary = JSONObject().put("schema", "newsmead_full_app_performance_v1")
            .put("recording_order", JSONArray(order)).put("warmup_seconds", warmup)
            .put("timed_seconds_requested", duration).put("recording_only_condition", placementScope == null)
            .put("changed_variables", JSONArray(listOfNotNull("numerical study recording", placementScope)))
            .put("interpretation", "Live article pipeline timing; neither accuracy nor human reading validity")
            .put("benchmark_coverage_guard", "Each timed block requires at least one coordinate result and no_face ratio <=0.20; diagnostic coverage only, not an accuracy or study eligibility threshold")
            .put("device_model", android.os.Build.MODEL).put("sdk", android.os.Build.VERSION.SDK_INT)
            .put("requested_mnn_cpu_threads", if (requestedBackend == "OPENCL") JSONObject.NULL else configuredThreads)
            .put("cpu_reference_thread_count", configuredThreads)
            .put("requested_mnn_backend", requestedBackend ?: "CPU")
            .put("warmup_basis", if (requestedBackend != null)
                "Requested warmup follows the first coordinate observation; startup wait excluded from timed FPS, unchanged provider expiry/watchdog"
                else "Requested warmup follows provider attachment")
            .put("requested_localizer", requestedLocalizer ?: JSONObject.NULL)
            .put("localizer_trial_limitation", "Same localizer weights/options; GPU calibration is an unvalidated reference mapping for explicit CPU execution; performance only, no compatibility or accuracy claim")
            .put("same_model_execution_trial", sameModelTrial)
            .put("process_pid", android.os.Process.myPid())
            .put("process_start_identity", processStartIdentity())
            .put("reference_calibration_runtime", MnnEstimator.RUNTIME)
            .put("cpu1_pool_observation_limitation", "CPU1 is inferred from zero newly observed native workers plus owner; this alone cannot exclude a pre-existing unobserved native pool. Separate fresh process and no prior unobserved MNN construction are required; no pool improvement claimed")
            .put("execution_trial_limitation", "Saved calibration matches the current default runtime; explicit execution candidates use it as an exploratory reference mapping only; candidate accuracy unvalidated; feature and accuracy evidence recorded separately; prior strict CPU2/CPU4 parity failed (~5.3e-5 versus unchanged 1e-5 criterion). Compare separate fresh processes, never counts switched in one process")
            .put("thermal_status_initial", thermalStatus())
            .put("observer_overhead", "Main-thread callback timers, 100ms lateness probe and FrameMetrics aggregation add diagnostic overhead; frame durations are not camera FPS")
            .put("baseline_limitation", "If a saved R/D/F or shared coordinate reference is unavailable, corresponding scoring/reference-dependent work is not represented; no baseline is synthesized")
        if (cpuMasks != null) summary.put("cpu_mask_order", JSONArray(cpuMasks)).put("cpu_placement_settle_ms", 1000)
        if (mnnCpuMasks != null) summary.put("mnn_cpu_mask_order", JSONArray(mnnCpuMasks)).put("cpu_placement_settle_ms", 1000)
        if (hintModes != null) summary.put("performance_hint_order", JSONArray(hintModes)).put("performance_hint_settle_ms", 1000)
        if (priorityModes != null) summary.put("worker_priority_order", JSONArray(priorityModes)).put("worker_priority_settle_ms", 1000)
        val phases = JSONArray(); summary.put("phases", phases)
        val storage = File(context.cacheDir, "study-device-test-live-perf-${UUID.randomUUID()}")
        val visits = mutableListOf<StudyVisit>()
        var isolated = false
        var successful = false
        var allProvidersClosed = true
        var failure: Throwable? = null
        val calibrationBefore = MgazeNetCalibrationStore(context).fingerprint()
        try {
            check(calibrationBefore != null) { "Saved calibration absent; no capture attempted." }
            val article = context.assets.open(StudyConfig.ARTICLES_ASSET).bufferedReader().use {
                StudyArticleCatalog.parse(it.readText()).first()
            }
            main { StudyCollection.useIsolatedStorageForTest(context, storage); isolated = true }
            order.forEachIndexed { index, condition ->
                val phase = JSONObject().put("index", index).put("recording", condition)
                phases.put(phase)
                val cpuLease = cpuMasks?.let { masks ->
                    val metadata = JSONObject(); phase.put("cpu_placement", metadata)
                    AppCpuPlacement.Lease(masks[index], metadata)
                }
                val mnnCpuLease = mnnCpuMasks?.let { masks ->
                    val metadata = JSONObject(); phase.put("mnn_cpu_placement", metadata)
                    MnnCpuPlacement(masks[index], metadata)
                }
                val priorityLease = priorityModes?.let { modes ->
                    val metadata = JSONObject(); phase.put("worker_priority", metadata)
                    MnnWorkerPriorityLease(modes[index], metadata)
                }
                var cpuRestoreFailure: Throwable? = null
                var priorityRestoreFailure: Throwable? = null
                var hintCleanupFailure: Throwable? = null
                var hintModel: MnnEstimator? = null
                var gpuCleanupFailure: Throwable? = null
                var gpuModel: MnnEstimator? = null
                var gpuModelReference: Future<MnnEstimator?>? = null
                var pipelineReady = false
                var phaseFailure: Throwable? = null
                if (hintModes != null) phase.put("performance_hint_requested", hintModes[index])
                if (condition == "ON") main {
                    StudyCollection.start(context, "live-performance-diagnostic", "recording-on", "reading")
                }
                var scenario: ActivityScenario<ArticleActivity>? = null
                var provider: MgazeNetGazeProvider? = null
                var measuredFragment: ArticleFragment? = null
                var providerContinuityValid = true
                val closed = CountDownLatch(1)
                val stats = Counters()
                val faceReadiness = FaceReadiness()
                phase.put("face_preflight", JSONObject().put("status", "not_started"))
                val probe = LatenessProbe(stats)
                var measuredWindow: Window? = null
                val frames = Window.OnFrameMetricsAvailableListener { _, metrics, dropped ->
                    if (stats.isTimed(now())) {
                        stats.frameDuration.add(metrics.getMetric(FrameMetrics.TOTAL_DURATION) / 1e6)
                        stats.frameObserverDrops += dropped
                    }
                }
                try {
                    val launchIntent = Intent(context, ArticleActivity::class.java).putExtra("articleItem", article)
                    if (sameModelTrial) launchIntent
                        .putExtra(SameModelExecutionTrial.OPT_IN, true)
                        .putExtra(SameModelExecutionTrial.THREADS, configuredThreads)
                    requestedLocalizer?.let { launchIntent.putExtra(SameModelExecutionTrial.LOCALIZER, it) }
                    requestedBackend?.let { launchIntent.putExtra(SameModelExecutionTrial.BACKEND, it) }
                    scenario = ActivityScenario.launch(launchIntent)
                    val deadline = now() + 10_000
                    while (provider == null && now() < deadline) {
                        scenario.onActivity { activity ->
                            val fragment = fragments(activity.supportFragmentManager.fragments).filterIsInstance<ArticleFragment>().firstOrNull()
                            if (fragment != null) {
                                check(MgazeNetCalibrationStore(context).compatibilityIssue(fragment.requireView()) == null) {
                                    "Saved calibration incompatible; recalibration is outside this diagnostic."
                                }
                                provider = field(fragment, "gazeProvider") as? MgazeNetGazeProvider
                                if (provider != null) {
                                    measuredFragment = fragment
                                    phase.put("aids_runtime_installed", field(fragment, "readingAidRuntime") != null)
                                    phase.put("aid_preferences", field(fragment, "aidPreferences").toString())
                                    phase.put("rdf_reference_available", field(fragment, "activeMahalanobisBaseline") != null)
                                    phase.put("shared_coordinate_reference_available", SharedReadingBaseline.current(
                                        calibrationBefore, StudyCollection.run?.participant) != null)
                                    (field(fragment, "studyVisit") as? StudyVisit)?.let { visits.add(it) }
                                    measuredWindow = activity.window
                                    activity.window.addOnFrameMetricsAvailableListener(frames, handler)
                                }
                            }
                        }
                        if (provider == null) SystemClock.sleep(100)
                    }
                    val live = checkNotNull(provider) { "Real article gaze provider unavailable after 10 seconds." }
                    main {
                        val originalGaze = checkNotNull(field(live, "listener") as? GazeProvider.OnGaze)
                        live.setOnGaze { x, y ->
                            val before = now()
                            try { originalGaze.onGaze(x, y) }
                            finally { if (stats.isTimed(before)) stats.gazeCallback.add(now() - before) }
                        }
                        val original = live.onObservation
                        live.onObservation = { observation ->
                            val before = now()
                            try { original?.invoke(observation) }
                            finally {
                                val originalCost = now() - before
                                if (!observation.timeoutWatchdog && observation.reason == "coordinate" &&
                                    observation.captureMs != null) pipelineReady = true
                                faceReadiness.observation(observation, before)
                                stats.observation(observation, originalCost, before)
                            }
                        }
                        val originalFailure = live.onFailure
                        live.onFailure = { message -> stats.failure = message; originalFailure?.invoke(message) }
                    }
                    if (requestedBackend == "OPENCL") gpuModelReference = postModelReference(live)
                    if (requestedBackend != null) {
                        val readyStarted = now()
                        var ready = false
                        var providerFailure: String? = null
                        while (now() - readyStarted < 15_000) {
                            main { ready = pipelineReady; providerFailure = stats.failure }
                            if (ready || providerFailure != null) break
                            SystemClock.sleep(50)
                        }
                        phase.put("pipeline_startup", JSONObject().put("ready", ready)
                            .put("waited_ms", now() - readyStarted).put("maximum_wait_ms", 15_000)
                            .put("definition", "First non-watchdog coordinate observation; processing eligibility only, not accuracy; excludes startup from timed FPS"))
                        check(providerFailure == null) { "Provider failed before processing readiness: $providerFailure" }
                        check(ready) { "No complete gaze-processing observation before startup deadline" }
                    }
                    SystemClock.sleep(warmup * 1000)
                    if (sameModelTrial) {
                        val actual = onModelWorker(live) { model ->
                            if (requestedBackend == "OPENCL") gpuModel = model
                            model.executionSnapshot()
                        }
                        phase.put("same_model_execution_actual", JSONObject(actual))
                        check(actual["backend"] == (requestedBackend ?: "CPU")) { "Actual gaze backend differs from the request" }
                        if (requestedBackend == "OPENCL") {
                            check(actual["actual_main_backend"] == 3) { "OpenCL main backend not observed; CPU fallback cannot pass" }
                        } else {
                            check(actual["requested_session_threads"] == configuredThreads) { "Actual model session thread count differs from requested trial" }
                            check(actual["initial_process_pool_thread_count"] == configuredThreads && actual["thread_count"] == configuredThreads) {
                                "Fresh process pool count unavailable or mismatched; restart process before each CPU1/CPU2/CPU3/CPU4 invocation"
                            }
                        }
                    }
                    if (requestedLocalizer != null) {
                        val actual = onModelWorker(live) { model ->
                            val source = checkNotNull(field(live, "source"))
                            val shape = checkNotNull(field(source, "signature") as? List<*>)
                            check(shape.size == 3)
                            val delegate = checkNotNull(field(source, "delegateName") as? String)
                            val saved = checkNotNull(field(source, "saved") as? com.newsmead.gaze.mgazenet.CalibrationBundle.Artifact)
                            mapOf("requested" to requestedLocalizer, "actual_delegate" to delegate,
                                "localizer_sha256" to com.newsmead.gaze.mgazenet.CalibrationIdentity.LOCALIZER,
                                "model_sha256" to MnnEstimator.MODEL_SHA, "runtime" to model.runtimeDescription,
                                "actual_acquisition" to "${shape[0]}x${shape[1]}:${shape[2]}:$delegate",
                                "reference_acquisition" to saved.identity.acquisition)
                        }
                        phase.put("localizer_execution_actual", JSONObject(actual))
                        check(actual["actual_delegate"] == if (requestedLocalizer == "CPU") "CPU_experimental" else "GPU") {
                            "Actual localizer does not match explicit request"
                        }
                        val reference = actual["reference_acquisition"] as String
                        val acquisition = actual["actual_acquisition"] as String
                        check(reference.endsWith(":GPU") && SameModelExecutionTrial.acquisitionMatches(reference, acquisition, requestedLocalizer)) {
                            "Explicit localizer trial requires unchanged camera geometry and saved GPU reference acquisition"
                        }
                        summary.put("localizer_execution_actual", JSONObject(actual))
                    }
                    awaitFaceReadiness(faceReadiness, phase)
                    if (cpuLease != null) {
                        cpuLease.apply()
                    }
                    if (mnnCpuLease != null) {
                        var worker: ExecutorService? = null
                        main {
                            val source = checkNotNull(field(live, "source")) { "Live camera source unavailable after warmup" }
                            worker = field(source, "worker") as? ExecutorService
                        }
                        mnnCpuLease.apply(checkNotNull(worker) { "MNN source worker unavailable" })
                    }
                    if (hintModes != null) {
                        phase.put("performance_hints_configured", JSONObject(onModelWorker(live) { model ->
                            hintModel = model
                            model.setPerformanceHintsEnabled(hintModes[index] == "ON")
                            model.performanceHintsSnapshot()
                        }))
                    }
                    if (priorityLease != null) {
                        var expected: List<MnnWorkerPriorityLease.Identity>? = null
                        val membership = onModelWorker(live) { model ->
                            val hints = checkNotNull(field(model, "performanceHints"))
                            val ownerTid = checkNotNull(field(hints, "ownerTid") as? Int)
                            check(android.os.Process.myTid() == ownerTid) {
                                "Stored membership must be read on the model owner"
                            }
                            val stored = checkNotNull(field(hints, "membership"))
                            val workers = checkNotNull(field(stored, "workers") as? List<*>)
                            val originals = workers.map { item ->
                                val worker = checkNotNull(item)
                                MnnWorkerPriorityLease.Identity(
                                    checkNotNull(field(worker, "tid") as? Int),
                                    checkNotNull(field(worker, "comm") as? String),
                                    checkNotNull(field(worker, "start") as? String),
                                )
                            }
                            expected = priorityLease.validatedWorkerIdentities(originals)
                            model.performanceHintsSnapshot()
                        }
                        phase.put("worker_priority_membership", JSONObject(membership))
                        val originals = checkNotNull(expected)
                        if (index == 0) phase.put("worker_priority_identity_mismatch_check",
                            MnnWorkerPriorityLease.verifyIdentityMismatchRejected(originals))
                        priorityLease.apply(originals)
                    }
                    if (cpuLease != null || mnnCpuLease != null || hintModes != null || priorityLease != null) {
                        SystemClock.sleep(1000)
                    }
                    var sameProvider = false
                    main {
                        sameProvider = measuredFragment?.isResumed == true &&
                            field(checkNotNull(measuredFragment), "gazeProvider") === live
                        if (sameProvider) {
                            val source = checkNotNull(field(live, "source")) { "Live camera source unavailable after warmup" }
                            phase.put("preprocessor_slot_count", (field(source, "preprocessors") as List<*>).size)
                            stats.start(now(), duration * 1000.0); probe.start()
                        }
                    }
                    phase.put("provider_identity_before_timing", sameProvider)
                    providerContinuityValid = sameProvider
                    check(sameProvider) { "Reader paused or gaze provider changed before timing; block rejected" }
                    SystemClock.sleep(duration * 1000)
                    main {
                        stats.end = now(); probe.stop(); phase.put("timing", stats.json())
                        sameProvider = measuredFragment?.isResumed == true &&
                            field(checkNotNull(measuredFragment), "gazeProvider") === live
                    }
                    val stoppedDuringTiming = stats.json().getJSONObject("reason_counts").optLong("stopped", 0) > 0
                    phase.put("provider_identity_after_timing", sameProvider)
                        .put("provider_stopped_during_timing", stoppedDuringTiming)
                    providerContinuityValid = sameProvider && !stoppedDuringTiming
                    check(providerContinuityValid) { "Reader paused or gaze provider stopped/restarted during timing; block rejected" }
                    check(stats.failure == null) { "Provider failed: ${stats.failure}" }
                    check(stats.cameraResults > 0) { "No timed camera results; check permission, face setup and camera availability." }
                    val coverage = stats.json()
                    val noFaceRatio = coverage.optDouble("no_face_ratio_camera_results", 1.0)
                    check(noFaceRatio.isFinite() && noFaceRatio <= 0.20 && coverage.optDouble("coordinate_hz", 0.0) > 0.0) {
                        "Insufficient face/coordinate coverage for a gaze-performance comparison; block rejected, check face setup"
                    }
                } catch (problem: Throwable) {
                    phaseFailure = problem
                    throw problem
                } finally {
                    if (priorityLease != null) {
                        try { main { if (stats.begin.isFinite() && !stats.end.isFinite()) stats.end = now(); probe.stop() } }
                        catch (problem: Throwable) { priorityRestoreFailure = problem }
                        val restoration = priorityLease.restore()
                        if (priorityRestoreFailure == null) priorityRestoreFailure = restoration
                        else if (restoration != null) priorityRestoreFailure?.addSuppressed(restoration)
                    }
                    if (hintModes != null && provider != null) try {
                        main { if (stats.begin.isFinite() && !stats.end.isFinite()) stats.end = now(); probe.stop() }
                        phase.put("performance_hints_counter_scope",
                            "Model-lifetime counters for enabled infer tasks, including settling and work through the queued post-window snapshot; not aligned with the FPS window")
                        phase.put("performance_hints_final_cumulative", JSONObject(onModelWorker(checkNotNull(provider)) { model ->
                            hintModel = model
                            model.performanceHintsSnapshot()
                        }))
                    } catch (problem: Throwable) {
                        phase.put("performance_hints_snapshot_failure", problem.javaClass.simpleName)
                        hintCleanupFailure = problem
                    }
                    // Restore while the source and foreground activity still exist. Never skip cleanup on failure.
                    cpuRestoreFailure = if (cpuLease != null) cpuLease.restore() else mnnCpuLease?.restore()
                    val previousProvidersClosed = allProvidersClosed
                    allProvidersClosed = false // Never infer closure after a failed lifecycle transition.
                    val closingProviders = linkedSetOf<MgazeNetGazeProvider>()
                    provider?.let { closingProviders.add(it) }
                    var quiesceFailure: Throwable? = null
                    try {
                        scenario?.onActivity { activity ->
                            fragments(activity.supportFragmentManager.fragments).filterIsInstance<ArticleFragment>().forEach { fragment ->
                                // Block an already queued policy-restart callback before pausing.
                                // This owned scenario is destroyed below; the suppression is never persisted.
                                fragment.javaClass.getDeclaredField("demoCameraSuppressed").apply { isAccessible = true }
                                    .setBoolean(fragment, true)
                                val current = field(fragment, "gazeProvider") as? MgazeNetGazeProvider
                                if (fragment !== measuredFragment || current !== provider) providerContinuityValid = false
                                current?.let { closingProviders.add(it) }
                            }
                        }
                        scenario?.moveToState(Lifecycle.State.CREATED)
                        scenario?.onActivity { activity ->
                            check(activity.lifecycle.currentState == Lifecycle.State.CREATED)
                        }
                        phase.put("cleanup_activity_quiesced", true)
                    } catch (problem: Throwable) {
                        quiesceFailure = problem
                        phase.put("cleanup_activity_quiesced", false)
                    }
                    val providerClosures = closingProviders.associateWith { if (it === provider) closed else CountDownLatch(1) }
                    main {
                        probe.stop(); measuredWindow?.removeOnFrameMetricsAvailableListener(frames)
                        providerClosures.forEach { (current, latch) -> current.stopAndThen { latch.countDown() } }
                        if (provider == null) closed.countDown()
                    }
                    val providerClosed = closed.await(15, TimeUnit.SECONDS)
                    val closureResults = providerClosures.values.map { it.await(15, TimeUnit.SECONDS) }
                    val replacementComplete = closureResults.all { it }
                    allProvidersClosed = previousProvidersClosed && quiesceFailure == null && providerClosed && replacementComplete && providerContinuityValid
                    phase.put("provider_closed", providerClosed)
                        .put("cleanup_observed_provider_count", providerClosures.size)
                        .put("replacement_provider_closed", replacementComplete)
                    if (requestedBackend == "OPENCL") {
                        if (providerClosed && gpuModel == null && gpuModelReference?.isDone == true) {
                            try { gpuModel = gpuModelReference?.get(0, TimeUnit.SECONDS) }
                            catch (problem: Throwable) { gpuCleanupFailure = problem }
                        }
                        val audit = if (providerClosed) gpuModel?.executionSnapshot() else null
                        phase.put("mnn_execution_after_model_close", audit?.let { JSONObject(it) } ?: JSONObject.NULL)
                        val verified = audit?.get("closed") == true && audit?.get("native_close_verified") == true
                        phase.put("gpu_native_close_verified", verified)
                        allProvidersClosed = allProvidersClosed && verified
                        if (!verified && gpuCleanupFailure == null)
                            gpuCleanupFailure = IllegalStateException("Experimental GPU model closure unverified")
                    }
                    if (hintModes != null) hintModel?.performanceHintsSnapshot()?.let { audit ->
                        phase.put("performance_hints_after_model_close", JSONObject(audit))
                        if (audit["session_close_verified"] != true || audit["closed"] != true)
                            hintCleanupFailure = IllegalStateException("Hint session/model closure unverified")
                    }
                    scenario?.close()
                    main { StudyCollection.stop("live_performance_phase_end") }
                    check(providerClosed) { "Provider close timed out; isolated evidence retained at $storage" }
                    visits.forEach { check(it.awaitClosed(10_000)) { "Writer close timed out at $storage" } }
                    phase.put("timing", stats.json())
                    quiesceFailure?.let { problem ->
                        if (phaseFailure == null) throw problem else phaseFailure?.addSuppressed(problem)
                    }
                    cpuRestoreFailure?.let { throw it }
                    priorityRestoreFailure?.let { problem ->
                        if (phaseFailure == null) throw problem else phaseFailure?.addSuppressed(problem)
                    }
                    hintCleanupFailure?.let { problem ->
                        if (phaseFailure == null) throw problem else phaseFailure?.addSuppressed(problem)
                    }
                    gpuCleanupFailure?.let { problem ->
                        if (phaseFailure == null) throw problem else phaseFailure?.addSuppressed(problem)
                    }
                }
            }
            summary.put("packages", packageSummaries(storage, visits))
            check(visits.size >= order.count { it == "ON" }) { "Missing real article recording visit." }
            summary.put("writer_health", JSONArray(visits.map { it.health() }))
            successful = true
        } catch (problem: Throwable) {
            failure = problem
            summary.put("failure", problem.javaClass.simpleName + ": " + problem.message)
        } finally {
            try {
                if (isolated) {
                    main { StudyCollection.stop("live_performance_cleanup") }
                    val writersClosed = visits.all { it.awaitClosed(10_000) }
                    summary.put("writers_closed", writersClosed)
                    summary.put("writer_health", JSONArray(visits.map { it.health() }))
                    if (writersClosed && allProvidersClosed) main { StudyCollection.useIsolatedStorageForTest(context, null) }
                    successful = successful && writersClosed
                }
                val calibrationUnchanged = calibrationBefore == MgazeNetCalibrationStore(context).fingerprint()
                summary.put("calibration_fingerprint_unchanged", calibrationUnchanged)
                if (!calibrationUnchanged) {
                    successful = false
                    summary.put("calibration_failure", "Saved calibration changed during diagnostic")
                }
                if (storage.exists()) {
                    check(allProvidersClosed && visits.all { it.awaitClosed(0) }) { "Blocking cleanup: provider/writer still active at $storage" }
                    val owned = storage.canonicalFile
                    check(owned.parentFile == context.cacheDir.canonicalFile && owned.name.startsWith("study-device-test-live-perf-"))
                    // Refuse linked descendants before recursively deleting this newly created directory.
                    check(storage.walkTopDown().all { it.canonicalFile.toPath().startsWith(owned.toPath()) })
                    check(storage.deleteRecursively()) { "Cannot remove isolated diagnostic directory: $storage" }
                    summary.put("isolated_raw_recordings_deleted", true)
                }
            } catch (cleanup: Throwable) {
                successful = false
                summary.put("cleanup_failure", cleanup.javaClass.simpleName + ": " + cleanup.message)
                if (failure == null) failure = cleanup else failure?.addSuppressed(cleanup)
            } finally {
                summary.put("thermal_status_final", thermalStatus()).put("successful", successful)
                if (storage.exists()) summary.put("blocking_cleanup_path", storage.absolutePath)
                File(context.cacheDir, "newsmead-live-performance-summary.json").writeText(summary.toString(2))
            }
        }
        failure?.let { throw it }
        check(successful)
    }

    private fun processStartIdentity(): String {
        val stat = File("/proc/self/stat").readText()
        val startTicks = stat.substring(stat.lastIndexOf(')') + 1).trim().split(Regex("\\s+"))[19]
        check(startTicks.toLongOrNull() != null)
        val bootId = runCatching { File("/proc/sys/kernel/random/boot_id").readText().trim() }.getOrDefault("unavailable")
        return "pid=${android.os.Process.myPid()};start_ticks=$startTicks;boot_id=$bootId"
    }
    private fun fragments(items: List<Fragment>): List<Fragment> = items.flatMap { listOf(it) + fragments(it.childFragmentManager.fragments) }
    private fun field(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name).apply { isAccessible = true }.get(target)
    private fun awaitFaceReadiness(readiness: FaceReadiness, phase: JSONObject) {
        val started = now()
        val deadline = started + FaceReadiness.WAIT_MS
        var ready = false
        var finalState = JSONObject()
        var prompt: Toast? = null
        var nextPromptAt = started
        try {
            while (true) {
                main {
                    val at = now()
                    ready = readiness.isReady(at)
                    finalState = readiness.snapshot(at)
                    if (!ready && at >= nextPromptAt) {
                        prompt?.cancel()
                        prompt = Toast.makeText(context, "Keep looking toward this phone’s front camera", Toast.LENGTH_LONG)
                        prompt?.show()
                        nextPromptAt = at + 4_000
                    }
                }
                val remaining = deadline - now()
                if (ready || remaining <= 0) break
                SystemClock.sleep(minOf(100L, remaining.toLong().coerceAtLeast(1L)))
            }
        } finally { main { prompt?.cancel() } }
        phase.put("face_preflight", finalState.put("status", if (ready) "ready" else "timeout")
            .put("waited_ms", now() - started).put("maximum_wait_ms", FaceReadiness.WAIT_MS)
            .put("required_continuous_face_present_ms", FaceReadiness.CONTINUOUS_MS)
            .put("maximum_observation_gap_and_age_ms", FaceReadiness.GAP_MS)
            .put("definition", "Timestamped non-watchdog camera observations: reason != no_face, including blink; no_face, nonmonotonic capture time or >500ms camera gap resets the run. Latest capture must be 0..500ms old")
            .put("interpretation", "Prior-to-intervention setup check only; not accuracy, tracking eligibility or a relaxed timed coverage guard"))
        check(ready) { "Face setup preflight timed out after 20 seconds; no intervention or timed block started. Keep looking toward this phone's front camera." }
    }
    /** Queued behind construction, before close; never imposes a startup snapshot deadline. */
    private fun postModelReference(provider: MgazeNetGazeProvider): Future<MnnEstimator?> {
        var source: Any? = null
        var worker: ExecutorService? = null
        main {
            source = checkNotNull(field(provider, "source"))
            worker = field(checkNotNull(source), "worker") as? ExecutorService
        }
        return checkNotNull(worker).submit(Callable { field(checkNotNull(source), "model") as? MnnEstimator })
    }

    private fun onModelWorker(provider: MgazeNetGazeProvider, action: (MnnEstimator) -> Map<String, Any?>): Map<String, Any?> {
        var source: Any? = null
        var worker: ExecutorService? = null
        main {
            source = checkNotNull(field(provider, "source")) { "Live source unavailable for hint configuration" }
            worker = field(checkNotNull(source), "worker") as? ExecutorService
        }
        val future = checkNotNull(worker).submit(Callable {
            action(checkNotNull(field(checkNotNull(source), "model") as? MnnEstimator))
        })
        return try { future.get(5, TimeUnit.SECONDS) } finally { if (!future.isDone) future.cancel(false) }
    }
    private fun thermalStatus(): Any = if (android.os.Build.VERSION.SDK_INT >= 29)
        (context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager).currentThermalStatus else JSONObject.NULL

    private fun packageSummaries(storage: File, visits: List<StudyVisit>): JSONArray {
        val root = File(storage, "study-packages-v1")
        check(root.isDirectory && visits.isNotEmpty()) { "Missing isolated recording root/visits" }
        val directories = root.listFiles()?.filter { it.isDirectory } ?: emptyList()
        check(directories.map { it.name }.toSet() == visits.map { it.packageId }.toSet() && directories.size == visits.size) { "Isolated package inventory mismatch" }
        val summaries = JSONArray()
        directories.forEach { directory ->
            check(directory.canonicalFile.parentFile == root.canonicalFile)
            val manifest = JSONObject(File(directory, "manifest.json").readText())
            val health = visits.single { it.packageId == directory.name }.health()
            check(health.isNull("failure") && health.getLong("lost_records") == 0L && health.getString("state") == "finished") { "Unhealthy isolated writer" }
            check(manifest.getString("package_id") == directory.name && manifest.getString("status") == "finished")
            check(manifest.getLong("record_count") == health.getLong("written_records")) { "Writer/manifest count mismatch" }
            val chunks = manifest.getJSONArray("chunks")
            check(chunks.length() > 0)
            val counts = JSONObject().put("scope", "Entire package including startup, warmup and cleanup; these are not timed-only rates")
            val kinds = JSONObject(); counts.put("record_kinds", kinds)
            var bytes = 0L; var records = 0L
            for (i in 0 until chunks.length()) {
                val chunk = chunks.getJSONObject(i)
                val file = File(directory, chunk.getString("file"))
                check(file.canonicalFile.parentFile == directory.canonicalFile && file.length() == chunk.getLong("byte_count"))
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input -> val buffer = ByteArray(8192); while (true) {
                    val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count)
                } }
                check(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == chunk.getString("sha256"))
                var actualRows = 0L
                StudyChunkIO.open(file, chunk).bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        val row = JSONObject(line); actualRows++
                        val kind = row.getString("kind"); increment(kinds, kind)
                        val payload = row.getJSONObject("payload")
                        when (kind) {
                            "scanpath_input" -> payload.optJSONObject("sample")?.let { sample ->
                                countField(counts, "scanpath_input_eligible", sample, "eligible")
                                countField(counts, "scanpath_input_exclusion", sample, "exclusion")
                            }
                            "aid_decision" -> payload.optJSONObject("decision")?.let { decision ->
                                countField(counts, "aid_decision_status", decision, "status")
                                countField(counts, "aid_decision_reason", decision, "reason")
                            }
                            "aid_presentation" -> listOf("drawn_proxy", "channel", "reason").forEach {
                                countField(counts, "aid_presentation_$it", payload, it)
                            }
                        }
                    }
                }
                check(actualRows == chunk.getLong("record_count")) { "Decoded chunk row count mismatch" }
                bytes += file.length()
                records += actualRows
            }
            check(records == manifest.getLong("record_count")) { "Chunk/manifest count mismatch" }
            summaries.put(JSONObject().put("status", manifest.getString("status"))
                .put("recording_visit_index", visits.indexOfFirst { it.packageId == directory.name })
                .put("content_complete", manifest.getBoolean("content_complete"))
                .put("unknown_tail", manifest.getBoolean("unknown_tail"))
                .put("losses", manifest.getJSONArray("losses")).put("record_count", manifest.getLong("record_count"))
                .put("decoded_record_counts", counts)
                .put("chunk_hashes_verified", true).put("chunk_bytes", bytes).put("manifest_bytes", File(directory, "manifest.json").length()))
            check(manifest.getBoolean("content_complete")) { "Incomplete isolated recording at $directory" }
        }
        return summaries
    }
    private fun increment(counts: JSONObject, key: String) { counts.put(key, counts.optLong(key) + 1) }
    private fun countField(counts: JSONObject, group: String, payload: JSONObject, field: String) {
        val bucket = counts.optJSONObject(group) ?: JSONObject().also { counts.put(group, it) }
        increment(bucket, if (payload.isNull(field)) "(missing_or_null)" else payload.get(field).toString())
    }

    /** Main-thread only. Stores reason/timing state; no coordinates or features. */
    private class FaceReadiness {
        private var runStart: Double? = null
        private var lastCapture: Double? = null
        private var lastFacePresent = false
        fun observation(observation: MgazeNetGazeProvider.Observation, arrival: Double) {
            if (observation.timeoutWatchdog) return
            val capture = observation.captureMs ?: return
            if (!capture.isFinite() || capture <= 0 || capture > arrival) {
                runStart = null; lastFacePresent = false; return
            }
            val previous = lastCapture
            val interrupted = previous == null || capture <= previous || capture - previous > GAP_MS || arrival - capture > GAP_MS
            lastCapture = capture
            lastFacePresent = observation.reason != "no_face"
            if (!lastFacePresent) runStart = null
            else if (interrupted || runStart == null) runStart = capture
        }
        fun isReady(at: Double): Boolean {
            val capture = lastCapture ?: return false
            val age = at - capture
            if (age !in 0.0..GAP_MS) { runStart = null; return false }
            val start = runStart ?: return false
            return lastFacePresent && capture - start >= CONTINUOUS_MS
        }
        fun snapshot(at: Double) = JSONObject()
            .put("continuous_face_present_ms", runStart?.let { start -> lastCapture?.let { it - start } } ?: 0.0)
            .put("latest_observation_age_ms", lastCapture?.let { at - it } ?: JSONObject.NULL)
            .put("latest_observation_face_present", lastFacePresent)
            .put("time_basis", "Camera captureMs and elapsedRealtime main polling, milliseconds")
        companion object {
            const val WAIT_MS = 20_000L
            const val CONTINUOUS_MS = 1_000.0
            const val GAP_MS = 500.0
        }
    }

    private class Aggregate {
        var count = 0L; private var sum = 0.0; private var max = 0.0
        fun add(value: Double) { if (value.isFinite() && value >= 0) { count++; sum += value; max = maxOf(max, value) } }
        fun json() = JSONObject().put("count", count).put("mean_ms", if (count > 0) sum / count else JSONObject.NULL)
            .put("max_ms", if (count > 0) max else JSONObject.NULL)
    }
    private class Counters {
        var begin = Double.POSITIVE_INFINITY; var end = Double.POSITIVE_INFINITY
        var cameraResults = 0L; private var early = 0L; private var late = 0L; private var midpoint = Double.POSITIVE_INFINITY
        var failure: String? = null
        private val reasons = linkedMapOf<String, Long>()
        private val age = Aggregate(); private val callback = Aggregate(); val lateness = Aggregate()
        val gazeCallback = Aggregate(); val frameDuration = Aggregate(); var frameObserverDrops = 0L
        private var firstArrivals: Long? = null; private var lastArrivals: Long? = null
        private var firstBusy: Long? = null; private var lastBusy: Long? = null
        var terminalArrivals: Long? = null; var terminalBusy: Long? = null
        private var ageAtMost250 = 0L; private var ageOver250 = 0L; private var ageUnavailable = 0L
        fun start(at: Double, durationMs: Double) { begin = at; midpoint = at + durationMs / 2 }
        fun isTimed(at: Double) = at >= begin && at < end
        fun observation(o: MgazeNetGazeProvider.Observation, cost: Double, at: Double) {
            if (o.reason == "stopped") { terminalArrivals = o.arrivals; terminalBusy = o.busyDrops }
            if (at < begin || at >= end) return
            callback.add(cost); reasons[o.reason] = (reasons[o.reason] ?: 0) + 1
            if (!o.timeoutWatchdog && o.captureMs != null) {
                cameraResults++
                if (at < midpoint) early++ else late++
                val captureAge = o.captureMs?.let { o.outputMs - it }
                if (captureAge == null || !captureAge.isFinite() || captureAge < 0) ageUnavailable++
                else { age.add(captureAge); if (captureAge <= 250) ageAtMost250++ else ageOver250++ }
                if (firstArrivals == null) firstArrivals = o.arrivals
                if (firstBusy == null) firstBusy = o.busyDrops
                lastArrivals = o.arrivals; lastBusy = o.busyDrops
            }
        }
        fun json(): JSONObject {
            val elapsed = end - begin
            val valid = elapsed.isFinite() && elapsed > 0
            return JSONObject().put("actual_timed_seconds", if (valid) elapsed / 1000 else JSONObject.NULL)
                .put("camera_result_callbacks", cameraResults)
                .put("camera_result_hz", if (valid) cameraResults * 1000 / elapsed else JSONObject.NULL)
                .put("coordinate_hz", if (valid) (reasons["coordinate"] ?: 0) * 1000.0 / elapsed else JSONObject.NULL)
                .put("rate_definition", "camera_result_hz counts timed non-watchdog observations with capture timestamps, including no_face; coordinate_hz counts coordinate observations")
                .put("early_seconds", if (valid) (minOf(midpoint, end) - begin) / 1000 else JSONObject.NULL)
                .put("late_seconds", if (valid) maxOf(0.0, end - midpoint) / 1000 else JSONObject.NULL)
                .put("early_camera_result_hz", if (valid) early * 1000 / (minOf(midpoint, end) - begin) else JSONObject.NULL)
                .put("late_camera_result_hz", if (valid && end > midpoint) late * 1000 / (end - midpoint) else JSONObject.NULL)
                .put("reason_counts", JSONObject(reasons as Map<*, *>))
                .put("no_face_ratio_camera_results", if (cameraResults > 0) (reasons["no_face"] ?: 0).toDouble() / cameraResults else JSONObject.NULL)
                .put("capture_to_delivery", age.json()).put("original_observation_callback_cost", callback.json())
                .put("camera_result_capture_age_at_most_250ms", ageAtMost250)
                .put("camera_result_capture_age_over_250ms", ageOver250).put("camera_result_capture_age_unavailable", ageUnavailable)
                .put("capture_age_count_interpretation", "Descriptive delivery age for timed camera results; not an accuracy gate or proof of aid eligibility")
                .put("original_on_gaze_callback_cost", gazeCallback.json()).put("window_frame_total_duration", frameDuration.json())
                .put("frame_metrics_observer_dropped_reports", frameObserverDrops)
                .put("main_handler_lateness", lateness.json()).put("provider_failure", failure ?: JSONObject.NULL)
                .put("analyzer_arrival_delta", delta(firstArrivals, lastArrivals)).put("observed_busy_drop_delta", delta(firstBusy, lastBusy))
                .put("terminal_analyzer_arrivals", terminalArrivals ?: JSONObject.NULL).put("terminal_busy_drops", terminalBusy ?: JSONObject.NULL)
        }
        private fun delta(a: Long?, b: Long?): Any = if (a != null && b != null) b - a else JSONObject.NULL
    }
    private inner class LatenessProbe(private val stats: Counters) : Runnable {
        private var active = false; private var expected = 0L
        fun start() { active = true; expected = SystemClock.uptimeMillis() + 100; handler.postAtTime(this, expected) }
        fun stop() { active = false; handler.removeCallbacks(this) }
        override fun run() {
            if (!active) return
            stats.lateness.add((SystemClock.uptimeMillis() - expected).toDouble())
            expected = SystemClock.uptimeMillis() + 100
            handler.postAtTime(this, expected)
        }
    }
}
