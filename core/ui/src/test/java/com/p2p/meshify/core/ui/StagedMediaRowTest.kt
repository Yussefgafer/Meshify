package com.p2p.meshify.core.ui

import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.p2p.meshify.core.ui.components.StagedMediaRow
import com.p2p.meshify.core.ui.model.StagedAttachment
import com.p2p.meshify.domain.model.MessageType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class StagedMediaRowTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `empty attachments do not render row`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                StagedMediaRow(
                    attachments=emptyList(),
                    onRemoveClick={}
                )
            }
        }
        rule.onNodeWithText("A").assertDoesNotExist()
    }

    @Test fun `image attachment renders thumbnail`() {
        val attachments = listOf(
            StagedAttachment(
                uri=Uri.parse("content://media/img"),
                bytes=byteArrayOf(1, 2, 3),
                type=MessageType.IMAGE
            )
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                StagedMediaRow(
                    attachments=attachments,
                    onRemoveClick={}
                )
            }
        }
    }

    @Test fun `video attachment renders without crash`() {
        val attachments = listOf(
            StagedAttachment(
                uri=Uri.parse("content://media/vid"),
                bytes=byteArrayOf(4, 5, 6),
                type=MessageType.VIDEO
            )
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                StagedMediaRow(
                    attachments=attachments,
                    onRemoveClick={}
                )
            }
        }
    }

    @Test fun `multiple attachments show index badge`() {
        val attachments = listOf(
            StagedAttachment(uri=Uri.parse("content://media/1"), bytes=byteArrayOf(1), type=MessageType.IMAGE),
            StagedAttachment(uri=Uri.parse("content://media/2"), bytes=byteArrayOf(2), type=MessageType.IMAGE)
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                StagedMediaRow(
                    attachments=attachments,
                    onRemoveClick={}
                )
            }
        }
        rule.onNodeWithText("2").assertIsDisplayed()
    }
}
