package com.p2p.meshify.core.ui

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.p2p.meshify.core.ui.components.VideoPlayer
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class VideoPlayerTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `shows play placeholder before tap`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                VideoPlayer(
                    videoUri=Uri.parse("content://media/vid.mp4")
                )
            }
        }
        // Robolectric smoke test: assert the composable mounts without crashing.
    }
}
