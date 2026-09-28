package com.p2p.meshify.core.network

import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.PeerDevice
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.model.TransportType
import com.p2p.meshify.domain.repository.ISettingsRepository
import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TransportManagerCapabilitiesTest {

    private class FakeTransport(
        override val transportName: String,
        override val isAvailable: Boolean = true,
        override val capabilities: Set<TransportCapability> = emptySet()
    ) : IMeshTransport {
        private val eventsFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
        private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
        private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
        private val _runtimeActive = MutableStateFlow(false)

        override val events = eventsFlow
        override val onlinePeers = _onlinePeers
        override val typingPeers = _typingPeers
        override val runtimeActive = _runtimeActive

        fun setOnlinePeers(peers: Set<String>) {
            _onlinePeers.value = peers
        }

        suspend fun emit(event: TransportEvent) {
            eventsFlow.emit(event)
        }

        override suspend fun start() = Unit
        override suspend fun stop() = Unit
        override suspend fun startDiscovery() = Unit
        override suspend fun stopDiscovery() = Unit
        override suspend fun sendPayload(
            targetDeviceId: String,
            payload: com.p2p.meshify.domain.model.Payload
        ): Result<Unit> = Result.success(Unit)
    }

    private fun makeManager(scheduler: TestCoroutineScheduler): Pair<TransportManager, TestScope> {
        val context = mockk<android.content.Context>(relaxed = true)
        val settings = mockk<ISettingsRepository>(relaxed = true)
        every { settings.transportMode } returns MutableStateFlow(TransportMode.MULTI_PATH)
        val scope = TestScope(UnconfinedTestDispatcher(scheduler))
        val manager = TransportManager(context, settings, injectedManagerScope = scope)
        return manager to scope
    }

    @Test
    fun selectBestTransport_multiPath_filtersByRequiredCapabilities() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport(
            "lan",
            capabilities = setOf(
                TransportCapability.FILE_TRANSFER,
                TransportCapability.HIGH_BANDWIDTH,
                TransportCapability.OFFLINE
            )
        )
        val ble = FakeTransport(
            "ble",
            capabilities = setOf(TransportCapability.LOW_POWER, TransportCapability.OFFLINE)
        )
        lan.setOnlinePeers(setOf("peerA"))
        ble.setOnlinePeers(setOf("peerA"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.MULTI_PATH)

        val selected = manager.selectBestTransport(
            "peerA",
            requiredCapabilities = setOf(TransportCapability.FILE_TRANSFER)
        )

        assertEquals(listOf("lan"), selected.map { it.transportName })
    }

    @Test
    fun selectBestTransport_multiPath_fallsBackToCapableTransportWhenPeerIsOffline() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.FILE_TRANSFER))
        val ble = FakeTransport("ble", capabilities = setOf(TransportCapability.LOW_POWER))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.MULTI_PATH)

        val selected = manager.selectBestTransport(
            "peerA",
            requiredCapabilities = setOf(TransportCapability.FILE_TRANSFER)
        )

        assertEquals(listOf("lan"), selected.map { it.transportName })
    }

    @Test
    fun selectBestTransport_auto_filtersByRequiredCapabilities() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.FILE_TRANSFER))
        val ble = FakeTransport("ble", capabilities = setOf(TransportCapability.LOW_POWER))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.AUTO)

        val selected = manager.selectBestTransport(
            "peerA",
            requiredCapabilities = setOf(TransportCapability.LOW_POWER)
        )

        assertEquals(listOf("ble"), selected.map { it.transportName })
    }

    @Test
    fun getTransportWithPeer_returnsTransportWithRequestedPeer() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        lan.setOnlinePeers(setOf("peerA"))
        ble.setOnlinePeers(setOf("peerB"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)

        assertEquals("lan", manager.getTransportWithPeer("peerA")?.transportName)
        assertEquals("ble", manager.getTransportWithPeer("peerB")?.transportName)
        assertNull(manager.getTransportWithPeer("unknown"))
    }

    @Test
    fun reRegisterTransport_cancelsOldForwarderAndActivatesReplacement() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val oldTransport = FakeTransport("lan")
        val replacement = FakeTransport("lan")
        manager.registerTransport("lan", oldTransport)
        scope.advanceUntilIdle()

        manager.getAllEventsFlow().test {
            oldTransport.emit(discovered("peerA", "Alice", TransportType.LAN))
            scope.advanceUntilIdle()
            assertTrue(awaitItem() is TransportEvent.DeviceDiscovered)

            manager.registerTransport("lan", replacement)
            scope.advanceUntilIdle()
            assertEquals(replacement, manager.getTransport("lan"))

            oldTransport.emit(discovered("peerA", "Alice", TransportType.LAN))
            scope.advanceUntilIdle()
            expectNoEvents()

            replacement.emit(discovered("peerA", "Alice", TransportType.LAN))
            scope.advanceUntilIdle()
            assertTrue(awaitItem() is TransportEvent.DeviceDiscovered)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun getAvailableTransports_filtersUnavailableTransports() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val available = FakeTransport("lan", isAvailable = true)
        val unavailable = FakeTransport("ble", isAvailable = false)
        manager.registerTransport("lan", available)
        manager.registerTransport("ble", unavailable)

        val transports = manager.getAvailableTransports()

        assertEquals(1, transports.size)
        assertTrue(available in transports)
        assertFalse(unavailable in transports)
    }

    private fun discovered(
        deviceId: String,
        deviceName: String,
        transportType: TransportType
    ) = TransportEvent.DeviceDiscovered(
        deviceId = deviceId,
        deviceName = deviceName,
        address = "10.0.0.10",
        rssi = -60,
        transportType = transportType
    )
}
