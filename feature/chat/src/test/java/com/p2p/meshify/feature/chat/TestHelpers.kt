package com.p2p.meshify.feature.chat

import android.net.Uri
import com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.domain.model.MessageType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * JUnit rule that replaces the Main dispatcher with a test dispatcher.
 * Ensures all coroutine code using Dispatchers.Main runs synchronously in tests.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

// ==================== Test Entity Factories ====================

/**
 * Creates a test MessageEntity with sensible defaults.
 */
fun testMessage(
    id: String = "msg-1",
    chatId: String = "test-peer",
    text: String? = "Test message",
    senderId: String = "me",
    timestamp: Long = 1000L,
    isFromMe: Boolean = true,
    type: MessageType = MessageType.TEXT,
    status: com.p2p.meshify.core.data.local.entity.MessageStatus = com.p2p.meshify.core.data.local.entity.MessageStatus.SENT,
    reaction: String? = null,
    replyToId: String? = null,
    groupId: String? = null,
    mediaPath: String? = null
): MessageEntity {
    return MessageEntity(
        id = id,
        chatId = chatId,
        senderId = senderId,
        text = text,
        mediaPath = mediaPath,
        type = type,
        timestamp = timestamp,
        isFromMe = isFromMe,
        status = status,
        reaction = reaction,
        replyToId = replyToId,
        groupId = groupId
    )
}

/**
 * Creates a test MessageAttachmentEntity with sensible defaults.
 */
fun testAttachment(
    id: String = "attach-1",
    type: MessageType = MessageType.IMAGE,
    messageId: String? = "msg-1",
    filePath: String = "/path/to/file.jpg"
): MessageAttachmentEntity {
    return MessageAttachmentEntity(
        id = id,
        type = type,
        messageId = messageId,
        filePath = filePath
    )
}

/**
 * Creates a mock Uri for testing.
 */
fun mockUri(uriString: String = "content://test/uri/1"): Uri {
    val mock = io.mockk.mockk<Uri>(relaxed = true)
    io.mockk.every { mock.toString() } returns uriString
    return mock
}
