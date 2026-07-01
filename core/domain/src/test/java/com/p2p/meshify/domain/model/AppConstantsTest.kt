package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AppConstants object.
 */
class AppConstantsTest {

    @Test
    fun `MAX_FILE_SIZE_BYTES is positive`() {
        assertTrue("MAX_FILE_SIZE_BYTES should be positive", AppConstants.MAX_FILE_SIZE_BYTES > 0)
    }

    @Test
    fun `MAX_FILE_SIZE_BYTES equals 100MB`() {
        assertEquals(100 * 1024 * 1024L, AppConstants.MAX_FILE_SIZE_BYTES)
    }

    @Test
    fun `MAX_FILE_SIZE_BYTES is exactly 104857600`() {
        assertEquals(104_857_600L, AppConstants.MAX_FILE_SIZE_BYTES)
    }

    @Test
    fun `MAX_FILE_SIZE_BYTES is not zero`() {
        assertNotEquals(0L, AppConstants.MAX_FILE_SIZE_BYTES)
    }

    @Test
    fun `DEFAULT_PEER_NAME_PREFIX is not blank`() {
        assertTrue(
            "DEFAULT_PEER_NAME_PREFIX should not be blank",
            AppConstants.DEFAULT_PEER_NAME_PREFIX.isNotBlank()
        )
    }

    @Test
    fun `DEFAULT_PEER_NAME_PREFIX equals Peer_`() {
        assertEquals("Peer_", AppConstants.DEFAULT_PEER_NAME_PREFIX)
    }

    @Test
    fun `DEFAULT_PEER_NAME_PREFIX ends with underscore`() {
        assertTrue(
            "DEFAULT_PEER_NAME_PREFIX should end with underscore for appending",
            AppConstants.DEFAULT_PEER_NAME_PREFIX.endsWith("_")
        )
    }

    @Test
    fun `DEFAULT_PEER_NAME_PREFIX does not contain whitespace`() {
        assertFalse(
            "DEFAULT_PEER_NAME_PREFIX should not contain whitespace",
            AppConstants.DEFAULT_PEER_NAME_PREFIX.contains(" ")
        )
    }

    @Test
    fun `all constants are non-null`() {
        assertNotNull(AppConstants.MAX_FILE_SIZE_BYTES)
        assertNotNull(AppConstants.DEFAULT_PEER_NAME_PREFIX)
    }
}
