package com.p2p.meshify.feature.settings

import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [SettingsViewModel].
 *
 * Scope:
 *   Drives SettingsViewModel as a plain class (no Hilt). Both constructor
 *   dependencies are interfaces: [ISettingsRepository] (mocked relaxed) and
 *   [TransportManager] (mocked relaxed). The init block fans 14 repository
 *   flows + `transportManager.bleRuntimeActive` into the uiState via `onEach`
 *   collectors — each Flow property must be stubbed to a real [MutableStateFlow]
 *   (relaxed mockk returns null for Flow getters, which would NPE the collector).
 *
 * Why no Robolectric:
 *   SettingsViewModel never reads Android resources / Context. `appVersion` is a
 *   plain non-suspend `getAppVersion()` call; `updateAvatar` (Uri/FileUtils) is
 *   intentionally NOT exercised here — that path is Robolectric-bound and covered
 *   separately. With `unitTests.isReturnDefaultValues = true`, the occasional
 *   `Logger` -> `android.util.Log` call returns 0 instead of throwing.
 *
 * What's tested:
 *   - init fan-in: a representative repo flow (displayName) and the
 *     bleRuntimeActive flow both land in uiState.
 *   - `getDeviceId` seeds deviceId + deviceIdLoaded in uiState.
 *   - `updateDisplayName` success nulls displayNameError.
 *   - `updateDisplayName` with illegal argument sets displayNameError from the
 *     exception message.
 *   - `setThemeMode` / `setBleEnabled` success leave errorMessage null.
 *   - `setThemeMode` failure surfaces the exception message in errorMessage.
 *   - `clearError` resets errorMessage.
 *   - `setTransportMode` delegates to the repository.
 *
 * Limitations:
 *   - `updateAvatar` (Uri + FileUtils disk IO) is not driven — it is contextual
 *     and covered by the Robolectric FileUtils tests instead.
 *   - Only a subset of the 14 set* delegate methods are asserted explicitly; the
 *     ones not named share the identical try/catch(errorMessage) shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(kotlinx.coroutines.test.StandardTestDispatcher())

    private lateinit var repository: ISettingsRepository
    private lateinit var transportManager: TransportManager
    private var currentVm: SettingsViewModel? = null

    // Controllable flows backing each repository property.
    private val displayNameFlow = MutableStateFlow("")
    private val themeModeFlow = MutableStateFlow(ThemeMode.SYSTEM)
    private val dynamicColorFlow = MutableStateFlow(true)
    private val hapticFlow = MutableStateFlow(true)
    private val networkVisibleFlow = MutableStateFlow(true)
    private val avatarHashFlow = MutableStateFlow<String?>(null)
    private val seedColorFlow = MutableStateFlow(0xFF006D68.toInt())
    private val bleEnabledFlow = MutableStateFlow(false)
    private val transportModeFlow = MutableStateFlow(TransportMode.MULTI_PATH)
    private val hasOnboardingFlow = MutableStateFlow(true)
    private val appLanguageFlow = MutableStateFlow("en")
    private val fontSizeFlow = MutableStateFlow(1.0f)
    private val notificationsFlow = MutableStateFlow(true)
    private val notificationSoundFlow = MutableStateFlow(true)
    private val notificationVibrateFlow = MutableStateFlow(true)
    private val bleRuntimeActiveFlow = MutableStateFlow(false)

    @Before
    fun setUpDispatchers() {
        Dispatchers.setMain(mainRule.dispatcher)
    }

    @After
    fun tearDownDispatchers() {
        currentVm?.viewModelScope?.cancel()
        currentVm = null
        Dispatchers.resetMain()
    }

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        transportManager = mockk(relaxed = true)

        every { repository.displayName } returns displayNameFlow
        every { repository.themeMode } returns themeModeFlow
        every { repository.dynamicColorEnabled } returns dynamicColorFlow
        every { repository.hapticFeedbackEnabled } returns hapticFlow
        every { repository.isNetworkVisible } returns networkVisibleFlow
        every { repository.avatarHash } returns avatarHashFlow
        every { repository.seedColor } returns seedColorFlow
        every { repository.bleEnabled } returns bleEnabledFlow
        every { repository.transportMode } returns transportModeFlow
        every { repository.hasCompletedOnboarding } returns hasOnboardingFlow
        every { repository.appLanguage } returns appLanguageFlow
        every { repository.fontSizeScale } returns fontSizeFlow
        every { repository.notificationsEnabled } returns notificationsFlow
        every { repository.notificationSound } returns notificationSoundFlow
        every { repository.notificationVibrate } returns notificationVibrateFlow
        every { transportManager.bleRuntimeActive } returns bleRuntimeActiveFlow

        every { repository.getAppVersion() } returns "1.1.4"
        coEvery { repository.getDeviceId() } returns "device-1"
        coEvery { repository.updateDisplayName(any()) } returns Unit
        coEvery { repository.setThemeMode(any()) } returns Unit
        coEvery { repository.setBleEnabled(any()) } returns Unit
        coEvery { repository.setTransportMode(any()) } returns Unit
    }

    private fun newVm(): SettingsViewModel =
        SettingsViewModel(repository, transportManager).also { currentVm = it }

    // ===== init fan-in =====

    @Test
    fun `init — displayName flow lands in uiState`() = runTest {
        displayNameFlow.value = "Yussef"
        val vm = newVm()
        runCurrent()

        assertEquals("Yussef", vm.settingsUiState.value.displayName)
    }

    @Test
    fun `init — bleRuntimeActive flow lands in uiState`() = runTest {
        bleRuntimeActiveFlow.value = true
        val vm = newVm()
        runCurrent()

        assertTrue(vm.settingsUiState.value.bleRuntimeActive)
    }

    @Test
    fun `init — getDeviceId seeds deviceId and deviceIdLoaded`() = runTest {
        val vm = newVm()
        runCurrent()

        assertEquals("device-1", vm.settingsUiState.value.deviceId)
        assertTrue(vm.settingsUiState.value.deviceIdLoaded)
    }

    @Test
    fun `appVersion — surfaces the repository version`() = runTest {
        val vm = newVm()
        runCurrent()

        assertEquals("1.1.4", vm.appVersion)
    }

    // ===== display name =====

    @Test
    fun `updateDisplayName — success nulls a prior displayNameError`() = runTest {
        // First call fails, leaving a displayNameError; subsequent calls succeed.
        var firstCall = true
        coEvery { repository.updateDisplayName(any()) } coAnswers {
            if (firstCall) {
                firstCall = false
                throw IllegalArgumentException("too long")
            } else Unit
        }
        val vm = newVm()
        runCurrent()

        vm.updateDisplayName("Bob")
        runCurrent()
        assertEquals("too long", vm.settingsUiState.value.displayNameError)

        vm.updateDisplayName("Bob")
        runCurrent()

        assertNull(vm.settingsUiState.value.displayNameError)
        coVerify(exactly = 2) { repository.updateDisplayName("Bob") }
    }

    @Test
    fun `updateDisplayName — illegal argument surfaces displayNameError`() = runTest {
        coEvery { repository.updateDisplayName("") } throws IllegalArgumentException("Name cannot be empty")
        val vm = newVm()
        runCurrent()

        vm.updateDisplayName("")
        runCurrent()

        assertEquals("Name cannot be empty", vm.settingsUiState.value.displayNameError)
    }

    // ===== set* delegate methods =====

    @Test
    fun `setThemeMode — success leaves errorMessage null`() = runTest {
        val vm = newVm()
        runCurrent()

        vm.setThemeMode(ThemeMode.DARK)
        runCurrent()

        assertNull(vm.errorMessage.value)
        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.DARK) }
    }

    @Test
    fun `setBleEnabled — success delegates to the repository`() = runTest {
        val vm = newVm()
        runCurrent()

        vm.setBleEnabled(true)
        runCurrent()

        coVerify(exactly = 1) { repository.setBleEnabled(true) }
        assertNull(vm.errorMessage.value)
    }

    @Test
    fun `setThemeMode — failure surfaces the exception message`() = runTest {
        coEvery { repository.setThemeMode(any()) } throws RuntimeException("disk write failed")
        val vm = newVm()
        runCurrent()

        vm.setThemeMode(ThemeMode.DARK)
        runCurrent()

        assertEquals("disk write failed", vm.errorMessage.value)
    }

    @Test
    fun `setTransportMode — delegates to the repository`() = runTest {
        val vm = newVm()
        runCurrent()

        vm.setTransportMode(TransportMode.LAN_ONLY)
        runCurrent()

        coVerify(exactly = 1) { repository.setTransportMode(TransportMode.LAN_ONLY) }
    }

    @Test
    fun `clearError — resets errorMessage`() = runTest {
        coEvery { repository.setThemeMode(any()) } throws RuntimeException("boom")
        val vm = newVm()
        runCurrent()

        vm.setThemeMode(ThemeMode.DARK)
        runCurrent()
        assertEquals("boom", vm.errorMessage.value)

        vm.clearError()
        assertNull(vm.errorMessage.value)
    }
}
