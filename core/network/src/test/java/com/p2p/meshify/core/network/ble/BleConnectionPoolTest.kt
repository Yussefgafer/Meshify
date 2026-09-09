package com.p2p.meshify.core.network.ble

import com.p2p.meshify.core.config.AppConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BleConnectionPoolTest {

    private lateinit var pool: BleConnectionPool

    @Before
    fun setUp() {
        pool = BleConnectionPool()
    }

    @Test
    fun addConnection_withinLimit_succeeds() {
        val ok = pool.addConnection("AA:BB:CC:DD:EE:01", BleConnectionType.CLIENT)
        assertTrue(ok)
        assertTrue(pool.isConnected("AA:BB:CC:DD:EE:01"))
        assertEquals(1, pool.getActiveConnectionCount())
        assertTrue(pool.getConnectedPeers().contains("AA:BB:CC:DD:EE:01"))
    }

    @Test
    fun addConnection_beyondMax_rejected() {
        for (i in 0 until AppConfig.BLE_MAX_CONNECTIONS) {
            val ok = pool.addConnection("peer_$i", BleConnectionType.CLIENT)
            assertTrue("peer_$i should fit", ok)
        }
        assertFalse(pool.hasRoom())
        assertEquals(AppConfig.BLE_MAX_CONNECTIONS, pool.getActiveConnectionCount())
        val rejected = pool.addConnection("overflow", BleConnectionType.SERVER)
        assertFalse(rejected)
        assertEquals(AppConfig.BLE_MAX_CONNECTIONS, pool.getActiveConnectionCount())
    }

    @Test
    fun removeConnection_existing_removesAndClearsTimestamp() {
        pool.addConnection("peerA", BleConnectionType.SERVER)
        pool.markActive("peerA")
        assertNotNull(pool.getLastActiveAt("peerA"))
        pool.removeConnection("peerA")
        assertFalse(pool.isConnected("peerA"))
        assertNull(pool.getLastActiveAt("peerA"))
        assertEquals(0, pool.getActiveConnectionCount())
    }

    @Test
    fun removeConnection_unknown_isNoOp() {
        pool.removeConnection("ghost")
        assertEquals(0, pool.getActiveConnectionCount())
    }

    @Test
    fun hasRoom_reflectsCapacity() {
        assertTrue(pool.hasRoom())
        for (i in 0 until AppConfig.BLE_MAX_CONNECTIONS - 1) {
            pool.addConnection("p$i", BleConnectionType.CLIENT)
        }
        assertTrue(pool.hasRoom())
        pool.addConnection("last", BleConnectionType.CLIENT)
        assertFalse(pool.hasRoom())
        pool.removeConnection("last")
        assertTrue(pool.hasRoom())
    }

    @Test
    fun markActive_refreshesTimestamp() {
        pool.addConnection("peerA", BleConnectionType.CLIENT)
        val before = pool.getLastActiveAt("peerA")!!
        Thread.sleep(5)
        pool.markActive("peerA")
        val after = pool.getLastActiveAt("peerA")!!
        assertTrue(after >= before)
    }

    @Test
    fun getLastActiveAt_unknown_returnsNull() {
        assertNull(pool.getLastActiveAt("unknown"))
    }

    @Test
    fun getConnectedPeers_snapshotIsCopy() {
        pool.addConnection("peerA", BleConnectionType.CLIENT)
        val snapshot = pool.getConnectedPeers()
        pool.addConnection("peerB", BleConnectionType.SERVER)
        assertFalse(snapshot.contains("peerB"))
        assertTrue(pool.getConnectedPeers().contains("peerB"))
    }

    @Test
    fun cleanupIdleConnections_removesOnlyStale() {
        pool.addConnection("fresh", BleConnectionType.CLIENT)
        pool.addConnection("stale", BleConnectionType.CLIENT)
        val staleTime = System.currentTimeMillis() - (2 * 60 * 1000L + 5_000L)
        val field = BleConnectionPool::class.java.getDeclaredField("connectionTimestamps")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = field.get(pool) as java.util.concurrent.ConcurrentHashMap<String, Long>
        map["stale"] = staleTime

        val cleaned = pool.cleanupIdleConnections()
        assertEquals(1, cleaned)
        assertFalse(pool.isConnected("stale"))
        assertTrue(pool.isConnected("fresh"))
    }

    @Test
    fun clearAll_removesAll() {
        pool.addConnection("a", BleConnectionType.CLIENT)
        pool.addConnection("b", BleConnectionType.SERVER)
        assertEquals(2, pool.getActiveConnectionCount())
        pool.clearAll()
        assertEquals(0, pool.getActiveConnectionCount())
        assertTrue(pool.getConnectedPeers().isEmpty())
        assertNull(pool.getLastActiveAt("a"))
        assertTrue(pool.hasRoom())
    }

    @Test
    fun connectionTypes_bothSupported() {
        pool.addConnection("serverPeer", BleConnectionType.SERVER)
        pool.addConnection("clientPeer", BleConnectionType.CLIENT)
        assertEquals(2, pool.getActiveConnectionCount())
    }

    @Test
    fun reAddAfterRemove_allowsSamePeer() {
        pool.addConnection("peerA", BleConnectionType.CLIENT)
        pool.removeConnection("peerA")
        assertTrue(pool.addConnection("peerA", BleConnectionType.CLIENT))
        assertEquals(1, pool.getActiveConnectionCount())
    }
}
