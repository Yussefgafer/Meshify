package com.p2p.meshify.core.network.lan

import io.mockk.mockk
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PooledSocketTest {

    private fun mockSocket(): Socket = mockk(relaxed = true)

    @Test
    fun construction_setsDefaults() {
        val sock = mockSocket()
        val before = System.currentTimeMillis()
        val pooled = PooledSocket(socket = sock)
        val after = System.currentTimeMillis()
        assertEquals(sock, pooled.socket)
        assertTrue(pooled.createdAt in before..after)
        assertTrue(pooled.lastUsedAt in before..after)
        assertFalse(pooled.isInUse)
    }

    @Test
    fun construction_withExplicitValues() {
        val sock = mockSocket()
        val pooled = PooledSocket(socket = sock, createdAt = 1000L, lastUsedAt = 2000L, isInUse = true)
        assertEquals(1000L, pooled.createdAt)
        assertEquals(2000L, pooled.lastUsedAt)
        assertTrue(pooled.isInUse)
    }

    @Test
    fun copy_preservesSocket() {
        val sock = mockSocket()
        val a = PooledSocket(socket = sock, createdAt = 100L, lastUsedAt = 200L, isInUse = false)
        val b = a.copy(isInUse = true)
        assertEquals(sock, b.socket)
        assertEquals(100L, b.createdAt)
        assertEquals(200L, b.lastUsedAt)
        assertTrue(b.isInUse)
        assertFalse(a.isInUse)
    }

    @Test
    fun equality_basedOnAllFields() {
        val sock = mockSocket()
        val a = PooledSocket(socket = sock, createdAt = 100L, lastUsedAt = 200L, isInUse = false)
        val b = PooledSocket(socket = sock, createdAt = 100L, lastUsedAt = 200L, isInUse = false)
        val c = PooledSocket(socket = sock, createdAt = 100L, lastUsedAt = 999L, isInUse = false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertFalse(a == c)
    }

    @Test
    fun lastUsedAt_isVolatileAndMutable() {
        val pooled = PooledSocket(socket = mockSocket())
        val original = pooled.lastUsedAt
        Thread.sleep(2)
        pooled.lastUsedAt = System.currentTimeMillis()
        assertTrue(pooled.lastUsedAt >= original)
    }

    @Test
    fun isInUse_isVolatileAndMutable() {
        val pooled = PooledSocket(socket = mockSocket())
        assertFalse(pooled.isInUse)
        pooled.isInUse = true
        assertTrue(pooled.isInUse)
        pooled.isInUse = false
        assertFalse(pooled.isInUse)
    }

    @Test
    fun dataClass_toStringContainsSocket() {
        val pooled = PooledSocket(socket = mockSocket())
        assertNotNull(pooled.toString())
    }
}
