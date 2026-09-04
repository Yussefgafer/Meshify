package com.p2p.meshify.feature.settings

import androidx.lifecycle.viewModelScope
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for [DeveloperViewModel] (dev-only mock-data harness).
 *
 * Scope:
 *   Drives DeveloperViewModel as a plain class (no Hilt). Both DAOs ([ChatDao],
 *   [MessageDao]) are mocked with mockk. The VM runs its work inside
 *   `viewModelScope.launch` and signals completion through an `onComplete`
 *   callback; we capture that callback via a [CompletableDeferred] and await it
 *   under `runTest` after a `runCurrent()` flush.
 *
 * Why no Robolectric:
 *   The DAO methods are suspend / Flow getters stubbed directly; the VM performs
 *   no Android-context work, so the test stays JVM-only — matching the Phase 5
 *   pattern used for the other feature ViewModels.
 *
 * What's tested:
 *   - `insertMockConversations` inserts 7 chats + their messages and reports the
 *     success count via onComplete.
 *   - `clearMockData` deletes every known mock peer's messages + chat rows and
 *     reports completion.
 *   - `clearAllData` reads attachments + all chats, then deletes them, and still
 *     invokes onComplete even when the DAO throws (error path).
 *
 * Limitations:
 *   - The exact count of per-peer messages and the deterministic peer list are
 *     asserted by verifying the *number* of `insertChat` / `insertMessage` calls
 *     against the known mock schema rather than inspecting the generated rows.
 *   - `insertMockMediaMessages` / `insertMockChatWithReactions` /
 *     `insertMockChatWithReplies` / `insertMockLongConversation` share the same
 *     shape as `insertMockConversations` and are not driven individually; the
 *     delegation pattern is already proven by `insertMockConversations`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeveloperViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule(kotlinx.coroutines.test.StandardTestDispatcher())

    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var vm: DeveloperViewModel
    private var currentVm: DeveloperViewModel? = null

    private val allChatsFlow = MutableStateFlow<List<ChatEntity>>(emptyList())

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
        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)

        every { chatDao.getAllChats() } returns allChatsFlow
        coEvery { messageDao.getAllAttachments() } returns emptyList()
        coEvery { messageDao.deleteMessages(any()) } returns Unit
        coEvery { messageDao.deleteAllMessagesForChat(any()) } returns Unit
        coEvery { chatDao.deleteChatById(any()) } returns Unit
        coEvery { chatDao.insertChat(any()) } returns Unit
        coEvery { messageDao.insertMessage(any()) } returns Unit

        vm = DeveloperViewModel(chatDao, messageDao).also { currentVm = it }
    }

    // ===== insertMockConversations =====

    @Test
    fun `insertMockConversations — inserts 7 chats and their messages, reports count`() = runTest {
        val deferred = CompletableDeferred<String>()
        vm.insertMockConversations { msg -> deferred.complete(msg) }
        runCurrent()
        val result = deferred.await()

        // 7 peers, each chat written twice (initial + last-message update) => 14 insertChat.
        coVerify(exactly = 14) { chatDao.insertChat(any()) }
        // each peer gets 4 generateMockMessages => 7 * 4 = 28 insertMessage.
        coVerify(exactly = 28) { messageDao.insertMessage(any()) }
        assertTrue(result.contains("Added 7 conversations"))
    }

    // ===== clearMockData =====

    @Test
    fun `clearMockData — deletes messages and chats for all known mock peers, reports completion`() = runTest {
        val deferred = CompletableDeferred<String>()
        vm.clearMockData { msg -> deferred.complete(msg) }
        runCurrent()
        val result = deferred.await()

        // 11 known mock peer ids.
        coVerify(exactly = 11) { messageDao.deleteAllMessagesForChat(any()) }
        coVerify(exactly = 11) { chatDao.deleteChatById(any()) }
        assertEquals("Cleared all mock data", result)
    }

    // ===== clearAllData =====

    @Test
    fun `clearAllData — reads attachments and chats then deletes them, reports via onComplete`() = runTest {
        val deferred = CompletableDeferred<Unit>()
        vm.clearAllData { deferred.complete(Unit) }
        runCurrent()
        deferred.await()

        coVerify(exactly = 1) { messageDao.getAllAttachments() }
        coVerify(exactly = 1) { chatDao.getAllChats() }
        coVerify(exactly = 1) { messageDao.deleteMessages(any()) }
        coVerify(exactly = 1) { messageDao.deleteAllMessagesForChat("") }
    }

    @Test
    fun `clearAllData — still calls onComplete when the DAO throws`() = runTest {
        coEvery { messageDao.getAllAttachments() } throws RuntimeException("db closed")

        val deferred = CompletableDeferred<Unit>()
        vm.clearAllData { deferred.complete(Unit) }
        runCurrent()
        deferred.await()

        // The catch block swallows the exception and completes anyway.
    }
}
