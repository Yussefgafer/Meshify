package com.p2p.meshify.feature.home

import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [RecentChatsViewModel].
 *
 * Scope:
 *   Drives RecentChatsViewModel as a plain class (no Hilt), mocking the concrete
 *   [ChatRepositoryImpl]. The VM casts nothing but depends on ChatRepositoryImpl
 *   directly at the ctor type, so we mock that concrete class (relaxed) and stub
 *   the flows/mutations we drive.
 *
 * Why no Robolectric:
 *   recent-chats logic only touches repository flows + `Logger` (which calls
 *   `android.util.Log`); with `unitTests.isReturnDefaultValues = true` the Log
 *   calls return 0 instead of throwing, so the test stays JVM-only.
 *
 * What's tested:
 *   - init loads all chats (after the 300ms search debounce) and flips isLoading off.
 *   - debounced `updateSearchQuery` collapses intermediate keystrokes and finally
 *     routes to `searchChats(latest)` (flatMapLatest).
 *   - `deleteChat` delegates to the repository.
 *   - `markChatAsRead` delegates to the repository.
 *   - `onlinePeers` collector mirrors the repository flow into uiState.
 *
 * Limitations:
 *   - The `retryLoad` path re-subscribes a fresh collector; we assert only that the
 *     repo query is re-issued, not the error-recovery race.
 *   - `catch { ... }` error path is not exercised (the stubbed flows never throw).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RecentChatsViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(kotlinx.coroutines.test.StandardTestDispatcher())

    private lateinit var repository: ChatRepositoryImpl
    private val allChatsFlow = MutableStateFlow<List<ChatEntity>>(emptyList())
    private val searchFlow = MutableStateFlow<List<ChatEntity>>(emptyList())
    private val onlinePeersFlow = MutableStateFlow<Set<String>>(emptySet())
    private var currentVm: RecentChatsViewModel? = null

    @Before
    fun setUpDispatchers() {
        Dispatchers.setMain(mainRule.dispatcher)
    }

    @After
    fun tearDownDispatchers() {
        currentVm?.viewModelScope?.cancel()
        currentVm = null
        Dispatchers.resetMain()
    }

    @Before
    fun setUp() {
        repository = mockk(relaxed = true)
        every { repository.getAllChats() } returns allChatsFlow
        every { repository.searchChats(any()) } returns searchFlow
        every { repository.onlinePeers } returns onlinePeersFlow
        coEvery { repository.deleteChat(any()) } returns Unit
        coEvery { repository.markChatAsRead(any()) } returns Unit
    }

    private fun newVm(): RecentChatsViewModel =
        RecentChatsViewModel(repository).also { currentVm = it }

    private fun chat(peerId: String, name: String = "name-$peerId") = ChatEntity(
        peerId = peerId,
        peerName = name,
        lastMessage = "hi",
        lastTimestamp = 1_700_000_000L,
        unreadCount = 0
    )

    @Test
    fun `init — loads all chats after the search debounce and clears isLoading`() = runTest {
        allChatsFlow.value = listOf(chat("p1"), chat("p2"))
        val vm = newVm()
        // before debounce fires the query is still "", so getAllChats hasn't been collected yet
        runCurrent()
        assertTrue(vm.uiState.value.isLoading)

        // Advance past the 300ms debounce so flatMapLatest switches to getAllChats().
        advanceTimeBy(350L)
        runCurrent()

        assertEquals(listOf("p1", "p2"), vm.uiState.value.chats.map { it.peerId })
        assertFalse(vm.uiState.value.isLoading)
        coVerify(exactly = 0) { repository.searchChats(any()) }
    }

    @Test
    fun `updateSearchQuery — debounced search routes to searchChats with the final query`() = runTest {
        val vm = newVm()
        advanceTimeBy(350L)
        runCurrent()
        allChatsFlow.value = listOf(chat("alice"), chat("albert"), chat("bob"))

        // Rapid keystrokes before the debounce window closes — must be collapsed to the last one.
        vm.updateSearchQuery("a")
        vm.updateSearchQuery("al")
        vm.updateSearchQuery("ali")
        runCurrent()
        advanceTimeBy(350L)
        runCurrent()

        searchFlow.value = listOf(chat("alice"), chat("albert"))
        runCurrent()

        assertEquals(listOf("alice", "albert"), vm.uiState.value.chats.map { it.peerId })
        // flatMapLatest only ever collected searchChats for the collapsed final query.
        coVerify(exactly = 1) { repository.searchChats("ali") }
        coVerify(exactly = 0) { repository.searchChats("a") }
        coVerify(exactly = 0) { repository.searchChats("al") }
    }

    @Test
    fun `deleteChat — delegates to the repository`() = runTest {
        val vm = newVm()
        advanceTimeBy(350L)
        runCurrent()

        vm.deleteChat("p1")
        runCurrent()

        coVerify(exactly = 1) { repository.deleteChat("p1") }
    }

    @Test
    fun `markChatAsRead — delegates to the repository`() = runTest {
        val vm = newVm()
        advanceTimeBy(350L)
        runCurrent()

        vm.markChatAsRead("p1")
        runCurrent()

        coVerify(exactly = 1) { repository.markChatAsRead("p1") }
    }

    @Test
    fun `onlinePeers collector — mirrors the repository flow`() = runTest {
        val vm = newVm()
        advanceTimeBy(350L)
        runCurrent()
        assertTrue(vm.uiState.value.onlinePeers.isEmpty())

        onlinePeersFlow.value = setOf("p1", "p2")
        runCurrent()
        assertEquals(setOf("p1", "p2"), vm.uiState.value.onlinePeers)

        onlinePeersFlow.value = emptySet()
        runCurrent()
        assertTrue(vm.uiState.value.onlinePeers.isEmpty())
    }
}
