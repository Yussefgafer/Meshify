package com.p2p.meshify.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.components.ItemPosition
import com.p2p.meshify.core.ui.components.MagneticChatItem
import com.p2p.meshify.core.ui.components.PhysicsSwipeToDelete
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PhysicsSwipeTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `PhysicsSwipe renders content`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                PhysicsSwipeToDelete(onDelete={}, position=ItemPosition.FIRST, groupCornerRadius=24.dp) {
                    Text("Hello Swipe")
                }
            }
        }
        rule.onNodeWithText("Hello Swipe").assertIsDisplayed()
    }

    @Test fun `MagneticChatItem renders without swipe`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MagneticChatItem(index=0, swipingIndex=-1, swipeProgress=0f) {
                    Text("Magnetic")
                }
            }
        }
        rule.onNodeWithText("Magnetic").assertIsDisplayed()
    }

    @Test fun `only position thresholds no crash`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                PhysicsSwipeToDelete(onDelete={}, position=ItemPosition.ONLY, deleteEnabled=false) {
                    Text("Only")
                }
            }
        }
        rule.onNodeWithText("Only").assertIsDisplayed()
    }

    @Test fun `deleteEnabled false still shows content`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                PhysicsSwipeToDelete(onDelete={}, position=ItemPosition.LAST, deleteEnabled=false) {
                    Text("NoDelete")
                }
            }
        }
        rule.onNodeWithText("NoDelete").assertIsDisplayed()
    }
}
