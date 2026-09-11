package com.p2p.meshify.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.p2p.meshify.core.ui.components.AlbumMediaGrid
import com.p2p.meshify.core.ui.model.AttachmentUiModel
import com.p2p.meshify.domain.model.MessageType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class AlbumMediaGridTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `empty attachments render caption only`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                AlbumMediaGrid(
                    attachments=emptyList(),
                    caption="Holiday",
                    onImageClick={}
                )
            }
        }
        rule.onNodeWithText("Holiday").assertIsDisplayed()
    }

    @Test fun `image attachment renders without crash`() {
        val attachments = listOf(
            AttachmentUiModel(id="1", type=MessageType.IMAGE, filePath="/data/img.jpg")
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                AlbumMediaGrid(
                    attachments=attachments,
                    caption=null,
                    onImageClick={}
                )
            }
        }
    }

    @Test fun `video attachment renders without crash`() {
        val attachments = listOf(
            AttachmentUiModel(id="v1", type=MessageType.VIDEO, filePath="/data/vid.mp4")
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                AlbumMediaGrid(
                    attachments=attachments,
                    caption=null,
                    onImageClick={}
                )
            }
        }
    }

    @Test fun `mixed attachments render without crash`() {
        val attachments = listOf(
            AttachmentUiModel(id="1", type=MessageType.IMAGE, filePath="/data/a.jpg"),
            AttachmentUiModel(id="2", type=MessageType.VIDEO, filePath="/data/b.mp4"),
            AttachmentUiModel(id="3", type=MessageType.IMAGE, filePath="/data/c.jpg")
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                AlbumMediaGrid(
                    attachments=attachments,
                    caption="Trip",
                    onImageClick={}
                )
            }
        }
        rule.onNodeWithText("Trip").assertIsDisplayed()
    }
}
