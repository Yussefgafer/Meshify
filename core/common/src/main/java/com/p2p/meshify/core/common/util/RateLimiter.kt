package com.p2p.meshify.core.common.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Rate limiter using sliding window algorithm.
 *
 * Prevents abuse by limiting the number of requests allowed within a time window.
 * Thread-safe implementation using ConcurrentHashMap.
 *
 * @param maxRequests Maximum number of requests allowed in the window
 * @param windowMs Time window in milliseconds
 * @param maxIdentifiers Maximum number of identifiers to track (prevents memory exhaustion)
 * @param scope CoroutineScope for lifecycle management (caller must cancel when done)
 */
class RateLimiter(
    private val maxRequests: Int,
    private val windowMs: Long,
    private val maxIdentifiers: Int = 10000, // Limit total tracked identifiers to prevent memory exhaustion
    private val scope: CoroutineScope
) {
    private val timestamps = ConcurrentHashMap<String, MutableList<Long>>()

    // Periodic cleanup task to prevent memory exhaustion
    private val cleanupInterval = windowMs * 2

    // Cleanup job reference for lifecycle management
    private val cleanupJob: Job

    init {
        // Schedule periodic cleanup
        cleanupJob = scope.launch {
            try {
                while (true) {
                    delay(cleanupInterval)
                    cleanup()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Expected on close() - cleanup job cancelled
                throw e
            } catch (e: Exception) {
                // Unexpected error - log and continue cleanup loop
                kotlinx.coroutines.delay(cleanupInterval)
            }
        }
    }

    /**
     * Close the rate limiter and cancel the cleanup job.
     * Call this when the rate limiter is no longer needed to prevent resource leaks.
     */
    fun close() {
        cleanupJob.cancel()
    }

    /**
     * Cleanup old timestamps and enforce max identifiers limit.
     * Called periodically to prevent memory exhaustion.
     */
    private fun cleanup() {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        // Remove expired timestamps and empty entries
        timestamps.entries.removeIf { (_, requestTimestamps) ->
            synchronized(requestTimestamps) {
                requestTimestamps.removeAll { it < windowStart }
                requestTimestamps.isEmpty()
            }
        }

        // Enforce max identifiers limit - remove oldest entries if exceeded
        if (timestamps.size > maxIdentifiers) {
            val oldestEntries = timestamps.entries
                .sortedBy { it.value.minOrNull() ?: 0L }
                .take(timestamps.size - maxIdentifiers)
            oldestEntries.forEach { timestamps.remove(it.key) }
        }
    }

    /**
     * Check if a request is allowed and record it if so.
     *
     * @param identifier Optional identifier to track rate limits per entity (e.g., chatId)
     * @return true if request is allowed, false if rate limit exceeded
     */
    fun allowRequest(identifier: String = "default"): Boolean {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        // Get or create timestamp list for this identifier
        val requestTimestamps = timestamps.getOrPut(identifier) { mutableListOf() }

        // Synchronize on the list to prevent concurrent modification
        synchronized(requestTimestamps) {
            // Remove timestamps outside the current window
            requestTimestamps.removeAll { it < windowStart }

            // Check if we're at the limit
            if (requestTimestamps.size >= maxRequests) {
                return false
            }

            // Record this request
            requestTimestamps.add(now)
            return true
        }
    }

    /**
     * Get the number of remaining requests allowed in the current window.
     *
     * @param identifier The identifier to check
     * @return Number of remaining requests
     */
    fun getRemainingRequests(identifier: String = "default"): Int {
        val now = System.currentTimeMillis()
        val windowStart = now - windowMs

        val requestTimestamps = timestamps[identifier] ?: return maxRequests

        synchronized(requestTimestamps) {
            val validTimestamps = requestTimestamps.filter { it >= windowStart }
            return maxRequests - validTimestamps.size
        }
    }

    /**
     * Reset the rate limiter for a specific identifier.
     *
     * @param identifier The identifier to reset
     */
    fun reset(identifier: String = "default") {
        timestamps.remove(identifier)
    }

    /**
     * Clear all rate limit data.
     */
    fun clear() {
        timestamps.clear()
    }
}
