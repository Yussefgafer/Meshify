package com.p2p.meshify.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import com.p2p.meshify.core.ui.components.MeshifyAvatar
import com.p2p.meshify.core.ui.components.MeshifyAvatarWithOnline
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class MeshifyKitTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `MeshifyAvatar shows initials`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MeshifyAvatar(initials="AB", size=56.dp)
            }
        }
        rule.onNodeWithText("AB").assertIsDisplayed()
    }

    @Test fun `MeshifyAvatar with empty initials renders without crash`() {
        rule.setContent { DxTestTheme(dynamicColor=false) { MeshifyAvatar(initials="", size=56.dp) } }
        rule.onNodeWithText("AB").assertDoesNotExist()
    }

    @Test fun `MeshifyAvatarWithOnline offline still shows`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MeshifyAvatarWithOnline(initials="CD", isOnline=false, size=56.dp)
            }
        }
        rule.onNodeWithText("CD").assertIsDisplayed()
    }

    @Test fun `MeshifyAvatar with null hash shows initials without crash`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                MeshifyAvatar(initials="EF", avatarHash=null, size=56.dp)
            }
        }
        rule.onNodeWithText("EF").assertIsDisplayed()
    }
}
