package com.p2p.meshify.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.p2p.meshify.core.ui.designsystem.DxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DxThemeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `DxTheme light renders content`() {
        rule.setContent {
            DxTheme(themeMode = "LIGHT", dynamicColor = false) {
                Text("LightTheme")
            }
        }
        rule.onNodeWithText("LightTheme").assertIsDisplayed()
    }

    @Test fun `DxTheme dark renders content`() {
        rule.setContent {
            DxTheme(themeMode = "DARK", dynamicColor = false) {
                Text("DarkTheme")
            }
        }
        rule.onNodeWithText("DarkTheme").assertIsDisplayed()
    }

    @Test fun `DxTheme system renders content`() {
        rule.setContent {
            DxTheme(themeMode = "SYSTEM", dynamicColor = false) {
                Text("SystemTheme")
            }
        }
        rule.onNodeWithText("SystemTheme").assertIsDisplayed()
    }

    @Test fun `DxTheme unknown mode falls back to system`() {
        rule.setContent {
            DxTheme(themeMode = "UNKNOWN", dynamicColor = false) {
                Text("FallbackTheme")
            }
        }
        rule.onNodeWithText("FallbackTheme").assertIsDisplayed()
    }

    @Test fun `DxTheme dynamicColor false does not crash`() {
        rule.setContent {
            DxTheme(themeMode = "LIGHT", dynamicColor = false) {
                Text("NoDynamic")
            }
        }
        rule.onNodeWithText("NoDynamic").assertIsDisplayed()
    }

    @Test fun `DxTheme provides MaterialExpressiveTheme colorScheme and shapes`() {
        var colorSchemePrimary: androidx.compose.ui.graphics.Color? = null
        var shapeMedium: androidx.compose.ui.graphics.Shape? = null
        rule.setContent {
            DxTheme(themeMode = "LIGHT", dynamicColor = false) {
                colorSchemePrimary = MaterialTheme.colorScheme.primary
                shapeMedium = MaterialTheme.shapes.medium
                Text("Probe")
            }
        }
        rule.onNodeWithText("Probe").assertIsDisplayed()
        assert(colorSchemePrimary != null)
        assert(shapeMedium != null)
    }

    @Test fun `DxTheme nested content with haptics works`() {
        val fake = FakePremiumHaptics()
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                DxTheme(themeMode = "DARK", dynamicColor = false) {
                    Text("NestedTheme")
                }
            }
        }
        rule.onNodeWithText("NestedTheme").assertIsDisplayed()
    }
}
