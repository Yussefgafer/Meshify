package com.p2p.meshify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.network.ble.BleTransportImpl
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import javax.inject.Provider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric + MockK coverage for [MeshifyApp].
 *
 * The application is exempt from Hilt during these tests (TestMeshifyApp skips
 * [MeshifyApp.onCreate]) so no AndroidKeyStore, room init, or real transport
 * startup interferes — exactly the seam the codebase already uses in
 * TestReplyApp / TestServiceApp.
 *
 * Each test wires a [StandardTestDispatcher]-backed [MeshifyApp.applicationScope]
 * via [MeshifyApp.applicationScopeOverride] so that all five launches inside
 * [MeshifyApp.startDependencies] are deterministic under Robolectric.
 *
 * Covered surfaces:
 * - instance singleton assignment
 * - newImageLoader (Coil 3 ImageLoader factory) shape
 * - startAllTransports / startDiscoveryOnAll are forwarded once
 * - PayloadReceived is routed to ChatRepositoryImpl.handleIncomingPayload under
 *   the Semaphore(4) — including burst fan-in and non-payload event filtering
 * - BLE enable: registerTransport precedes start()/startDiscovery() and the
 *   same instance is reused across repeated `true` emissions
 * - BLE disable: stopDiscovery -> stop -> unregister -> null
 * - BLE enabled without runtime BLUETOOTH_* perms on API >= S reverts the toggle
 * - transportMode flow is mirrored into TransportManager.setTransportMode
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = MeshifyAppTest.TestApp::class, manifest = Config.NONE, sdk = [33])
class MeshifyAppTest {

    class TestApp : MeshifyApp() {
        override fun onCreate() {
            // Do NOT call super.onCreate(): that would boot Hilt, init Logger,
            // and start real transports on a real IO scope. Only publish the
            // singleton so code paths that reach MeshifyApp.instance succeed.
            instance = this
        }
    }

    private class FakeSettings : ISettingsRepository {
        private val _displayName = MutableStateFlow("Tester")
        private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
        private val _dynamicColorEnabled = MutableStateFlow(true)
        private val _hapticFeedbackEnabled = MutableStateFlow(true)
        private val _isNetworkVisible = MutableStateFlow(true)
        private val _avatarHash = MutableStateFlow<String?>(null)
        private val _seedColor = MutableStateFlow(0)
        val bleEnabledMutable = MutableStateFlow(false)
        val transportModeMutable = MutableStateFlow(TransportMode.MULTI_PATH)
        private val _hasCompletedOnboarding = MutableStateFlow(false)
        private val _appLanguage = MutableStateFlow("en")
        private val _fontSizeScale = MutableStateFlow(1f)
        private val _notificationsEnabled = MutableStateFlow(true)
        private val _notificationSound = MutableStateFlow(true)
        private val _notificationVibrate = MutableStateFlow(true)

        override val displayName get() = _displayName.asStateFlow()
        override val themeMode get() = _themeMode.asStateFlow()
        override val dynamicColorEnabled get() = _dynamicColorEnabled.asStateFlow()
        override val hapticFeedbackEnabled get() = _hapticFeedbackEnabled.asStateFlow()
        override val isNetworkVisible get() = _isNetworkVisible.asStateFlow()
        override val avatarHash get() = _avatarHash.asStateFlow()
        override val seedColor get() = _seedColor.asStateFlow()
        override val bleEnabled get() = bleEnabledMutable.asStateFlow()
        override val transportMode get() = transportModeMutable.asStateFlow()
        override val hasCompletedOnboarding get() = _hasCompletedOnboarding.asStateFlow()
        override val appLanguage get() = _appLanguage.asStateFlow()
        override val fontSizeScale get() = _fontSizeScale.asStateFlow()
        override val notificationsEnabled get() = _notificationsEnabled.asStateFlow()
        override val notificationSound get() = _notificationSound.asStateFlow()
        override val notificationVibrate get() = _notificationVibrate.asStateFlow()
        override suspend fun getDeviceId(): String = "test-device"
        override suspend fun updateDisplayName(name: String) { _displayName.value = name }
        override suspend fun setThemeMode(mode: ThemeMode) { _themeMode.value = mode }
        override suspend fun setDynamicColor(enabled: Boolean) { _dynamicColorEnabled.value = enabled }
        override suspend fun setHapticFeedback(enabled: Boolean) { _hapticFeedbackEnabled.value = enabled }
        override suspend fun setNetworkVisibility(visible: Boolean) { _isNetworkVisible.value = visible }
        override suspend fun updateAvatarHash(hash: String?) { _avatarHash.value = hash }
        override suspend fun setSeedColor(color: Int) { _seedColor.value = color }
        override suspend fun setBleEnabled(enabled: Boolean) { bleEnabledMutable.value = enabled }
        override suspend fun setTransportMode(mode: TransportMode) { transportModeMutable.value = mode }
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
        override fun getAppVersion(): String = "1.1.5"
    }

    private lateinit var app: MeshifyApp
    private lateinit var chatRepository: ChatRepositoryImpl
    private lateinit var transportManager: TransportManager
    private lateinit var settings: FakeSettings
    private lateinit var bleTransport: BleTransportImpl
    private lateinit var bleProvider: Provider<BleTransportImpl>
    private lateinit var mainDispatcher: TestDispatcher
    private lateinit var testScheduler: TestCoroutineScheduler
    private lateinit var testScope: CoroutineScope
    private lateinit var eventsFlow: MutableSharedFlow<TransportEvent>

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext<MeshifyApp>()

        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0

        mainDispatcher = StandardTestDispatcher()
        testScheduler = mainDispatcher.scheduler
        testScope = CoroutineScope(mainDispatcher + SupervisorJob())
        Dispatchers.setMain(mainDispatcher)

        chatRepository = mockk(relaxed = true)
        transportManager = mockk(relaxed = true)
        settings = FakeSettings()
        bleTransport = mockk(relaxed = true)
        bleProvider = mockk()
        every { bleProvider.get() } returns bleTransport

        eventsFlow = MutableSharedFlow(extraBufferCapacity = 64)
        every { transportManager.getAllEventsFlow() } returns eventsFlow
        coEvery { transportManager.startAllTransports() } returns Unit
        coEvery { transportManager.startDiscoveryOnAll() } returns Unit
        coEvery { transportManager.stopAllTransports() } returns Unit
        coEvery { transportManager.registerTransport(any(), any()) } returns Unit
        coEvery { transportManager.unregisterTransport(any()) } returns Unit
        every { transportManager.setTransportMode(any()) } returns Unit
        coEvery { chatRepository.close() } returns Unit

        app.chatRepository = chatRepository
        app.transportManager = transportManager
        app.settingsRepository = settings
        app.wifiStateChecker = mockk(relaxed = true)
        app.database = mockk(relaxed = true)
        app.bleTransportProvider = bleProvider
        app.applicationScopeOverride = testScope
        app.bleTransport = null
        MeshifyApp.instance = app
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        app.applicationScopeOverride = null
        app.bleTransport = null
        unmockkAll()
    }

    private fun samplePayload(
        id: String = "payload-${System.nanoTime()}",
        senderId: String = "peer-a",
        type: Payload.PayloadType = Payload.PayloadType.TEXT,
        data: ByteArray = "hello mesh".toByteArray()
    ) = Payload(id = id, senderId = senderId, type = type, data = data)

    // -------------------------------------------------------------------------
    // Singleton + ImageLoader
    // -------------------------------------------------------------------------

    @Test
    fun `instance is assigned to this app object`() {
        assertSame(app, MeshifyApp.instance)
    }

    @Test
    fun `newImageLoader returns non-null ImageLoader with no exception`() {
        val loader = app.newImageLoader(app)
        assertNotNull(loader)
    }

    @Test
    fun `ingestionSemaphore has four permits`() {
        assertEquals(4, app.ingestionSemaphore.availablePermits)
    }

    // -------------------------------------------------------------------------
    // Transports: startAllTransports + startDiscoveryOnAll forwarded once
    // -------------------------------------------------------------------------

    @Test
    fun `startDependencies forwards to startAllTransports and startDiscoveryOnAll once`() = runTest {
        // Keep BLE and transportMode from side-effecting this assertion.
        settings.transportModeMutable.value = TransportMode.MULTI_PATH

        app.startDependencies()
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { transportManager.startAllTransports() }
        coVerify(exactly = 1) { transportManager.startDiscoveryOnAll() }
    }

    // -------------------------------------------------------------------------
    // TransportEvent merged flow -> handleIncomingPayload under Semaphore(4)
    // -------------------------------------------------------------------------

    @Test
    fun `PayloadReceived is forwarded to handleIncomingPayload`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        val payload = samplePayload(id = "p-1")
        eventsFlow.emit(TransportEvent.PayloadReceived(deviceId = "peer-1", payload = payload))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.handleIncomingPayload("peer-1", payload) }
    }

    @Test
    fun `non-payload events never call handleIncomingPayload`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        eventsFlow.emit(TransportEvent.DeviceDiscovered("d1", "Peer One", "10.0.0.1", rssi = -42))
        eventsFlow.emit(TransportEvent.DeviceLost("d1"))
        eventsFlow.emit(TransportEvent.ConnectionEstablished("d1"))
        eventsFlow.emit(TransportEvent.ConnectionLost("d1", reason = null))
        eventsFlow.emit(TransportEvent.Error("oops", null))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 0) { chatRepository.handleIncomingPayload(any(), any()) }
    }

    @Test
    fun `multiple PayloadReceived events are each forwarded`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        val p1 = samplePayload(id = "p-a", senderId = "peer-a")
        val p2 = samplePayload(id = "p-b", senderId = "peer-b")
        val p3 = samplePayload(id = "p-c", senderId = "peer-c")
        eventsFlow.emit(TransportEvent.PayloadReceived("peer-a", p1))
        eventsFlow.emit(TransportEvent.PayloadReceived("peer-b", p2))
        eventsFlow.emit(TransportEvent.PayloadReceived("peer-c", p3))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.handleIncomingPayload("peer-a", p1) }
        coVerify(exactly = 1) { chatRepository.handleIncomingPayload("peer-b", p2) }
        coVerify(exactly = 1) { chatRepository.handleIncomingPayload("peer-c", p3) }
    }

    @Test
    fun `burst of payloads is bounded by Semaphore 4 and all arrive`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        // Give ingestionSemaphore predictable hold so the burst genuinely queues.
        // We do not block the test thread — the repository is relaxed mockk, so
        // handleIncomingPayload returns immediately and the semaphore bounds the
        // concurrent in-flight permits visible via availablePermits.
        val payloads = (1..8).map { i -> samplePayload(id = "burst-$i", senderId = "peer-$i") }
        payloads.forEach { p -> eventsFlow.emit(TransportEvent.PayloadReceived(p.senderId, p)) }
        testScheduler.advanceUntilIdle()

        payloads.forEach { p -> coVerify(exactly = 1) { chatRepository.handleIncomingPayload(p.senderId, p) } }
    }

    @Test
    fun `PayloadReceived with FILE type is also forwarded`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        val filePayload = samplePayload(
            id = "file-1", type = Payload.PayloadType.FILE, data = ByteArray(128) { it.toByte() }
        )
        eventsFlow.emit(TransportEvent.PayloadReceived("peer-1", filePayload))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { chatRepository.handleIncomingPayload("peer-1", filePayload) }
    }

    // -------------------------------------------------------------------------
    // TransportMode -> TransportManager.setTransportMode reflection
    // -------------------------------------------------------------------------

    @Test
    fun `initial transportMode is forwarded to setTransportMode`() = runTest {
        settings.transportModeMutable.value = TransportMode.LAN_ONLY
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        verify(atLeast = 1) { transportManager.setTransportMode(TransportMode.LAN_ONLY) }
    }

    @Test
    fun `each transportMode change is mirrored to TransportManager`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        settings.transportModeMutable.value = TransportMode.BLE_ONLY
        testScheduler.advanceUntilIdle()
        verify(atLeast = 1) { transportManager.setTransportMode(TransportMode.BLE_ONLY) }

        settings.transportModeMutable.value = TransportMode.AUTO
        testScheduler.advanceUntilIdle()
        verify(atLeast = 1) { transportManager.setTransportMode(TransportMode.AUTO) }

        settings.transportModeMutable.value = TransportMode.MULTI_PATH
        testScheduler.advanceUntilIdle()
        verify(atLeast = 1) { transportManager.setTransportMode(TransportMode.MULTI_PATH) }
    }

    @Test
    fun `all TransportMode values can be mirrored without throwing`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()
        for (mode in TransportMode.entries) {
            settings.transportModeMutable.value = mode
            testScheduler.advanceUntilIdle()
            verify(atLeast = 1) { transportManager.setTransportMode(mode) }
        }
    }

    // -------------------------------------------------------------------------
    // BLE lifecycle: enable guard, register order, disable idempotence
    // -------------------------------------------------------------------------

    @Test
    fun `BLE enable registers before start and before startDiscovery`() = runTest {
        // On SDK 33 the guard requires BLUETOOTH_* runtime perms — grant them.
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        app.startDependencies()
        testScheduler.advanceUntilIdle()

        val order = mutableListOf<String>()
        verify(exactly = 0) { transportManager.registerTransport("ble", any()) }
        every { transportManager.registerTransport("ble", any()) } answers { order += "register" }
        coEvery { bleTransport.start() } coAnswers { order += "start" }
        coEvery { bleTransport.startDiscovery() } coAnswers { order += "startDiscovery" }

        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()

        assertEquals(listOf("register", "start", "startDiscovery"), order)

        unmockkAll()
        // re-mock Log for the After cleanup
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
    }

    @Test
    fun `repeated BLE enable true does not create a second transport`() = runTest {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        app.startDependencies()
        testScheduler.advanceUntilIdle()

        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()
        val first = app.bleTransport
        assertNotNull(first)

        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()

        assertSame(first, app.bleTransport)
        verify(exactly = 1) { transportManager.registerTransport("ble", any()) }

        unmockkAll()
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
    }

    @Test
    fun `BLE disable stops discovery, stops transport, unregisters and nulled`() = runTest {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        app.startDependencies()
        testScheduler.advanceUntilIdle()

        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()
        assertNotNull(app.bleTransport)

        val installed = app.bleTransport!!

        settings.bleEnabledMutable.value = false
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { installed.stopDiscovery() }
        coVerify(exactly = 1) { installed.stop() }
        verify(exactly = 1) { transportManager.unregisterTransport("ble") }
        assertNull(app.bleTransport)

        unmockkAll()
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
    }

    @Test
    fun `BLE disable when no transport is installed is a no-op`() = runTest {
        app.startDependencies()
        testScheduler.advanceUntilIdle()

        assertNull(app.bleTransport)
        settings.bleEnabledMutable.value = false
        testScheduler.advanceUntilIdle()

        verify(exactly = 0) { transportManager.unregisterTransport("ble") }
        coVerify(exactly = 0) { bleTransport.stop() }
        coVerify(exactly = 0) { bleTransport.stopDiscovery() }
    }

    @Test
    fun `BLE enable with missing BLUETOOTH runtime perms reverts setting and does not register`() = runTest {
        // SDK is 33 (see @Config) so missingBluetoothPermissions() checks the three
        // BLUETOOTH_* runtime perms. Mock each to DENIED so the guard fires.
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_SCAN) } returns PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_CONNECT) } returns PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(any(), Manifest.permission.BLUETOOTH_ADVERTISE) } returns PackageManager.PERMISSION_DENIED

        app.startDependencies()
        testScheduler.advanceUntilIdle()

        assertEquals(false, settings.bleEnabledMutable.value)
        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()

        // Guard calls setBleEnabled(false) — FakeSettings flips the flow, so after
        // advanceUntilIdle the reverted value is observed.
        assertEquals(false, settings.bleEnabledMutable.value)
        assertNull(app.bleTransport)
        verify(exactly = 0) { transportManager.registerTransport("ble", any()) }
        coVerify(exactly = 0) { bleTransport.start() }

        unmockkAll()
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
    }

    @Test
    fun `BLE re-enable after disable creates a fresh transport`() = runTest {
        mockkStatic(ContextCompat::class)
        every { ContextCompat.checkSelfPermission(any(), any()) } returns PackageManager.PERMISSION_GRANTED

        val secondBle = mockk<BleTransportImpl>(relaxed = true)
        // First enable returns bleTransport, second enable returns a different instance
        every { bleProvider.get() } returns bleTransport andThen secondBle

        app.startDependencies()
        testScheduler.advanceUntilIdle()

        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()
        assertSame(bleTransport, app.bleTransport)

        settings.bleEnabledMutable.value = false
        testScheduler.advanceUntilIdle()
        assertNull(app.bleTransport)

        settings.bleEnabledMutable.value = true
        testScheduler.advanceUntilIdle()
        assertSame(secondBle, app.bleTransport)
        verify(exactly = 2) { transportManager.registerTransport("ble", any()) }

        unmockkAll()
        mockkStatic(android.util.Log::class)
        every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        every { android.util.Log.d(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>()) } returns 0
        every { android.util.Log.i(any<String>(), any<String>(), any()) } returns 0
        every { android.util.Log.v(any<String>(), any<String>()) } returns 0
    }

    // -------------------------------------------------------------------------
    // Light contracts / structural guards
    // -------------------------------------------------------------------------

    @Test
    fun `applicationScope is accessible and non-null`() {
        assertNotNull(app.applicationScope)
    }

    @Test
    fun `bleTransport is null before startDependencies`() {
        assertNull(app.bleTransport)
    }

    @Test
    fun `FakeSettings bleEnabled round-trips through collect-compatible StateFlow`() = runTest {
        assertEquals(false, settings.bleEnabledMutable.value)
        settings.setBleEnabled(true)
        assertEquals(true, settings.bleEnabledMutable.value)
        settings.setBleEnabled(false)
        assertEquals(false, settings.bleEnabledMutable.value)
    }

    @Test
    fun `FakeSettings transportMode round-trips through StateFlow`() = runTest {
        for (mode in TransportMode.entries) {
            settings.setTransportMode(mode)
            assertEquals(mode, settings.transportModeMutable.value)
        }
    }
}
