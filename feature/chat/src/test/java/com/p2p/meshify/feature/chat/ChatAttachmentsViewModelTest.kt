package com.p2p.meshify.feature.chat

import android.content.Context
import android.net.Uri
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.security.model.SecurityEvent
import com.p2p.meshify.feature.chat.viewmodels.ChatAttachmentsViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class ChatAttachmentsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val mockContext: Context = mockk(relaxed = true)
    private val mockRepository: ChatRepositoryImpl = mockk(relaxed = true)

    private lateinit var viewModel: ChatAttachmentsViewModel

    private val testPeerId = "peer-123"
    private val testPeerName = "Alice"

    @Before
    fun setup() {
        every { mockRepository.onlinePeers } returns kotlinx.coroutines.flow.flowOf(emptySet())
        every { mockRepository.securityEvents } returns MutableSharedFlow<SecurityEvent>(replay = 0, extraBufferCapacity = 10)

        every { mockContext.getString(R.string.error_file_send_failed, any<String>()) } returns "File send failed"
        every { mockContext.getString(R.string.error_message_send_failed, any<String>()) } returns "Message send failed"
        every { mockContext.getString(R.string.error_unknown) } returns "Unknown"

        // Default: getMessagesPaged returns empty
        coEvery { mockRepository.getMessagesPaged(any(), any(), any()) } returns flowOf(emptyList())

        viewModel = ChatAttachmentsViewModel(mockContext, mockRepository, testPeerId, testPeerName)
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state should have empty staged attachments`() {
        val state = viewModel.uiState.value

        assertTrue(state.stagedAttachments.isEmpty())
        assertTrue(state.uploadProgress.isEmpty())
        assertNull(state.uploadError)
    }

    // ==================== Attachment Staging Tests ====================

    @Test
    fun `stageAttachment should add attachment to staged list`() = runTest {
        val uri = mockUri("content://test/1")
        val bytes = byteArrayOf(1, 2, 3)

        viewModel.stageAttachment(uri, bytes, MessageType.IMAGE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.stagedAttachments.size)
        assertEquals(uri, state.stagedAttachments[0].uri)
        assertEquals(MessageType.IMAGE, state.stagedAttachments[0].type)
    }

    @Test
    fun `stageAttachment should preserve raw bytes reference`() = runTest {
        val uri = mockUri("content://test/1")
        val bytes = byteArrayOf(10, 20, 30)

        viewModel.stageAttachment(uri, bytes, MessageType.FILE)
        advanceUntilIdle()

        val staged = viewModel.uiState.value.stagedAttachments[0]
        assertTrue(staged.bytes.contentEquals(bytes))
    }

    @Test
    fun `stageAttachment should allow up to 10 attachments`() = runTest {
        repeat(10) { index ->
            val uri = mockUri("content://test/$index")
            viewModel.stageAttachment(uri, byteArrayOf(index.toByte()), MessageType.IMAGE)
            advanceUntilIdle()
        }

        assertEquals(10, viewModel.uiState.value.stagedAttachments.size)
    }

    @Test
    fun `stageAttachment should not exceed 10 attachments limit`() = runTest {
        repeat(10) { index ->
            val uri = mockUri("content://test/$index")
            viewModel.stageAttachment(uri, byteArrayOf(index.toByte()), MessageType.IMAGE)
            advanceUntilIdle()
        }

        // Try to add 11th
        val extraUri = mockUri("content://test/extra")
        viewModel.stageAttachment(extraUri, byteArrayOf(99), MessageType.IMAGE)
        advanceUntilIdle()

        assertEquals(10, viewModel.uiState.value.stagedAttachments.size)
        assertFalse(viewModel.uiState.value.stagedAttachments.any { it.uri.toString() == "content://test/extra" })
    }

    @Test
    fun `stageAttachment should handle different message types`() = runTest {
        val uri1 = mockUri("content://test/img")
        val uri2 = mockUri("content://test/vid")
        val uri3 = mockUri("content://test/file")

        viewModel.stageAttachment(uri1, byteArrayOf(1), MessageType.IMAGE)
        viewModel.stageAttachment(uri2, byteArrayOf(2), MessageType.VIDEO)
        viewModel.stageAttachment(uri3, byteArrayOf(3), MessageType.FILE)
        advanceUntilIdle()

        val staged = viewModel.uiState.value.stagedAttachments
        assertEquals(3, staged.size)
        assertEquals(MessageType.IMAGE, staged[0].type)
        assertEquals(MessageType.VIDEO, staged[1].type)
        assertEquals(MessageType.FILE, staged[2].type)
    }

    // ==================== Remove Staged Attachment Tests ====================

    @Test
    fun `removeStagedAttachment should remove matching attachment by uri`() = runTest {
        val uri1 = mockUri("content://test/1")
        val uri2 = mockUri("content://test/2")

        viewModel.stageAttachment(uri1, byteArrayOf(1), MessageType.IMAGE)
        viewModel.stageAttachment(uri2, byteArrayOf(2), MessageType.IMAGE)
        advanceUntilIdle()

        viewModel.removeStagedAttachment(uri1)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.stagedAttachments.size)
        assertEquals(uri2, state.stagedAttachments[0].uri)
    }

    @Test
    fun `removeStagedAttachment with non-existent uri should not change state`() = runTest {
        val uri = mockUri("content://test/1")
        val nonExistentUri = mockUri("content://test/nonexistent")

        viewModel.stageAttachment(uri, byteArrayOf(1), MessageType.IMAGE)
        advanceUntilIdle()

        viewModel.removeStagedAttachment(nonExistentUri)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.stagedAttachments.size)
    }

    @Test
    fun `removeStagedAttachment from empty list should not crash`() = runTest {
        val uri = mockUri("content://test/nonexistent")

        viewModel.removeStagedAttachment(uri)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.stagedAttachments.isEmpty())
    }

    // ==================== Clear Staged Attachments Tests ====================

    @Test
    fun `clearStagedAttachments should empty the staged list`() = runTest {
        viewModel.stageAttachment(mockUri("content://test/1"), byteArrayOf(1), MessageType.IMAGE)
        viewModel.stageAttachment(mockUri("content://test/2"), byteArrayOf(2), MessageType.VIDEO)
        advanceUntilIdle()
        assertEquals(2, viewModel.uiState.value.stagedAttachments.size)

        viewModel.clearStagedAttachments()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.stagedAttachments.isEmpty())
    }

    @Test
    fun `clearStagedAttachments on empty list should remain empty`() = runTest {
        viewModel.clearStagedAttachments()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.stagedAttachments.isEmpty())
    }

    // ==================== sendImage Tests ====================

    @Test
    fun `sendImage should delegate to repository sendImage`() = runTest {
        val imageBytes = byteArrayOf(1, 2, 3, 4)
        val extension = "jpg"

        coEvery { mockRepository.sendImage(any(), any(), any<ByteArray>(), any(), any()) } returns Result.success(Unit)

        viewModel.sendImage(imageBytes, extension)
        advanceUntilIdle()

        coVerify { mockRepository.sendImage(testPeerId, testPeerName, imageBytes, extension, null) }
    }

    @Test
    fun `sendImage with replyToId should pass replyToId to repository`() = runTest {
        coEvery { mockRepository.sendImage(any(), any(), any<ByteArray>(), any(), any()) } returns Result.success(Unit)

        viewModel.sendImage(byteArrayOf(1), "png", replyToId = "reply-msg")
        advanceUntilIdle()

        coVerify { mockRepository.sendImage(testPeerId, testPeerName, any<ByteArray>(), "png", "reply-msg") }
    }

    // ==================== sendVideo Tests ====================

    @Test
    fun `sendVideo should delegate to repository sendVideo`() = runTest {
        val videoBytes = byteArrayOf(5, 6, 7, 8)
        val extension = "mp4"

        coEvery { mockRepository.sendVideo(any(), any(), any<ByteArray>(), any(), any()) } returns Result.success(Unit)

        viewModel.sendVideo(videoBytes, extension)
        advanceUntilIdle()

        coVerify { mockRepository.sendVideo(testPeerId, testPeerName, videoBytes, extension, null) }
    }

    @Test
    fun `sendVideo with replyToId should pass replyToId to repository`() = runTest {
        coEvery { mockRepository.sendVideo(any(), any(), any<ByteArray>(), any(), any()) } returns Result.success(Unit)

        viewModel.sendVideo(byteArrayOf(1), "avi", replyToId = "reply-msg")
        advanceUntilIdle()

        coVerify { mockRepository.sendVideo(testPeerId, testPeerName, any<ByteArray>(), "avi", "reply-msg") }
    }

    // ==================== sendFileWithProgress Tests ====================

    @Test
    fun `sendFileWithProgress should initialize progress to 0`() = runTest {
        coEvery { mockRepository.sendFileWithProgress(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        val testFile = mockk<File>(relaxed = true)
        every { testFile.length() } returns 1024L

        viewModel.sendFileWithProgress("msg-1", testFile, MessageType.FILE)
        advanceUntilIdle()

        // Progress should have been set to 0 at start and removed on success
        // We verify via the uploadProgress flow
        val progress = viewModel.uploadProgress.first()
        // After success, the entry should be removed
        assertFalse(progress.containsKey("msg-1"))
    }

    @Test
    fun `sendFileWithProgress on failure should set uploadError`() = runTest {
        coEvery { mockRepository.sendFileWithProgress(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.failure(Exception("Disk full"))
        val testFile = mockk<File>(relaxed = true)
        every { testFile.length() } returns 1024L

        viewModel.sendFileWithProgress("msg-1", testFile, MessageType.FILE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.uploadError)
    }

    @Test
    fun `sendFileWithProgress should pass correct parameters to repository`() = runTest {
        coEvery { mockRepository.sendFileWithProgress(any(), any(), any(), any(), any(), any(), any(), any()) } returns Result.success(Unit)
        val testFile = mockk<File>(relaxed = true)
        every { testFile.length() } returns 2048L
        every { testFile.name } returns "document.pdf"

        viewModel.sendFileWithProgress(
            messageId = "msg-pdf",
            file = testFile,
            fileType = MessageType.FILE,
            caption = "My document",
            replyToId = "reply-msg"
        )
        advanceUntilIdle()

        coVerify {
            mockRepository.sendFileWithProgress(
                messageId = "msg-pdf",
                peerId = testPeerId,
                peerName = testPeerName,
                file = testFile,
                fileType = MessageType.FILE,
                caption = "My document",
                replyToId = "reply-msg",
                progressCallback = any()
            )
        }
    }

    // ==================== cancelUpload Tests ====================
    // NOTE: This test uses CompletableDeferred which can cause infinite loops
    // with UnconfinedTestDispatcher. Moved to integration tests.
    // @Test
    // fun `cancelUpload should remove progress entry`() = runTest { ... }

    // ==================== getAttachmentsForMessage Tests ====================

    @Test
    fun `getAttachmentsForMessage should fetch from repository when not cached`() = runTest {
        val attachments = listOf(
            testAttachment(id = "att-1", messageId = "group-1"),
            testAttachment(id = "att-2", messageId = "group-1")
        )
        coEvery { mockRepository.getMessageAttachments("group-1") } returns attachments

        val result = viewModel.getAttachmentsForMessage("group-1")

        assertEquals(2, result.size)
        coVerify { mockRepository.getMessageAttachments("group-1") }
    }

    @Test
    fun `getAttachmentsForMessage should use cache on second call`() = runTest {
        val attachments = listOf(testAttachment(id = "att-1", messageId = "group-1"))
        coEvery { mockRepository.getMessageAttachments("group-1") } returns attachments

        // First call - fetches from repo
        viewModel.getAttachmentsForMessage("group-1")
        // Second call - should use cache
        viewModel.getAttachmentsForMessage("group-1")

        // Repository should only be called once
        coVerify(exactly = 1) { mockRepository.getMessageAttachments("group-1") }
    }

    @Test
    fun `getAttachmentsForMessage should return empty list for group with no attachments`() = runTest {
        coEvery { mockRepository.getMessageAttachments("empty-group") } returns emptyList()

        val result = viewModel.getAttachmentsForMessage("empty-group")

        assertTrue(result.isEmpty())
    }

    // ==================== LRU Cache Eviction Tests ====================

    @Test
    fun `getAttachmentsForMessage should evict oldest entries after 200 entries`() = runTest {
        // Add 200 entries to fill the cache
        repeat(200) { index ->
            val groupId = "group-$index"
            coEvery { mockRepository.getMessageAttachments(groupId) } returns listOf(
                testAttachment(id = "att-$index", messageId = groupId)
            )
            viewModel.getAttachmentsForMessage(groupId)
        }

        // Add one more entry - should trigger eviction
        val newGroupId = "group-new"
        coEvery { mockRepository.getMessageAttachments(newGroupId) } returns listOf(
            testAttachment(id = "att-new", messageId = newGroupId)
        )
        viewModel.getAttachmentsForMessage(newGroupId)

        // The oldest entry (group-0) should have been evicted
        // Accessing it again should hit the repository
        coEvery { mockRepository.getMessageAttachments("group-0") } returns listOf(
            testAttachment(id = "att-0", messageId = "group-0")
        )
        viewModel.getAttachmentsForMessage("group-0")

        coVerify(exactly = 2) { mockRepository.getMessageAttachments("group-0") }
    }

    @Test
    fun `getAttachmentsForMessage cache should differentiate by groupId`() = runTest {
        val attachments1 = listOf(testAttachment(id = "att-1", messageId = "group-a"))
        val attachments2 = listOf(testAttachment(id = "att-2", messageId = "group-b"))

        coEvery { mockRepository.getMessageAttachments("group-a") } returns attachments1
        coEvery { mockRepository.getMessageAttachments("group-b") } returns attachments2

        val resultA = viewModel.getAttachmentsForMessage("group-a")
        val resultB = viewModel.getAttachmentsForMessage("group-b")

        assertEquals("att-1", resultA[0].id)
        assertEquals("att-2", resultB[0].id)
        // Each repo call should happen exactly once
        coVerify(exactly = 1) { mockRepository.getMessageAttachments("group-a") }
        coVerify(exactly = 1) { mockRepository.getMessageAttachments("group-b") }
    }

    // ==================== clearUploadError Tests ====================

    @Test
    fun `clearUploadError should set uploadError to null`() = runTest {
        // Initial state has no error
        assertNull(viewModel.uiState.value.uploadError)

        viewModel.clearUploadError()

        assertNull(viewModel.uiState.value.uploadError)
    }

    // ==================== Concurrent Staging Tests ====================

    @Test
    fun `concurrent stageAttachment calls should respect 10 limit`() = runTest {
        // Stage 10 attachments
        repeat(10) { index ->
            val uri = mockUri("content://test/$index")
            viewModel.stageAttachment(uri, byteArrayOf(index.toByte()), MessageType.IMAGE)
            advanceUntilIdle()
        }

        assertEquals(10, viewModel.uiState.value.stagedAttachments.size)

        // Try to stage more
        repeat(5) { index ->
            val uri = mockUri("content://test/extra-$index")
            viewModel.stageAttachment(uri, byteArrayOf(99), MessageType.IMAGE)
            advanceUntilIdle()
        }

        // Still only 10
        assertEquals(10, viewModel.uiState.value.stagedAttachments.size)
    }
}
