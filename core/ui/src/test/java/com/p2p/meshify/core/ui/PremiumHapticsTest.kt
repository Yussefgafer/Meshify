package com.p2p.meshify.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.p2p.meshify.core.ui.designsystem.components.DxSettingsItem
import com.p2p.meshify.core.ui.hooks.HapticPattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PremiumHapticsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `pop is called on DxSettingsItem click`() {
        val fake = FakePremiumHaptics()
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                DxSettingsItem(
                    icon = Icons.Default.Add,
                    title = "Title",
                    subtitle = "Sub",
                    onClick = {},
                    showChevron = true
                )
            }
        }
        rule.onNodeWithText("Title").assertIsDisplayed()
        rule.onNodeWithText("Title").performClick()
        assert(fake.calls.contains(HapticPattern.Pop))
    }

    @Test fun `switch toggle fires haptic tick`() {
        val fake = FakePremiumHaptics()
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                com.p2p.meshify.core.ui.designsystem.components.DxSwitchSettingItem(
                    icon = Icons.Default.Add,
                    title = "Notif",
                    subtitle = "On",
                    checked = false,
                    onCheckedChange = {}
                )
            }
        }
        // click the whole row -> toggle invokes Tick
        rule.onNodeWithText("Notif").performClick()
        assert(fake.calls.contains(HapticPattern.Tick))
    }
}
