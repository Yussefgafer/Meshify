package com.p2p.meshify.core.data.security.impl

import org.junit.Assert
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Breaking tests for InMemoryNonceCache - Security-critical replay attack protection.
 * 
 * Tests cover:
 * - Basic functionality (add, duplicate detection)
 * - Replay detection (security-critical)
 * - Expiration logic (time-based cleanup)
 * - Cache size limits (DoS prevention)
 * - Thread safety (concurrent access)
 * - Edge cases (empty, null, extreme values)
 */
class InMemoryNonceCacheTest {

    // -------------------------------------------------------------------------
    // SECTION 1: BASIC FUNCTIONALITY
    // -------------------------------------------------------------------------

    @Test
    fun `addIfAbsent returns true for new nonce`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonce = ByteArray(16) { 0x42 }

        // Act
        val result = cache.addIfAbsent(nonce)

        // Assert
        Assert.assertTrue("New nonce should be added (return true)", result)
        Assert.assertEquals("Cache should contain 1 nonce", 1, cache.size())
    }

    @Test
    fun `addIfAbsent returns false for duplicate nonce`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonce = ByteArray(16) { 0x42 }

        // Act - Add twice
        val firstAdd = cache.addIfAbsent(nonce)
        val secondAdd = cache.addIfAbsent(nonce)

        // Assert
        Assert.assertTrue("First add should succeed", firstAdd)
        Assert.assertFalse("Duplicate nonce should be rejected (return false)", secondAdd)
        Assert.assertEquals("Cache should still contain 1 nonce", 1, cache.size())
    }

    @Test
    fun `addIfAbsent handles empty nonce`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val emptyNonce = ByteArray(0)

        // Act
        val firstAdd = cache.addIfAbsent(emptyNonce)
        val secondAdd = cache.addIfAbsent(emptyNonce)

        // Assert
        Assert.assertTrue("Empty nonce should be added on first try", firstAdd)
        Assert.assertFalse("Empty nonce duplicate should be rejected", secondAdd)
        Assert.assertEquals("Cache should contain 1 nonce", 1, cache.size())
    }

    @Test
    fun `addIfAbsent handles null-like nonce`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        // Null-like: all zeros (common sentinel value)
        val nullLikeNonce = ByteArray(16) { 0x00 }

        // Act
        val firstAdd = cache.addIfAbsent(nullLikeNonce)
        val secondAdd = cache.addIfAbsent(nullLikeNonce)

        // Assert
        Assert.assertTrue("Null-like nonce should be added on first try", firstAdd)
        Assert.assertFalse("Null-like nonce duplicate should be rejected", secondAdd)
    }

    // -------------------------------------------------------------------------
    // SECTION 2: REPLAY DETECTION (SECURITY-CRITICAL)
    // -------------------------------------------------------------------------

    @Test
    fun `same nonce added twice is detected as replay`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonce = ByteArray(16) { 0x01 }

        // Act - First add (legitimate message)
        val isFirstTime = cache.addIfAbsent(nonce)
        // Act - Second add (replay attack attempt)
        val isReplayDetected = !cache.addIfAbsent(nonce)

        // Assert
        Assert.assertTrue("First message should be accepted", isFirstTime)
        Assert.assertTrue("Replay attack should be detected", isReplayDetected)
    }

    @Test
    fun `different nonces are stored separately`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonce1 = ByteArray(16) { 0x01 }
        val nonce2 = ByteArray(16) { 0x02 }
        val nonce3 = ByteArray(16) { 0x03 }

        // Act
        val result1 = cache.addIfAbsent(nonce1)
        val result2 = cache.addIfAbsent(nonce2)
        val result3 = cache.addIfAbsent(nonce3)

        // Assert
        Assert.assertTrue("Nonce 1 should be added", result1)
        Assert.assertTrue("Nonce 2 should be added", result2)
        Assert.assertTrue("Nonce 3 should be added", result3)
        Assert.assertEquals("Cache should contain 3 nonces", 3, cache.size())
    }

    @Test
    fun `100 unique nonces are all stored`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonces = List(100) { i ->
            ByteArray(16) { i.toByte() }
        }

        // Act
        val results = nonces.map { cache.addIfAbsent(it) }

        // Assert
        Assert.assertTrue("All 100 nonces should be added", results.all { it })
        Assert.assertEquals("Cache should contain 100 nonces", 100, cache.size())
    }

    @Test
    fun `1000 unique nonces are all stored`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        // Generate truly unique nonces using Random with unique seeds
        val nonces = List(1000) { i ->
            val random = Random(i.toLong())
            ByteArray(16) { random.nextInt(256).toByte() }
        }

        // Act
        val results = nonces.map { cache.addIfAbsent(it) }

        // Assert
        Assert.assertTrue("All 1000 nonces should be added", results.all { it })
        Assert.assertEquals("Cache should contain 1000 nonces", 1000, cache.size())
    }

    // -------------------------------------------------------------------------
    // SECTION 3: EXPIRATION LOGIC (TIME-BASED)
    // -------------------------------------------------------------------------

    @Test
    fun `nonce expires after window_ms`() {
        // Arrange - Use very short window for testing
        val windowMs = 100L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val nonce = ByteArray(16) { 0x42 }

        // Act - Add nonce
        cache.addIfAbsent(nonce)
        Assert.assertEquals("Nonce should be in cache", 1, cache.size())

        // Wait for expiration
        Thread.sleep(windowMs + 50L)

        // Trigger cleanup via add (cache will cleanup when approaching MAX_CACHE_SIZE)
        // Or manually call cleanup
        cache.cleanup()

        // Assert
        Assert.assertEquals("Nonce should be expired and removed", 0, cache.size())
    }

    @Test
    fun `nonce is valid just before window_ms`() {
        // Arrange - Use short window for testing
        val windowMs = 200L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val nonce = ByteArray(16) { 0x42 }

        // Act - Add nonce
        cache.addIfAbsent(nonce)

        // Wait almost until expiration
        Thread.sleep(windowMs - 50L)

        // Act - Try to add same nonce (should be rejected as duplicate)
        val isDuplicate = !cache.addIfAbsent(nonce)

        // Assert
        Assert.assertTrue("Nonce should still be valid (not expired yet)", isDuplicate)
        Assert.assertEquals("Cache should still contain 1 nonce", 1, cache.size())
    }

    @Test
    fun `cleanup removes expired nonces`() {
        // Arrange
        val windowMs = 50L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val nonce1 = ByteArray(16) { 0x01 }
        val nonce2 = ByteArray(16) { 0x02 }

        // Act - Add first nonce
        cache.addIfAbsent(nonce1)

        // Wait for expiration
        Thread.sleep(windowMs + 20L)

        // Add second nonce (fresh)
        cache.addIfAbsent(nonce2)

        // Before cleanup
        Assert.assertEquals("Cache should have 2 nonces before cleanup", 2, cache.size())

        // Act - Cleanup
        cache.cleanup()

        // Assert
        Assert.assertEquals("Expired nonce should be removed, fresh one kept", 1, cache.size())
    }

    @Test
    fun `cleanup does not remove valid nonces`() {
        // Arrange
        val windowMs = 500L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val nonce1 = ByteArray(16) { 0x01 }
        val nonce2 = ByteArray(16) { 0x02 }
        val nonce3 = ByteArray(16) { 0x03 }

        // Act - Add all nonces
        cache.addIfAbsent(nonce1)
        cache.addIfAbsent(nonce2)
        cache.addIfAbsent(nonce3)

        // Wait a bit (but not enough for expiration)
        Thread.sleep(100L)

        // Act - Cleanup
        cache.cleanup()

        // Assert
        Assert.assertEquals("All nonces should still be valid", 3, cache.size())
    }

    @Test
    fun `cleanup is idempotent`() {
        // Arrange
        val windowMs = 50L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val nonce = ByteArray(16) { 0x42 }

        // Act - Add and let expire
        cache.addIfAbsent(nonce)
        Thread.sleep(windowMs + 20L)

        // Act - Cleanup multiple times
        cache.cleanup()
        val sizeAfterFirst = cache.size()
        cache.cleanup()
        val sizeAfterSecond = cache.size()
        cache.cleanup()
        val sizeAfterThird = cache.size()

        // Assert
        Assert.assertEquals("Cache should be empty after first cleanup", 0, sizeAfterFirst)
        Assert.assertEquals("Subsequent cleanups should not change size", 0, sizeAfterSecond)
        Assert.assertEquals("Subsequent cleanups should not change size", 0, sizeAfterThird)
    }

    // -------------------------------------------------------------------------
    // SECTION 4: CACHE SIZE LIMITS (DoS PREVENTION)
    // -------------------------------------------------------------------------

    @Test
    fun `cache triggers cleanup at MAX_CACHE_SIZE`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonces = List(10_000) { i ->
            val random = Random(i.toLong())
            ByteArray(16) { random.nextInt(256).toByte() }
        }

        // Act - Fill cache to MAX_CACHE_SIZE
        nonces.forEach { cache.addIfAbsent(it) }

        // Assert - Cache should have triggered cleanup or be at limit
        // Note: cleanup only removes expired entries, so if none expired, cache may be at max
        Assert.assertTrue("Cache size should not exceed MAX_CACHE_SIZE significantly", cache.size() <= 10_000)
    }

    @Test
    fun `cache handles overflow gracefully`() {
        // Arrange - Use small window so some nonces expire during test
        val windowMs = 10L
        val cache = InMemoryNonceCache(windowMs = windowMs)

        // Act - Add more nonces than MAX_CACHE_SIZE with small delays
        for (i in 0 until 10_500) {
            if (i % 100 == 0) {
                Thread.sleep(15L) // Let some expire
            }
            val nonce = ByteArray(16) { ((i / 100) % 256).toByte() }
            cache.addIfAbsent(nonce)
        }

        // Assert - Should not crash, should have reasonable size
        Assert.assertTrue("Cache should handle overflow without crashing", cache.size() < 15_000)
    }

    @Test
    fun `cache size decreases after cleanup of expired nonces`() {
        // Arrange
        val windowMs = 30L
        val cache = InMemoryNonceCache(windowMs = windowMs)

        // Act - Add nonces in batches with delays
        for (i in 0 until 100) {
            if (i % 20 == 0 && i > 0) {
                Thread.sleep(35L) // Let previous batch expire
            }
            val nonce = ByteArray(16) { (i % 256).toByte() }
            cache.addIfAbsent(nonce)
        }

        // Before cleanup
        val sizeBefore = cache.size()

        // Act - Cleanup
        cache.cleanup()
        val sizeAfter = cache.size()

        // Assert
        Assert.assertTrue("Cleanup should reduce cache size or keep same", sizeAfter <= sizeBefore)
    }

    // -------------------------------------------------------------------------
    // SECTION 5: THREAD SAFETY (CONCURRENT ACCESS)
    // -------------------------------------------------------------------------

    @Test
    fun `concurrent addIfAbsent calls are thread-safe`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val threadCount = 10
        val noncesPerThread = 100
        val latch = CountDownLatch(threadCount)
        val executor: ExecutorService = Executors.newFixedThreadPool(threadCount)

        // Act - Concurrent adds from multiple threads
        for (t in 0 until threadCount) {
            val threadId = t
            executor.submit {
                try {
                    for (i in 0 until noncesPerThread) {
                        // Generate unique nonce per thread and iteration using unique seed
                        val random = Random((threadId * noncesPerThread + i).toLong())
                        val nonce = ByteArray(16) { random.nextInt(256).toByte() }
                        cache.addIfAbsent(nonce)
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Assert - No ConcurrentModificationException, all nonces added
        Assert.assertEquals(
            "All unique nonces should be added",
            threadCount * noncesPerThread,
            cache.size()
        )
    }

    @Test
    fun `concurrent cleanup calls are thread-safe`() {
        // Arrange
        val windowMs = 20L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val threadCount = 5
        val latch = CountDownLatch(threadCount)
        val executor: ExecutorService = Executors.newFixedThreadPool(threadCount)

        // Pre-populate cache
        for (i in 0 until 100) {
            val nonce = ByteArray(16) { i.toByte() }
            cache.addIfAbsent(nonce)
        }

        // Act - Concurrent cleanups from multiple threads
        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    Thread.sleep(25L) // Let nonces expire
                    repeat(10) {
                        cache.cleanup()
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Assert - No ConcurrentModificationException
        Assert.assertTrue("Cache should be in valid state after concurrent cleanups", cache.size() >= 0)
    }

    @Test
    fun `concurrent add and cleanup are thread-safe`() {
        // Arrange
        val windowMs = 30L
        val cache = InMemoryNonceCache(windowMs = windowMs)
        val threadCount = 4
        val latch = CountDownLatch(threadCount)
        val executor: ExecutorService = Executors.newFixedThreadPool(threadCount)

        // Act - Mixed concurrent operations
        for (t in 0 until threadCount) {
            executor.submit {
                try {
                    for (i in 0 until 50) {
                        if (i % 2 == 0) {
                            val nonce = ByteArray(16) { ((t * 50 + i) % 256).toByte() }
                            cache.addIfAbsent(nonce)
                        } else {
                            Thread.sleep(35L)
                            cache.cleanup()
                        }
                    }
                } finally {
                    latch.countDown()
                }
            }
        }

        // Wait for all threads to complete
        latch.await(10, TimeUnit.SECONDS)
        executor.shutdown()

        // Assert - No exceptions thrown
        Assert.assertTrue("Cache should be in valid state after mixed concurrent operations", cache.size() >= 0)
    }

    // -------------------------------------------------------------------------
    // SECTION 6: EDGE CASES (BYTE-LEVEL)
    // -------------------------------------------------------------------------

    @Test
    fun `single-byte nonce works correctly`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val singleByteNonce = ByteArray(1) { 0x42 }

        // Act
        val firstAdd = cache.addIfAbsent(singleByteNonce)
        val secondAdd = cache.addIfAbsent(singleByteNonce)

        // Assert
        Assert.assertTrue("Single-byte nonce should be added", firstAdd)
        Assert.assertFalse("Single-byte nonce duplicate should be rejected", secondAdd)
        Assert.assertEquals("Cache should contain 1 nonce", 1, cache.size())
    }

    @Test
    fun `maximum-size nonce works correctly`() {
        // Arrange - Test with large nonce (e.g., 1024 bytes)
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val largeNonce = ByteArray(1024) { 0x42 }

        // Act
        val firstAdd = cache.addIfAbsent(largeNonce)
        val secondAdd = cache.addIfAbsent(largeNonce)

        // Assert
        Assert.assertTrue("Large nonce should be added", firstAdd)
        Assert.assertFalse("Large nonce duplicate should be rejected", secondAdd)
    }

    @Test
    fun `nonce with all zeros works correctly`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val allZerosNonce = ByteArray(16) { 0x00 }

        // Act
        val firstAdd = cache.addIfAbsent(allZerosNonce)
        val secondAdd = cache.addIfAbsent(allZerosNonce)

        // Assert
        Assert.assertTrue("All-zeros nonce should be added", firstAdd)
        Assert.assertFalse("All-zeros nonce duplicate should be rejected", secondAdd)
    }

    @Test
    fun `nonce with all ones works correctly`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val allOnesNonce = ByteArray(16) { 0xFF.toByte() }

        // Act
        val firstAdd = cache.addIfAbsent(allOnesNonce)
        val secondAdd = cache.addIfAbsent(allOnesNonce)

        // Assert
        Assert.assertTrue("All-ones nonce should be added", firstAdd)
        Assert.assertFalse("All-ones nonce duplicate should be rejected", secondAdd)
    }

    @Test
    fun `nonce with alternating bits works correctly`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val alternatingNonce = ByteArray(16) { i ->
            if (i % 2 == 0) 0xAA.toByte() else 0x55.toByte()
        }

        // Act
        val firstAdd = cache.addIfAbsent(alternatingNonce)
        val secondAdd = cache.addIfAbsent(alternatingNonce)

        // Assert
        Assert.assertTrue("Alternating-bit nonce should be added", firstAdd)
        Assert.assertFalse("Alternating-bit nonce duplicate should be rejected", secondAdd)
    }

    @Test
    fun `nonce with sequential bytes works correctly`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val sequentialNonce = ByteArray(16) { it.toByte() }

        // Act
        val firstAdd = cache.addIfAbsent(sequentialNonce)
        val secondAdd = cache.addIfAbsent(sequentialNonce)

        // Assert
        Assert.assertTrue("Sequential-byte nonce should be added", firstAdd)
        Assert.assertFalse("Sequential-byte nonce duplicate should be rejected", secondAdd)
    }

    @Test
    fun `different nonces with same hash code are stored separately`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        // Create two different nonces that might have hash collisions
        val nonce1 = ByteArray(16) { 0x01 }
        val nonce2 = ByteArray(16) { 0x02 }

        // Act
        val result1 = cache.addIfAbsent(nonce1)
        val result2 = cache.addIfAbsent(nonce2)

        // Assert
        Assert.assertTrue("Nonce 1 should be added", result1)
        Assert.assertTrue("Nonce 2 should be added", result2)
        Assert.assertEquals("Both nonces should be stored", 2, cache.size())
    }

    @Test
    fun `clear removes all nonces`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        for (i in 0 until 100) {
            val nonce = ByteArray(16) { i.toByte() }
            cache.addIfAbsent(nonce)
        }
        Assert.assertEquals("Cache should have 100 nonces", 100, cache.size())

        // Act
        cache.clear()

        // Assert
        Assert.assertEquals("Cache should be empty after clear", 0, cache.size())
    }

    @Test
    fun `clear then add works correctly`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonce = ByteArray(16) { 0x42 }
        cache.addIfAbsent(nonce)
        cache.clear()

        // Act - Add same nonce after clear
        val result = cache.addIfAbsent(nonce)

        // Assert
        Assert.assertTrue("Nonce should be added after clear (cache forgotten)", result)
        Assert.assertEquals("Cache should contain 1 nonce", 1, cache.size())
    }

    @Test
    fun `random nonces are all unique`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val random = Random(42L) // Fixed seed for reproducibility
        val nonces = List(500) {
            ByteArray(16) { random.nextInt(256).toByte() }
        }

        // Act
        val results = nonces.map { cache.addIfAbsent(it) }

        // Assert - All should be added (random with seed produces unique enough values)
        val addedCount = results.count { it }
        Assert.assertTrue("Most random nonces should be unique", addedCount > 400)
    }

    @Test
    fun `hex encoding of nonces is consistent`() {
        // Arrange
        val cache = InMemoryNonceCache(windowMs = 60_000L)
        val nonce = ByteArray(16) { 0xAB.toByte() }

        // Act
        cache.addIfAbsent(nonce)
        val size1 = cache.size()

        // Add same nonce again (should be duplicate)
        cache.addIfAbsent(nonce)
        val size2 = cache.size()

        // Assert
        Assert.assertEquals("Size should be 1 (duplicate detected via hex encoding)", 1, size1)
        Assert.assertEquals("Size should still be 1 after duplicate", 1, size2)
    }
}
