package com.p2p.meshify.core.network.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.SocketTimeoutException

class LanTransportFailureIntegrationTest {

    private lateinit var context: Context
    private lateinit var settingsRepository: ISettingsRepository
    private lateinit var peerIdProvider: SimplePeerIdProvider
    private lateinit var socketManager: SocketManager
    private var virtualTime: Long = 0L

    @Before
    fun setUp() {
        virtualTime = 0L
        val nsdManager = mockk<NsdManager>(relaxed = true)
        val connectivityManager = mockk<ConnectivityManager>(relaxed = true)
        context = mockk(relaxed = true)
        every { context.getSystemService(Context.NSD_SERVICE) } returns nsdManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        settingsRepository = mockk(relaxed = true)
        peerIdProvider = mockk(relaxed = true)
        socketManager = mockk(relaxed = true)
    }

    @Test
    fun failureTracker_rollingWindow_expiresOldFailures() {
        val tracker = FailureTracker()

        repeat(AppConfig.FAILURE_MAX_FAILURES - 1) { index ->
            assertFalse(tracker.recordFailure("peer", nowMs = index * 10_000L))
        }

        val expiredAt = AppConfig.FAILURE_WINDOW_MS + (AppConfig.FAILURE_MAX_FAILURES - 1) * 10_000L + 1L
        tracker.cleanupExpired(nowMs = expiredAt)
        assertEquals(0, tracker.failureCount("peer", nowMs = expiredAt))
    }

    @Test
    fun lanTransport_failureCountUsesInjectedClockAndExpires() = runBlocking {
        val transport = newTransport()
        injectPeer(transport, "peer", "10.0.0.10")
        coEvery { socketManager.sendPayload(any(), any()) } returns
            Result.failure(SocketTimeoutException("timeout"))
        val payload = payload()

        repeat(AppConfig.FAILURE_MAX_FAILURES - 1) { index ->
            virtualTime = index * 10_000L
            transport.sendPayload("peer", payload)
        }
        assertEquals(
            AppConfig.FAILURE_MAX_FAILURES - 1,
            transport.currentFailureCountForTest("peer")
        )

        virtualTime += AppConfig.FAILURE_WINDOW_MS + 1L

        assertEquals(0, transport.currentFailureCountForTest("peer"))
    }

    @Test
    fun lanTransport_successfulSendResetsFailureWindow() = runBlocking {
        val transport = newTransport()
        injectPeer(transport, "peer", "10.0.0.10")
        coEvery { socketManager.sendPayload(any(), any()) } returns
            Result.failure(SocketTimeoutException("timeout"))
        val payload = payload()

        transport.sendPayload("peer", payload)
        transport.sendPayload("peer", payload)
        assertEquals(2, transport.currentFailureCountForTest("peer"))

        coEvery { socketManager.sendPayload(any(), any()) } returns Result.success(Unit)
        val result = transport.sendPayload("peer", payload)

        assertTrue(result.isSuccess)
        assertEquals(0, transport.currentFailureCountForTest("peer"))
    }

    @Test
    fun lanTransport_deadPeerEventEmitsOnlyWhenThresholdIsReached() = runBlocking {
        val transport = newTransport()
        injectPeer(transport, "peer", "10.0.0.10")
        coEvery { socketManager.sendPayload(any(), any()) } returns
            Result.failure(SocketTimeoutException("timeout"))
        val payload = payload()
        val lostEvents = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            transport.events.collect { event ->
                if (event is TransportEvent.DeviceLost && event.deviceId == "peer") {
                    lostEvents += event
                }
            }
        }

        repeat(AppConfig.FAILURE_MAX_FAILURES - 1) {
            transport.sendPayload("peer", payload)
            delay(10)
            assertTrue(lostEvents.isEmpty())
        }
        assertEquals(
            AppConfig.FAILURE_MAX_FAILURES - 1,
            transport.currentFailureCountForTest("peer")
        )

        transport.sendPayload("peer", payload)
        withTimeoutOrNull(1_000L) {
            while (lostEvents.isEmpty()) delay(10)
        }

        assertEquals(1, lostEvents.size)
        assertEquals("peer", (lostEvents.single() as TransportEvent.DeviceLost).deviceId)
        assertEquals(0, transport.currentFailureCountForTest("peer"))
        collector.cancel()
    }

    @Test
    fun lanTransport_scatteredHistoricalFailuresDoNotMarkPeerDead() = runBlocking {
        val transport = newTransport()
        injectPeer(transport, "peer", "10.0.0.10")
        coEvery { socketManager.sendPayload(any(), any()) } returns
            Result.failure(SocketTimeoutException("timeout"))
        val payload = payload()
        val lostEvents = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            transport.events.collect { event ->
                if (event is TransportEvent.DeviceLost && event.deviceId == "peer") {
                    lostEvents += event
                }
            }
        }

        val scatteredTimes = listOf(
            0L,
            AppConfig.FAILURE_WINDOW_MS / 3,
            (AppConfig.FAILURE_WINDOW_MS * 2L) / 3,
            AppConfig.FAILURE_WINDOW_MS,
            (AppConfig.FAILURE_WINDOW_MS * 4L) / 3
        )
        scatteredTimes.forEach { timestamp ->
            virtualTime = timestamp
            transport.sendPayload("peer", payload)
            delay(10)
        }

        assertNull(
            withTimeoutOrNull(200L) {
                while (lostEvents.isEmpty()) delay(10)
                lostEvents.singleOrNull()
            }
        )
        assertEquals(
            AppConfig.FAILURE_MAX_FAILURES - 1,
            transport.currentFailureCountForTest("peer")
        )
        collector.cancel()
    }

    private fun newTransport(): LanTransportImpl = LanTransportImpl(
        context = context,
        socketManager = socketManager,
        settingsRepository = settingsRepository,
        peerIdProvider = peerIdProvider,
        clock = { virtualTime }
    )

    private fun injectPeer(transport: LanTransportImpl, peerId: String, address: String) {
        val field = LanTransportImpl::class.java.getDeclaredField("peerMap")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peers = field.get(transport) as java.util.concurrent.ConcurrentHashMap<String, String>
        peers[peerId] = address
    }

    private fun payload() = Payload(
        senderId = "self",
        type = Payload.PayloadType.TEXT,
        data = byteArrayOf(1)
    )
}
