package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FailureTrackerTest {

    private lateinit var tracker: FailureTracker

    @Before
    fun setUp() {
        tracker = FailureTracker(windowMs = 60_000L, maxFailures = 5)
    }

    @Test
    fun recordFailure_belowThreshold_returnsFalse() {
        val dead = tracker.recordFailure("peer1", nowMs = 1000L)
        assertFalse(dead)
        assertEquals(1, tracker.failureCount("peer1", nowMs = 1000L))
    }

    @Test
    fun recordFailure_atThreshold_returnsTrue() {
        // 4 failures below threshold
        for (i in 1..4) {
            val dead = tracker.recordFailure("peer1", nowMs = 1000L * i)
            assertFalse("Failure $i should not mark dead", dead)
        }
        // 5th failure reaches threshold → returns true and removes entry
        val dead = tracker.recordFailure("peer1", nowMs = 5000L)
        assertTrue("5th failure should mark peer dead", dead)
        assertEquals(0, tracker.failureCount("peer1", nowMs = 5000L))
    }

    @Test
    fun recordFailure_outsideWindow_notCounted() {
        val windowMs = 60_000L
        tracker = FailureTracker(windowMs = windowMs, maxFailures = 3)

        // 3 failures outside the window
        tracker.recordFailure("peer1", nowMs = 0L)
        tracker.recordFailure("peer1", nowMs = 1L)
        tracker.recordFailure("peer1", nowMs = 2L)

        // Now record one just inside the window — only 1 counted
        val dead = tracker.recordFailure("peer1", nowMs = windowMs + 100L)
        assertFalse(dead)
        assertEquals(1, tracker.failureCount("peer1", nowMs = windowMs + 100L))
    }

    @Test
    fun reset_clearsHistory() {
        tracker.recordFailure("peer1", nowMs = 1000L)
        tracker.recordFailure("peer1", nowMs = 2000L)
        assertEquals(2, tracker.failureCount("peer1", nowMs = 2000L))

        tracker.reset("peer1")
        assertEquals(0, tracker.failureCount("peer1", nowMs = 2000L))
    }

    @Test
    fun failureCount_returnsZeroForUnknownPeer() {
        assertEquals(0, tracker.failureCount("unknown"))
    }

    @Test
    fun cleanupExpired_removesStaleEntries() {
        val windowMs = 60_000L
        tracker = FailureTracker(windowMs = windowMs, maxFailures = 10)

        tracker.recordFailure("peer1", nowMs = 0L)
        tracker.recordFailure("peer2", nowMs = windowMs + 100L)

        tracker.cleanupExpired(nowMs = windowMs + 200L)

        // peer1 expired (0 < windowMs+200), peer2 still valid
        assertEquals(0, tracker.failureCount("peer1", nowMs = windowMs + 200L))
        assertEquals(1, tracker.failureCount("peer2", nowMs = windowMs + 200L))
    }

    @Test
    fun multiplePeers_independentTracking() {
        tracker.recordFailure("peerA", nowMs = 1000L)
        tracker.recordFailure("peerA", nowMs = 2000L)
        tracker.recordFailure("peerB", nowMs = 1000L)

        assertEquals(2, tracker.failureCount("peerA", nowMs = 2000L))
        assertEquals(1, tracker.failureCount("peerB", nowMs = 2000L))

        tracker.reset("peerA")
        assertEquals(0, tracker.failureCount("peerA", nowMs = 2000L))
        assertEquals(1, tracker.failureCount("peerB", nowMs = 2000L))
    }

    @Test
    fun defaultConstruction_usesAppConfigConstants() {
        val tracker = FailureTracker()
        // AppConfig.FAILURE_MAX_FAILURES - 1 failures inside the window do not
        // trip the dead threshold; the AppConfig-matching cap does.
        repeat(AppConfig.FAILURE_MAX_FAILURES - 1) { i ->
            val dead = tracker.recordFailure("peer1", nowMs = i * 10_000L)
            assertFalse("Failure ${i + 1} should not mark dead", dead)
        }
        val dead = tracker.recordFailure("peer1", nowMs = 40_000L)
        assertTrue("Failure #${AppConfig.FAILURE_MAX_FAILURES} should mark peer dead", dead)
    }

    @Test
    fun concurrentRecordings_doNotLoseFailures() {
        // Cap above the total (8 threads x 25) so the threshold never trips mid-test
        val tracker = FailureTracker(windowMs = 60_000L, maxFailures = 250)
        val threads = (1..8).map { threadIndex ->
            Thread {
                repeat(25) { iteration ->
                    tracker.recordFailure("peer1", nowMs = (threadIndex * 25 + iteration).toLong())
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }

        // 8 threads x 25 failures, all inside the window and under the cap
        assertEquals(200, tracker.failureCount("peer1", nowMs = 2_000L))
    }
}
