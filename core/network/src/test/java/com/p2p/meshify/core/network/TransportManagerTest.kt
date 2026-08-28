package com.p2p.meshify.core.network

import app.cash.turbine.test
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mocks IMeshTransport to simulate LAN/BLE peer discovery. Limitations:
 * this test verifies the TransportManager's own merging/fan-in logic, not the
 * real LAN or BLE transports — on-device transport behavior must still be
 * validated via feature:real-device-testing on real hardware.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TransportManagerTest {

    private class FakeTransport(
        override val transportName: String,
        override val isAvailable: Boolean = true,
        override val capabilities: Set<TransportCapability> = emptySet()
    ) : IMeshTransport {
        val eventsFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
        private val _online = MutableStateFlow<Set<String>>(emptySet())
        private val _typing = MutableStateFlow<Set<String>>(emptySet())
        private val _runtime = MutableStateFlow(false)

        override val events: kotlinx.coroutines.flow.Flow<TransportEvent> = eventsFlow
        override val onlinePeers = _online
        override val typingPeers = _typing
        override val runtimeActive = _runtime

        fun setOnlinePeers(peers: Set<String>) { _online.value = peers }
        fun setRuntimeActive(active: Boolean) { _runtime.value = active }

        suspend fun emit(event: TransportEvent) { eventsFlow.emit(event) }

        override suspend fun start() {}
        override suspend fun stop() {}
        override suspend fun startDiscovery() {}
        override suspend fun stopDiscovery() {}
        override suspend fun sendPayload(targetDeviceId: String, payload: com.p2p.meshify.domain.model.Payload): Result<Unit> =
            Result.success(Unit)
    }

    private fun makeManager(scheduler: TestCoroutineScheduler): Pair<TransportManager, TestScope> {
        val context = mockk<android.content.Context>(relaxed = true)
        val settings = mockk<ISettingsRepository>(relaxed = true)
        every { settings.transportMode } returns MutableStateFlow(TransportMode.MULTI_PATH)
        val scope = TestScope(UnconfinedTestDispatcher(scheduler))
        val manager = TransportManager(
            context = context,
            settingsRepository = settings,
            injectedManagerScope = scope
        )
        return manager to scope
    }

    @Test
    fun registerTransport_mergesDiscoveredPeersIntoStateFlow() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)

        manager.discoveredPeers.test {
            assertEquals(emptyMap<String, PeerDevice>(), awaitItem())

            lan.emit(
                TransportEvent.DeviceDiscovered(
                    deviceId = "peerA",
                    deviceName = "Alice",
                    address = "10.0.0.2",
                    rssi = -55,
                    transportType = TransportType.LAN
                )
            )
            scope.advanceUntilIdle()
            val first = awaitItem()
            assertEquals(1, first.size)
            val peerA = first["peerA"]!!
            assertEquals("Alice", peerA.name)
            assertEquals(TransportType.LAN, peerA.transportType)
            assertEquals(-55, peerA.rssi)

            lan.emit(
                TransportEvent.DeviceDiscovered(
                    deviceId = "peerB",
                    deviceName = "Bob",
                    address = "10.0.0.3",
                    transportType = TransportType.LAN
                )
            )
            scope.advanceUntilIdle()
            val second = awaitItem()
            assertEquals(2, second.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun deviceLost_removesPeer() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)

        manager.discoveredPeers.test {
            assertEquals(emptyMap<String, PeerDevice>(), awaitItem())
            lan.emit(TransportEvent.DeviceDiscovered("peerA", "Alice", "10.0.0.2"))
            scope.advanceUntilIdle()
            awaitItem()
            lan.emit(TransportEvent.DeviceLost("peerA"))
            scope.advanceUntilIdle()
            assertEquals(emptyMap<String, PeerDevice>(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun connectionEvents_doNotChangeDiscoveredPeers() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)

        manager.discoveredPeers.test {
            assertEquals(emptyMap<String, PeerDevice>(), awaitItem())
            lan.emit(TransportEvent.DeviceDiscovered("peerA", "Alice", "10.0.0.2"))
            scope.advanceUntilIdle()
            awaitItem()
            lan.emit(TransportEvent.ConnectionEstablished("peerA"))
            lan.emit(TransportEvent.PayloadReceived("peerA", mockk(relaxed = true)))
            scope.advanceUntilIdle()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun selectBestTransport_multiPath_returnsAllOnlinePeers() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        lan.setOnlinePeers(setOf("peerA"))
        ble.setOnlinePeers(setOf("peerA"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.MULTI_PATH)

        val chosen = manager.selectBestTransport("peerA")
        assertEquals(2, chosen.size)
    }

    @Test
    fun selectBestTransport_lanOnly_returnsLanOnly() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        lan.setOnlinePeers(setOf("peerA"))
        ble.setOnlinePeers(setOf("peerA"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.LAN_ONLY)

        val chosen = manager.selectBestTransport("peerA")
        assertEquals(1, chosen.size)
        assertEquals("lan", chosen.first().transportName)
    }

    @Test
    fun selectBestTransport_bleOnly_returnsBleOnly() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        lan.setOnlinePeers(setOf("peerA"))
        ble.setOnlinePeers(setOf("peerA"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.BLE_ONLY)

        val chosen = manager.selectBestTransport("peerA")
        assertEquals(1, chosen.size)
        assertEquals("ble", chosen.first().transportName)
    }

    @Test
    fun selectBestTransport_auto_prefersTransportWithPeerOnline() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        lan.setOnlinePeers(setOf("peerA"))
        ble.setOnlinePeers(emptySet())
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.AUTO)

        val chosen = manager.selectBestTransport("peerA")
        assertEquals(1, chosen.size)
        assertEquals("lan", chosen.first().transportName)
    }

    @Test
    fun ble_runtimeActive_mirrorsBleTransport() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        scope.advanceUntilIdle()

        assertFalse(manager.bleRuntimeActive.value)
        ble.setRuntimeActive(true)
        scope.advanceUntilIdle()
        manager.bleRuntimeActive.test {
            assertTrue(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        manager.unregisterTransport("ble")
        scope.advanceUntilIdle()
        assertFalse(manager.bleRuntimeActive.value)
    }

    @Test
    fun getAllTransports_returnsRegisteredNames() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)
        assertEquals(1, manager.getAllTransports().size)
        manager.unregisterTransport("lan")
        assertEquals(0, manager.getAllTransports().size)
    }

    @Test
    fun unregisterUnknown_returnsNoop() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        manager.unregisterTransport("nonexistent")
        // No exception
    }
}
