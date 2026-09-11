package com.p2p.meshify.feature.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.LayoutDirection
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.feature.chat.components.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ChatComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `ChatTopBar shows peer and online`() {
        rule.setContent { DxTestThemeChat { ChatTopBar(peerName="Ahmed", isOnline=true, onBackClick={}, onSearchClick={}) } }
        rule.onNodeWithText("Ahmed").assertIsDisplayed()
        rule.onNodeWithText("Online").assertIsDisplayed()
        rule.onNode(hasContentDescription("Search")).assertIsDisplayed()
    }

    @Test fun `ChatTopBar offline label`() {
        rule.setContent { DxTestThemeChat { ChatTopBar(peerName="Sara", isOnline=false, onBackClick={}) } }
        rule.onNodeWithText("Offline").assertIsDisplayed()
    }

    @Test fun `SelectionModeTopBar shows count`() {
        rule.setContent { DxTestThemeChat { SelectionModeTopBar(selectedCount=2, onBackClick={}, onForwardClick={}, onDeleteClick={}, onCopyClick={}) } }
        rule.onNodeWithText("2 selected").assertIsDisplayed()
    }

    @Test fun `MessageBubble text and time`() {
        val msg = MessageEntity(id="1", chatId="c", senderId="me", text="Hello world", timestamp=System.currentTimeMillis(), isFromMe=true, status=MessageStatus.SENT)
        rule.setContent {
            DxTestThemeChat {
                MessageBubble(message=msg, attachments=emptyList(), peerName="Ahmed", onLongClick={}, onImageClick={}, onReactionClick={_: String? ->})
            }
        }
        rule.onNodeWithText("Hello world").assertIsDisplayed()
    }

    @Test fun `MessageBubble deleted shows placeholder`() {
        val msg = MessageEntity(id="2", chatId="c", senderId="peer", text=null, timestamp=0, isFromMe=false, isDeletedForEveryone=true)
        rule.setContent {
            DxTestThemeChat { MessageBubble(message=msg, attachments=emptyList(), peerName="P", onLongClick={}, onImageClick={}, onReactionClick={_: String? ->}) }
        }
        rule.onNodeWithText("This message was deleted").assertIsDisplayed()
    }

    @Test fun `MessageBubble with reply shows quote`() {
        val reply = MessageEntity(id="r1", chatId="c", senderId="peer", text="Original", timestamp=0, isFromMe=false)
        val msg = MessageEntity(id="m1", chatId="c", senderId="me", text="Reply text", timestamp=0, isFromMe=true, replyToId="r1")
        rule.setContent {
            DxTestThemeChat { MessageBubble(message=msg, replyMessage=reply, attachments=emptyList(), peerName="P", onLongClick={}, onImageClick={}, onReactionClick={_: String? ->}) }
        }
        rule.onNodeWithText("Original").assertIsDisplayed()
        rule.onNodeWithText("Reply text").assertIsDisplayed()
    }

    @Test fun `ReplyIndicator hidden when null`() {
        rule.setContent { DxTestThemeChat { ReplyIndicator(replyTo=null, onDismissClick={}) } }
        rule.onNodeWithText("Replying to").assertDoesNotExist()
    }

    @Test fun `ReplyIndicator shows reply label when reply present`() {
        val reply = MessageEntity(id="r", chatId="c", senderId="peer", text="Hi", timestamp=0, isFromMe=false)
        rule.setContent { DxTestThemeChat { ReplyIndicator(replyTo=reply, onDismissClick={}) } }
        rule.onNodeWithText("Replying to").assertIsDisplayed()
    }

    @Test fun `ScrollToFAB gating visible false does not show`() {
        rule.setContent { DxTestThemeChat { ScrollToFAB(isVisible=false, onScrollToBottom={}) } }
        // FAB icon description "Scroll to bottom" hidden when not visible -> not displayed
        rule.onNode(hasContentDescription("Scroll to bottom")).assertDoesNotExist()
    }

    @Test fun `ScrollToFAB visible shows`() {
        rule.setContent { DxTestThemeChat { ScrollToFAB(isVisible=true, onScrollToBottom={}) } }
        rule.onNode(hasContentDescription("Scroll to bottom")).assertIsDisplayed()
    }

    @Test fun `DeleteConfirmationDialog single shows`() {
        rule.setContent {
            DxTestThemeChat {
                DeleteConfirmationDialog(action=DeleteAction.Single("id1", com.p2p.meshify.domain.model.DeleteType.DELETE_FOR_ME), onDismiss={}, onConfirm={})
            }
        }
        rule.onNodeWithText("Delete Message?").assertIsDisplayed()
    }

    @Test fun `DeleteConfirmationDialog multiple shows plural`() {
        rule.setContent {
            DxTestThemeChat {
                DeleteConfirmationDialog(action=DeleteAction.Multiple(setOf("1","2")), onDismiss={}, onConfirm={})
            }
        }
        rule.onNodeWithText("Delete Messages?").assertIsDisplayed()
    }

    @Test fun `BackConfirmationDialog renders`() {
        rule.setContent { DxTestThemeChat { BackConfirmationDialog(onDismiss={}, onDiscard={}) } }
        rule.onNodeWithText("Discard Message?").assertIsDisplayed()
    }

    @Test fun `MessageList empty shows placeholder`() {
        rule.setContent { DxTestThemeChat { MessageList(attachmentsByGroupId=emptyMap(), replyById=emptyMap(), messages=emptyList(), isLoading=false, selectedMessages=emptySet(), uploadProgressMap=emptyMap(), transportUsed=emptyMap(), peerName="Ahmed", onLongClick={}, onClick={}, onImageClick={}, onReaction={_: String, _: String? ->}) } }
        rule.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test fun `MessageList with three messages shows first and last`() {
        val msgs = (0..2).map { i -> MessageEntity(id="$i", chatId="c", senderId=if(i%2==0) "me" else "peer", text="Msg $i", timestamp=i.toLong(), isFromMe=i%2==0, status=MessageStatus.SENT) }
        rule.setContent { DxTestThemeChat { MessageList(attachmentsByGroupId=emptyMap(), replyById=emptyMap(), messages=msgs, isLoading=false, selectedMessages=emptySet(), uploadProgressMap=emptyMap(), transportUsed=emptyMap(), peerName="P", onLongClick={}, onClick={}, onImageClick={}, onReaction={_: String, _: String? ->}) } }
        rule.onNodeWithText("Msg 0").assertIsDisplayed()
        rule.onNodeWithText("Msg 2").assertIsDisplayed()
    }

    @Test fun `ChatInputBar renders`() {
        rule.setContent {
            DxTestThemeChat {
                ChatInputBar(textState=TextFieldValue("hello"), onTextChange={}, onSendClick={}, stagedAttachments=emptyList(), onRemoveAttachment={}, onStageAttachment={_,_->}, isSending=false, isStaging=false)
            }
        }
        rule.onNode(hasText("hello")).assertIsDisplayed()
    }

    @Test fun `RTL MessageBubble no crash`() {
        val msg = MessageEntity(id="1", chatId="c", senderId="me", text="RTL", timestamp=0, isFromMe=true)
        rule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxTestThemeChat { MessageBubble(message=msg, attachments=emptyList(), peerName="P", onLongClick={}, onImageClick={}, onReactionClick={_: String? ->}) }
            }
        }
        rule.onNodeWithText("RTL").assertIsDisplayed()
    }
}
