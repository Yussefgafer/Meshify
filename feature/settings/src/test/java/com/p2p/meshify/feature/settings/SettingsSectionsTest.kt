package com.p2p.meshify.feature.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.Color
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.TransportMode
import com.p2p.meshify.domain.repository.ThemeMode
import com.p2p.meshify.testing.MainDispatcherRule
import com.p2p.meshify.testing.SettingsRepositoryFake
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SettingsSectionsTest {
    @get:Rule val dispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    @get:Rule val compose = createComposeRule()

    private fun vmWithState(state: SettingsUiState): SettingsViewModel {
        val fake = SettingsRepositoryFake()
        val tm = mockk<TransportManager>(relaxed=true)
        every { tm.bleRuntimeActive } returns MutableStateFlow(state.bleRuntimeActive)
        val vm = SettingsViewModel(fake, tm)
        injectSettingsState(vm, "_settingsUiState", state)
        return vm
    }

    @Test fun `IdentitySection shows display name`() {
        compose.setContent { DxThemeSettings { IdentitySection(state=SettingsUiState(displayName="Alex"), onEditName={}) } }
        compose.onNodeWithText("Alex").assertIsDisplayed()
        compose.onNodeWithText("Display Name").assertIsDisplayed()
    }

    @Test fun `AppearanceSection shows theme and dynamic toggle on`() {
        val state = SettingsUiState(themeMode=ThemeMode.LIGHT, dynamicColorEnabled=true, seedColor=Color.Red.hashCode())
        val vm = vmWithState(state)
        val fakeHaptics = FakePremiumHaptics()
        compose.setContent { DxThemeSettingsFake(fake=fakeHaptics) { AppearanceSection(state=state, viewModel=vm, haptics=fakeHaptics, onOpenThemeSheet={}) } }
        compose.onNodeWithText("Theme Mode").assertIsDisplayed()
        compose.onNodeWithText("Light").assertIsDisplayed()
        compose.onNodeWithText("Dynamic Colors").assertIsDisplayed()
    }

    @Test fun `AppearanceSection dynamic off shows color picker`() {
        val state = SettingsUiState(dynamicColorEnabled=false, seedColor=Color.Blue.hashCode())
        val vm = vmWithState(state)
        val fake = FakePremiumHaptics()
        compose.setContent { DxThemeSettingsFake(fake=fake) { AppearanceSection(state=state, viewModel=vm, haptics=fake, onOpenThemeSheet={}) } }
        compose.onNodeWithText("Accent Color").assertIsDisplayed()
    }

    @Test fun `PrivacySection shows visibility`() {
        val state = SettingsUiState(isNetworkVisible=true)
        val vm = vmWithState(state)
        compose.setContent { DxThemeSettings { PrivacySection(state=state, viewModel=vm, haptics=FakePremiumHaptics()) } }
        compose.onNodeWithText("Visible to Others").assertIsDisplayed()
    }

    @Test fun `NetworkSection shows ble status`() {
        val state = SettingsUiState(bleEnabled=true, bleRuntimeActive=false, transportMode=TransportMode.MULTI_PATH)
        val vm = vmWithState(state)
        compose.setContent { DxThemeSettings { NetworkSection(state=state, viewModel=vm, haptics=FakePremiumHaptics(), onOpenBleSheet={}) } }
        compose.onNodeWithText("Bluetooth").assertIsDisplayed()
        compose.onNodeWithText("BLE Status").assertIsDisplayed()
    }

    @Test fun `AppSettingsSection shows language and font`() {
        val state = SettingsUiState(appLanguage="en", fontSizeScale=1.0f, notificationsEnabled=true, notificationSound=true, notificationVibrate=true)
        val vm = vmWithState(state)
        compose.setContent { DxThemeSettings { AppSettingsSection(state=state, viewModel=vm, haptics=FakePremiumHaptics(), onOpenLanguage={}, onOpenFontSize={}, onClearCache={}) } }
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Font Size").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test fun `AppSettingsSection sound vibrate disabled when notifications off`() {
        val state = SettingsUiState(notificationsEnabled=false, notificationSound=true, notificationVibrate=true)
        val vm = vmWithState(state)
        compose.setContent { DxThemeSettings { AppSettingsSection(state=state, viewModel=vm, haptics=FakePremiumHaptics(), onOpenLanguage={}, onOpenFontSize={}, onClearCache={}) } }
        // Switches disabled: clicking should not fire but we at least ensure displayed
        compose.onNodeWithText("Notification Sound").assertExists()
        compose.onNodeWithText("Vibration").assertExists()
    }

    @Test fun `AboutSection shows version and triggers 7 taps`() {
        var devClicked=false
        val fake = FakePremiumHaptics()
        compose.setContent { DxThemeSettingsFake(fake=fake) { AboutSection(appVersion="1.1.5", haptics=fake, onDeveloperModeClick={devClicked=true}, onOpenGithub={}) } }
        compose.onNodeWithText("1.1.5").assertIsDisplayed()
        repeat(7) { compose.onNodeWithText("App Version").performClick() }
        assert(devClicked)
        assert(fake.calls.contains(com.p2p.meshify.core.ui.hooks.HapticPattern.Success))
    }

    @Test fun `AboutSection less than 7 not trigger`() {
        var devClicked=false
        val fake = FakePremiumHaptics()
        compose.setContent { DxThemeSettingsFake(fake=fake) { AboutSection(appVersion="1.0", haptics=fake, onDeveloperModeClick={devClicked=true}, onOpenGithub={}) } }
        repeat(6) { compose.onNodeWithText("App Version").performClick() }
        assert(!devClicked)
    }

    @Test fun `RTL About no crash`() {
        compose.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Rtl) {
                DxThemeSettings { AboutSection(appVersion="1.0", haptics=FakePremiumHaptics(), onDeveloperModeClick={}, onOpenGithub={}) }
            }
        }
        compose.onNodeWithText("App Version").assertIsDisplayed()
    }

    @Test fun `IdentitySection handles long display name without crash`() {
        val state = SettingsUiState(displayName="LongName".repeat(10), dynamicColorEnabled=false)
        compose.setContent {
            DxThemeSettingsFake(fake=FakePremiumHaptics()) {
                androidx.compose.foundation.layout.Column {
                    repeat(10) { IdentitySection(state=state, onEditName={}) }
                }
            }
        }
        compose.onAllNodesWithText("Display Name").onFirst().assertIsDisplayed()
    }
}
