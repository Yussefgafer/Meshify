package com.p2p.meshify.core.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.p2p.meshify.core.ui.components.ForwardDialogState
import com.p2p.meshify.core.ui.components.ForwardMessageDialog
import com.p2p.meshify.core.ui.model.ChatUiModel
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.PeerDevice
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ForwardMessageDialogTest {
    @get:Rule val rule = createComposeRule()

    private val emptyState = ForwardDialogState()

    @Test fun `empty state shows no peers`() {
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                ForwardMessageDialog(
                    state=emptyState,
                    onDismiss={},
                    onToggleSelection={},
                    onSearchQueryChange={},
                    onForwardClick={}
                )
            }
        }
        rule.onNodeWithText("No conversations found").assertIsDisplayed()
    }

    @Test fun `recent chats section header appears`() {
        val state = emptyState.copy(
            recentChats=listOf(ChatUiModel(peerId="p1", peerName="Alice", lastMessage="Hi")),
            selectedPeerIds=setOf("p1")
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                ForwardMessageDialog(
                    state=state,
                    onDismiss={},
                    onToggleSelection={},
                    onSearchQueryChange={},
                    onForwardClick={}
                )
            }
        }
        rule.onNodeWithText("Alice").assertIsDisplayed()
    }

    @Test fun `forwarding state shows progress text`() {
        val state = emptyState.copy(
            selectedPeerIds=setOf("p1", "p2"),
            isForwarding=true,
            forwardProgress=1
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                ForwardMessageDialog(
                    state=state,
                    onDismiss={},
                    onToggleSelection={},
                    onSearchQueryChange={},
                    onForwardClick={}
                )
            }
        }
        rule.onNodeWithText("1/2").assertIsDisplayed()
    }

    @Test fun `discovered device renders with name`() {
        val state = emptyState.copy(
            discoveredDevices=listOf(PeerDevice(id="d1", name="Device", address="127.0.0.1"))
        )
        rule.setContent {
            DxTestTheme(dynamicColor=false) {
                ForwardMessageDialog(
                    state=state,
                    onDismiss={},
                    onToggleSelection={},
                    onSearchQueryChange={},
                    onForwardClick={}
                )
            }
        }
        rule.onNodeWithText("Device").assertIsDisplayed()
    }
}
