package com.p2p.meshify.core.common.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimiterTest {

    @Test
    fun allowRequest_returnsTrueUpToLimit_thenFalse() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limiter = RateLimiter(maxRequests = 3, windowMs = 10_000, scope = scope)

        assertTrue(limiter.allowRequest())
        assertTrue(limiter.allowRequest())
        assertTrue(limiter.allowRequest())
        assertFalse(limiter.allowRequest())
    }

    @Test
    fun allowRequest_isolatesPerIdentifier() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limiter = RateLimiter(maxRequests = 2, windowMs = 10_000, scope = scope)

        assertTrue(limiter.allowRequest("a"))
        assertTrue(limiter.allowRequest("a"))
        assertFalse(limiter.allowRequest("a"))
        // different identifier is unaffected
        assertTrue(limiter.allowRequest("b"))
    }

    @Test
    fun getRemaining_reflectsUsedRequests() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limiter = RateLimiter(maxRequests = 3, windowMs = 10_000, scope = scope)

        assertEquals(3, limiter.getRemainingRequests("a"))
        limiter.allowRequest("a")
        limiter.allowRequest("a")
        assertEquals(1, limiter.getRemainingRequests("a"))
    }

    @Test
    fun reset_clearsIdentifier() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limiter = RateLimiter(maxRequests = 3, windowMs = 10_000, scope = scope)

        limiter.allowRequest("a")
        limiter.allowRequest("a")
        limiter.reset("a")
        assertEquals(3, limiter.getRemainingRequests("a"))
    }

    @Test
    fun clear_removesAllIdentifiers() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limiter = RateLimiter(maxRequests = 3, windowMs = 10_000, scope = scope)

        limiter.allowRequest("a")
        limiter.allowRequest("b")
        limiter.clear()
        assertEquals(3, limiter.getRemainingRequests("a"))
        assertEquals(3, limiter.getRemainingRequests("b"))
    }

    @Test
    fun close_cancelsInternalCleanupJob() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val limiter = RateLimiter(maxRequests = 5, windowMs = 1000, scope = scope)

        val job: Job? = scope.coroutineContext[Job]?.children?.first()
        assertNotNull(job)
        assertFalse(job!!.isCancelled)

        limiter.close()
        assertTrue(job.isCancelled)
    }
}
