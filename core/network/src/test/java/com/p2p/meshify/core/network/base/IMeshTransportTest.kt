package com.p2p.meshify.core.network.base

import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IMeshTransportTest {

    @Test
    fun contract_lanTransport_emitsDiscoveryAndSendSuccess() = runTest {
        val events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 16)
        val online = MutableStateFlow<Set<String>>(emptySet())
        val typing = MutableStateFlow<Set<String>>(emptySet())
        val transport = object : IMeshTransport {
            override val transportName: String = "lan"
            override val isAvailable: Boolean = true
            override val capabilities: Set<TransportCapability> = setOf(TransportCapability.FILE_TRANSFER)
            override val events: Flow<TransportEvent> = events
            override val onlinePeers: StateFlow<Set<String>> = online
            override val typingPeers: StateFlow<Set<String>> = typing
            override suspend fun start() {}
            override suspend fun stop() {}
            override suspend fun startDiscovery() {
                events.tryEmit(TransportEvent.DeviceDiscovered("peerA", "Alice", "10.0.0.2"))
            }
            override suspend fun stopDiscovery() {}
            override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> =
                Result.success(Unit)
        }

        transport.start()
        online.value = setOf("peerA")
        transport.startDiscovery()
        transport.sendPayload("peerA", Payload(senderId = "self", type = Payload.PayloadType.TEXT, data = "hi".toByteArray()))
        transport.stopDiscovery()
        transport.stop()

        assertEquals("lan", transport.transportName)
        assertTrue(transport.isAvailable)
        assertTrue(transport.capabilities.contains(TransportCapability.FILE_TRANSFER))
        assertTrue(transport.onlinePeers.value.contains("peerA"))
        assertTrue(transport.typingPeers.value.isEmpty())
    }
}
