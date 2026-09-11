package com.p2p.meshify.feature.chat

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.SavedStateHandle
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ChatScreenTest {
    @get:Rule val dispatcherRule = MainDispatcherRule(StandardTestDispatcher())
    @get:Rule val compose = createComposeRule()

    private fun createVm(peerId: String="p1"): ChatViewModel {
        // ChatViewModel casts the repository as ChatRepositoryImpl, so the mock must be the concrete impl
        val impl = mockk<com.p2p.meshify.core.data.repository.ChatRepositoryImpl>(relaxed=true)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val handle = SavedStateHandle(mapOf("peerId" to peerId, "peerName" to "Ahmed"))
        every { impl.onlinePeers } returns MutableStateFlow(setOf(peerId))
        every { impl.securityEvents } returns kotlinx.coroutines.flow.MutableSharedFlow()
        every { impl.typingPeers } returns MutableStateFlow(emptySet())
        every { impl.observeLatestMessages(any(), any()) } returns MutableStateFlow(emptyList())
        io.mockk.coEvery { impl.getMessagesByIds(any()) } returns emptyList()
        io.mockk.coEvery { impl.getAttachmentsForGroups(any()) } returns emptyList()
        io.mockk.coEvery { impl.getMessagesBefore(any(), any(), any()) } returns emptyList()
        io.mockk.coEvery { impl.markChatAsRead(any()) } returns Unit
        return ChatViewModel(context, handle, impl)
    }

    @Test fun `loading shows spinner when messages empty and isLoading`() {
        val vm = createVm()
        injectChatState(vm, "_uiState", ChatUiState(isLoading=true, messages=emptyList()))
        compose.setContent { DxTestThemeChat { ChatScreen(viewModel=vm, peerId="p1", peerName="Ahmed", onBackClick={}) } }
        compose.onNode(hasContentDescription("Loading messages")).assertIsDisplayed()
    }

    @Test fun `empty state shows placeholder`() {
        val vm = createVm()
        injectChatState(vm, "_uiState", ChatUiState(isLoading=false, messages=emptyList()))
        compose.setContent { DxTestThemeChat { ChatScreen(viewModel=vm, peerId="p1", peerName="Ahmed", onBackClick={}) } }
        compose.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test fun `selection mode top bar appears when selectedMessages not empty`() {
        val vm = createVm()
        injectChatState(vm, "_selectedMessages", setOf("m1"))
        injectChatState(vm, "_uiState", ChatUiState(isLoading=false, messages=listOf(MessageEntity(id="m1", chatId="p1", senderId="me", text="Hi", timestamp=0, isFromMe=true))))
        compose.setContent { DxTestThemeChat { ChatScreen(viewModel=vm, peerId="p1", peerName="Ahmed", onBackClick={}) } }
        compose.onNodeWithText("1 selected").assertIsDisplayed()
    }

    @Test fun `search mode shows search field`() {
        val vm = createVm()
        injectChatState(vm, "_isSearching", true)
        compose.setContent { DxTestThemeChat { ChatScreen(viewModel=vm, peerId="p1", peerName="Ahmed", onBackClick={}) } }
        compose.onNodeWithText("Search messages…").assertIsDisplayed()
    }

    @Test fun `RTL no crash`() {
        val vm = createVm()
        injectChatState(vm, "_uiState", ChatUiState(isLoading=false, messages=emptyList()))
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                DxTestThemeChat { ChatScreen(viewModel=vm, peerId="p1", peerName="Ahmed", onBackClick={}) }
            }
        }
        compose.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test fun `input bar renders peer name when empty and not sending`() {
        val vm = createVm()
        injectChatState(vm, "_uiState", ChatUiState(isLoading=false, messages=emptyList(), isSending=false))
        compose.setContent { DxTestThemeChat { ChatScreen(viewModel=vm, peerId="p1", peerName="Ahmed", onBackClick={}) } }
        compose.onNodeWithText("Ahmed").assertIsDisplayed()
    }
}
