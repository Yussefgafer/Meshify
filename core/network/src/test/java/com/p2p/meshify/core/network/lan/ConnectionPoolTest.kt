package com.p2p.meshify.core.network.lan

import io.mockk.mockk
import io.mockk.verify
import java.net.Socket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPoolTest {

    private fun newSocket(): Socket = mockk(relaxed = true)

    @Test
    fun addConnection_returnsPooledSocketAndIncrementsActiveCount() {
        val pool = ConnectionPool()
        val sock = newSocket()
        val pooled = pool.addConnection("peerA", sock)
        assertNotNull(pooled)
        assertEquals(1, pool.getActiveConnectionCount())
        assertSame(sock, pool.getConnection("peerA"))
    }

    @Test
    fun addConnection_replaceEvictsOldSocketAndReleasesPermit() {
        val pool = ConnectionPool()
        val oldSock = newSocket()
        val newSock = newSocket()
        pool.addConnection("peerA", oldSock)
        val added = pool.addConnection("peerA", newSock)
        assertNotNull(added)
        verify { oldSock.close() }
        assertEquals(1, pool.getActiveConnectionCount())
        // Still tracks only one peer — pool size is bounded
        for (i in 0 until 50) {
            val s = newSocket()
            val ps = pool.addConnection("peer_$i", s)
            assertNotNull("expected addConnection for peer_$i to succeed", ps)
        }
    }

    @Test
    fun addConnection_poolFullRejectsNull() {
        val pool = ConnectionPool()
        // MAX_POOL_SIZE = 100. Filling it, then a 101st must be rejected.
        for (i in 0 until 100) {
            val s = newSocket()
            val ps = pool.addConnection("peer_$i", s)
            assertNotNull("peer_$i should fit", ps)
        }
        val overflow = newSocket()
        val ps = pool.addConnection("overflow", overflow)
        assertNull("101st addConnection must be rejected", ps)
        assertEquals(100, pool.getActiveConnectionCount())
    }

    @Test
    fun removeConnection_releasesPermitAndAllowsRefill() {
        val pool = ConnectionPool()
        for (i in 0 until 100) {
            pool.addConnection("peer_$i", newSocket())
        }
        assertNull(pool.addConnection("overflow", newSocket()))

        // Free one permit
        pool.removeConnection("peer_0")

        val refill = newSocket()
        val ps = pool.addConnection("refilled", refill)
        assertNotNull("permit should be available after remove", ps)
        assertEquals(100, pool.getActiveConnectionCount())
    }

    @Test
    fun cleanupIdle_skipsConnectionsInUse() {
        val pool = ConnectionPool()
        val peerA = pool.addConnection("peerA", newSocket())!!
        val peerB = pool.addConnection("peerB", newSocket())!!
        // Make peerA look ancient and in-use, peerB ancient and idle.
        peerA.lastUsedAt = System.currentTimeMillis() - (ConnectionPool.IDLE_TIMEOUT_MS + 60_000L)
        peerA.isInUse = true
        peerB.lastUsedAt = System.currentTimeMillis() - (ConnectionPool.IDLE_TIMEOUT_MS + 60_000L)
        peerB.isInUse = false

        val cleaned = pool.cleanupIdleConnections()
        assertEquals(1, cleaned)
        assertEquals(1, pool.getActiveConnectionCount())
        // peerA still present
        assertEquals(peerA, pool.getActiveConnections()["peerA"])
    }

    @Test
    fun updateLastUsed_refreshesTimestamp() {
        val pool = ConnectionPool()
        val pooled = pool.addConnection("peerA", newSocket())!!
        val original = pooled.lastUsedAt
        Thread.sleep(2)
        pool.updateLastUsed("peerA")
        assertTrue(pooled.lastUsedAt > original)
    }

    @Test
    fun hasValidConnection_returnsFalseForUnknownPeer() {
        val pool = ConnectionPool()
        assertFalse(pool.hasValidConnection("ghost"))
    }

    @Test
    fun clearAll_closesAllSocketsAndResetsPermits() {
        val pool = ConnectionPool()
        val sockets = (0 until 5).map { pool.addConnection("peer_$it", newSocket())!! }
        pool.clearAll()
        sockets.forEach { verify { it.socket.close() } }
        assertEquals(0, pool.getActiveConnectionCount())
        // After clearAll we must be able to fill the pool again
        for (i in 0 until 100) {
            assertNotNull(pool.addConnection("p$i", newSocket()))
        }
    }

    @Test
    fun getOrCreateConnectionLock_returnsSameInstanceForSamePeer() {
        val pool = ConnectionPool()
        val a1 = pool.getOrCreateConnectionLock("peerA")
        val a2 = pool.getOrCreateConnectionLock("peerA")
        assertSame(a1, a2)
    }

    @Test
    fun setConnectionInUse_doesNotRemoveEntry() {
        val pool = ConnectionPool()
        pool.addConnection("peerA", newSocket())
        pool.setConnectionInUse("peerA", true)
        assertEquals(1, pool.getActiveConnectionCount())
    }
}
