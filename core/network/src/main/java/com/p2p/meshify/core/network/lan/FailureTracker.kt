package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Tracks send-failure timestamps per peer inside a rolling time window.
 * A peer is considered "dead" when it accumulates [maxFailures] failures
 * within [windowMs].
 *
 * Thread-safe: every read and mutation of a peer's failure list is serialized
 * by a per-peer [ReentrantLock], so concurrent sends and the periodic
 * [cleanupExpired] job can never interleave on the same peer's list.
 */
class FailureTracker(
    private val windowMs: Long = AppConfig.FAILURE_WINDOW_MS,
    private val maxFailures: Int = AppConfig.FAILURE_MAX_FAILURES
) {
    private val failures = ConcurrentHashMap<String, MutableList<Long>>()
    private val peerLocks = ConcurrentHashMap<String, ReentrantLock>()

    private fun peerLock(peerId: String) = peerLocks.getOrPut(peerId) { ReentrantLock() }

    /**
     * Records a failure for [peerId] at [nowMs] and returns true when the
     * peer has reached the dead threshold within the rolling window.
     */
    fun recordFailure(peerId: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        return peerLock(peerId).withLock {
            val timestamps = failures.getOrPut(peerId) { mutableListOf() }
            timestamps.add(nowMs)
            timestamps.removeAll { nowMs - it > windowMs }

            Logger.w("FailureTracker -> $peerId: ${timestamps.size} failures in last ${windowMs / 1000}s")

            if (timestamps.size >= maxFailures) {
                Logger.w("FailureTracker -> Marking $peerId as dead after ${timestamps.size} failures")
                failures.remove(peerId)
                true
            } else {
                false
            }
        }
    }

    /**
     * Clears failure history for [peerId] (called on successful send).
     */
    fun reset(peerId: String) {
        peerLock(peerId).withLock {
            failures.remove(peerId)
        }
    }

    /**
     * Returns the current failure count for [peerId] within the window.
     */
    fun failureCount(peerId: String, nowMs: Long = System.currentTimeMillis()): Int {
        return peerLock(peerId).withLock {
            val timestamps = failures[peerId] ?: return@withLock 0
            timestamps.count { nowMs - it <= windowMs }
        }
    }

    /**
     * Removes entries whose entire window has expired.
     */
    fun cleanupExpired(nowMs: Long = System.currentTimeMillis()) {
        failures.keys.forEach { peerId ->
            peerLock(peerId).withLock {
                val timestamps = failures[peerId] ?: return@withLock
                if (timestamps.none { nowMs - it <= windowMs }) {
                    failures.remove(peerId)
                }
            }
        }
    }
}