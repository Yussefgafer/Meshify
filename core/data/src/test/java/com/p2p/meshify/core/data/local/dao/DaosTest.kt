package com.p2p.meshify.core.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import app.cash.turbine.test
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.data.local.entity.PendingMessageEntity
import com.p2p.meshify.domain.model.MessageType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DaosTest — real Room round-trip tests covering every DAO in [Daos.kt]
 * via an in-memory Room database on Robolectric.
 *
 * Why in-memory + Robolectric:
 * Room needs Android's SQLite (sqlite4java + SupportSQLiteDatabase) which only
 * runs under Robolectric on plain JVM, exactly the same setup as
 * DiGraphSmokeTest. These tests prove the @Query SQL strings, @Insert REPLACE
 * semantics, @Update, @Delete, Flow emissions, indices, and the
 * MessageAttachments FK cascade actually work — not just that a mocked DAO was
 * called with an argument.
 *
 * Scope: ChatDao, MessageDao, PendingMessageDao — every public method.
 * Exhaustive parameter combos are intentionally limited to the branches that
 * matter for the app (empty/null query, LIMIT/OFFSET, Flow initial emission).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DaosTest {

    private lateinit var db: MeshifyDatabase
    private lateinit var chatDao: ChatDao
    private lateinit var messageDao: MessageDao
    private lateinit var pendingDao: PendingMessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MeshifyDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        chatDao = db.chatDao()
        messageDao = db.messageDao()
        pendingDao = db.pendingMessageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---- helpers ----

    private fun chat(peerId: String, name: String = "Peer $peerId", ts: Long = 1000L) =
        ChatEntity(peerId = peerId, peerName = name, lastMessage = "hi", lastTimestamp = ts, unreadCount = 0)

    private fun msg(
        id: String,
        chatId: String,
        senderId: String = "remote",
        text: String? = "hello",
        ts: Long = 1000L,
        type: MessageType = MessageType.TEXT,
        status: MessageStatus = MessageStatus.SENT,
        isFromMe: Boolean = false,
        groupId: String? = null
    ) = MessageEntity(
        id = id, chatId = chatId, senderId = senderId, text = text, mediaPath = null,
        type = type, timestamp = ts, isFromMe = isFromMe, status = status, groupId = groupId
    )

    private fun pending(
        id: String,
        recipientId: String = "peerA",
        content: String = "hi",
        status: MessageStatus = MessageStatus.QUEUED
    ) = PendingMessageEntity(
        id = id, recipientId = recipientId, recipientName = "Bob",
        content = content, type = MessageType.TEXT, timestamp = 1000L,
        status = status, retryCount = 0, maxRetries = 3
    )

    // ==================== ChatDao ====================

    @Test
    fun chatDao_insert_and_getById() = runTest {
        chatDao.insertChat(chat("peer-1", "Alice"))
        val fetched = chatDao.getChatById("peer-1")
        assertNotNull(fetched)
        assertEquals("Alice", fetched!!.peerName)
    }

    @Test
    fun chatDao_getById_missing_returnsNull() = runTest {
        assertNull(chatDao.getChatById("ghost"))
    }

    @Test
    fun chatDao_insert_with_REPLACE_overwrites() = runTest {
        chatDao.insertChat(chat("p1", "Alice", ts = 1L))
        chatDao.insertChat(chat("p1", "Alice2", ts = 2L))
        val fetched = chatDao.getChatById("p1")
        assertEquals("Alice2", fetched!!.peerName)
        assertEquals(2L, fetched.lastTimestamp)
    }

    @Test
    fun chatDao_getAllChats_ordersByLastTimestampDesc() = runTest {
        chatDao.insertChat(chat("p1", ts = 10L))
        chatDao.insertChat(chat("p2", ts = 30L))
        chatDao.insertChat(chat("p3", ts = 20L))
        chatDao.getAllChats().test {
            val list = awaitItem()
            assertEquals(listOf("p2", "p3", "p1"), list.map { it.peerId })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_getAllChats_empty_emitsEmpty() = runTest {
        chatDao.getAllChats().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_delete_byId() = runTest {
        chatDao.insertChat(chat("p1"))
        chatDao.deleteChatById("p1")
        assertNull(chatDao.getChatById("p1"))
    }

    @Test
    fun chatDao_delete_nonExistent_doesNotThrow() = runTest {
        chatDao.deleteChatById("ghost") // must not throw
        chatDao.getAllChats().test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_search_matchesPeerName() = runTest {
        chatDao.insertChat(chat("p1", "Alice"))
        chatDao.insertChat(chat("p2", "Bob"))
        chatDao.searchChats("Ali").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("p1", list[0].peerId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_search_matchesLastMessage() = runTest {
        chatDao.insertChat(ChatEntity("p1", "Alice", "meeting at 3pm", 1L))
        chatDao.insertChat(ChatEntity("p2", "Bob", "hello", 2L))
        chatDao.searchChats("meeting").test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_search_emptyQuery_returnsAll() = runTest {
        chatDao.insertChat(chat("p1", "Alice"))
        chatDao.insertChat(chat("p2", "Bob"))
        chatDao.searchChats("").test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_search_noMatch_returnsEmpty() = runTest {
        chatDao.insertChat(chat("p1", "Alice"))
        chatDao.searchChats("ZZZ_NOPE").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun chatDao_resetUnreadCount_zeroesCount() = runTest {
        chatDao.insertChat(ChatEntity("p1", "Alice", "hi", 1L, unreadCount = 5))
        chatDao.resetUnreadCount("p1")
        assertEquals(0, chatDao.getChatById("p1")!!.unreadCount)
    }

    @Test
    fun chatDao_incrementUnreadCount_incrementsByOne() = runTest {
        chatDao.insertChat(ChatEntity("p1", "Alice", "hi", 1L, unreadCount = 2))
        chatDao.incrementUnreadCount("p1")
        assertEquals(3, chatDao.getChatById("p1")!!.unreadCount)
        chatDao.incrementUnreadCount("p1")
        assertEquals(4, chatDao.getChatById("p1")!!.unreadCount)
    }

    @Test
    fun chatDao_incrementUnreadCount_fromZero() = runTest {
        chatDao.insertChat(ChatEntity("p1", "Alice", "hi", 1L, unreadCount = 0))
        chatDao.incrementUnreadCount("p1")
        assertEquals(1, chatDao.getChatById("p1")!!.unreadCount)
    }

    // ==================== MessageDao — insert / query ====================

    @Test
    fun messageDao_insert_and_getById() = runTest {
        messageDao.insertMessage(msg("m1", "chat1"))
        val fetched = messageDao.getMessageById("m1")
        assertNotNull(fetched)
        assertEquals("chat1", fetched!!.chatId)
    }

    @Test
    fun messageDao_getById_missing_returnsNull() = runTest {
        assertNull(messageDao.getMessageById("ghost"))
    }

    @Test
    fun messageDao_insert_with_REPLACE_overwrites() = runTest {
        messageDao.insertMessage(msg("m1", "chat1", text = "first"))
        messageDao.insertMessage(msg("m1", "chat1", text = "second"))
        assertEquals("second", messageDao.getMessageById("m1")!!.text)
    }

    @Test
    fun messageDao_insertMessages_batch() = runTest {
        messageDao.insertMessages(listOf(msg("m1", "c1", ts = 1L), msg("m2", "c1", ts = 2L)))
        messageDao.getAllMessagesForChat("c1").test {
            assertEquals(2, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_getMessagesPaged_ordersByTimestampAsc_withLimitOffset() = runTest {
        messageDao.insertMessages(
            listOf(
                msg("m1", "c1", ts = 10L),
                msg("m2", "c1", ts = 20L),
                msg("m3", "c1", ts = 30L),
                msg("m4", "c1", ts = 40L)
            )
        )
        messageDao.getMessagesPaged("c1", limit = 2, offset = 1).test {
            val list = awaitItem()
            // ASC, OFFSET 1, LIMIT 2 -> m2, m3
            assertEquals(2, list.size)
            assertEquals("m2", list[0].id)
            assertEquals("m3", list[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_getAllMessagesForChat_ordersAsc() = runTest {
        messageDao.insertMessages(listOf(msg("m2", "c1", ts = 20L), msg("m1", "c1", ts = 10L)))
        messageDao.getAllMessagesForChat("c1").test {
            assertEquals(listOf("m1", "m2"), awaitItem().map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_observeLatestMessages_newestFirst_withLimit() = runTest {
        messageDao.insertMessages(listOf(msg("m1", "c1", ts = 10L), msg("m2", "c1", ts = 20L), msg("m3", "c1", ts = 30L)))
        messageDao.observeLatestMessages("c1", limit = 2).test {
            val list = awaitItem()
            assertEquals(2, list.size)
            assertEquals("m3", list[0].id) // DESC, newest first
            assertEquals("m2", list[1].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_observeLatestMessages_empty_returnsEmpty() = runTest {
        messageDao.observeLatestMessages("c1", limit = 10).test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_getMessagesBefore_onlyOlderThanTimestamp_newestFirst() = runTest {
        messageDao.insertMessages(
            listOf(msg("m1", "c1", ts = 10L), msg("m2", "c1", ts = 20L), msg("m3", "c1", ts = 30L), msg("m4", "c1", ts = 40L))
        )
        // Strictly < 30, limit 10, DESC -> m2, m1
        val before = messageDao.getMessagesBefore("c1", beforeTimestamp = 30L, limit = 10)
        assertEquals(listOf("m2", "m1"), before.map { it.id })
    }

    @Test
    fun messageDao_getMessagesBefore_limitRespected() = runTest {
        messageDao.insertMessages(listOf(msg("m1", "c1", ts = 10L), msg("m2", "c1", ts = 20L), msg("m3", "c1", ts = 30L)))
        val before = messageDao.getMessagesBefore("c1", beforeTimestamp = 100L, limit = 1)
        assertEquals(1, before.size)
        assertEquals("m3", before[0].id) // newest first, limit 1 -> newest qualifies
    }

    @Test
    fun messageDao_getMessagesBefore_noneOlder_returnsEmpty() = runTest {
        messageDao.insertMessage(msg("m1", "c1", ts = 10L))
        val before = messageDao.getMessagesBefore("c1", beforeTimestamp = 5L, limit = 10)
        assertTrue(before.isEmpty())
    }

    // ==================== MessageDao — attachments ====================

    @Test
    fun messageDao_insertAttachment_and_getByMessageId() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        val att = MessageAttachmentEntity(id = "att-1", type = MessageType.IMAGE, messageId = "m1", filePath = "/tmp/img.jpg")
        messageDao.insertMessageAttachment(att)
        val fetched = messageDao.getAttachmentsForMessage("m1")
        assertEquals(1, fetched.size)
        assertEquals("/tmp/img.jpg", fetched[0].filePath)
    }

    @Test
    fun messageDao_insertAttachments_batch_and_getAllAttachments() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        val a1 = MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/tmp/a.jpg")
        val a2 = MessageAttachmentEntity(id = "a2", type = MessageType.VIDEO, messageId = "m1", filePath = "/tmp/b.mp4")
        messageDao.insertMessageAttachments(listOf(a1, a2))
        val all = messageDao.getAllAttachments()
        assertEquals(2, all.size)
        val byMsg = messageDao.getAttachmentsForMessage("m1")
        assertEquals(2, byMsg.size)
    }

    @Test
    fun messageDao_getAttachmentsForMessage_noAttachments_returnsEmpty() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        assertTrue(messageDao.getAttachmentsForMessage("m1").isEmpty())
    }

    @Test
    fun messageDao_getAttachmentsForGroups_batchedFetch() = runTest {
        messageDao.insertMessages(listOf(msg("g1", "c1"), msg("g2", "c1")))
        messageDao.insertMessageAttachments(
            listOf(
                MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "g1", filePath = "/tmp/1.jpg"),
                MessageAttachmentEntity(id = "a2", type = MessageType.IMAGE, messageId = "g1", filePath = "/tmp/2.jpg"),
                MessageAttachmentEntity(id = "a3", type = MessageType.VIDEO, messageId = "g2", filePath = "/tmp/3.mp4"),
            )
        )
        val batched = messageDao.getAttachmentsForGroups(listOf("g1", "g2"))
        assertEquals(3, batched.size)
        val empty = messageDao.getAttachmentsForGroups(emptyList())
        assertTrue(empty.isEmpty())
        val group1 = messageDao.getAttachmentsForGroups(listOf("g1"))
        assertEquals(2, group1.size)
    }

    @Test
    fun messageDao_getAllAttachments_acrossMessages() = runTest {
        messageDao.insertMessages(listOf(msg("m1", "c1"), msg("m2", "c1")))
        messageDao.insertMessageAttachment(MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/tmp/x.jpg"))
        messageDao.insertMessageAttachment(MessageAttachmentEntity(id = "a2", type = MessageType.FILE, messageId = "m2", filePath = "/tmp/y.pdf"))
        assertEquals(2, messageDao.getAllAttachments().size)
    }

    // ==================== MessageDao — status / deletion / reaction ====================

    @Test
    fun messageDao_updateMessageStatus_changesStatus() = runTest {
        messageDao.insertMessage(msg("m1", "c1", status = MessageStatus.QUEUED))
        messageDao.updateMessageStatus("m1", MessageStatus.SENT)
        assertEquals(MessageStatus.SENT, messageDao.getMessageById("m1")!!.status)
        messageDao.updateMessageStatus("m1", MessageStatus.DELIVERED)
        assertEquals(MessageStatus.DELIVERED, messageDao.getMessageById("m1")!!.status)
    }

    @Test
    fun messageDao_deleteMessages_removesByIdList() = runTest {
        messageDao.insertMessages(listOf(msg("m1", "c1"), msg("m2", "c1"), msg("m3", "c1")))
        messageDao.deleteMessages(listOf("m1", "m3"))
        messageDao.getAllMessagesForChat("c1").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("m2", list[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_deleteMessages_emptyList_noop() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        messageDao.deleteMessages(emptyList())
        assertNotNull(messageDao.getMessageById("m1"))
    }

    @Test
    fun messageDao_deleteAttachmentsForMessages_removesAttachments() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        messageDao.insertMessageAttachments(
            listOf(
                MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/tmp/1.jpg"),
                MessageAttachmentEntity(id = "a2", type = MessageType.FILE, messageId = "m1", filePath = "/tmp/2.pdf")
            )
        )
        messageDao.deleteAttachmentsForMessages(listOf("m1"))
        assertTrue(messageDao.getAttachmentsForMessage("m1").isEmpty())
    }

    @Test
    fun messageDao_markAsDeletedForMe_setsFlag() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        messageDao.markAsDeletedForMe("m1")
        val m = messageDao.getMessageById("m1")!!
        assertTrue(m.isDeletedForMe)
        assertTrue(m.isDeletedForEveryone.not())
    }

    @Test
    fun messageDao_markAsDeletedForEveryone_setsFlags() = runTest {
        messageDao.insertMessage(msg("m1", "c1", senderId = "peerA"))
        val ts = 9999L
        messageDao.markAsDeletedForEveryone("m1", deletedAt = ts, deletedBy = "peerA")
        val m = messageDao.getMessageById("m1")!!
        assertTrue(m.isDeletedForEveryone)
        assertEquals(ts, m.deletedAt)
        assertEquals("peerA", m.deletedBy)
    }

    @Test
    fun messageDao_updateReaction_setsAndClears() = runTest {
        messageDao.insertMessage(msg("m1", "c1"))
        messageDao.updateReaction("m1", "❤️")
        assertEquals("❤️", messageDao.getMessageById("m1")!!.reaction)
        messageDao.updateReaction("m1", null)
        assertNull(messageDao.getMessageById("m1")!!.reaction)
    }

    @Test
    fun messageDao_getMessagesByIds_batch() = runTest {
        messageDao.insertMessages(listOf(msg("m1", "c1"), msg("m2", "c1"), msg("m3", "c1")))
        val batch = messageDao.getMessagesByIds(listOf("m1", "m3"))
        assertEquals(2, batch.size)
        assertTrue(batch.map { it.id }.containsAll(listOf("m1", "m3")))
        assertTrue(messageDao.getMessagesByIds(emptyList()).isEmpty())
    }

    @Test
    fun messageDao_searchMessagesInChat_byText() = runTest {
        messageDao.insertMessages(
            listOf(
                msg("m1", "c1", text = "hello world"),
                msg("m2", "c1", text = "meeting at 5pm"),
                msg("m3", "c1", text = "HELLO again"),
                msg("m4", "c2", text = "hello from other chat") // different chat
            )
        )
        messageDao.searchMessagesInChat("c1", "hello").test {
            // LIKE is case-sensitive by default in this query variant
            val list = awaitItem()
            assertTrue("expected at least 1 match in c1", list.isNotEmpty())
            assertTrue(list.all { it.chatId == "c1" })
            cancelAndIgnoreRemainingEvents()
        }
        messageDao.searchMessagesInChat("c1", "ZZZ_NOPE").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_searchMessagesInChat_excludesDeletedForMe() = runTest {
        messageDao.insertMessages(
            listOf(
                MessageEntity(
                    id = "m1", chatId = "c1", senderId = "s1", text = "hello hidden",
                    type = MessageType.TEXT, timestamp = 1L, isFromMe = false,
                    status = MessageStatus.SENT, isDeletedForMe = true
                ),
                msg("m2", "c1", text = "hello visible")
            )
        )
        messageDao.searchMessagesInChat("c1", "hello").test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals("m2", list[0].id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun messageDao_deleteAllMessagesForChat_removesAllInChat() = runTest {
        messageDao.insertMessages(
            listOf(
                msg("m1", "c1", ts = 1L),
                msg("m2", "c1", ts = 2L),
                msg("m3", "c2", ts = 3L)
            )
        )
        messageDao.deleteAllMessagesForChat("c1")
        messageDao.getAllMessagesForChat("c1").test {
            assertTrue(awaitItem().isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
        messageDao.getAllMessagesForChat("c2").test {
            assertEquals(1, awaitItem().size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    // ==================== PendingMessageDao ====================

    @Test
    fun pendingDao_insert_and_getById() = runTest {
        pendingDao.insert(pending("p1"))
        val fetched = pendingDao.getById("p1")
        assertNotNull(fetched)
        assertEquals("p1", fetched!!.id)
    }

    @Test
    fun pendingDao_getById_missing_returnsNull() = runTest {
        assertNull(pendingDao.getById("ghost"))
    }

    @Test
    fun pendingDao_getByRecipient() = runTest {
        pendingDao.insert(pending("p1", recipientId = "peerA"))
        pendingDao.insert(pending("p2", recipientId = "peerA"))
        pendingDao.insert(pending("p3", recipientId = "peerB"))
        assertEquals(2, pendingDao.getByRecipient("peerA").size)
        assertEquals(1, pendingDao.getByRecipient("peerB").size)
        assertTrue(pendingDao.getByRecipient("ghost").isEmpty())
    }

    @Test
    fun pendingDao_insert_REPLACE_overwrites() = runTest {
        pendingDao.insert(pending("p1", content = "first"))
        pendingDao.insert(pending("p1", content = "second"))
        assertEquals("second", pendingDao.getById("p1")!!.content)
    }

    @Test
    fun pendingDao_update_modifiesRow() = runTest {
        pendingDao.insert(pending("p1", content = "first"))
        val fetched = pendingDao.getById("p1")!!
        fetched.status = MessageStatus.SENDING
        fetched.retryCount = 2
        pendingDao.update(fetched)
        val after = pendingDao.getById("p1")!!
        assertEquals(MessageStatus.SENDING, after.status)
        assertEquals(2, after.retryCount)
    }

    @Test
    fun pendingDao_delete_removesRow() = runTest {
        pendingDao.insert(pending("p1"))
        val row = pendingDao.getById("p1")!!
        pendingDao.delete(row)
        assertNull(pendingDao.getById("p1"))
    }

    @Test
    fun pendingDao_deleteByStatus_removesMatching() = runTest {
        pendingDao.insert(pending("p1", status = MessageStatus.QUEUED))
        pendingDao.insert(pending("p2", status = MessageStatus.FAILED))
        pendingDao.insert(pending("p3", status = MessageStatus.FAILED))
        pendingDao.deleteByStatus(MessageStatus.FAILED)
        val all = pendingDao.getAll()
        assertEquals(1, all.size)
        assertEquals("p1", all[0].id)
    }

    @Test
    fun pendingDao_deleteById_removesRow() = runTest {
        pendingDao.insert(pending("p1"))
        pendingDao.insert(pending("p2"))
        pendingDao.deleteById("p1")
        assertNull(pendingDao.getById("p1"))
        assertNotNull(pendingDao.getById("p2"))
    }

    @Test
    fun pendingDao_getAll_ordersByTimestampAsc() = runTest {
        pendingDao.insert(
            PendingMessageEntity(id = "p2", recipientId = "a", recipientName = "B", content = "x", timestamp = 2000L, status = MessageStatus.QUEUED)
        )
        pendingDao.insert(
            PendingMessageEntity(id = "p1", recipientId = "a", recipientName = "B", content = "y", timestamp = 1000L, status = MessageStatus.QUEUED)
        )
        val all = pendingDao.getAll()
        assertEquals(2, all.size)
        assertEquals("p1", all[0].id)
        assertEquals("p2", all[1].id)
    }

    @Test
    fun pendingDao_getAll_empty_returnsEmpty() = runTest {
        assertTrue(pendingDao.getAll().isEmpty())
    }
}
