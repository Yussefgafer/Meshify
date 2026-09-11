package com.p2p.meshify.feature.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.p2p.meshify.domain.repository.ThemeMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.p2p.meshify.domain.model.TransportMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class DialogsAndSheetsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `SettingsNameDialog shows placeholder`() {
        rule.setContent { DxThemeSettings { SettingsNameDialog(displayName="Bob", errorText=null, onConfirm={}, onDismiss={}) } }
        rule.onNodeWithText("Edit Display Name").assertIsDisplayed()
    }

    @Test fun `SettingsNameDialog error shows`() {
        rule.setContent { DxThemeSettings { SettingsNameDialog(displayName="Bob", errorText="Too short", onConfirm={}, onDismiss={}) } }
        rule.onNodeWithText("Too short").assertIsDisplayed()
    }

    @Test fun `SettingsLanguageDialog shows English and Arabic`() {
        rule.setContent { DxThemeSettings { SettingsLanguageDialog(appLanguage="en", onDismiss={}, onLanguageSelected={}) } }
        rule.onNodeWithText("English").assertIsDisplayed()
        rule.onNodeWithText("العربية").assertExists()
    }

    @Test fun `SettingsFontSizeDialog shows 100%`() {
        rule.setContent { DxThemeSettings { SettingsFontSizeDialog(fontSizeScale=1.0f, onFontSizeSelected={}, onDismiss={}) } }
        rule.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test fun `SettingsThemeSheet shows Light option`() {
        rule.setContent { DxThemeSettings { SettingsThemeSheet(currentTheme=ThemeMode.LIGHT, onThemeSelected={}, onDismiss={}) } }
        rule.onNodeWithText("Light").assertIsDisplayed()
    }

    @Test fun `BleStatusBottomSheet shows Bluetooth status header`() {
        rule.setContent { DxThemeSettings { BleStatusBottomSheet(bleRuntimeActive=true, transportMode=TransportMode.MULTI_PATH, onModeSelected={}, onDismiss={}) } }
        rule.onNodeWithText("Bluetooth", substring=true).assertIsDisplayed()
    }

    @Test fun `BleStatusBottomSheet shows transport mode options`() {
        rule.setContent { DxThemeSettings { BleStatusBottomSheet(bleRuntimeActive=false, transportMode=TransportMode.LAN_ONLY, onModeSelected={}, onDismiss={}) } }
        rule.onNodeWithText("Transport Mode", substring=true).assertExists()
        rule.onNodeWithText("Multi-Path").assertExists()
        rule.onNodeWithText("LAN Only").assertExists()
    }

    @Test fun `DeveloperScreen sections exist`() {
        val mockDaoChat = io.mockk.mockk<com.p2p.meshify.core.data.local.dao.ChatDao>(relaxed=true)
        val mockDaoMsg = io.mockk.mockk<com.p2p.meshify.core.data.local.dao.MessageDao>(relaxed=true)
        val vm = DeveloperViewModel(mockDaoChat, mockDaoMsg)
        rule.setContent { DxThemeSettings { DeveloperScreen(viewModel=vm, onBackClick={}) } }
        rule.onNodeWithText("Developer", substring=true).assertIsDisplayed()
        rule.onNodeWithText("MOCK DATA").assertIsDisplayed()
    }

    @Test fun `DeveloperScreen renders without crash`() {
        val vm = DeveloperViewModel(io.mockk.mockk(relaxed=true), io.mockk.mockk(relaxed=true))
        rule.setContent { DxThemeSettings { DeveloperScreen(viewModel=vm, onBackClick={}) } }
        rule.onNodeWithText("Developer", substring = true).assertIsDisplayed()
    }
}
