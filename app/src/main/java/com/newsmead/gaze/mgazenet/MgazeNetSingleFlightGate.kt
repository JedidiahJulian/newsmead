package com.newsmead.gaze.mgazenet

import java.util.concurrent.atomic.AtomicBoolean

/** Bounds prepared/inference work to one frame; CameraX supplies the next latest frame after release. */
internal class MgazeNetSingleFlightGate {
    private val held = AtomicBoolean(false)

    fun tryAcquire(): Boolean = held.compareAndSet(false, true)

    fun release() {
        check(held.compareAndSet(true, false)) { "Single-flight gate released without ownership" }
    }

    fun isHeld(): Boolean = held.get()
}
