package com.newsmead.research.study

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class StudyCaptureContextTest {
    @Test fun delayedCaptureKeepsPreviousPresentationAndSettings() {
        val context = StudyCaptureContext(100.0)
        val previous = context.change(110.0, true, "presentation-before",
            JSONObject().put("font_size_sp", 22).put("aid", "guide"))
        val later = context.change(200.0, null, "presentation-after",
            JSONObject().put("font_size_sp", 28).put("aid", "focus"))

        // The delivery occurs after the change; lookup still uses capture time.
        val delayed = context.at(150.0)!!
        assertEquals(previous.epoch, delayed.epoch)
        assertEquals("presentation-before", delayed.presentationId)
        assertEquals(22, delayed.settings.getInt("font_size_sp"))
        assertEquals("guide", delayed.settings.getString("aid"))
        assertEquals(later.epoch, context.at(200.0)!!.epoch)
        assertEquals("presentation-after", context.at(210.0)!!.presentationId)
        assertEquals(28, context.at(210.0)!!.settings.getInt("font_size_sp"))
        assertTrue(context.at(210.0)!!.allowed)
        assertNotEquals(previous.epoch, later.epoch)
    }

    @Test fun excludedIntervalAndResumeUseCaptureTimeIncludingBoundaries() {
        val context = StudyCaptureContext(100.0)
        val reading = context.change(110.0, true, "reading", JSONObject())
        val excluded = context.change(200.0, false, "paused", JSONObject().put("reason", "background"))
        val stillExcluded = context.change(250.0, null, "paused-updated", JSONObject())
        val resumed = context.change(300.0, true, "resumed", JSONObject())

        assertTrue(context.at(199.0)!!.allowed)
        assertEquals(reading.epoch, context.at(199.0)!!.epoch)
        assertFalse(context.at(200.0)!!.allowed)
        assertEquals(excluded.epoch, context.at(249.0)!!.epoch)
        assertFalse(context.at(250.0)!!.allowed)
        assertEquals(stillExcluded.epoch, context.at(299.0)!!.epoch)
        assertTrue(context.at(300.0)!!.allowed)
        assertEquals(resumed.epoch, context.at(300.0)!!.epoch)
        // Resume must not retroactively make a delayed excluded observation eligible.
        assertFalse(context.at(225.0)!!.allowed)
        assertEquals("paused", context.at(225.0)!!.presentationId)
    }

    @Test fun preStartAndNonfiniteCaptureHaveUnavailableContext() {
        val context = StudyCaptureContext(100.0)
        assertNull(context.at(99.999))
        assertNull(context.at(Double.NaN))
        assertNull(context.at(Double.POSITIVE_INFINITY))
        assertNull(context.at(Double.NEGATIVE_INFINITY))
        assertNotNull(context.at(100.0))
        assertTrue(context.at(100.0)!!.allowed)
        assertNull(context.at(100.0)!!.presentationId)
    }

    @Test fun invalidConsumerSamplesAreNotPresentationTransitions() {
        assertFalse(StudyCaptureContext.presentationBoundary("invalid_sample"))
        assertFalse(StudyCaptureContext.presentationBoundary("non_monotonic_capture"))
        assertTrue(StudyCaptureContext.presentationBoundary("background"))
        assertTrue(StudyCaptureContext.presentationBoundary("presentation_changed"))
    }

    @Test fun maintenanceBlocksStartUntilReleased() {
        val gate = StudyMaintenanceGate()
        gate.claim()
        expectBlocked { gate.startRun() }
        expectBlocked { gate.claim() }
        gate.release()
        gate.startRun()
        gate.endRun()
        gate.claim()
        gate.release()
    }

    @Test fun activeRunBlocksMaintenanceUntilEnded() {
        val gate = StudyMaintenanceGate()
        gate.startRun()
        expectBlocked { gate.claim() }
        gate.endRun()
        gate.claim()
        expectBlocked { gate.startRun() }
        gate.release()
    }

    @Test fun simultaneousRunAndMaintenanceReservationHaveExactlyOneWinner() {
        val gate = StudyMaintenanceGate()
        val barrier = CyclicBarrier(3)
        val done = CountDownLatch(2)
        val runWins = AtomicInteger()
        val maintenanceWins = AtomicInteger()
        val blocked = AtomicInteger()
        val errors = ConcurrentLinkedQueue<Throwable>()

        fun contender(reserve: () -> Unit, wins: AtomicInteger): Thread = Thread {
            try {
                barrier.await(5, TimeUnit.SECONDS)
                try { reserve(); wins.incrementAndGet() }
                catch (_: IllegalStateException) { blocked.incrementAndGet() }
                // Keep the winner's reservation until both attempts complete.
            } catch (error: Throwable) {
                errors.add(error)
            } finally {
                done.countDown()
            }
        }
        val run = contender({ gate.startRun() }, runWins)
        val maintenance = contender({ gate.claim() }, maintenanceWins)
        run.start(); maintenance.start()
        barrier.await(5, TimeUnit.SECONDS)
        assertTrue("Reservation contenders timed out", done.await(5, TimeUnit.SECONDS))
        assertTrue(errors.toString(), errors.isEmpty())
        assertEquals(1, runWins.get() + maintenanceWins.get())
        assertEquals(1, blocked.get())

        if (runWins.get() == 1) {
            expectBlocked { gate.claim() }
            gate.endRun()
            gate.claim()
            gate.release()
        } else {
            expectBlocked { gate.startRun() }
            gate.release()
            gate.startRun()
            gate.endRun()
        }
    }

    private fun expectBlocked(action: () -> Unit) {
        try {
            action()
            fail("Expected reservation to be blocked")
        } catch (_: IllegalStateException) { }
    }
}
