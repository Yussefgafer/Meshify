package com.p2p.meshify.core.network

import app.cash.turbine.test
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.TransportMode
import io.mockk.every
import io.mockk.mockk
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

class TransportManagerExtendedTest {

    @org.junit.Before
    fun setUpLog() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        io.mockk.every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        io.mockk.every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.i(any<String>(), any<String>()) } returns 0
    }

    @org.junit.After
    fun tearDownLog() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    private class FakeTransport(
        override val transportName: String,
        override val isAvailable: Boolean = true,
        override val capabilities: Set<TransportCapability> = emptySet(),
        private val throwOnStart: Boolean = false
    ) : IMeshTransport {
        val eventsFlow = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 64)
        private val _online = MutableStateFlow<Set<String>>(emptySet())
        private val _typing = MutableStateFlow<Set<String>>(emptySet())
        private val _runtime = MutableStateFlow(false)
        override val events = eventsFlow
        override val onlinePeers = _online
        override val typingPeers = _typing
        override val runtimeActive = _runtime
        var startCalled = false
        var stopCalled = false
        var startDiscoveryCalled = false
        var stopDiscoveryCalled = false
        fun setOnline(peers: Set<String>) { _online.value = peers }
        suspend fun emit(e: TransportEvent) { eventsFlow.emit(e) }
        override suspend fun start() {
            startCalled = true
            if (throwOnStart) throw RuntimeException("start fail")
        }
        override suspend fun stop() { stopCalled = true }
        override suspend fun startDiscovery() { startDiscoveryCalled = true }
        override suspend fun stopDiscovery() { stopDiscoveryCalled = true }
        override suspend fun sendPayload(targetDeviceId: String, payload: com.p2p.meshify.domain.model.Payload): Result<Unit> = Result.success(Unit)
    }

    private fun makeManager(scheduler: TestCoroutineScheduler): Pair<TransportManager, TestScope> {
        val context = mockk<android.content.Context>(relaxed = true)
        val settings = mockk<com.p2p.meshify.domain.repository.ISettingsRepository>(relaxed = true)
        every { settings.transportMode } returns MutableStateFlow(TransportMode.MULTI_PATH)
        val scope = TestScope(UnconfinedTestDispatcher(scheduler))
        return TransportManager(context, settings, injectedManagerScope = scope) to scope
    }

    @Test
    fun getAvailableTransports_filtersUnavailable() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", isAvailable = true)
        val ble = FakeTransport("ble", isAvailable = false)
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        val available = manager.getAvailableTransports()
        assertEquals(1, available.size)
        assertEquals("lan", available.first().transportName)
    }

    @Test
    fun getTransportWithPeer_returnsCorrectTransport() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        lan.setOnline(setOf("peerA"))
        ble.setOnline(setOf("peerB"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        assertEquals("lan", manager.getTransportWithPeer("peerA")?.transportName)
        assertEquals("ble", manager.getTransportWithPeer("peerB")?.transportName)
        assertNull(manager.getTransportWithPeer("ghost"))
    }

    @Test
    fun getTransport_returnsRegisteredOrNull() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)
        assertNotNull(manager.getTransport("lan"))
        assertNull(manager.getTransport("ble"))
    }

    @Test
    fun selectBestTransport_multiPath_withCapabilities_filters() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.FILE_TRANSFER, TransportCapability.OFFLINE))
        val ble = FakeTransport("ble", capabilities = setOf(TransportCapability.LOW_POWER, TransportCapability.OFFLINE))
        lan.setOnline(setOf("peerA"))
        ble.setOnline(setOf("peerA"))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.MULTI_PATH)

        // Require FILE_TRANSFER -> only LAN qualifies
        val filtered = manager.selectBestTransport("peerA", setOf(TransportCapability.FILE_TRANSFER))
        assertEquals(1, filtered.size)
        assertEquals("lan", filtered.first().transportName)
    }

    @Test
    fun selectBestTransport_multiPath_noPeerOnline_fallsBackToCapable() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.FILE_TRANSFER))
        val ble = FakeTransport("ble", capabilities = setOf(TransportCapability.OFFLINE))
        // neither has peer online
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.MULTI_PATH)
        val chosen = manager.selectBestTransport("ghost", setOf(TransportCapability.FILE_TRANSFER))
        assertEquals(1, chosen.size)
        assertEquals("lan", chosen.first().transportName)
    }

    @Test
    fun selectBestTransport_multiPath_noCapable_fallsBackToAvailable() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.OFFLINE))
        lan.setOnline(emptySet())
        manager.registerTransport("lan", lan)
        manager.setTransportMode(TransportMode.MULTI_PATH)
        // Require FILE_TRANSFER but none capable and no peer online -> fallback to available
        val chosen = manager.selectBestTransport("ghost", setOf(TransportCapability.FILE_TRANSFER))
        assertEquals(1, chosen.size)
        assertEquals("lan", chosen.first().transportName)
    }

    @Test
    fun selectBestTransport_auto_withCapabilities() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.FILE_TRANSFER))
        val ble = FakeTransport("ble", capabilities = setOf(TransportCapability.LOW_POWER))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.AUTO)
        // No peer online, require LOW_POWER -> ble
        val chosen = manager.selectBestTransport("ghost", setOf(TransportCapability.LOW_POWER))
        assertEquals(1, chosen.size)
        assertEquals("ble", chosen.first().transportName)
    }

    @Test
    fun selectBestTransport_auto_prefersLanWhenNoPeerAndNoCapable() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan", capabilities = setOf(TransportCapability.OFFLINE))
        val ble = FakeTransport("ble", capabilities = setOf(TransportCapability.OFFLINE))
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.setTransportMode(TransportMode.AUTO)
        // No peer online, require FILE_TRANSFER -> none capable, fallback to lan
        val chosen = manager.selectBestTransport("ghost", setOf(TransportCapability.FILE_TRANSFER))
        assertEquals(1, chosen.size)
        assertEquals("lan", chosen.first().transportName)
    }

    @Test
    fun discoveredPeers_errorEventsDoNotMutateRetainedPeers() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)
        manager.discoveredPeers.test {
            assertEquals(emptyMap<String, com.p2p.meshify.domain.model.PeerDevice>(), awaitItem())

            lan.emit(TransportEvent.DeviceDiscovered("peerA", "Alice", "10.0.0.2"))
            scope.advanceUntilIdle()
            val discovered = awaitItem()
            assertEquals(1, discovered.size)
            assertEquals("Alice", discovered["peerA"]?.name)

            // Non-lifecycle events should not remove the peer from discoveredPeers.
            lan.emit(TransportEvent.Error("something broke"))
            lan.emit(TransportEvent.ConnectionLost("peerA", null))
            scope.advanceUntilIdle()
            expectNoEvents()

            assertEquals(setOf("peerA"), manager.discoveredPeers.value.keys)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun registerTransport_replacesExisting() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan1 = FakeTransport("lan")
        val lan2 = FakeTransport("lan")
        manager.registerTransport("lan", lan1)
        manager.registerTransport("lan", lan2)
        assertEquals(1, manager.getAllTransports().size)
        assertEquals(lan2, manager.getTransport("lan"))
    }

    @Test
    fun startAllTransports_callsEachAndSurvivesFailure() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val good = FakeTransport("lan")
        val bad = FakeTransport("bad", throwOnStart = true)
        manager.registerTransport("lan", good)
        manager.registerTransport("bad", bad)
        manager.startAllTransports()
        assertTrue(good.startCalled)
        assertTrue(bad.startCalled)
    }

    @Test
    fun stopAllTransports_callsEach() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.stopAllTransports()
        assertTrue(lan.stopCalled)
        assertTrue(ble.stopCalled)
    }

    @Test
    fun startDiscoveryOnAll_callsEach() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)
        manager.startDiscoveryOnAll()
        assertTrue(lan.startDiscoveryCalled)
    }

    @Test
    fun stopDiscoveryOnAll_callsEach() = runTest {
        val (manager, _) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)
        manager.stopDiscoveryOnAll()
        assertTrue(lan.stopDiscoveryCalled)
    }

    @Test
    fun getAllEventsFlow_mergesFromMultipleTransports() = runTest {
        val (manager, scope) = makeManager(testScheduler)
        val lan = FakeTransport("lan")
        val ble = FakeTransport("ble")
        manager.registerTransport("lan", lan)
        manager.registerTransport("ble", ble)
        manager.getAllEventsFlow().test {
            lan.emit(TransportEvent.DeviceDiscovered("peerA", "Alice", "10.0.0.2"))
            scope.advanceUntilIdle()
            val e1 = awaitItem()
            assertTrue(e1 is TransportEvent.DeviceDiscovered)

            ble.emit(TransportEvent.DeviceLost("peerA"))
            scope.advanceUntilIdle()
            val e2 = awaitItem()
            assertTrue(e2 is TransportEvent.DeviceLost)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun unregister_cancelsAndHidesTransport() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        val lan = FakeTransport("lan")
        manager.registerTransport("lan", lan)
        manager.unregisterTransport("lan")
        assertNull(manager.getTransport("lan"))
        assertTrue(manager.getAllTransports().isEmpty())
        // re-register should work
        manager.registerTransport("lan", lan)
        assertNotNull(manager.getTransport("lan"))
    }

    @Test
    fun selectBestTransport_lanOnly_noLanRegistered_returnsEmpty() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        manager.setTransportMode(TransportMode.LAN_ONLY)
        val chosen = manager.selectBestTransport("ghost")
        assertTrue(chosen.isEmpty())
    }

    @Test
    fun selectBestTransport_bleOnly_noBleRegistered_returnsEmpty() {
        val (manager, _) = makeManager(TestCoroutineScheduler())
        manager.setTransportMode(TransportMode.BLE_ONLY)
        val chosen = manager.selectBestTransport("ghost")
        assertTrue(chosen.isEmpty())
    }
}
