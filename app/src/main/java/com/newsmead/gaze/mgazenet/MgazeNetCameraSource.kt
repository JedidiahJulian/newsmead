package com.newsmead.gaze.mgazenet

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import java.util.Locale
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Explicit-start production source. MNN inference and native calibration are confined to one worker. */
class MgazeNetCameraSource(private val context: Context, private val owner: LifecycleOwner,
    private val ready: (Map<String, Any?>) -> Unit, private val result: (Frame) -> Unit,
    private val failed: (String) -> Unit,
    private val store: MgazeNetCalibrationStore,
    private val saved: CalibrationBundle.Artifact? = null,
    private val eyelidTelemetryEnabled: Boolean = false,
    private val preferStableFrameRate: Boolean = false,
    private val diagnosticMnnThreads: Int = MnnEstimator.DEFAULT_THREADS) {
    init {
        require(diagnosticMnnThreads in 1..8)
        // Diagnostic thread experiments must not load or produce a production calibration.
        require(diagnosticMnnThreads == MnnEstimator.DEFAULT_THREADS || saved == null)
    }
    data class Frame(val captureMs: Double, val outputMs: Double, val features: FloatArray?,
        val leftArea: Double, val rightArea: Double, val prediction: FloatArray?, val reason: String,
        val cropSizes: List<List<Int>>?, val posture: MgazeNetPostureGate.Sample?,
        val arrivals: Long, val busyDrops: Long,
        val eyelids: MgazeNetEyelidGeometry.Sample? = null) {
        val eligible get() = features?.let { it.size == 258 && it.all(Float::isFinite) } == true &&
            leftArea.isFinite() && rightArea.isFinite() && leftArea > 10 && rightArea > 10
    }
    private val main = ContextCompat.getMainExecutor(context)
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val worker = Executors.newSingleThreadExecutor()
    private val stopped = AtomicBoolean(false)
    private val failedOnce = AtomicBoolean(false)
    private val drops = AtomicLong()
    private val arrivals = AtomicLong()
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var model: MnnEstimator? = null
    private var landmarker: FaceLandmarker? = null
    private var preprocessors: List<Preprocessor> = emptyList()
    private val availablePreprocessors = ArrayBlockingQueue<Preprocessor>(PREPROCESSOR_SLOT_COUNT)
    private var svr: SvrCalibration? = null
    private val frames = CameraFrames()
    @Volatile private var clockReady = false
    private var signature: List<Int>? = null
    private var lastTimestamp = -1L
    private var delegateName = "GPU"
    private var requestedFpsRange: Range<Int>? = null
    private val performanceMetrics = PipelinePerformanceMetrics()
    private val admission = BoundedFrameAdmission(PREPROCESSOR_SLOT_COUNT)
    private val landmarkX = DoubleArray(478)
    private val landmarkY = DoubleArray(478)

    fun start() {
        worker.execute {
            try {
                if (stopped.get()) return@execute
                check(org.opencv.android.OpenCVLoader.initLocal())
                check(CalibrationIdentity.hash(context.assets.open("face_landmarker.task").use { it.readBytes() }) == CalibrationIdentity.LOCALIZER)
                store.verifyNativePersistence()
                model = MnnEstimator(context, diagnosticMnnThreads)
                preprocessors = List(PREPROCESSOR_SLOT_COUNT) { Preprocessor() }
                preprocessors.forEach { availablePreprocessors.add(it) }
                svr = saved?.let { store.load(it) } ?: SvrCalibration()
                fun localizer(delegate: Delegate) = FaceLandmarker.createFromOptions(context,
                    FaceLandmarker.FaceLandmarkerOptions.builder().setBaseOptions(BaseOptions.builder()
                        .setModelAssetPath("face_landmarker.task").setDelegate(delegate).build())
                        .setRunningMode(RunningMode.VIDEO).setNumFaces(1).setMinFaceDetectionConfidence(.1f)
                        .setMinTrackingConfidence(.1f).setMinFacePresenceConfidence(.5f).build())
                landmarker = try { localizer(Delegate.GPU) } catch (_: Exception) {
                    delegateName = "CPU_initialization_fallback"; localizer(Delegate.CPU)
                }
                main.execute { if (!stopped.get()) bind() }
            } catch (e: Throwable) { error(e) }
        }
    }
    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun bind() {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (stopped.get()) return@addListener
            try {
                provider = future.get()
                val analysisBuilder = ImageAnalysis.Builder().setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setResolutionSelector(ResolutionSelector.Builder().setResolutionStrategy(
                        ResolutionStrategy(Size(640,480),ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)).build())
                // Live article opt-in only. Keep calibration and frozen measurement defaults.
                // Never request a range the selected front camera does not advertise.
                if (preferStableFrameRate) {
                    val cameraInfo = CameraSelector.DEFAULT_FRONT_CAMERA.filter(provider!!.availableCameraInfos).first()
                    val ranges = Camera2CameraInfo.from(cameraInfo).getCameraCharacteristic(
                        CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES)
                    requestedFpsRange = ranges?.firstOrNull { it.lower == 30 && it.upper == 30 }
                    requestedFpsRange?.let { range ->
                        Camera2Interop.Extender(analysisBuilder).setCaptureRequestOption(
                            CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, range)
                    }
                    Log.i(PERFORMANCE_TAG, "ae_target_fps_request=${requestedFpsRange ?: "default (fixed 30 unsupported)"}")
                }
                analysis = analysisBuilder.build().also { it.setAnalyzer(cameraExecutor,::analyze) }
                val camera = provider!!.bindToLifecycle(owner,CameraSelector.DEFAULT_FRONT_CAMERA,analysis!!)
                check(Camera2CameraInfo.from(camera.cameraInfo).getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) ==
                    CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME) {
                    "Shared camera clock unavailable; accuracy capture is not supported by this build on this configuration."
                }
                clockReady = true
            } catch (e: Throwable) { error(e) }
        },main)
    }
    private fun analyze(image: ImageProxy) {
        if (stopped.get() || !clockReady) { image.close(); return }
        val analyzerStartedNs = SystemClock.elapsedRealtimeNanos()
        arrivals.incrementAndGet()
        if (!admission.tryAcquire()) {
            drops.incrementAndGet()
            performanceMetrics.arrival(analyzerStartedNs, busy = true)?.let { Log.i(PERFORMANCE_TAG, it) }
            image.close()
            return
        }
        performanceMetrics.arrival(analyzerStartedNs, busy = false)?.let { Log.i(PERFORMANCE_TAG, it) }
        var reservedPreprocessor: Preprocessor? = null
        var handedToWorker = false
        try {
            val captureNs = image.imageInfo.timestamp
            check(captureNs > 0 && captureNs <= SystemClock.elapsedRealtimeNanos()) { "Invalid shared camera timestamp" }
            val shape = listOf(image.width,image.height,image.imageInfo.rotationDegrees)
            val acquisition = "${image.width}x${image.height}:${image.imageInfo.rotationDegrees}:$delegateName"
            check(saved == null || saved.identity.acquisition == acquisition) { "Camera/localizer configuration changed" }
            val bitmap = frames.copy(image)
            val copyFinishedNs = SystemClock.elapsedRealtimeNanos()
            if (signature == null) {
                signature = shape
                val metadata = linkedMapOf<String,Any?>(
                    "acquisition" to acquisition,
                    "model_sha256" to MnnEstimator.MODEL_SHA,"runtime" to model!!.runtime,
                    "mnn_cpu_threads" to model!!.threads,
                    "diagnostic_thread_override" to (diagnosticMnnThreads != MnnEstimator.DEFAULT_THREADS),
                    "face_landmarker_sha256" to "64184e229b263107bc2b804c6625db1341ff2bb731874b0bcc2fe6544e0bc9ff",
                    "localizer" to "Tasks 0.10.29 VIDEO one-face detection=.1 tracking=.1 presence=.5",
                    "delegate" to delegateName,"camera_source_shape_rotation" to shape,
                    "upright_size" to listOf(bitmap.width,bitmap.height),"mirrored" to false,
                    "pixel_contract" to "RGBA unpack; upright RGB; OpenCV uint8 INTER_LINEAR /255 NCHW; only right eye flipped",
                    "camera_clock" to "REALTIME verified","output_clock" to "main_callback_delivery_elapsedRealtimeNanos",
                    
                    "android_sdk" to android.os.Build.VERSION.SDK_INT,"opencv" to org.opencv.core.Core.VERSION)
                if (preferStableFrameRate) {
                    metadata["camera_ae_target_fps_request"] = requestedFpsRange?.let { listOf(it.lower,it.upper) }
                }
                main.execute { if (!stopped.get()) ready(metadata) }
            } else check(signature == shape) { "Camera dimensions or orientation changed; new calibration required" }
            val videoMs = captureNs/1_000_000
            check(videoMs > lastTimestamp) { "Nonmonotonic MediaPipe capture clock" }
            lastTimestamp = videoMs
            val mp = BitmapImageBuilder(bitmap).build()
            try {
                // Localization stays serial on the analyzer, while CPU inference for
                // the prior frame can run concurrently on the worker.
                val localizerStartedNs = SystemClock.elapsedRealtimeNanos()
                val detected = landmarker!!.detectForVideo(mp,videoMs)
                val localizerFinishedNs = SystemClock.elapsedRealtimeNanos()
                val landmarks = detected.faceLandmarks().firstOrNull()
                val points = landmarks?.takeIf { it.size == landmarkX.size }
                points?.forEachIndexed { index, point ->
                    landmarkX[index] = point.x().toDouble()
                    landmarkY[index] = point.y().toDouble()
                }
                // Immutable scalars are captured per frame before the analyzer arrays are reused.
                // This telemetry does not alter pinned crop arithmetic or calibration eligibility.
                val eyelids = if (eyelidTelemetryEnabled) points?.let { MgazeNetEyelidGeometry.from(
                    landmarkX, landmarkY, bitmap.width, bitmap.height) } else null
                val crops = points?.let { GazeGeometry.crops(landmarkX,landmarkY,bitmap.width,bitmap.height) }
                val reason = when { landmarks == null -> "no_face"; crops == null -> "invalid_crops"; else -> "features" }
                val posture = crops?.let { MgazeNetPostureGate.from(it,bitmap.width,bitmap.height) }
                // A permit covers localization, preprocessing and the worker submission,
                // including no-face results. No bitmap work or result backlog when both are busy.
                val preprocessor = if (crops == null) null else checkNotNull(availablePreprocessors.poll())
                reservedPreprocessor = preprocessor
                val preprocessStartedNs = SystemClock.elapsedRealtimeNanos()
                val inputs = if (crops == null) null else preprocessor!!.prepare(bitmap,crops)
                val preprocessFinishedNs = SystemClock.elapsedRealtimeNanos()
                val queuedNs = SystemClock.elapsedRealtimeNanos()
                worker.execute {
                    var handedToMain = false
                    var preprocessorReturned = false
                    try {
                        if (stopped.get()) return@execute
                        val workerStartedNs = SystemClock.elapsedRealtimeNanos()
                        val inferenceStartedNs = SystemClock.elapsedRealtimeNanos()
                        val features = inputs?.let { model!!.infer(it) }
                        val inferenceFinishedNs = SystemClock.elapsedRealtimeNanos()
                        val prediction = try { features?.let { if (svr!!.trained) svr!!.predict(it) else null } }
                            catch (e: Throwable) { features?.fill(0f); throw e }
                        val finishedNs = SystemClock.elapsedRealtimeNanos()
                        performanceMetrics.processed(
                            reason = reason,
                            copyMs = (copyFinishedNs - analyzerStartedNs) / 1e6,
                            queueMs = (workerStartedNs - queuedNs) / 1e6,
                            localizerMs = (localizerFinishedNs - localizerStartedNs) / 1e6,
                            preprocessMs = (preprocessFinishedNs - preprocessStartedNs) / 1e6,
                            inferenceMs = (inferenceFinishedNs - inferenceStartedNs) / 1e6,
                            predictionMs = (finishedNs - inferenceFinishedNs) / 1e6,
                            totalMs = (finishedNs - analyzerStartedNs) / 1e6,
                        )
                        // Return mutable input storage before main can release the admission permit.
                        preprocessor?.let { availablePreprocessors.offer(it) }
                        preprocessorReturned = true
                        deliver(captureNs,features,crops,posture,prediction,reason,eyelids)
                        handedToMain = true
                    } catch (e: Throwable) { error(e) }
                    finally {
                        if (!preprocessorReturned) preprocessor?.let { availablePreprocessors.offer(it) }
                        if (!handedToMain) admission.release()
                    }
                }
                handedToWorker = true
            } finally { mp.close() }
        } catch (e: Throwable) { error(e) }
        finally {
            if (!handedToWorker) {
                reservedPreprocessor?.let { availablePreprocessors.offer(it) }
                admission.release()
            }
            image.close()
        }
    }

    private fun deliver(captureNs: Long, features: FloatArray?, crops: GazeGeometry.Crops?,
        posture: MgazeNetPostureGate.Sample?, prediction: FloatArray?, reason: String,
        eyelids: MgazeNetEyelidGeometry.Sample?) {
        main.execute callback@{
            // Delivery time includes main-queue delay. Final queued results precede close completion.
            try {
                if (stopped.get()) return@callback
                val deliveryAgeMs = (SystemClock.elapsedRealtimeNanos() - captureNs) / 1e6
                performanceMetrics.delivered(reason, deliveryAgeMs)
                result(Frame(captureNs/1e6,now(),features,crops?.leftOpenness ?: 0.0,
                crops?.rightOpenness ?: 0.0,prediction,reason,crops?.let {
                    listOf(listOf(it.face.width,it.face.height),listOf(it.left.width,it.left.height),
                        listOf(it.right.width,it.right.height))
                },posture,arrivals.get(),drops.get(),eyelids))
            } finally {
                features?.fill(0f)
                admission.release()
            }
        }
    }
    fun fit(features: Array<FloatArray>, labels: Array<FloatArray>, complete: (Boolean) -> Unit) {
        check(diagnosticMnnThreads == MnnEstimator.DEFAULT_THREADS) { "Diagnostic thread override cannot fit calibration" }
        worker.execute {
            val success = try { if (stopped.get()) false else { svr!!.fit(features,labels); true } }
                catch (_: Throwable) { false }
                finally { features.forEach { it.fill(0f) }; labels.forEach { it.fill(0f) } }
            main.execute { if (!stopped.get()) complete(success) }
        }
    }
    fun save(identity: CalibrationIdentity, complete: (Boolean) -> Unit) {
        check(diagnosticMnnThreads == MnnEstimator.DEFAULT_THREADS) { "Diagnostic thread override cannot save calibration" }
        worker.execute {
            val success = try { if (stopped.get()) false else { store.save(identity,svr!!) { !stopped.get() }; true } }
                catch (_: Throwable) { false }
            main.execute { if (!stopped.get()) complete(success) }
        }
    }
    private fun error(e: Throwable) {
        if (!failedOnce.compareAndSet(false,true)) return
        clockReady = false
        main.execute {
            if (!stopped.get()) {
                // Error class is safe; exception messages may contain native model content.
                failed("MGazeNet stopped (${e.javaClass.simpleName}). Resolve the error before restarting.")
                close {}
            }
        }
    }
    /** Stop capture while retaining the worker-owned fitted models for the explicit Save decision. */
    fun pauseCapture() {
        clockReady = false; analysis?.clearAnalyzer(); analysis?.let { provider?.unbind(it) }
    }
    /** Called on main. Drain analyzer submissions before releasing worker-owned resources. */
    fun close(complete: (Map<String,Any?>) -> Unit) {
        if (!stopped.compareAndSet(false,true)) return
        pauseCapture()
        cameraExecutor.shutdown()
        Thread {
            while (!cameraExecutor.awaitTermination(1,TimeUnit.SECONDS)) { /* wait for outstanding copy */ }
            worker.execute {
                val errors = mutableListOf<String>()
                try {
                    listOf<() -> Unit>({ landmarker?.close() },{ model?.close() },
                        { preprocessors.forEach { it.close() }; preprocessors = emptyList(); availablePreprocessors.clear() },
                        { svr?.close() },{ frames.close() }).forEach { close ->
                        runCatching { close() }.exceptionOrNull()?.let { errors.add(it.toString()) }
                    }
                }
                finally {
                    worker.shutdown()
                    main.execute {
                        performanceMetrics.finish(SystemClock.elapsedRealtimeNanos())?.let { Log.i(PERFORMANCE_TAG, it) }
                        complete(mapOf("analyzer_arrivals" to arrivals.get(),"observed_busy_drops" to drops.get(),
                        "early_admission_busy_drops" to drops.get(),"late_busy_drops" to 0L,
                        "cameraX_undelivered_frames" to "not_observable","resource_close_errors" to errors))
                    }
                }
            }
        }.start()
    }
    companion object {
        private const val PREPROCESSOR_SLOT_COUNT = 2
        private const val PERFORMANCE_TAG = "MGazeNetPerf"
        fun now() = SystemClock.elapsedRealtimeNanos()/1e6
    }
}

/** Permits bound frame work through main delivery, including no-face submissions. */
internal class BoundedFrameAdmission(capacity: Int) {
    init { require(capacity > 0) }
    private val slots = Semaphore(capacity)
    fun tryAcquire(): Boolean = slots.tryAcquire()
    fun release() = slots.release()
}

/** Event-time windows, not capture cohorts. Fixed three buckets; no per-frame history or I/O.
 * Arrival/drop counts refer only to delivered analyzer callbacks (CameraX omissions are unknown).
 * Result rate counts main callback invocations; processed rate counts worker completions.
 */
internal class PipelinePerformanceMetrics(private val windowNs: Long = 2_000_000_000L) {
    init { require(windowNs > 0) }
    private var startedNs: Long? = null
    private var arrivals = 0L
    private var earlyDrops = 0L
    private var arrivalsTotal = 0L
    private var dropsTotal = 0L
    // "features" means usable face/eye crops reached MNN, not a provider-valid coordinate.
    private val face = Stages("face_features")
    private val invalidFace = Stages("face_present_invalid_crops")
    private val noFace = Stages("no_face")

    @Synchronized fun arrival(nowNs: Long, busy: Boolean): String? {
        val start = startedNs ?: nowNs.also { startedNs = it }
        require(nowNs >= start)
        arrivals++
        arrivalsTotal++
        if (busy) { earlyDrops++; dropsTotal++ }
        if (nowNs - start < windowNs) return null
        return report(nowNs)
    }

    @Synchronized fun finish(nowNs: Long): String? = report(nowNs)

    private fun report(nowNs: Long): String? {
        val start = startedNs ?: return null
        if (nowNs <= start) return null
        val seconds = (nowNs - start) / 1e9
        val processed = face.count + invalidFace.count + noFace.count
        val delivered = face.results + invalidFace.results + noFace.results
        val message = String.format(Locale.US,
            "window_s=%.3f samples=%d processed_hz=%.2f results=%d result_hz=%.2f arrivals=%d early_busy_drops=%d late_busy_drops=0 busy_face_status=unobserved arrivals_total=%d drops_total=%d; %s; %s; %s",
            seconds, processed, processed / seconds, delivered, delivered / seconds,
            arrivals, earlyDrops, arrivalsTotal, dropsTotal,
            face.summary(seconds, inference = true), invalidFace.summary(seconds, inference = false),
            noFace.summary(seconds, inference = false))
        startedNs = nowNs
        arrivals = 0
        earlyDrops = 0
        face.clear(); invalidFace.clear(); noFace.clear()
        return message
    }

    @Synchronized fun processed(reason: String, copyMs: Double, queueMs: Double, localizerMs: Double,
        preprocessMs: Double, inferenceMs: Double, predictionMs: Double, totalMs: Double) {
        bucket(reason).apply {
            count++
            copy += copyMs; queue += queueMs; localizer += localizerMs
            preprocess += preprocessMs; inference += inferenceMs; prediction += predictionMs; total += totalMs
        }
    }

    @Synchronized fun delivered(reason: String, deliveryAgeMs: Double) {
        require(deliveryAgeMs.isFinite() && deliveryAgeMs >= 0.0)
        bucket(reason).apply {
            results++
            deliveryAgeTotal += deliveryAgeMs
            deliveryAgeMax = maxOf(deliveryAgeMax, deliveryAgeMs)
        }
    }

    private fun bucket(reason: String): Stages = when (reason) {
        "features" -> face
        "invalid_crops" -> invalidFace
        "no_face" -> noFace
        else -> error("Unknown frame reason")
    }

    private class Stages(val name: String) {
        var count = 0L
        var results = 0L
        var copy = 0.0
        var queue = 0.0
        var localizer = 0.0
        var preprocess = 0.0
        var inference = 0.0
        var prediction = 0.0
        var total = 0.0
        var deliveryAgeTotal = 0.0
        var deliveryAgeMax = 0.0
        fun summary(seconds: Double, inference: Boolean): String {
            if (count == 0L) return String.format(Locale.US,
                "%s processed=0 processed_hz=0.00 results=%d result_hz=%.2f avg_ms=unavailable delivery_age_ms=%s",
                name, results, results / seconds, deliveryAgeSummary())
            val prefix = String.format(Locale.US,
                "%s processed=%d processed_hz=%.2f results=%d result_hz=%.2f avg_ms copy=%.1f queue=%.1f localizer=%.1f analyzer_to_worker=%.1f delivery_age_ms=%s",
                name, count, count / seconds, results, results / seconds,
                copy / count, queue / count, localizer / count, total / count, deliveryAgeSummary())
            return if (inference) prefix + String.format(Locale.US,
                " preprocess=%.1f inference=%.1f svr=%.1f", preprocess / count, this.inference / count, prediction / count)
            else "$prefix preprocess=not_run inference=not_run svr=not_run"
        }
        private fun deliveryAgeSummary(): String = if (results == 0L) "unavailable"
            else String.format(Locale.US, "mean=%.1f,max=%.1f", deliveryAgeTotal / results, deliveryAgeMax)
        fun clear() {
            count = 0; results = 0
            copy = 0.0; queue = 0.0; localizer = 0.0; preprocess = 0.0
            inference = 0.0; prediction = 0.0; total = 0.0
            deliveryAgeTotal = 0.0; deliveryAgeMax = 0.0
        }
    }
}
