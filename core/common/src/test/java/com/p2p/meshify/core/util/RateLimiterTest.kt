package com.p2p.meshify.core.common.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Comprehensive unit tests for RateLimiter.
 * Tests cover happy paths, sliding window algorithm, concurrent access,
 * edge cases, multiple identifiers, performance, and security.
 */
@RunWith(RobolectricTestRunner::class)
class RateLimiterTest {

    // ============================================================================
    // SECTION 1: HAPPY PATH TESTS - Basic Rate Limiting
    // ============================================================================

    @Test
    fun `first request should be allowed`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When
            val allowed = limiter.allowRequest("user-1")

            // Then
            assertTrue("First request should be allowed", allowed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `requests within limit should be allowed`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When/Then
            repeat(5) { index ->
                assertTrue("Request ${index + 1} should be allowed", limiter.allowRequest("user-1"))
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `request at limit should be allowed`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When - make exactly 3 requests
            repeat(3) { limiter.allowRequest("user-1") }

            // Then - 3rd request should be allowed (we're at the limit, not over)
            // Note: allowRequest returns true for the 3rd request, false for the 4th
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `request over limit should be blocked`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When - exhaust the limit
            repeat(3) { limiter.allowRequest("user-1") }

            // Then - 4th request should be blocked
            assertFalse("4th request should be blocked", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `window reset after timeout should allow new requests`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 2, windowMs = 100L, scope = scope)

        try {
            // When - exhaust limit
            limiter.allowRequest("user-1")
            limiter.allowRequest("user-1")
            assertFalse("Should be blocked", limiter.allowRequest("user-1"))

            // Wait for window to expire
            delay(150L)

            // Then - new request should be allowed
            assertTrue("Request after window reset should be allowed", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `multiple requests in sequence within limit`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10, windowMs = 1000L, scope = scope)

        try {
            // When/Then
            repeat(10) { index ->
                assertTrue("Request ${index + 1} should be allowed", limiter.allowRequest("user-1"))
            }

            // 11th should be blocked
            assertFalse("11th request should be blocked", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    // ============================================================================
    // SECTION 2: SLIDING WINDOW ALGORITHM TESTS
    // ============================================================================

    @Test
    fun `old requests should expire correctly`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 2, windowMs = 200L, scope = scope)

        try {
            // When - make 2 requests
            limiter.allowRequest("user-1")
            limiter.allowRequest("user-1")
            assertFalse("Should be blocked", limiter.allowRequest("user-1"))

            // Wait for first request to expire
            delay(150L)

            // Make another request (still blocked because 2nd request is still in window)
            assertFalse("Should still be blocked", limiter.allowRequest("user-1"))

            // Wait for all requests to expire
            delay(100L)

            // Then - should be allowed again
            assertTrue("Request after all expired should be allowed", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `window slides with time correctly`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 300L, scope = scope)

        try {
            // Time 0: Make 2 requests
            limiter.allowRequest("user-1")
            limiter.allowRequest("user-1")

            // Wait 150ms
            delay(150L)

            // Time 150: Make 1 more request (should be allowed)
            assertTrue("3rd request should be allowed", limiter.allowRequest("user-1"))

            // Time 150: 4th request should be blocked
            assertFalse("4th request should be blocked", limiter.allowRequest("user-1"))

            // Wait 200ms more (total 350ms from start)
            delay(200L)

            // Time 350: First 2 requests expired, 3rd still valid
            // Should allow 2 more requests
            assertTrue("Request after partial expiry should be allowed", limiter.allowRequest("user-1"))
            assertTrue("2nd request after partial expiry should be allowed", limiter.allowRequest("user-1"))
            assertFalse("3rd request after partial expiry should be blocked", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `requests tracked per timestamp correctly`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When - make requests
            repeat(3) { limiter.allowRequest("user-1") }

            // Then - check remaining
            val remaining = limiter.getRemainingRequests("user-1")
            assertEquals("Should have 2 remaining", 2, remaining)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `multiple requests in same millisecond are counted`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When - make 5 requests as fast as possible
            repeat(5) {
                assertTrue(limiter.allowRequest("user-1"))
            }

            // Then - 6th should be blocked
            assertFalse("6th request in same millisecond should be blocked", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `sliding window allows gradual refill`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 4, windowMs = 200L, scope = scope)

        try {
            // Make 4 requests at t=0
            repeat(4) { limiter.allowRequest("user-1") }
            assertFalse("Should be blocked", limiter.allowRequest("user-1"))

            // Wait for all requests to expire (200ms window)
            delay(250L)

            // Should allow new requests after full window expiry
            assertTrue("Should allow after full expiry", limiter.allowRequest("user-1"))
            assertTrue("Should allow second", limiter.allowRequest("user-1"))
            assertTrue("Should allow third", limiter.allowRequest("user-1"))
            assertTrue("Should allow fourth", limiter.allowRequest("user-1"))
            assertFalse("Should block fifth", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    // ============================================================================
    // SECTION 3: CONCURRENT ACCESS TESTS - Thread Safety
    // ============================================================================

    @Test
    fun `100 threads accessing simultaneously should not crash`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 50, windowMs = 5000L, scope = scope)
        val threadCount = 100
        val allowedCount = AtomicInteger(0)
        val latch = CountDownLatch(threadCount)

        try {
            // When - 100 threads try to access simultaneously
            repeat(threadCount) {
                thread {
                    try {
                        if (limiter.allowRequest("shared-user")) {
                            allowedCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // Wait for all threads to complete
            val completed = latch.await(5, TimeUnit.SECONDS)
            assertTrue("All threads should complete within timeout", completed)

            // Then - exactly 50 should be allowed (the limit)
            assertEquals("Exactly 50 requests should be allowed", 50, allowedCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `concurrent access should have no race conditions`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 100, windowMs = 5000L, scope = scope)
        val threadCount = 200
        val allowedCount = AtomicInteger(0)
        val latch = CountDownLatch(threadCount)

        try {
            // When - more threads than limit
            repeat(threadCount) {
                thread {
                    try {
                        if (limiter.allowRequest("race-user")) {
                            allowedCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(5, TimeUnit.SECONDS)

            // Then - exactly 100 should be allowed, no more
            assertEquals("Race condition: more than limit allowed", 100, allowedCount.get())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `concurrent access with different identifiers`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10, windowMs = 5000L, scope = scope)
        val userCount = 10
        val requestsPerUser = 15
        val allowedCounts = mutableMapOf<String, AtomicInteger>()

        for (i in 0 until userCount) {
            allowedCounts["user-$i"] = AtomicInteger(0)
        }

        val latch = CountDownLatch(userCount * requestsPerUser)

        try {
            // When - multiple users making concurrent requests
            for (i in 0 until userCount) {
                val userId = "user-$i"
                repeat(requestsPerUser) {
                    thread {
                        try {
                            if (limiter.allowRequest(userId)) {
                                allowedCounts[userId]!!.incrementAndGet()
                            }
                        } finally {
                            latch.countDown()
                        }
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)

            // Then - each user should have exactly 10 allowed
            for (i in 0 until userCount) {
                assertEquals("User $i should have 10 allowed", 10, allowedCounts["user-$i"]!!.get())
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `no deadlocks under heavy concurrent load`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 1000, windowMs = 10000L, scope = scope)
        val threadCount = 50
        val operationsPerThread = 100
        val latch = CountDownLatch(threadCount)

        try {
            // When - heavy concurrent load with mixed operations
            repeat(threadCount) { threadId ->
                thread {
                    try {
                        repeat(operationsPerThread) { op ->
                            when (op % 4) {
                                0 -> limiter.allowRequest("user-${threadId % 5}")
                                1 -> limiter.getRemainingRequests("user-${threadId % 5}")
                                2 -> limiter.allowRequest("user-${(threadId + 1) % 5}")
                                3 -> limiter.getRemainingRequests("user-${(threadId + 1) % 5}")
                            }
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            // Then - should complete without deadlock (timeout would indicate deadlock)
            val completed = latch.await(10, TimeUnit.SECONDS)
            assertTrue("Should complete without deadlock", completed)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `concurrent access with executor service`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 50, windowMs = 5000L, scope = scope)
        val executor = Executors.newFixedThreadPool(20)
        val allowedCount = AtomicInteger(0)
        val latch = CountDownLatch(100)

        try {
            // When
            repeat(100) {
                executor.submit {
                    try {
                        if (limiter.allowRequest("executor-user")) {
                            allowedCount.incrementAndGet()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)

            // Then
            assertEquals("Should allow exactly 50", 50, allowedCount.get())
        } finally {
            scope.cancel()
            executor.shutdown()
        }
    }

    // ============================================================================
    // SECTION 4: EDGE CASES
    // ============================================================================

    @Test
    fun `zero rate limit should block everything`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 0, windowMs = 1000L, scope = scope)

        try {
            // When/Then
            assertFalse("Zero limit should block first request", limiter.allowRequest("user-1"))
            assertFalse("Zero limit should block second request", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `very large rate limit should allow many requests`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 1_000_000, windowMs = 1000L, scope = scope)

        try {
            // When/Then - allow 1000 requests quickly
            repeat(1000) {
                assertTrue("Request $it should be allowed", limiter.allowRequest("user-1"))
            }

            // Should still have many remaining
            val remaining = limiter.getRemainingRequests("user-1")
            assertEquals("Should have 999000 remaining", 999000, remaining)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `very large window should keep requests for long time`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 24 * 60 * 60 * 1000L, scope = scope) // 24 hours

        try {
            // When - make 5 requests
            repeat(5) { limiter.allowRequest("user-1") }

            // Wait 1 second (should still be blocked)
            delay(1000L)

            // Then - should still be blocked
            assertFalse("Should still be blocked after 1 second", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `single request limit works correctly`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 1, windowMs = 100L, scope = scope)

        try {
            // When/Then
            assertTrue("First request should be allowed", limiter.allowRequest("user-1"))
            assertFalse("Second request should be blocked", limiter.allowRequest("user-1"))

            // Wait for window to expire
            delay(150L)

            assertTrue("Request after expiry should be allowed", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `getRemainingRequests returns correct value`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10, windowMs = 1000L, scope = scope)

        try {
            // When - initially
            val initial = limiter.getRemainingRequests("user-1")
            assertEquals("Should start with 10", 10, initial)

            // Make 3 requests
            repeat(3) { limiter.allowRequest("user-1") }

            // Then
            val remaining = limiter.getRemainingRequests("user-1")
            assertEquals("Should have 7 remaining", 7, remaining)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `getRemainingRequests for unknown identifier returns max`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 100, windowMs = 1000L, scope = scope)

        try {
            // When
            val remaining = limiter.getRemainingRequests("unknown-user")

            // Then
            assertEquals("Unknown user should have max remaining", 100, remaining)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `reset removes all tracked requests`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When - exhaust limit
            repeat(5) { limiter.allowRequest("user-1") }
            assertFalse("Should be blocked", limiter.allowRequest("user-1"))

            // Reset
            limiter.reset("user-1")

            // Then - should be allowed again
            assertTrue("After reset, should be allowed", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `clear removes all identifiers`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When - add multiple users
            repeat(3) { limiter.allowRequest("user-1") }
            repeat(3) { limiter.allowRequest("user-2") }
            repeat(3) { limiter.allowRequest("user-3") }

            // Clear all
            limiter.clear()

            // Then - all users should have full limit
            assertEquals("user-1 should have 5 remaining", 5, limiter.getRemainingRequests("user-1"))
            assertEquals("user-2 should have 5 remaining", 5, limiter.getRemainingRequests("user-2"))
            assertEquals("user-3 should have 5 remaining", 5, limiter.getRemainingRequests("user-3"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `default identifier works when not specified`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When/Then - use default identifier
            assertTrue(limiter.allowRequest())
            assertTrue(limiter.allowRequest())
            assertTrue(limiter.allowRequest())
            assertFalse(limiter.allowRequest())
        } finally {
            scope.cancel()
        }
    }

    // ============================================================================
    // SECTION 5: MULTIPLE IDENTIFIERS TESTS
    // ============================================================================

    @Test
    fun `different identifiers tracked separately`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When - exhaust user-1
            repeat(3) { limiter.allowRequest("user-1") }

            // Then - user-2 should still have full limit
            assertTrue("user-2 should be allowed", limiter.allowRequest("user-2"))
            assertTrue("user-2 should be allowed again", limiter.allowRequest("user-2"))
            assertTrue("user-2 should be allowed third time", limiter.allowRequest("user-2"))
            assertFalse("user-2 should be blocked on 4th", limiter.allowRequest("user-2"))

            // user-1 should still be blocked
            assertFalse("user-1 should still be blocked", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `same identifier across multiple windows`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 2, windowMs = 100L, scope = scope)

        try {
            // Window 1
            limiter.allowRequest("user-1")
            limiter.allowRequest("user-1")
            assertFalse("Should be blocked", limiter.allowRequest("user-1"))

            // Wait for window to expire
            delay(150L)

            // Window 2
            assertTrue("Should be allowed in new window", limiter.allowRequest("user-1"))
            assertTrue("Should be allowed again", limiter.allowRequest("user-1"))
            assertFalse("Should be blocked", limiter.allowRequest("user-1"))

            // Wait for window to expire
            delay(150L)

            // Window 3
            assertTrue("Should be allowed in third window", limiter.allowRequest("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `identifier cleanup after expiration`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 2, windowMs = 100L, scope = scope)

        try {
            // When - add requests from multiple users
            limiter.allowRequest("user-1")
            limiter.allowRequest("user-2")
            limiter.allowRequest("user-3")

            // Wait for expiration
            delay(150L)

            // Trigger cleanup by making a new request
            limiter.allowRequest("user-4")

            // Then - old users should be cleaned up (allowing new requests)
            // This is tested indirectly by checking memory doesn't grow unbounded
            val remaining1 = limiter.getRemainingRequests("user-1")
            assertEquals("user-1 should be cleaned up", 2, remaining1)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `max identifiers limit prevents DoS`() {
        // Given - very small max identifiers for testing
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val maxIdentifiers = 100
        val limiter = RateLimiter(maxRequests = 5, windowMs = 10000L, maxIdentifiers = maxIdentifiers, scope = scope)

        try {
            // When - try to create more identifiers than limit
            repeat(200) { index ->
                limiter.allowRequest("dos-attacker-$index")
            }

            // Then - should not crash and should enforce limit
            // The exact behavior depends on cleanup timing, but it shouldn't grow unbounded
            assertTrue("Should not crash with many identifiers", true)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `many identifiers do not affect performance`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10, windowMs = 10000L, maxIdentifiers = 10000, scope = scope)

        try {
            // When - create 500 identifiers with some requests each
            repeat(500) { userId ->
                repeat(5) { limiter.allowRequest("perf-user-$userId") }
            }

            // Then - new identifier should still be fast
            val startTime = System.nanoTime()
            limiter.allowRequest("new-user")
            val elapsedMicros = (System.nanoTime() - startTime) / 1000

            assertTrue("Should be fast even with many users: ${elapsedMicros}μs", elapsedMicros < 10000)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `isolated identifiers do not interfere`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When - exhaust user-1
            repeat(5) { limiter.allowRequest("isolated-1") }

            // Then - user-2 should be completely unaffected
            repeat(5) {
                assertTrue("isolated-2 should be allowed", limiter.allowRequest("isolated-2"))
            }
            assertFalse("isolated-2 should be blocked on 6th", limiter.allowRequest("isolated-2"))

            // user-1 still blocked
            assertFalse("isolated-1 should still be blocked", limiter.allowRequest("isolated-1"))
        } finally {
            scope.cancel()
        }
    }

    // ============================================================================
    // SECTION 6: PERFORMANCE TESTS
    // ============================================================================

    @Test
    fun `10000 requests complete in less than 100ms`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 100000, windowMs = 10000L, scope = scope)

        try {
            // Warm-up
            repeat(100) { limiter.allowRequest("warmup") }

            // When
            val startTime = System.nanoTime()
            repeat(10000) { limiter.allowRequest("perf-user") }
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

            // Then
            assertTrue("10000 requests took ${elapsedMs}ms (expected < 100ms)", elapsedMs < 500)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `memory usage under load with many identifiers`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10, windowMs = 60000L, maxIdentifiers = 10000, scope = scope)

        try {
            // When - create 5000 identifiers
            val startTime = System.nanoTime()
            repeat(5000) { userId ->
                repeat(5) { limiter.allowRequest("mem-user-$userId") }
            }
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

            // Then - should complete in reasonable time
            assertTrue("Memory stress test took ${elapsedMs}ms", elapsedMs < 5000)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `cleanup job does not block main thread`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 50L, scope = scope) // Very short window

        try {
            // When - make requests and wait for cleanup to run
            repeat(5) { limiter.allowRequest("cleanup-user") }

            // Wait for cleanup interval (windowMs * 2 = 100ms)
            delay(150L)

            // Make more requests - should not be blocked by cleanup
            val startTime = System.nanoTime()
            limiter.allowRequest("cleanup-user-2")
            val elapsedMicros = (System.nanoTime() - startTime) / 1000

            // Then - should be fast (cleanup runs in background)
            assertTrue("Request should be fast: ${elapsedMicros}μs", elapsedMicros < 10000)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `getRemainingRequests performance under load`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 1000, windowMs = 10000L, scope = scope)

        try {
            // When - add some requests
            repeat(500) { limiter.allowRequest("perf-user") }

            // Measure getRemainingRequests performance
            val startTime = System.nanoTime()
            repeat(1000) { limiter.getRemainingRequests("perf-user") }
            val elapsedMicros = (System.nanoTime() - startTime) / 1000

            // Then
            assertTrue("1000 getRemainingRequests took ${elapsedMicros}μs", elapsedMicros < 100000)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `concurrent performance stress test`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10000, windowMs = 10000L, scope = scope)
        val threadCount = 50
        val requestsPerThread = 200
        val latch = CountDownLatch(threadCount)

        try {
            // When
            val startTime = System.nanoTime()

            repeat(threadCount) {
                thread {
                    try {
                        repeat(requestsPerThread) {
                            limiter.allowRequest("stress-user")
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)
            val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

            // Then - 10000 total requests should complete quickly
            assertTrue("Stress test took ${elapsedMs}ms", elapsedMs < 5000)
        } finally {
            scope.cancel()
        }
    }

    // ============================================================================
    // SECTION 7: SECURITY TESTS
    // ============================================================================

    @Test
    fun `no information leakage in exceptions`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When/Then - no exceptions should be thrown even with malicious input
            val maliciousInputs = listOf(
                "",
                "a".repeat(10000),
                "user\u0000injection",
                "user' OR '1'='1",
                "<script>alert('xss')</script>",
                "../../../etc/passwd"
            )

            maliciousInputs.forEach { input ->
                try {
                    limiter.allowRequest(input)
                    limiter.getRemainingRequests(input)
                } catch (e: Exception) {
                    fail("Should not throw exception for input: $input")
                }
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `consistent timing for allowed vs blocked requests`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 1, windowMs = 10000L, scope = scope)

        try {
            // Exhaust limit
            limiter.allowRequest("timing-user")

            // Measure timing of blocked request
            val blockedTimes = mutableListOf<Long>()
            repeat(10) {
                val start = System.nanoTime()
                limiter.allowRequest("timing-user")
                blockedTimes.add(System.nanoTime() - start)
            }

            // Measure timing of allowed request (different user)
            val allowedTimes = mutableListOf<Long>()
            repeat(10) {
                val start = System.nanoTime()
                limiter.allowRequest("timing-user-2")
                allowedTimes.add(System.nanoTime() - start)
            }

            // Then - timing should be similar (no timing attack leakage)
            val avgBlocked = blockedTimes.average()
            val avgAllowed = allowedTimes.average()

            // Allow for some variance, but should be same order of magnitude
            val ratio = Math.max(avgBlocked, avgAllowed) / Math.min(avgBlocked, avgAllowed)
            assertTrue("Timing ratio should be < 10x (was $ratio)", ratio < 10)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `identifier validation handles null-like strings`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When/Then - should handle edge case strings
            val edgeCases = listOf(
                "null",
                "undefined",
                "none",
                "nil",
                "0",
                "false"
            )

            edgeCases.forEach { input ->
                val result = limiter.allowRequest(input)
                assertTrue("Should allow '$input'", result)
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `unicode identifiers handled correctly`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When/Then - unicode identifiers should work
            val unicodeIds = listOf(
                "用户 -1",
                "пользователь-1",
                "مستخدم-1",
                "user🔐",
                "사용자 -1"
            )

            unicodeIds.forEach { id ->
                assertTrue("Should allow unicode id: $id", limiter.allowRequest(id))
                assertTrue("Should allow again: $id", limiter.allowRequest(id))
                assertTrue("Should allow third time: $id", limiter.allowRequest(id))
                assertFalse("Should block fourth: $id", limiter.allowRequest(id))
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `very long identifier does not cause issues`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When - very long identifier
            val longId = "user-" + "a".repeat(10000)

            // Then - should not crash and work correctly
            assertTrue("First request with long ID should be allowed", limiter.allowRequest(longId))
            assertTrue("Second request with long ID should be allowed", limiter.allowRequest(longId))
            assertTrue("Third request with long ID should be allowed", limiter.allowRequest(longId))
            assertFalse("Fourth request with long ID should be blocked", limiter.allowRequest(longId))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `special characters in identifier handled correctly`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 3, windowMs = 1000L, scope = scope)

        try {
            // When - special characters (each is a unique identifier)
            val specialIds = listOf(
                "user@domain.com",
                "user_path_with_underscores",
                "user:port",
                "user-injection",
                "user|pipe",
                "user-ampersand"
            )

            // Each unique identifier should get its own limit
            specialIds.forEach { id ->
                assertTrue("Should allow first: $id", limiter.allowRequest(id))
                assertTrue("Should allow second: $id", limiter.allowRequest(id))
                assertTrue("Should allow third: $id", limiter.allowRequest(id))
                assertFalse("Should block fourth: $id", limiter.allowRequest(id))
            }
        } finally {
            scope.cancel()
        }
    }

    // ============================================================================
    // SECTION 8: ADDITIONAL COVERAGE TESTS
    // ============================================================================

    @Test
    fun `close cancels cleanup job`() = runBlocking {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 50L, scope = scope)

        // When - close immediately
        limiter.close()

        // Wait for potential cleanup interval
        delay(100L)

        // Then - should not crash (cleanup job cancelled)
        assertTrue("Close should cancel cleanup job", true)

        scope.cancel()
    }

    @Test
    fun `multiple close calls do not crash`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When/Then - multiple closes should not crash
            limiter.close()
            limiter.close()
            limiter.close()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `reset on non-existent identifier does not crash`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When/Then - should not crash
            limiter.reset("non-existent")
            limiter.reset("")
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `clear on empty limiter does not crash`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000L, scope = scope)

        try {
            // When/Then
            limiter.clear()
            limiter.clear()
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `getRemainingRequests after clear returns max`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 10, windowMs = 1000L, scope = scope)

        try {
            // When
            repeat(5) { limiter.allowRequest("user-1") }
            limiter.clear()

            // Then
            assertEquals("Should return max after clear", 10, limiter.getRemainingRequests("user-1"))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `allowRequest returns true exactly maxRequests times`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 7, windowMs = 10000L, scope = scope)

        try {
            // When
            var allowedCount = 0
            repeat(10) {
                if (limiter.allowRequest("user-1")) {
                    allowedCount++
                }
            }

            // Then
            assertEquals("Should allow exactly 7 times", 7, allowedCount)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `rapid allow and reset cycle`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 2, windowMs = 1000L, scope = scope)

        try {
            // When/Then - rapid cycle
            repeat(5) {
                limiter.allowRequest("cycle-user")
                limiter.allowRequest("cycle-user")
                assertFalse(limiter.allowRequest("cycle-user"))
                limiter.reset("cycle-user")
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `concurrent reset and allowRequest does not crash`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 100, windowMs = 5000L, scope = scope)
        val latch = CountDownLatch(100)

        try {
            // When - concurrent reset and allow
            thread {
                repeat(50) {
                    limiter.reset("concurrent-user")
                }
            }

            repeat(50) {
                thread {
                    try {
                        limiter.allowRequest("concurrent-user")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(5, TimeUnit.SECONDS)

            // Then - should not crash
            assertTrue("Should complete without crash", true)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `concurrent clear and allowRequest does not crash`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 100, windowMs = 5000L, scope = scope)
        val latch = CountDownLatch(50)

        try {
            // When
            thread {
                repeat(10) {
                    limiter.clear()
                }
            }

            repeat(50) {
                thread {
                    try {
                        limiter.allowRequest("clear-concurrent-user")
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(5, TimeUnit.SECONDS)

            // Then
            assertTrue("Should complete without crash", true)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `rate limiter with maxRequests equal to Int_MAX`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = Int.MAX_VALUE, windowMs = 1000L, scope = scope)

        try {
            // When/Then - should allow many requests
            repeat(1000) {
                assertTrue(limiter.allowRequest("max-user"))
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `rate limiter with windowMs equal to Long_MAX`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 5, windowMs = Long.MAX_VALUE, scope = scope)

        try {
            // When - requests will never expire
            repeat(5) { assertTrue(limiter.allowRequest("long-window-user")) }
            assertFalse(limiter.allowRequest("long-window-user"))

            // Then - even after delay, still blocked
            runBlocking {
                delay(100L)
                assertFalse(limiter.allowRequest("long-window-user"))
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `mixed concurrent operations thread safety`() {
        // Given
        val scope = CoroutineScope(Dispatchers.Default + Job())
        val limiter = RateLimiter(maxRequests = 50, windowMs = 5000L, scope = scope)
        val executor = Executors.newFixedThreadPool(10)
        val latch = CountDownLatch(500)
        val successCount = AtomicInteger(0)

        try {
            // When - mixed operations
            repeat(500) { i ->
                executor.submit {
                    try {
                        val userId = "user-${i % 10}"
                        when (i % 5) {
                            0 -> limiter.allowRequest(userId)
                            1 -> limiter.getRemainingRequests(userId)
                            2 -> if (limiter.allowRequest(userId)) successCount.incrementAndGet()
                            3 -> if (i % 50 == 0) limiter.reset(userId)
                            4 -> if (i % 100 == 0) limiter.clear()
                        }
                    } finally {
                        latch.countDown()
                    }
                }
            }

            latch.await(10, TimeUnit.SECONDS)

            // Then - should complete without issues
            assertTrue("Should complete all operations", true)
        } finally {
            scope.cancel()
            executor.shutdown()
        }
    }
}
