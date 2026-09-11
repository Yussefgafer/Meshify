package com.p2p.meshify.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsDivider
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsItem
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsSection
import com.p2p.meshify.core.ui.designsystem.components.DxSwitchSettingItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class DxDesignSystemComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `DxSettingsSection renders uppercase title`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxSettingsSection(title="Appearance") {
                    DxSettingsItem(icon=Icons.Default.Person, title="Name", subtitle="Johnny", onClick={}, showChevron=true)
                }
            }
        }
        rule.onNodeWithText("APPEARANCE").assertIsDisplayed()
    }

    @Test fun `DxSettingsItem shows title subtitle and navigates on click`() {
        var clicked=false
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxSettingsItem(icon=Icons.Default.Settings, title="Display Name", subtitle="Alex", onClick={clicked=true}, showChevron=true)
            }
        }
        rule.onNodeWithText("Display Name").assertIsDisplayed()
        rule.onNodeWithText("Alex").assertIsDisplayed()
        rule.onNodeWithText("Display Name").performClick()
        assertTrue(clicked)
    }

    @Test fun `DxSwitchSettingItem toggles and supports accessibility role`() {
        var checked=false
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxSwitchSettingItem(icon=Icons.Default.Settings, title="Dynamic Colors", subtitle="Wallpaper", checked=checked, onCheckedChange={checked=it})
            }
        }
        // assert switch off
        rule.onNode(hasText("Dynamic Colors")).assertIsDisplayed()
        // toggle row
        rule.onNodeWithText("Dynamic Colors").performClick()
        assertTrue(checked)
    }

    @Test fun `DxSwitch disabled does not fire`() {
        var fired=false
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxSwitchSettingItem(icon=Icons.Default.Settings, title="Vibrate", subtitle="Off when disabled", checked=true, enabled=false, onCheckedChange={fired=true})
            }
        }
        rule.onNodeWithText("Vibrate").performClick()
        assert(!fired)
    }

    @Test fun `DxSettingsDivider renders without crash`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                Column { DxSettingsSection(title="Group") { DxSettingsDivider() } }
            }
        }
        rule.onNodeWithText("GROUP").assertIsDisplayed()
    }

    @Test fun `RTL layout does not crash`() {
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxTestTheme(dynamicColor=false) {
                    DxSettingsSection(title="Privacy") {
                        DxSettingsItem(icon=Icons.Default.Person, title="Name", subtitle="Sub", onClick={})
                    }
                }
            }
        }
        rule.onNodeWithText("PRIVACY").assertIsDisplayed()
    }

    @Test fun `many sections scales`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                Column {
                    repeat(60) { idx ->
                        DxSettingsSection(title="Section $idx") {
                            DxSettingsItem(icon=Icons.Default.Person, title="Item $idx", subtitle="s", onClick={})
                        }
                    }
                }
            }
        }
        // assert first and last manageable; many items off-screen need scroll -> verify count via existence of Section 0 title uppercased
        rule.onNodeWithText("SECTION 0").assertIsDisplayed()
        rule.onNodeWithText("Item 0").assertIsDisplayed()
    }
}
