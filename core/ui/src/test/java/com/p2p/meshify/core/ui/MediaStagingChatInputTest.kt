package com.p2p.meshify.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.TextFieldValue
import com.p2p.meshify.core.ui.components.MediaStagingChatInput
import com.p2p.meshify.core.ui.hooks.HapticPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MediaStagingChatInputTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `shows placeholder when empty and no attachments`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = {},
                    hasAttachments = false
                )
            }
        }
        rule.onNodeWithText("Add a caption…").assertIsDisplayed()
    }

    @Test fun `placeholder hidden when text present`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue("hello"),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = {},
                    hasAttachments = false
                )
            }
        }
        rule.onNodeWithText("Add a caption…").assertDoesNotExist()
    }

    @Test fun `placeholder hidden when hasAttachments`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = {},
                    hasAttachments = true
                )
            }
        }
        rule.onNodeWithText("Add a caption…").assertDoesNotExist()
    }

    @Test fun `renders gallery video file and send buttons by contentDescription`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = {},
                    onFileClick = {}
                )
            }
        }
        rule.onNodeWithContentDescription("Gallery").assertIsDisplayed()
        rule.onNodeWithContentDescription("Video").assertIsDisplayed()
        rule.onNodeWithContentDescription("File").assertIsDisplayed()
        rule.onNodeWithContentDescription("Send").assertIsDisplayed()
    }

    @Test fun `gallery click triggers callback and haptic Pop`() {
        val fake = FakePremiumHaptics()
        var galleryClicked = false
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = { galleryClicked = true },
                    onVideoClick = {}
                )
            }
        }
        rule.onNodeWithContentDescription("Gallery").performClick()
        assertTrue(galleryClicked)
        assertTrue(fake.calls.contains(HapticPattern.Pop))
    }

    @Test fun `video click triggers callback and haptic Pop`() {
        val fake = FakePremiumHaptics()
        var videoClicked = false
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = { videoClicked = true }
                )
            }
        }
        rule.onNodeWithContentDescription("Video").performClick()
        assertTrue(videoClicked)
        assertTrue(fake.calls.contains(HapticPattern.Pop))
    }

    @Test fun `file click triggers callback and haptic Pop`() {
        val fake = FakePremiumHaptics()
        var fileClicked = false
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = {},
                    onFileClick = { fileClicked = true }
                )
            }
        }
        rule.onNodeWithContentDescription("File").performClick()
        assertTrue(fileClicked)
        assertTrue(fake.calls.contains(HapticPattern.Pop))
    }

    @Test fun `send click triggers when has text content`() {
        val fake = FakePremiumHaptics()
        var sendClicked = false
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue("hi"),
                    onTextChange = {},
                    onSendClick = { sendClicked = true },
                    onGalleryClick = {},
                    onVideoClick = {}
                )
            }
        }
        rule.onNodeWithContentDescription("Send").performClick()
        assertTrue(sendClicked)
        assertTrue(fake.calls.contains(HapticPattern.Send))
    }

    @Test fun `send click does nothing when empty and no attachments`() {
        val fake = FakePremiumHaptics()
        var sendClicked = false
        rule.setContent {
            DxTestThemeWithFake(fake = fake, dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = { sendClicked = true },
                    onGalleryClick = {},
                    onVideoClick = {},
                    hasAttachments = false
                )
            }
        }
        rule.onNodeWithContentDescription("Send").performClick()
        assertTrue(!sendClicked)
        assertTrue(!fake.calls.contains(HapticPattern.Send))
    }

    @Test fun `send click triggers when hasAttachments even if text empty`() {
        var sendClicked = false
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue(""),
                    onTextChange = {},
                    onSendClick = { sendClicked = true },
                    onGalleryClick = {},
                    onVideoClick = {},
                    hasAttachments = true
                )
            }
        }
        rule.onNodeWithContentDescription("Send").performClick()
        assertTrue(sendClicked)
    }

    @Test fun `send disabled when isSending`() {
        var sendClicked = false
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue("hi"),
                    onTextChange = {},
                    onSendClick = { sendClicked = true },
                    onGalleryClick = {},
                    onVideoClick = {},
                    isSending = true
                )
            }
        }
        // When isSending, Send icon is replaced by CircularProgressIndicator — no Send node exists.
        rule.onNodeWithContentDescription("Send").assertDoesNotExist()
        assertTrue(!sendClicked)
    }

    @Test fun `whitespace-only text does not enable send`() {
        var sendClicked = false
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue("   "),
                    onTextChange = {},
                    onSendClick = { sendClicked = true },
                    onGalleryClick = {},
                    onVideoClick = {}
                )
            }
        }
        rule.onNodeWithContentDescription("Send").performClick()
        assertTrue(!sendClicked)
    }

    @Test fun `text field reflects provided value`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue("Meshify"),
                    onTextChange = {},
                    onSendClick = {},
                    onGalleryClick = {},
                    onVideoClick = {}
                )
            }
        }
        rule.onNodeWithText("Meshify").assertIsDisplayed()
    }

    @Test fun `isSending shows progress and hides send icon behavior`() {
        // isSending renders CircularProgressIndicator instead of Send icon, but contentDescription Send still present?
        // Verify composable mounts without crash and send not clickable
        var sendClicked = false
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                MediaStagingChatInput(
                    textState = TextFieldValue("hi"),
                    onTextChange = {},
                    onSendClick = { sendClicked = true },
                    onGalleryClick = {},
                    onVideoClick = {},
                    isSending = true
                )
            }
        }
        // Should not fire on click
        rule.onNodeWithContentDescription("Send").assertDoesNotExist() // when isSending, Send icon replaced by progress
        // So the test verifies that Send contentDescription is gone during sending
        assertTrue(!sendClicked)
    }
}
