package com.p2p.meshify.core.network.lan

import com.p2p.meshify.domain.model.Payload
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KeepAliveManagerTest {

    private lateinit var pool: ConnectionPool
    private lateinit var sentPayloads: MutableList<Pair<String, Payload>>
    private lateinit var manager: KeepAliveManager

    private val fakeSendPayload: suspend (String, Payload) -> Result<Unit> = { peerId, payload ->
        sentPayloads.add(peerId to payload)
        Result.success(Unit)
    }

    @Before
    fun setUp() {
        pool = ConnectionPool()
        sentPayloads = mutableListOf()
        manager = KeepAliveManager(
            connectionPool = pool,
            senderId = "local_peer",
            sendPayload = fakeSendPayload
        )
    }

    @Test
    fun getKeepAliveInterval_returns60Seconds() {
        assertEquals(60_000L, manager.getKeepAliveInterval())
    }

    @Test
    fun sendKeepAlivePings_noConnections_returns0() = runTest {
        val count = manager.sendKeepAlivePings()
        assertEquals(0, count)
        assertTrue(sentPayloads.isEmpty())
    }

    @Test
    fun sendKeepAlivePings_activeConnection_sendsPing() = runTest {
        pool.addConnection("peer1", mockk(relaxed = true))

        val count = manager.sendKeepAlivePings()

        assertEquals(1, count)
        assertEquals(1, sentPayloads.size)
        assertEquals("peer1", sentPayloads[0].first)
        assertEquals(Payload.PayloadType.SYSTEM_CONTROL, sentPayloads[0].second.type)
        assertEquals("PING", String(sentPayloads[0].second.data))
        assertEquals("local_peer", sentPayloads[0].second.senderId)
    }

    @Test
    fun sendKeepAlivePings_idleConnection_skipped() = runTest {
        pool.addConnection("peer1", mockk(relaxed = true))

        // Set lastUsedAt to longer than half the idle timeout
        val pooledSocket = pool.getActiveConnections()["peer1"]!!
        pooledSocket.lastUsedAt = System.currentTimeMillis() - (ConnectionPool.IDLE_TIMEOUT_MS / 2 + 1)

        val count = manager.sendKeepAlivePings()

        assertEquals(0, count)
        assertTrue(sentPayloads.isEmpty())
    }

    @Test
    fun sendKeepAlivePings_activeConnection_updatesLastUsed() = runTest {
        pool.addConnection("peer1", mockk(relaxed = true))

        val pooledSocket = pool.getActiveConnections()["peer1"]!!
        val before = pooledSocket.lastUsedAt

        manager.sendKeepAlivePings()

        assertTrue(pooledSocket.lastUsedAt >= before)
    }

    @Test
    fun sendKeepAlivePings_failedSend_removesConnection() = runTest {
        val failSend: suspend (String, Payload) -> Result<Unit> = { _, _ ->
            Result.failure(Exception("dead connection"))
        }
        val managerWithFail = KeepAliveManager(
            connectionPool = pool,
            senderId = "local_peer",
            sendPayload = failSend
        )

        pool.addConnection("peer1", mockk(relaxed = true))
        assertEquals(1, pool.getActiveConnectionCount())

        managerWithFail.sendKeepAlivePings()

        assertEquals(0, pool.getActiveConnectionCount())
    }

    @Test
    fun sendKeepAlivePings_multipleConnections_pingsAllActive() = runTest {
        pool.addConnection("peer1", mockk(relaxed = true))
        pool.addConnection("peer2", mockk(relaxed = true))
        pool.addConnection("peer3", mockk(relaxed = true))

        // Make peer2 idle
        val peer2Socket = pool.getActiveConnections()["peer2"]!!
        peer2Socket.lastUsedAt = System.currentTimeMillis() - (ConnectionPool.IDLE_TIMEOUT_MS / 2 + 1)

        val count = manager.sendKeepAlivePings()

        assertEquals(2, count)
        val pingedPeers = sentPayloads.map { it.first }.toSet()
        assertTrue(pingedPeers.contains("peer1"))
        assertTrue(pingedPeers.contains("peer3"))
        assertTrue(!pingedPeers.contains("peer2"))
    }
}
