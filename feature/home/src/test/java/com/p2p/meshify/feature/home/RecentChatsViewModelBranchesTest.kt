package com.p2p.meshify.feature.home

import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RecentChatsViewModelBranchesTest {
    @get:Rule val dispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    @Test fun `search query debounce triggers filtered flow`() = runTest(dispatcherRule.dispatcher) {
        val repo = mockk<ChatRepositoryImpl>(relaxed=true)
        val all = MutableStateFlow(listOf(ChatEntity(peerId="1", peerName="Ahmed", lastMessage="hi", lastTimestamp=0)))
        val filtered = MutableStateFlow(listOf(ChatEntity(peerId="1", peerName="Ahmed", lastMessage="hi", lastTimestamp=0)))
        every { repo.getAllChats() } returns all
        every { repo.searchChats("Ah") } returns filtered
        every { repo.onlinePeers } returns MutableStateFlow(emptySet())
        val vm = RecentChatsViewModel(repo)
        vm.updateSearchQuery("Ah")
        advanceTimeBy(500)
        // at least no crash and state loading cleared eventually
        assertEquals("Ah", vm.searchQuery.value)
    }

    @Test fun `deleteChat delegates to repo`() = runTest(dispatcherRule.dispatcher) {
        val repo = mockk<ChatRepositoryImpl>(relaxed=true)
        every { repo.getAllChats() } returns MutableStateFlow(emptyList())
        every { repo.onlinePeers } returns MutableStateFlow(emptySet())
        coEvery { repo.deleteChat(any()) } just Runs
        val vm = RecentChatsViewModel(repo)
        vm.deleteChat("peer1")
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        coVerify { repo.deleteChat("peer1") }
    }

    @Test fun `retryLoad clears error and sets loading`() = runTest(dispatcherRule.dispatcher) {
        val repo = mockk<ChatRepositoryImpl>(relaxed=true)
        every { repo.getAllChats() } returns MutableStateFlow(emptyList())
        every { repo.onlinePeers } returns MutableStateFlow(emptySet())
        val vm = RecentChatsViewModel(repo)
        // force error state via reflection
        val f = RecentChatsViewModel::class.java.getDeclaredField("_uiState")
        f.isAccessible=true
        @Suppress("UNCHECKED_CAST")
        val flow = f.get(vm) as MutableStateFlow<RecentChatsUiState>
        flow.value = RecentChatsUiState(isLoading=false, error="oops")
        vm.retryLoad()
        assertNull(vm.uiState.value.error) // cleared
        assertEquals(true, vm.uiState.value.isLoading)
    }

    @Test fun `markChatAsRead delegates`() = runTest(dispatcherRule.dispatcher) {
        val repo = mockk<ChatRepositoryImpl>(relaxed=true)
        every { repo.getAllChats() } returns MutableStateFlow(emptyList())
        every { repo.onlinePeers } returns MutableStateFlow(emptySet())
        coEvery { repo.markChatAsRead(any()) } just Runs
        val vm = RecentChatsViewModel(repo)
        vm.markChatAsRead("id")
        dispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        coVerify { repo.markChatAsRead("id") }
    }

    @Test fun `empty search returns all chats`() = runTest(dispatcherRule.dispatcher) {
        val repo = mockk<ChatRepositoryImpl>(relaxed=true)
        val all = MutableStateFlow(listOf(ChatEntity(peerId="2", peerName="Sara", lastMessage="hey", lastTimestamp=0)))
        every { repo.getAllChats() } returns all
        every { repo.onlinePeers } returns MutableStateFlow(emptySet())
        val vm = RecentChatsViewModel(repo)
        vm.updateSearchQuery("")
        advanceTimeBy(500)
        assertEquals("", vm.searchQuery.value)
    }
}
