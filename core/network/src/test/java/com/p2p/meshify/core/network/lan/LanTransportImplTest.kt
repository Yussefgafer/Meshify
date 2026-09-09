package com.p2p.meshify.core.network.lan

import android.content.Context
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.crypto.MessageCipher
import com.p2p.meshify.core.crypto.PeerPublicKeyStore
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.core.network.base.TransportCapability
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ConnectException
import java.net.SocketTimeoutException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LanTransportImplTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var settings: FakeSettings
    private lateinit var peerIdProvider: SimplePeerIdProvider
    private lateinit var messageCipher: MessageCipher
    private lateinit var peerPublicKeyStore: PeerPublicKeyStore
    private lateinit var socketManager: SocketManager
    private lateinit var incomingFlow: MutableSharedFlow<Pair<String, Payload>>
    private lateinit var nsdManager: NsdManager
    private lateinit var connectivityManager: ConnectivityManager
    private lateinit var context: Context

    private class FakeSettings : ISettingsRepository {
        private val _displayName = MutableStateFlow("Tester")
        private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        private val _dynamicColorEnabled = MutableStateFlow(true)
        private val _hapticFeedbackEnabled = MutableStateFlow(true)
        private val _isNetworkVisible = MutableStateFlow(true)
        private val _avatarHash = MutableStateFlow<String?>(null)
        private val _seedColor = MutableStateFlow(0)
        private val _bleEnabled = MutableStateFlow(false)
        private val _transportMode = MutableStateFlow(TransportMode.MULTI_PATH)
        private val _hasCompletedOnboarding = MutableStateFlow(false)
        private val _appLanguage = MutableStateFlow("en")
        private val _fontSizeScale = MutableStateFlow(1f)
        private val _notificationsEnabled = MutableStateFlow(true)
        private val _notificationSound = MutableStateFlow(true)
        private val _notificationVibrate = MutableStateFlow(true)
        var deviceIdValue = "self-device-id"
        override val displayName get() = _displayName.asStateFlow()
        override val themeMode get() = _themeMode.asStateFlow()
        override val dynamicColorEnabled get() = _dynamicColorEnabled.asStateFlow()
        override val hapticFeedbackEnabled get() = _hapticFeedbackEnabled.asStateFlow()
        override val isNetworkVisible get() = _isNetworkVisible.asStateFlow()
        override val avatarHash get() = _avatarHash.asStateFlow()
        override val seedColor get() = _seedColor.asStateFlow()
        override val bleEnabled get() = _bleEnabled.asStateFlow()
        override val transportMode get() = _transportMode.asStateFlow()
        override val hasCompletedOnboarding get() = _hasCompletedOnboarding.asStateFlow()
        override val appLanguage get() = _appLanguage.asStateFlow()
        override val fontSizeScale get() = _fontSizeScale.asStateFlow()
        override val notificationsEnabled get() = _notificationsEnabled.asStateFlow()
        override val notificationSound get() = _notificationSound.asStateFlow()
        override val notificationVibrate get() = _notificationVibrate.asStateFlow()
        override suspend fun getDeviceId(): String = deviceIdValue
        override suspend fun updateDisplayName(name: String) { _displayName.value = name }
        override suspend fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode }
        override suspend fun setDynamicColor(enabled: Boolean) { _dynamicColorEnabled.value = enabled }
        override suspend fun setHapticFeedback(enabled: Boolean) { _hapticFeedbackEnabled.value = enabled }
        override suspend fun setNetworkVisibility(visible: Boolean) { _isNetworkVisible.value = visible }
        override suspend fun updateAvatarHash(hash: String?) { _avatarHash.value = hash }
        override suspend fun setSeedColor(color: Int) { _seedColor.value = color }
        override suspend fun setBleEnabled(enabled: Boolean) { _bleEnabled.value = enabled }
        override suspend fun setTransportMode(mode: TransportMode) { _transportMode.value = mode }
        override suspend fun setOnboardingCompleted() { _hasCompletedOnboarding.value = true }
        override suspend fun resetOnboardingCompleted() { _hasCompletedOnboarding.value = false }
        override suspend fun setAppLanguage(language: String) { _appLanguage.value = language }
        override suspend fun setFontSizeScale(scale: Float) { _fontSizeScale.value = scale }
        override suspend fun setNotificationsEnabled(enabled: Boolean) { _notificationsEnabled.value = enabled }
        override suspend fun setNotificationSound(enabled: Boolean) { _notificationSound.value = enabled }
        override suspend fun setNotificationVibrate(enabled: Boolean) { _notificationVibrate.value = enabled }
        override suspend fun clearCache() {}
        override suspend fun exportBackup(): Result<String> = Result.success("{}")
        override suspend fun importBackup(json: String): Result<Unit> = Result.success(Unit)
        override fun getAppVersion(): String = "1.1.4"
    }

    @Before
    fun setUp() {
        settings = FakeSettings()
        settings.deviceIdValue = "self-device-id"

        peerIdProvider = mockk(relaxed = true)
        every { peerIdProvider.getPeerId() } returns "self-peer-id"

        messageCipher = mockk(relaxed = true)
        every { messageCipher.getPublicKeyBase64() } returns "test-public-key-base64"

        peerPublicKeyStore = mockk(relaxed = true)

        nsdManager = mockk(relaxed = true)
        connectivityManager = mockk(relaxed = true)

        incomingFlow = MutableSharedFlow(extraBufferCapacity = 64)
        socketManager = mockk(relaxed = true)
        every { socketManager.incomingPayloads } returns incomingFlow
        coEvery { socketManager.startListening() } returns Unit
        coEvery { socketManager.stopListening() } returns Unit
        coEvery { socketManager.sendPayload(any(), any()) } returns Result.success(Unit)

        val appContext = mockk<Context>(relaxed = true)
        every { appContext.getSystemService(Context.NSD_SERVICE) } returns nsdManager
        every { appContext.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { appContext.getSystemService(Context.WIFI_SERVICE) } returns mockk(relaxed = true)
        every { appContext.filesDir } returns tmp.newFolder("files")

        context = mockk(relaxed = true)
        every { context.getSystemService(Context.NSD_SERVICE) } returns nsdManager
        every { context.getSystemService(Context.CONNECTIVITY_SERVICE) } returns connectivityManager
        every { context.getSystemService(Context.WIFI_SERVICE) } returns mockk(relaxed = true)
        every { context.applicationContext } returns appContext
        every { context.filesDir } returns appContext.filesDir
    }

    private fun newTransport(): LanTransportImpl {
        return LanTransportImpl(context, socketManager, settings, peerIdProvider, messageCipher, peerPublicKeyStore)
    }

    @Test
    fun transportMetadata_correct() {
        val t = newTransport()
        assertEquals("lan", t.transportName)
        assertTrue(t.isAvailable)
        assertTrue(t.capabilities.contains(TransportCapability.FILE_TRANSFER))
        assertTrue(t.capabilities.contains(TransportCapability.HIGH_BANDWIDTH))
        assertTrue(t.capabilities.contains(TransportCapability.OFFLINE))
        assertTrue(t.capabilities.contains(TransportCapability.LOW_LATENCY))
        assertEquals(4, t.capabilities.size)
    }

    @Test
    fun onlinePeers_initiallyEmpty() {
        val t = newTransport()
        assertTrue(t.onlinePeers.value.isEmpty())
        assertTrue(t.typingPeers.value.isEmpty())
    }

    @Test
    fun start_idempotent() = runBlocking {
        val t = newTransport()
        t.start()
        delay(200)
        t.start()
        delay(100)
        t.stop()
        delay(200)
    }

    @Test
    fun stop_beforeStart_doesNotThrow() = runTest {
        val t = newTransport()
        t.stop()
    }

    @Test
    fun sendPayload_unknownPeer_returnsFailure() = runBlocking {
        val t = newTransport()
        t.start()
        delay(250)
        val payload = Payload(senderId = "self-device-id", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())
        val result = t.sendPayload("unknown-peer", payload)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("offline", ignoreCase = true))
        t.stop()
        delay(100)
    }

    @Test
    fun sendPayload_knownPeer_successResetsFailure() = runBlocking {
        val t = newTransport()
        t.start()
        delay(250)
        val peerMapField = LanTransportImpl::class.java.getDeclaredField("peerMap")
        peerMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = peerMapField.get(t) as java.util.concurrent.ConcurrentHashMap<String, String>
        peerMap["peerA"] = "10.0.0.5"
        val onlineField = LanTransportImpl::class.java.getDeclaredField("_onlinePeers")
        onlineField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val online = onlineField.get(t) as MutableStateFlow<Set<String>>
        online.value = setOf("peerA")

        val payload = Payload(senderId = "self-device-id", type = Payload.PayloadType.TEXT, data = "hello".toByteArray())
        val result = t.sendPayload("peerA", payload)
        assertTrue(result.isSuccess)
        coVerify { socketManager.sendPayload("10.0.0.5", any()) }

        val trackerField = LanTransportImpl::class.java.getDeclaredField("failureTracker")
        trackerField.isAccessible = true
        val tracker = trackerField.get(t) as FailureTracker
        assertEquals(0, tracker.failureCount("peerA"))

        t.stop()
        delay(100)
    }

    @Test
    fun sendPayload_socketTimeout_countsTowardsDeadAndEmitsDeviceLostOnThreshold() = runBlocking {
        val t = newTransport()
        coEvery { socketManager.sendPayload(any(), any()) } returns Result.failure(SocketTimeoutException("timeout"))
        t.start()
        delay(300)

        val peerMapField = LanTransportImpl::class.java.getDeclaredField("peerMap")
        peerMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = peerMapField.get(t) as java.util.concurrent.ConcurrentHashMap<String, String>
        peerMap["victim"] = "10.0.0.99"
        val onlineField = LanTransportImpl::class.java.getDeclaredField("_onlinePeers")
        onlineField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val online = onlineField.get(t) as MutableStateFlow<Set<String>>
        online.value = setOf("victim")

        val payload = Payload(senderId = "self-device-id", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())

        val lostEvents = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch {
            t.events.collect { e ->
                if (e is TransportEvent.DeviceLost && e.deviceId == "victim") lostEvents.add(e)
            }
        }
        delay(150)

        repeat(AppConfig.FAILURE_MAX_FAILURES - 1) {
            val r = t.sendPayload("victim", payload)
            assertTrue(r.isFailure)
            assertTrue(lostEvents.isEmpty())
        }
        val final = t.sendPayload("victim", payload)
        assertTrue(final.isFailure)
        withTimeoutOrNull(2000) {
            while (lostEvents.isEmpty()) delay(50)
        }
        assertEquals(1, lostEvents.size)
        assertEquals("victim", (lostEvents.first() as TransportEvent.DeviceLost).deviceId)
        assertFalse(peerMap.containsKey("victim"))
        collector.cancel()
        t.stop()
        delay(100)
    }

    @Test
    fun sendPayload_connectException_alsoCounts() = runBlocking {
        val t = newTransport()
        coEvery { socketManager.sendPayload(any(), any()) } returns Result.failure(ConnectException("refused"))
        t.start()
        delay(300)
        val peerMapField = LanTransportImpl::class.java.getDeclaredField("peerMap")
        peerMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = peerMapField.get(t) as java.util.concurrent.ConcurrentHashMap<String, String>
        peerMap["peerB"] = "10.0.0.10"
        val payload = Payload(senderId = "self-device-id", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())
        repeat(AppConfig.FAILURE_MAX_FAILURES) {
            t.sendPayload("peerB", payload)
        }
        delay(500)
        assertFalse(peerMap.containsKey("peerB"))
        t.stop()
        delay(100)
    }

    @Test
    fun sendPayload_genericException_doesNotCountTowardsDead() = runBlocking {
        val t = newTransport()
        coEvery { socketManager.sendPayload(any(), any()) } returns Result.failure(RuntimeException("generic"))
        t.start()
        delay(300)
        val peerMapField = LanTransportImpl::class.java.getDeclaredField("peerMap")
        peerMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = peerMapField.get(t) as java.util.concurrent.ConcurrentHashMap<String, String>
        peerMap["peerC"] = "10.0.0.11"
        val payload = Payload(senderId = "self-device-id", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())
        repeat(AppConfig.FAILURE_MAX_FAILURES) {
            t.sendPayload("peerC", payload)
        }
        delay(300)
        assertTrue(peerMap.containsKey("peerC"))
        val trackerField = LanTransportImpl::class.java.getDeclaredField("failureTracker")
        trackerField.isAccessible = true
        val tracker = trackerField.get(t) as FailureTracker
        assertEquals(0, tracker.failureCount("peerC"))
        t.stop()
        delay(100)
    }

    @Test
    fun systemControl_typingUpdatesTypingPeers() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        incomingFlow.emit("192.168.1.10" to Payload(senderId = "peerTypist", type = Payload.PayloadType.SYSTEM_CONTROL, data = "TYPING_ON".toByteArray()))
        withTimeout(2000) { while (!t.typingPeers.value.contains("peerTypist")) delay(50) }
        assertTrue(t.typingPeers.value.contains("peerTypist"))

        incomingFlow.emit("192.168.1.10" to Payload(senderId = "peerTypist", type = Payload.PayloadType.SYSTEM_CONTROL, data = "TYPING_OFF".toByteArray()))
        withTimeout(2000) { while (t.typingPeers.value.contains("peerTypist")) delay(50) }
        assertFalse(t.typingPeers.value.contains("peerTypist"))
        t.stop()
        delay(100)
    }

    @Test
    fun systemControl_pingTriggersPong() = runBlocking {
        val t = newTransport()
        t.start()
        delay(350)
        val peerMapField = LanTransportImpl::class.java.getDeclaredField("peerMap")
        peerMapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val peerMap = peerMapField.get(t) as java.util.concurrent.ConcurrentHashMap<String, String>
        peerMap["pinger"] = "192.168.1.20"
        incomingFlow.emit("192.168.1.20" to Payload(senderId = "pinger", type = Payload.PayloadType.SYSTEM_CONTROL, data = "PING".toByteArray()))
        delay(800)
        coVerify(timeout = 2000) {
            socketManager.sendPayload(any(), match { it.type == Payload.PayloadType.SYSTEM_CONTROL && String(it.data) == "PONG" })
        }
        t.stop()
        delay(100)
    }

    @Test
    fun systemControl_unknownCommand_ignored() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        incomingFlow.emit("192.168.1.20" to Payload(senderId = "someone", type = Payload.PayloadType.SYSTEM_CONTROL, data = "UNKNOWN_CMD".toByteArray()))
        delay(300)
        assertTrue(t.typingPeers.value.isEmpty())
        t.stop()
        delay(100)
    }

    @Test
    fun handshake_validJson_emitsPayloadAndStoresKeyWhenPresent() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        val handshake = Handshake(name = "Alice", avatarHash = null, publicKeyBase64 = null)
        val payload = Payload(senderId = "peerAlice", type = Payload.PayloadType.HANDSHAKE, data = Json.encodeToString(handshake).toByteArray())
        val received = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch { t.events.collect { received.add(it) } }
        delay(150)
        incomingFlow.emit("10.0.0.2" to payload)
        withTimeout(3000) {
            while (received.filterIsInstance<TransportEvent.PayloadReceived>().isEmpty()) delay(50)
        }
        val payloadEvents = received.filterIsInstance<TransportEvent.PayloadReceived>()
        assertTrue(payloadEvents.isNotEmpty())
        val last = payloadEvents.last()
        assertEquals("peerAlice", last.deviceId)
        assertTrue(String(last.payload.data).startsWith("HELO_"))
        verify { peerPublicKeyStore.markPeerAsUnsupported("peerAlice") }
        collector.cancel()
        t.stop()
        delay(100)
    }

    @Test
    fun handshake_withPublicKey_storesKey() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        val fakeKey = "fake-base64-key"
        val handshake = Handshake(name = "Bob", publicKeyBase64 = fakeKey)
        val payload = Payload(senderId = "peerBob", type = Payload.PayloadType.HANDSHAKE, data = Json.encodeToString(handshake).toByteArray())
        incomingFlow.emit("10.0.0.3" to payload)
        delay(700)
        verify { peerPublicKeyStore.store("peerBob", fakeKey) }
        t.stop()
        delay(100)
    }

    @Test
    fun handshake_legacyHeloFormat_parsed() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        val payload = Payload(senderId = "legacyPeer", type = Payload.PayloadType.HANDSHAKE, data = "HELO_LegacyName".toByteArray())
        val received = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch { t.events.collect { received.add(it) } }
        delay(150)
        incomingFlow.emit("10.0.0.4" to payload)
        withTimeout(3000) {
            while (received.filterIsInstance<TransportEvent.PayloadReceived>().none { it.deviceId == "legacyPeer" }) delay(50)
        }
        assertTrue(received.filterIsInstance<TransportEvent.PayloadReceived>().any { it.deviceId == "legacyPeer" })
        collector.cancel()
        t.stop()
        delay(100)
    }

    @Test
    fun handshake_duplicatePeer_emitsOnlyOnce() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        val hs = Handshake(name = "Charlie")
        val p = Payload(senderId = "peerCharlie", type = Payload.PayloadType.HANDSHAKE, data = Json.encodeToString(hs).toByteArray())
        val events = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch { t.events.collect { events.add(it) } }
        delay(150)
        incomingFlow.emit("10.0.0.5" to p)
        delay(600)
        val afterFirst = events.filterIsInstance<TransportEvent.PayloadReceived>().count { it.deviceId == "peerCharlie" }
        incomingFlow.emit("10.0.0.5" to p)
        delay(600)
        val afterSecond = events.filterIsInstance<TransportEvent.PayloadReceived>().count { it.deviceId == "peerCharlie" }
        assertEquals(afterFirst + 1, afterSecond)
        collector.cancel()
        t.stop()
        delay(100)
    }

    @Test
    fun startDiscovery_idempotent() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        t.startDiscovery()
        delay(200)
        t.startDiscovery()
        delay(200)
        t.stopDiscovery()
        delay(200)
        t.stop()
        delay(100)
    }

    @Test
    fun stopDiscovery_beforeStart_doesNotThrow() = runTest {
        val t = newTransport()
        t.stopDiscovery()
    }

    @Test
    fun textPayload_emitsPayloadReceived() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        val payload = Payload(senderId = "peerMsg", type = Payload.PayloadType.TEXT, data = "hello".toByteArray())
        val events = mutableListOf<TransportEvent>()
        val collector = CoroutineScope(Dispatchers.Default).launch { t.events.collect { events.add(it) } }
        delay(150)
        incomingFlow.emit("10.0.0.6" to payload)
        withTimeout(3000) {
            while (events.filterIsInstance<TransportEvent.PayloadReceived>().none { it.deviceId == "peerMsg" }) delay(50)
        }
        assertTrue(events.filterIsInstance<TransportEvent.PayloadReceived>().any { it.deviceId == "peerMsg" && it.payload.type == Payload.PayloadType.TEXT })
        collector.cancel()
        t.stop()
        delay(100)
    }

    @Test
    fun incomingText_addsToPeerMapAndOnlinePeers() = runBlocking {
        val t = newTransport()
        t.start()
        delay(300)
        val payload = Payload(senderId = "newPeer", type = Payload.PayloadType.TEXT, data = "hi".toByteArray())
        incomingFlow.emit("192.168.1.99" to payload)
        withTimeout(2000) { while (!t.onlinePeers.value.contains("newPeer")) delay(50) }
        assertTrue(t.onlinePeers.value.contains("newPeer"))
        val mapField = LanTransportImpl::class.java.getDeclaredField("peerMap")
        mapField.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val map = mapField.get(t) as java.util.concurrent.ConcurrentHashMap<String, String>
        assertEquals("192.168.1.99", map["newPeer"])
        t.stop()
        delay(100)
    }
}
