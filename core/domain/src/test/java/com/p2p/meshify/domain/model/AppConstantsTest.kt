package com.p2p.meshify.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConstantsTest {

    @Test
    fun `MAX_FILE_SIZE_BYTES is 10MB minus 64KB`() {
        val expected = 10L * 1024 * 1024 - 64L * 1024
        assertEquals(expected, AppConstants.MAX_FILE_SIZE_BYTES)
        assertEquals(10_420_224L, AppConstants.MAX_FILE_SIZE_BYTES)
    }

    @Test
    fun `MAX_FILE_SIZE_BYTES is positive and strictly below 10MB`() {
        assertTrue(AppConstants.MAX_FILE_SIZE_BYTES > 0)
        assertTrue(AppConstants.MAX_FILE_SIZE_BYTES < 10L * 1024 * 1024)
    }

    @Test
    fun `MAX_FILE_SIZE_BYTES leaves exactly 64KB margin for framing overhead`() {
        val margin = 10L * 1024 * 1024 - AppConstants.MAX_FILE_SIZE_BYTES
        assertEquals(64L * 1024, margin)
    }

    @Test
    fun `DEFAULT_PEER_NAME_PREFIX is Peer underscore`() {
        assertEquals("Peer_", AppConstants.DEFAULT_PEER_NAME_PREFIX)
    }

    @Test
    fun `DEFAULT_PEER_NAME_PREFIX is non-empty and usable as display prefix`() {
        assertTrue(AppConstants.DEFAULT_PEER_NAME_PREFIX.isNotEmpty())
        assertEquals("Peer_Alice", AppConstants.DEFAULT_PEER_NAME_PREFIX + "Alice")
    }
}
