package com.p2p.meshify.feature.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.testing.MainDispatcherRule
import com.p2p.meshify.testing.SettingsRepositoryFake
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class SettingsScreenTest {
    @get:Rule val dispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    @get:Rule val compose = createComposeRule()

    private fun createVm(displayName:String="Tester", deviceIdLoaded:Boolean=true): SettingsViewModel {
        val fake = SettingsRepositoryFake().apply { deviceId = "ABCD1234EFGH5678" }
        val tm = mockk<TransportManager>(relaxed=true)
        every { tm.bleRuntimeActive } returns MutableStateFlow(false)
        val vm = SettingsViewModel(fake, tm)
        injectSettingsState(vm, "_settingsUiState", SettingsUiState(displayName=displayName, deviceId="ABCD1234EFGH5678", deviceIdLoaded=deviceIdLoaded, avatarHash=null))
        return vm
    }

    @Test fun `SettingsScreen shows identity and avatar`() {
        val vm = createVm("Alex")
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}) } }
        compose.onAllNodesWithText("Alex").onFirst().assertIsDisplayed()
        compose.onNodeWithText("AL").assertIsDisplayed()
    }

    @Test fun `SettingsScreen shows sections`() {
        val vm = createVm()
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}) } }
        compose.onNodeWithText("PROFILE IDENTITY", substring=true).assertIsDisplayed()
        compose.onNodeWithText("APPEARANCE").assertExists()
        compose.onNodeWithText("PRIVACY & NETWORK", substring=true).assertExists()
    }

    @Test fun `deviceId pill shows short id uppercased`() {
        val vm = createVm()
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}) } }
        compose.onNodeWithText("ABCD1234").assertIsDisplayed()
    }

    @Test fun `back button calls onBackClick`() {
        val vm = createVm()
        var back=false
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={back=true}) } }
        compose.onNode(hasContentDescription("Back")).performClick()
        assert(back)
    }

    @Test fun `name dialog appears on edit name click`() {
        val vm = createVm()
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}) } }
        compose.onNodeWithText("Display Name").performClick()
        // should show dialog title Edit Display Name
        compose.onNodeWithText("Edit Display Name").assertIsDisplayed()
    }

    @Test fun `RTL no crash`() {
        val vm = createVm()
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}) }
            }
        }
        compose.onNodeWithText("PROFILE IDENTITY", substring=true).assertIsDisplayed()
    }

    @Test fun `SettingsScreen renders without crash`() {
        val vm = createVm()
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}) } }
        compose.onNodeWithText("PROFILE IDENTITY", substring=true).assertIsDisplayed()
    }

    @Test fun `about secret 7 taps triggers developer`() {
        val vm = createVm()
        var dev=false
        compose.setContent { DxThemeSettings { SettingsScreen(viewModel=vm, onBackClick={}, onDeveloperModeClick={dev=true}) } }
        val versionItem = compose.onNodeWithText("App Version")
        repeat(7) { versionItem.performScrollTo().performClick() }
        assert(dev)
    }
}
