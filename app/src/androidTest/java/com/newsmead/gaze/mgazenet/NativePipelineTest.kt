package com.newsmead.gaze.mgazenet

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.framework.image.BitmapImageBuilder
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.opencv.android.OpenCVLoader
import java.nio.ByteBuffer

/** Camera-free instrumentation. Compiling does not run these native checks. */
class NativePipelineTest {
    @Before fun initialize() { check(OpenCVLoader.initLocal()) }

    @Test fun rgbaStrideRotationAndNoMirror() {
        // Two rows, padding after row 0, no padding after last row.
        val raw = ByteArray(20)
        listOf(0,4,12,16).forEachIndexed { i, offset ->
            raw[offset] = ((i+1)*10).toByte(); raw[offset+1] = 5; raw[offset+2] = 7; raw[offset+3] = 255.toByte()
        }
        CameraFrames().use { f ->
            for ((rotation, red) in mapOf(0 to listOf(10,20,30,40),90 to listOf(30,10,40,20),180 to listOf(40,30,20,10),270 to listOf(20,40,10,30))) {
                val rgb = f.rgb(f.copyRgba(ByteBuffer.wrap(raw),2,2,12,rotation)).rgb
                assertEquals(red, (0..3).map { rgb[it*3].toInt() and 255 })
                assertTrue((0..3).all { rgb[it*3+1].toInt()==5 && rgb[it*3+2].toInt()==7 })
            }
            val tall = f.copyRgba(ByteBuffer.wrap(byteArrayOf(10,5,7,-1,20,5,7,-1,30,5,7,-1)),1,3,4,90)
            assertEquals(3,tall.width); assertEquals(1,tall.height)
            assertEquals(listOf(30,20,10), (0..2).map { f.rgb(tall).rgb[it*3].toInt() })
        }
    }
    @Test fun rgbPlanarPackingFlipsOnlyRightEye() {
        // At resize corners, INTER_LINEAR preserves endpoint colors.
        val frame = RgbFrame(2,1, byteArrayOf(-1,0,0,0,0,-1))
        val box = GazeGeometry.Box(0,0,2,1)
        Preprocessor().use {
            val p = it.prepare(frame, GazeGeometry.Crops(box,box,box,0.0,0.0))
            assertEquals(1f,p.face[0],0f); assertEquals(0f,p.face[223],0f)
            assertEquals(0f,p.face[224*224],0f); assertEquals(1f,p.face[2*224*224+223],0f)
            assertEquals(1f,p.left[0],0f); assertEquals(0f,p.right[0],0f)
            assertEquals(1f,p.right[2*112*112],0f)
        }
    }
    @Test fun bitmapAndPackedRgbPreprocessingAreExactlyEquivalent() {
        val pixels = intArrayOf(
            Color.rgb(255,0,0), Color.rgb(0,255,0), Color.rgb(0,0,255),
            Color.rgb(15,31,63), Color.rgb(127,191,223), Color.rgb(255,255,255),
        )
        val bitmap = Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels,0,3,0,0,3,2)
        }
        val packed = ByteArray(pixels.size * 3)
        pixels.forEachIndexed { index, color ->
            packed[index * 3] = Color.red(color).toByte()
            packed[index * 3 + 1] = Color.green(color).toByte()
            packed[index * 3 + 2] = Color.blue(color).toByte()
        }
        val box = GazeGeometry.Box(0,0,3,2)
        val crops = GazeGeometry.Crops(box,box,box,0.0,0.0)
        val packedInputs = Preprocessor().use { prep ->
            prep.prepare(RgbFrame(3,2,packed),crops).let {
                Preprocessor.Inputs(it.face.copyOf(),it.left.copyOf(),it.right.copyOf(),it.rect.copyOf())
            }
        }
        val bitmapInputs = Preprocessor().use { prep ->
            prep.prepare(bitmap,crops).let {
                Preprocessor.Inputs(it.face.copyOf(),it.left.copyOf(),it.right.copyOf(),it.rect.copyOf())
            }
        }
        assertArrayEquals(packedInputs.face,bitmapInputs.face,0f)
        assertArrayEquals(packedInputs.left,bitmapInputs.left,0f)
        assertArrayEquals(packedInputs.right,bitmapInputs.right,0f)
        assertArrayEquals(packedInputs.rect,bitmapInputs.rect,0f)
        bitmap.recycle()
    }
    @Test fun bitmapAndPackedRgbSubCropPreprocessingAreExactlyEquivalent() {
        val width = 16
        val height = 12
        val pixels = IntArray(width * height) { i ->
            val x = i % width
            val y = i / width
            Color.rgb((x * 17 + y * 5) and 255, (y * 23 + x * 3) and 255, ((x + y) * 19) and 255)
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
        val packed = ByteArray(pixels.size * 3)
        pixels.forEachIndexed { index, color ->
            packed[index * 3] = Color.red(color).toByte()
            packed[index * 3 + 1] = Color.green(color).toByte()
            packed[index * 3 + 2] = Color.blue(color).toByte()
        }
        val crops = GazeGeometry.Crops(
            face = GazeGeometry.Box(3, 2, 11, 9),
            left = GazeGeometry.Box(1, 3, 5, 4),
            right = GazeGeometry.Box(8, 2, 6, 5),
            leftOpenness = 12.0,
            rightOpenness = 14.0,
        )
        val packedInputs = Preprocessor().use { prep ->
            prep.prepare(RgbFrame(width, height, packed), crops).let {
                Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
            }
        }
        val bitmapInputs = Preprocessor().use { prep ->
            prep.prepare(bitmap, crops).let {
                Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
            }
        }
        assertArrayEquals(packedInputs.face, bitmapInputs.face, 0f)
        assertArrayEquals(packedInputs.left, bitmapInputs.left, 0f)
        assertArrayEquals(packedInputs.right, bitmapInputs.right, 0f)
        assertArrayEquals(packedInputs.rect, bitmapInputs.rect, 0f)
        bitmap.recycle()
    }
    @Test fun preprocessingBeforeMediaPipeCloseMatchesPackedRgbAndRecyclesOnClose() {
        val raw = ByteArray(480 * 640 * 4)
        for (i in 0 until 480 * 640) {
            raw[i * 4] = ((i * 7) and 255).toByte()
            raw[i * 4 + 1] = ((i * 13 + 31) and 255).toByte()
            raw[i * 4 + 2] = ((i * 29 + 97) and 255).toByte()
            raw[i * 4 + 3] = 255.toByte()
        }
        val crops = GazeGeometry.Crops(
            face = GazeGeometry.Box(40, 60, 260, 260),
            left = GazeGeometry.Box(90, 120, 72, 54),
            right = GazeGeometry.Box(190, 120, 72, 54),
            leftOpenness = 100.0,
            rightOpenness = 100.0,
        )
        CameraFrames().use { frames ->
            val bitmap = frames.copyRgba(ByteBuffer.wrap(raw), 640, 480, 640 * 4, 90)
            val mp = BitmapImageBuilder(bitmap).build()
            val expected = Preprocessor().use { prep ->
                prep.prepare(frames.rgb(bitmap), crops).let {
                    Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                }
            }
            val actual = try {
                Preprocessor().use { prep ->
                    prep.prepare(bitmap, crops).let {
                        Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                    }
                }
            } finally {
                mp.close()
            }
            assertTrue(bitmap.isRecycled)
            assertArrayEquals(expected.face, actual.face, 0f)
            assertArrayEquals(expected.left, actual.left, 0f)
            assertArrayEquals(expected.right, actual.right, 0f)
            assertArrayEquals(expected.rect, actual.rect, 0f)
            val next = frames.copyRgba(ByteBuffer.wrap(raw), 640, 480, 640 * 4, 90)
            assertFalse(next.isRecycled)
        }
    }
    @Test fun uprightMatAndBitmapPreprocessingMatchAcrossRotationsAndPaddedStride() {
        val width = 18
        val height = 14
        val stride = width * 4 + 12
        val raw = ByteArray(stride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * stride + x * 4
                raw[offset] = ((x * 13 + y * 7) and 255).toByte()
                raw[offset + 1] = ((x * 5 + y * 19 + 11) and 255).toByte()
                raw[offset + 2] = ((x * 23 + y * 3 + 47) and 255).toByte()
                raw[offset + 3] = 255.toByte()
            }
        }
        val box = GazeGeometry.Box(1, 2, 10, 9)
        val crops = GazeGeometry.Crops(box, GazeGeometry.Box(2, 3, 5, 4), GazeGeometry.Box(7, 4, 5, 4), 50.0, 50.0)
        CameraFrames().use { frames ->
            Preprocessor().use { prep ->
                for (rotation in listOf(0, 90, 180, 270)) {
                    val bitmap = frames.copyRgba(ByteBuffer.wrap(raw), width, height, stride, rotation)
                    val matInputs = prep.prepare(frames.uprightMat, crops).let {
                        Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                    }
                    val bitmapInputs = prep.prepare(bitmap, crops).let {
                        Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                    }
                    val rgbInputs = prep.prepare(frames.rgb(bitmap), crops).let {
                        Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                    }
                    assertArrayEquals("face rotation=$rotation", bitmapInputs.face, matInputs.face, 0f)
                    assertArrayEquals("left rotation=$rotation", bitmapInputs.left, matInputs.left, 0f)
                    assertArrayEquals("right rotation=$rotation", bitmapInputs.right, matInputs.right, 0f)
                    assertArrayEquals("rect rotation=$rotation", bitmapInputs.rect, matInputs.rect, 0f)
                    assertArrayEquals("rgb face rotation=$rotation", rgbInputs.face, matInputs.face, 0f)
                    assertArrayEquals("rgb left rotation=$rotation", rgbInputs.left, matInputs.left, 0f)
                    assertArrayEquals("rgb right rotation=$rotation", rgbInputs.right, matInputs.right, 0f)
                    assertArrayEquals("rgb rect rotation=$rotation", rgbInputs.rect, matInputs.rect, 0f)
                }
            }
        }
    }
    @Test fun externallyRecycledUprightBitmapIsRecreatedBeforeTheNextFrame() {
        val raw = byteArrayOf(10,5,7,-1,20,5,7,-1)
        CameraFrames().use { frames ->
            val first = frames.copyRgba(ByteBuffer.wrap(raw),2,1,8,0)
            first.recycle()
            val second = frames.copyRgba(ByteBuffer.wrap(raw),2,1,8,0)
            assertNotSame(first,second)
            assertFalse(second.isRecycled)
            assertEquals(listOf(10,20),(0..1).map { frames.rgb(second).rgb[it*3].toInt() and 255 })
        }
    }
    @Test fun copyRgbaToMatAndUprightBitmapMatchLegacyPathAndRecoverFromRecycle() {
        val width = 18
        val height = 14
        val stride = width * 4 + 12
        val raw = ByteArray(stride * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val offset = y * stride + x * 4
                raw[offset] = ((x * 11 + y * 13) and 255).toByte()
                raw[offset + 1] = ((x * 7 + y * 5 + 3) and 255).toByte()
                raw[offset + 2] = ((x * 17 + y * 19 + 29) and 255).toByte()
                raw[offset + 3] = 255.toByte()
            }
        }
        val box = GazeGeometry.Box(1, 2, 10, 9)
        val crops = GazeGeometry.Crops(box, GazeGeometry.Box(2, 3, 5, 4), GazeGeometry.Box(7, 4, 5, 4), 50.0, 50.0)
        CameraFrames().use { matFrames ->
            CameraFrames().use { legacyFrames ->
                Preprocessor().use { prep ->
                    for (rotation in listOf(0, 90, 180, 270)) {
                        val mat = matFrames.copyRgbaToMat(ByteBuffer.wrap(raw), width, height, stride, rotation)
                        val legacyBitmap = legacyFrames.copyRgba(ByteBuffer.wrap(raw), width, height, stride, rotation)
                        assertEquals(legacyBitmap.width, mat.cols())
                        assertEquals(legacyBitmap.height, mat.rows())
                        val lazyBitmap = matFrames.uprightBitmap()
                        val matRgb = matFrames.rgb(lazyBitmap).rgb
                        val legacyRgb = legacyFrames.rgb(legacyBitmap).rgb
                        assertArrayEquals("rgb bytes rotation=$rotation", legacyRgb, matRgb)
                        val matInputs = prep.prepare(matFrames.uprightMat, crops).let {
                            Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                        }
                        val legacyInputs = prep.prepare(legacyBitmap, crops).let {
                            Preprocessor.Inputs(it.face.copyOf(), it.left.copyOf(), it.right.copyOf(), it.rect.copyOf())
                        }
                        assertArrayEquals("face rotation=$rotation", legacyInputs.face, matInputs.face, 0f)
                        assertArrayEquals("left rotation=$rotation", legacyInputs.left, matInputs.left, 0f)
                        assertArrayEquals("right rotation=$rotation", legacyInputs.right, matInputs.right, 0f)
                        assertArrayEquals("rect rotation=$rotation", legacyInputs.rect, matInputs.rect, 0f)
                        lazyBitmap.recycle()
                        val recovered = matFrames.uprightBitmap()
                        assertFalse(recovered.isRecycled)
                        assertArrayEquals("recovered rgb rotation=$rotation", legacyRgb, matFrames.rgb(recovered).rgb)
                    }
                }
            }
        }
    }
    @Test fun compactFaceLocalizerRejectsNonFaceAndRunsTrackedPassCleanly() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val width = 480
        val height = 640
        val raw = ByteArray(width * height * 4)
        for (i in 0 until width * height) {
            raw[i * 4] = ((i * 7) and 255).toByte()
            raw[i * 4 + 1] = ((i * 13 + 31) and 255).toByte()
            raw[i * 4 + 2] = ((i * 29 + 97) and 255).toByte()
            raw[i * 4 + 3] = 255.toByte()
        }
        val normX = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT)
        val normY = DoubleArray(CompactFaceLocalizerGeometry.FACE_LANDMARK_COUNT)
        CameraFrames().use { frames ->
            val mat = frames.copyRgbaToMat(ByteBuffer.wrap(raw), width, height, width * 4, 0)
            CompactFaceLocalizer(context).use { localizer ->
                val result = localizer.detect(mat, { frames.uprightBitmap() }, normX, normY)
                assertFalse(result.found)
                assertTrue(result.detectorRan)
                // Previous BlazeFace MPImage.close() recycles uprightBitmap(); next call must recover cleanly.
                val second = localizer.detect(mat, { frames.uprightBitmap() }, normX, normY)
                assertFalse(second.found)
                assertTrue(second.detectorRan)
                val recycled = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { recycle() }
                try {
                    localizer.benchmarkDetectorPass(recycled)
                    fail("Expected recycled bitmap to be rejected before FaceDetector JNI")
                } catch (_: IllegalArgumentException) {
                }
                val presence = localizer.benchmarkTrackedPass(
                    mat,
                    CompactFaceLocalizerGeometry.FaceCrop(240f, 320f, 220f, 0f),
                    normX,
                    normY,
                )
                assertTrue(presence.isFinite() && presence in 0f..1f)
                assertTrue(normX.all { it.isFinite() })
                assertTrue(normY.all { it.isFinite() })
            }
        }
    }
    @Test fun modelHas258StableFeaturesAndRejectsBadInputBeforeJni() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Preprocessor().use { prep -> MnnEstimator(context).use { model ->
            assertEquals("CPU", model.executionBackend)
            assertEquals(MnnEstimator.Precision.LOW, model.precision)
            assertEquals(MnnEstimator.RUNTIME, model.runtimeDescription)
            assertEquals(2, model.performanceHintsSnapshot()["requested_session_threads"])
            assertTrue((model.executionSnapshot()["actual_main_backend"] as? Int) in listOf(0, 13))
            val f = SyntheticFixtures.images().first(); val p = prep.prepare(f.frame,f.crops)
            val a = model.infer(p); assertEquals(258,a.size); assertTrue(a.all { it.isFinite() })
            assertArrayEquals(a, model.infer(p), 1e-5f)
            try { model.infer(p.copy(face = FloatArray(3))); fail("Bad input reached JNI") }
            catch (_: IllegalArgumentException) { }
        } }
    }
    @Test fun nativeOpenCvSupportsFrameLevelSvr() {
        SvrCalibration().use { svr ->
            svr.fit(SyntheticFixtures.calibrationFeatures(),SyntheticFixtures.calibrationLabels())
            assertTrue(svr.trained)
            SyntheticFixtures.queries().forEach { assertTrue(svr.predict(it).all { v -> v.isFinite() }) }
        }
    }
}
