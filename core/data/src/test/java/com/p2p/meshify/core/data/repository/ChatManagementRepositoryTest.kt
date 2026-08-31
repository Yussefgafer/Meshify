package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.MessageType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ChatManagementRepositoryTest — verifies the destructive chat/message
 * management ops (delete/forward/mark-read/delete-message branching) over
 * MOCKED DAOs, matching the existing [MessageRepositoryTest] /
 * [ChatRepositoryImplTest] convention.
 *
 * Why mocked DAOs (not real Room): real Room needs Android's
 * `SQLiteDatabase` native runtime, only available via Robolectric. The project
 * compiles test bytecode to class-file v65 (via compileOptions VERSION_21 in
 * this module's build.gradle.kts). Robolectric is available here but adds no
 * value for a pure-DAO-wiring test, so these tests use mocked DAOs to assert
 * wiring (DAO call sequence, branching, Result outcomes, forwarded-row
 * construction) deterministically and fast, which is the load-bearing behavior.
 *
 * Limitation: we do not assert real transactional atomicity or the persisted
 * row set; that belongs in an instrumented / on-device test with a real DB.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatManagementRepositoryTest {

    private lateinit var context: Context
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var repo: ChatManagementRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        context = mockk(relaxed = true)
        chatDao = mockk(relaxed = true)
        messageDao = mockk(relaxed = true)
        // buildForwardContext() formats the forwarded text via Context.getString;
        // a relaxed mock returns "" on plain JVM, which would defeat the
        // "contains Forwarded" assertion. Echo "Forwarded" so the branch is observable.
        every { context.getString(any<Int>(), any(), any()) } returns "Forwarded from x: y"
        every { context.getString(any<Int>(), any()) } returns "Forwarded from x"
        every { context.getString(any<Int>()) } returns "Forwarded"
        repo = ChatManagementRepository(context, chatDao, messageDao)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun msg(id: String, chatId: String, senderId: String = chatId, type: MessageType = MessageType.TEXT) =
        MessageEntity(
            id = id, chatId = chatId, senderId = senderId, text = "msg $id", type = type,
            timestamp = 1L, isFromMe = false, status = MessageStatus.RECEIVED
        )

    @Test
    fun deleteChat_cascadesChatAndMessages() = runTest {
        repo.deleteChat("peerA")
        coVerify(exactly = 1) { chatDao.deleteChatById("peerA") }
        coVerify(exactly = 1) { messageDao.deleteAllMessagesForChat("peerA") }
    }

    @Test
    fun deleteChat_propagatesDaoFailure() = runTest {
        coEvery { chatDao.deleteChatById(any()) } throws RuntimeException("db down")
        var thrown: Throwable? = null
        try {
            repo.deleteChat("peerA")
        } catch (e: Throwable) {
            thrown = e
        }
        assertTrue("deleteChat rethrows DAO failure", thrown is RuntimeException)
    }

    @Test
    fun markChatAsRead_resetsUnreadCountViaDao() = runTest {
        repo.markChatAsRead("peerB")
        coVerify(exactly = 1) { chatDao.resetUnreadCount("peerB") }
    }

    @Test
    fun deleteMessage_unknownId_returnsFailure() = runTest {
        coEvery { messageDao.getMessageById(any()) } returns null
        val result = repo.deleteMessage("ghost", DeleteType.DELETE_FOR_ME)
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageDao.markAsDeletedForMe(any()) }
        coVerify(exactly = 0) { messageDao.markAsDeletedForEveryone(any(), any(), any()) }
    }

    @Test
    fun deleteMessage_deleteForMe_marksRow() = runTest {
        coEvery { messageDao.getMessageById("m9") } returns msg("m9", "peerC", senderId = "peerC")
        val result = repo.deleteMessage("m9", DeleteType.DELETE_FOR_ME)
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { messageDao.markAsDeletedForMe("m9") }
        coVerify(exactly = 0) { messageDao.markAsDeletedForEveryone(any(), any(), any()) }
    }

    @Test
    fun deleteMessage_deleteForEveryone_marksRowWithSender() = runTest {
        coEvery { messageDao.getMessageById("m10") } returns msg("m10", "peerD", senderId = "peerD")
        val result = repo.deleteMessage("m10", DeleteType.DELETE_FOR_EVERYONE)
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) {
            messageDao.markAsDeletedForEveryone("m10", any(), "peerD")
        }
    }

    @Test
    fun forwardMessage_copiesIntoTargetChatAndCreatesMessage() = runTest {
        // Original message lives in chat peerA.
        coEvery { messageDao.getMessageById("orig1") } returns msg("orig1", "peerA", senderId = "peerA")
        // Target chat does not yet exist -> repository creates it.
        coEvery { chatDao.getChatById("peerTarget") } returns null

        val result = repo.forwardMessage("orig1", listOf("peerTarget"))
        assertTrue(result.isSuccess)

        // A new message row was inserted into the TARGET chat, preserving sender.
        val inserted = slot<MessageEntity>()
        coVerify(exactly = 1) { messageDao.insertMessage(capture(inserted)) }
        assertEquals("peerTarget", inserted.captured.chatId)
        assertEquals("peerA", inserted.captured.senderId)
        assertTrue("forwarded copy is marked from me", inserted.captured.isFromMe)
        assertTrue("text carries forwarded context", inserted.captured.text?.contains("Forwarded") == true)

        // The target chat was created (since it didn't exist) and then bumped.
        coVerify(exactly = 2) { chatDao.insertChat(any<ChatEntity>()) }
    }

    @Test
    fun forwardMessage_reusesExistingTargetChat() = runTest {
        coEvery { messageDao.getMessageById("orig1") } returns msg("orig1", "peerA", senderId = "peerA")
        coEvery { chatDao.getChatById("peerTarget") } returns ChatEntity(
            peerId = "peerTarget", peerName = "Existing", lastMessage = "x", lastTimestamp = 1L
        )
        val result = repo.forwardMessage("orig1", listOf("peerTarget"))
        assertTrue(result.isSuccess)
        // Existing chat present -> only the preview bump fires (no create).
        coVerify(exactly = 1) { chatDao.insertChat(any<ChatEntity>()) }
        coVerify(exactly = 1) { messageDao.insertMessage(any()) }
    }

    @Test
    fun forwardMessage_unknownId_returnsFailure() = runTest {
        coEvery { messageDao.getMessageById(any()) } returns null
        val result = repo.forwardMessage("nope", listOf("peerTarget"))
        assertTrue(result.isFailure)
    }

    @Test
    fun getMessagesBefore_delegatesToDaoWithLimit() = runTest {
        repo.getMessagesBefore("peerA", beforeTimestamp = 100L, limit = 7)
        coVerify(exactly = 1) { messageDao.getMessagesBefore("peerA", 100L, 7) }
    }

    @Test
    fun getMessagesPaged_delegatesToDao() = runTest {
        repo.getMessagesPaged("peerA", limit = 20, offset = 40)
        coVerify(exactly = 1) { messageDao.getMessagesPaged("peerA", 20, 40) }
    }

    @Test
    fun searchChats_passthrough() = runTest {
        repo.searchChats("Zeta")
        coVerify(exactly = 1) { chatDao.searchChats("Zeta") }
    }
}
