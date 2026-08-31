package com.p2p.meshify.core.data.repository

import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.repository.IFileManager
import io.mockk.coEvery
import io.mockk.coVerify
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
 * MessageAttachmentRepositoryTest — verifies save/query of album attachments
 * over MOCKED dependencies (DAO + [IFileManager]), matching the existing
 * [MessageRepositoryTest] convention.
 *
 * Why mocked (not real Room): real Room needs Android's `SQLiteDatabase` native
 * runtime, only available via Robolectric. The project compiles test bytecode to
 * class-file v65 (via compileOptions VERSION_21 in this module's build.gradle.kts).
 * Robolectric is available here but adds no value for a pure-DAO-wiring test, so
 * these tests use mocked DAOs to assert the repository's wiring: the DAO insert
 * carries the right entities, empty input fails, and queries pass through to the DAO.
 *
 * Limitation: we don't assert the persisted row set; that needs a real DB in an
 * instrumented / on-device test.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageAttachmentRepositoryTest {

    private lateinit var messageDao: MessageDao
    private lateinit var fileManager: IFileManager
    private lateinit var repo: MessageAttachmentRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        messageDao = mockk(relaxed = true)
        fileManager = mockk(relaxed = true)
        repo = MessageAttachmentRepository(
            messageDao = messageDao,
            fileManager = fileManager,
            ioDispatcher = Dispatchers.Unconfined
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun saveAttachments_emptyList_returnsFailure() = runTest {
        val result = repo.saveAttachments("albumX", emptyList())
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageDao.insertMessageAttachments(any()) }
    }

    @Test
    fun saveAttachments_savesEachByteAndInsertsAllEntities() = runTest {
        coEvery { fileManager.saveMedia(any(), any()) } returns "/tmp/sent_album_album1_0.jpg"

        val result = repo.saveAttachments(
            messageId = "album1",
            attachments = listOf(
                ByteArray(4) to MessageType.IMAGE,
                ByteArray(8) to MessageType.VIDEO
            )
        )

        assertTrue(result.isSuccess)
        val saved = result.getOrThrow()
        assertEquals(2, saved.size)
        assertEquals(listOf("album1", "album1"), saved.map { it.messageId })

        // One file-save + one batched insert for the two attachments.
        coVerify(exactly = 2) { fileManager.saveMedia(any(), any()) }
        val inserted = slot<List<MessageAttachmentEntity>>()
        coVerify(exactly = 1) { messageDao.insertMessageAttachments(capture(inserted)) }
        assertEquals(2, inserted.captured.size)
        assertEquals(setOf(MessageType.IMAGE, MessageType.VIDEO), inserted.captured.map { it.type }.toSet())
    }

    @Test
    fun saveAttachments_fileSaveFailure_returnsFailure() = runTest {
        // First attachment saves, second fails -> whole op must fail (no partial insert).
        coEvery { fileManager.saveMedia("sent_album_album1_0.jpg", any()) } returns "/tmp/ok.jpg"
        coEvery { fileManager.saveMedia("sent_album_album1_1.mp4", any()) } returns null

        val result = repo.saveAttachments(
            messageId = "album1",
            attachments = listOf(ByteArray(4) to MessageType.IMAGE, ByteArray(8) to MessageType.VIDEO)
        )
        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageDao.insertMessageAttachments(any()) }
    }

    @Test
    fun getAttachmentsForMessage_delegatesToDao() = runTest {
        val expected = listOf(
            MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/p1")
        )
        coEvery { messageDao.getAttachmentsForMessage("m1") } returns expected

        val result = repo.getAttachmentsForMessage("m1")
        assertEquals(expected, result)
        coVerify(exactly = 1) { messageDao.getAttachmentsForMessage("m1") }
    }

    @Test
    fun getAllAttachments_delegatesToDao() = runTest {
        val all = listOf(
            MessageAttachmentEntity(id = "a1", type = MessageType.IMAGE, messageId = "m1", filePath = "/p1"),
            MessageAttachmentEntity(id = "a2", type = MessageType.FILE, messageId = "m2", filePath = "/p2")
        )
        coEvery { messageDao.getAllAttachments() } returns all

        val result = repo.getAllAttachments()
        assertEquals(all, result)
        coVerify(exactly = 1) { messageDao.getAllAttachments() }
    }
}
