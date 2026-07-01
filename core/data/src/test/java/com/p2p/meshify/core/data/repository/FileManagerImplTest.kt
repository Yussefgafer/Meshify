package com.p2p.meshify.core.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Unit tests for FileManagerImpl.
 *
 * Uses Robolectric for real file system access.
 */
@RunWith(RobolectricTestRunner::class)
class FileManagerImplTest {

    private lateinit var context: Context
    private lateinit var fileManager: FileManagerImpl
    private lateinit var mediaDir: File

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fileManager = FileManagerImpl(context)
        mediaDir = File(context.filesDir, "media")
    }

    @After
    fun teardown() {
        // Clean up media files
        if (mediaDir.exists()) {
            mediaDir.deleteRecursively()
        }
    }

    // ============================================================================================
    // saveMedia() TESTS
    // ============================================================================================

    @Test
    fun `saveMedia saves bytes to media directory`() = runTest {
        // Given
        val fileName = "test_image.jpg"
        val data = byteArrayOf(0x01, 0x02, 0x03, 0x04)

        // When
        val savedPath = fileManager.saveMedia(fileName, data)

        // Then
        assertNotNull(savedPath)
        assertTrue(savedPath!!.endsWith("media/test_image.jpg"))

        val savedFile = File(savedPath)
        assertTrue(savedFile.exists())
        assertEquals(4, savedFile.length())
    }

    @Test
    fun `saveMedia fails when media directory is missing`() = runTest {
        // Given: ensure media directory doesn't exist
        if (mediaDir.exists()) {
            mediaDir.deleteRecursively()
        }
        assertFalse(mediaDir.exists())

        // When: saveMedia doesn't recreate the directory itself
        val savedPath = fileManager.saveMedia("new_file.txt", byteArrayOf(0x01))

        // Then: save fails because parent directory doesn't exist
        assertNull(savedPath)
    }

    @Test
    fun `saveMedia handles duplicate filenames`() = runTest {
        // Given: first save
        val fileName = "duplicate.txt"
        val firstPath = fileManager.saveMedia(fileName, byteArrayOf(0x01))
        assertNotNull(firstPath)

        // When: second save with same name
        val secondPath = fileManager.saveMedia(fileName, byteArrayOf(0x02, 0x03))

        // Then: overwrites (no deduplication logic, last write wins)
        assertNotNull(secondPath)
        assertEquals(firstPath, secondPath)

        val savedFile = File(secondPath!!)
        assertEquals(2, savedFile.length()) // Second write's bytes
    }

    @Test
    fun `saveMedia returns null on error`() = runTest {
        // Given: try to save to an invalid path
        // Create a file at the media directory location to cause failure
        if (mediaDir.exists()) {
            mediaDir.deleteRecursively()
        }
        // Create a FILE instead of a directory at media path
        mediaDir.parentFile?.let { parent ->
            val blockingFile = File(parent, "media")
            blockingFile.writeText("not a directory")
        }

        // When
        val savedPath = fileManager.saveMedia("should_fail.bin", byteArrayOf(0x01))

        // Then: returns null
        assertNull(savedPath)

        // Cleanup
        val blockingFile = File(context.filesDir, "media")
        if (blockingFile.exists() && blockingFile.isFile) {
            blockingFile.delete()
        }
    }

    @Test
    fun `saveMedia saves empty byte array`() = runTest {
        // Given
        val fileName = "empty.bin"
        val data = ByteArray(0)

        // When
        val savedPath = fileManager.saveMedia(fileName, data)

        // Then
        assertNotNull(savedPath)
        val savedFile = File(savedPath!!)
        assertTrue(savedFile.exists())
        assertEquals(0, savedFile.length())
    }

    @Test
    fun `saveMedia saves large file`() = runTest {
        // Given: 1MB file
        val fileName = "large.bin"
        val data = ByteArray(1024 * 1024) { it.toByte() }

        // When
        val savedPath = fileManager.saveMedia(fileName, data)

        // Then
        assertNotNull(savedPath)
        val savedFile = File(savedPath!!)
        assertEquals(1024 * 1024, savedFile.length())
    }

    @Test
    fun `saveMedia preserves exact bytes`() = runTest {
        // Given
        val expectedData = "Hello, Meshify!".toByteArray(Charsets.UTF_8)

        // When
        val savedPath = fileManager.saveMedia("message.txt", expectedData)

        // Then
        assertNotNull(savedPath)
        val readBytes = File(savedPath!!).readBytes()
        assertTrue(expectedData.contentEquals(readBytes))
    }

    // ============================================================================================
    // getAppVersion() TESTS
    // ============================================================================================

    @Test
    fun `getAppVersion returns version string`() {
        val version = fileManager.getAppVersion()
        // Should return a non-empty string (from build config or "1.0" fallback)
        assertNotNull(version)
        assertTrue(version.isNotEmpty())
    }
}
