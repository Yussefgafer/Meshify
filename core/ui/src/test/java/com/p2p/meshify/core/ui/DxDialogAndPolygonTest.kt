package com.p2p.meshify.core.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import com.p2p.meshify.core.ui.designsystem.components.DxDialog
import com.p2p.meshify.core.ui.designsystem.components.DxPolygonShape
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class DxDialogAndPolygonTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `DxDialog hidden shows nothing`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxDialog(visible=false, onDismiss={}, icon=Icons.Default.Info, title={Text("Hello")}, text={Text("Body")}, confirmButton={TextButton(onClick={}){Text("OK")}})
            }
        }
        rule.onNodeWithText("Hello").assertDoesNotExist()
        rule.onNodeWithText("OK").assertDoesNotExist()
    }

    @Test fun `DxDialog visible shows title and confirm`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxDialog(
                    visible=true,
                    onDismiss={},
                    icon=Icons.Default.Info,
                    title={Text("Hello")},
                    text={Text("Body")},
                    confirmButton={ TextButton(onClick={}) {Text("OK")} }
                )
            }
        }
        rule.onNodeWithText("Hello").assertIsDisplayed()
        rule.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test fun `DxPolygonShape creates outline non-crash`() {
        val polygon = MaterialShapes.Sunny
        @Suppress("DEPRECATION")
        val shape: Shape = DxPolygonShape(polygon)
        assertNotNull(shape)
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                androidx.compose.foundation.layout.Box(modifier=Modifier.size(48.dp))
            }
        }
    }

    @Test fun `DxDialog with polygon still renders`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                DxDialog(visible=true, onDismiss={}, icon=Icons.Default.Info, polygon=MaterialShapes.Cookie9Sided, title={Text("P")})
            }
        }
        rule.onNodeWithText("P").assertIsDisplayed()
    }
}
