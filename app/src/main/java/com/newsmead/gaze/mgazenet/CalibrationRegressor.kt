package com.newsmead.gaze.mgazenet

interface CalibrationRegressor : AutoCloseable {
    val trained: Boolean
    fun fit(features: Array<FloatArray>, labels: Array<FloatArray>)
    fun predict(features: FloatArray): FloatArray
    override fun close()
}
