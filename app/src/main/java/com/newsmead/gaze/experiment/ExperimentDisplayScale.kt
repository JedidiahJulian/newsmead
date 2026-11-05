package com.newsmead.gaze.experiment

import android.view.View
import com.newsmead.gaze.physicalDisplaySize

/** Same per-axis Android display DPI source as the existing calibration/accuracy checks. */
object ExperimentDisplayScale {
    const val SOURCE = "android_display_metrics_xdpi_ydpi_v1"
    fun snapshot(view: View): PhysicalDisplayScale? = runCatching {
        val size = view.physicalDisplaySize()
        val metrics = view.resources.displayMetrics
        PhysicalDisplayScale(metrics.xdpi / 2.54, metrics.ydpi / 2.54,
            size.x, size.y, view.display.rotation, SOURCE)
    }.getOrNull()
}
