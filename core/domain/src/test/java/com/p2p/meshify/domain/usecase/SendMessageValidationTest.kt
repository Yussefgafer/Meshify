package com.p2p.meshify.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SendMessageValidation.
 *
 * Tests the validation rules extracted from ChatInputViewModel.sendMessage():
 * - Empty text with no attachments should fail
 * - Text with content should pass
 * - Rapid calls (debouncing) should be prevented
 * - Reply-to self should fail
 * - Message size limits
 */
class SendMessageValidationTest {

    private lateinit var subject: SendMessageValidation

    @Before
    fun setup() {
        subject = SendMessageValidation()
    }

    // ============================================================================================
    // EMPTY CONTENT TESTS
    // ============================================================================================

    @Test
    fun `empty text with no attachments fails`() {
        // Given
        val text = ""

        // When
        val result = subject.validate(text = text, hasAttachments = false)

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.EMPTY_CONTENT, result.errorCode)
    }

    @Test
    fun `whitespace-only text with no attachments fails`() {
        // Given
        val text = "   "

        // When
        val result = subject.validate(text = text, hasAttachments = false)

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.EMPTY_CONTENT, result.errorCode)
    }

    @Test
    fun `empty text with attachments passes`() {
        // Given
        val text = ""

        // When
        val result = subject.validate(text = text, hasAttachments = true)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `whitespace-only text with attachments passes`() {
        // Given
        val text = "  \n  "

        // When
        val result = subject.validate(text = text, hasAttachments = true)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    // ============================================================================================
    // VALID CONTENT TESTS
    // ============================================================================================

    @Test
    fun `normal text passes`() {
        // Given
        val text = "Hello, world!"

        // When
        val result = subject.validate(text = text)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `single character text passes`() {
        // Given
        val text = "a"

        // When
        val result = subject.validate(text = text)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `text with unicode passes`() {
        // Given
        val text = "🔐🌐💬"

        // When
        val result = subject.validate(text = text)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `text with newlines passes`() {
        // Given
        val text = "Line 1\nLine 2\nLine 3"

        // When
        val result = subject.validate(text = text)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    // ============================================================================================
    // DEBOUNCING TESTS
    // ============================================================================================

    @Test
    fun `send within 500ms debounce window fails`() {
        // Given
        val lastSendTime = 1000L
        val currentTime = 1400L // 400ms later

        // When
        val result = subject.validate(
            text = "Hello",
            lastSendTimeMillis = lastSendTime,
            currentTimeMillis = currentTime
        )

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.SEND_DEBOUNCED, result.errorCode)
    }

    @Test
    fun `send exactly at 500ms boundary passes`() {
        // Given
        val lastSendTime = 1000L
        val currentTime = 1500L // exactly 500ms later (500 < 500 is false)

        // When
        val result = subject.validate(
            text = "Hello",
            lastSendTimeMillis = lastSendTime,
            currentTimeMillis = currentTime
        )

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `send after debounce window passes`() {
        // Given
        val lastSendTime = 1000L
        val currentTime = 1501L // 501ms later

        // When
        val result = subject.validate(
            text = "Hello",
            lastSendTimeMillis = lastSendTime,
            currentTimeMillis = currentTime
        )

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `first send with no previous send time passes`() {
        // Given
        val text = "First message"

        // When
        val result = subject.validate(
            text = text,
            lastSendTimeMillis = 0,
            currentTimeMillis = 1000L
        )

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `rapid consecutive sends are all blocked except first`() {
        // Given
        val baseTime = 1000L

        // When: First send
        val first = subject.validate(
            text = "msg1",
            lastSendTimeMillis = 0,
            currentTimeMillis = baseTime
        )

        // Then: First passes
        assertTrue(first.isValid)

        // When: Second send 100ms later
        val second = subject.validate(
            text = "msg2",
            lastSendTimeMillis = baseTime,
            currentTimeMillis = baseTime + 100
        )

        // Then: Second blocked
        assertFalse(second.isValid)
        assertEquals(SendMessageValidation.ErrorCode.SEND_DEBOUNCED, second.errorCode)

        // When: Third send 200ms later
        val third = subject.validate(
            text = "msg3",
            lastSendTimeMillis = baseTime,
            currentTimeMillis = baseTime + 200
        )

        // Then: Third blocked
        assertFalse(third.isValid)
        assertEquals(SendMessageValidation.ErrorCode.SEND_DEBOUNCED, third.errorCode)
    }

    // ============================================================================================
    // REPLY-TO-SELF TESTS
    // ============================================================================================

    @Test
    fun `reply to self fails`() {
        // Given
        val text = "Replying to myself"

        // When
        val result = subject.validate(text = text, isReplyToSelf = true)

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.REPLY_TO_SELF, result.errorCode)
    }

    @Test
    fun `reply to other passes`() {
        // Given
        val text = "Replying to peer"

        // When
        val result = subject.validate(text = text, isReplyToSelf = false)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `reply to self with attachments also fails`() {
        // Given
        val text = ""

        // When
        val result = subject.validate(text = text, hasAttachments = true, isReplyToSelf = true)

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.REPLY_TO_SELF, result.errorCode)
    }

    // ============================================================================================
    // MESSAGE SIZE LIMIT TESTS
    // ============================================================================================

    @Test
    fun `message at exact max length passes`() {
        // Given
        val text = "x".repeat(SendMessageValidation.MAX_MESSAGE_LENGTH)

        // When
        val result = subject.validate(text = text)

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }

    @Test
    fun `message one character over max length fails`() {
        // Given
        val text = "x".repeat(SendMessageValidation.MAX_MESSAGE_LENGTH + 1)

        // When
        val result = subject.validate(text = text)

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.MESSAGE_TOO_LONG, result.errorCode)
    }

    @Test
    fun `message at double max length fails`() {
        // Given
        val text = "x".repeat(SendMessageValidation.MAX_MESSAGE_LENGTH * 2)

        // When
        val result = subject.validate(text = text)

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.MESSAGE_TOO_LONG, result.errorCode)
    }

    // ============================================================================================
    // COMBINED VALIDATION TESTS
    // ============================================================================================

    @Test
    fun `empty text and reply to self returns empty content error first`() {
        // Given: Both empty content and reply-to-self are true
        val text = ""

        // When
        val result = subject.validate(text = text, isReplyToSelf = true)

        // Then: Empty content is checked first
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.EMPTY_CONTENT, result.errorCode)
    }

    @Test
    fun `too long message and reply to self returns message too long first`() {
        // Given: Both too long and reply-to-self
        val text = "x".repeat(SendMessageValidation.MAX_MESSAGE_LENGTH + 1)

        // When
        val result = subject.validate(text = text, isReplyToSelf = true)

        // Then: Message too long is checked before reply-to-self
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.MESSAGE_TOO_LONG, result.errorCode)
    }

    @Test
    fun `too long message and debounced returns message too long first`() {
        // Given
        val text = "x".repeat(SendMessageValidation.MAX_MESSAGE_LENGTH + 1)

        // When
        val result = subject.validate(
            text = text,
            lastSendTimeMillis = 1000L,
            currentTimeMillis = 1100L
        )

        // Then
        assertFalse(result.isValid)
        assertEquals(SendMessageValidation.ErrorCode.MESSAGE_TOO_LONG, result.errorCode)
    }

    @Test
    fun `valid message with all parameters set passes`() {
        // Given
        val text = "Valid message"

        // When
        val result = subject.validate(
            text = text,
            hasAttachments = true,
            lastSendTimeMillis = 0,
            currentTimeMillis = 1000L,
            isReplyToSelf = false
        )

        // Then
        assertTrue(result.isValid)
        assertNull(result.errorCode)
    }
}
