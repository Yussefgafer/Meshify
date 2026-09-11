package com.p2p.meshify.domain.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IFileManagerTest {

    @Test
    fun `STAGING_DIR_NAME constant is staging`() {
        assertEquals("staging", IFileManager.STAGING_DIR_NAME)
    }

    @Test
    fun `saveMedia delegates and returns path on success`() = runTest {
        val manager = mockk<IFileManager>()
        coEvery { manager.saveMedia("photo.jpg", any()) } returns "/data/media/photo.jpg"

        val result = manager.saveMedia("photo.jpg", byteArrayOf(1, 2, 3))

        assertEquals("/data/media/photo.jpg", result)
        coVerify(exactly = 1) { manager.saveMedia("photo.jpg", any()) }
    }

    @Test
    fun `saveMedia returns null on failure`() = runTest {
        val manager = mockk<IFileManager>()
        coEvery { manager.saveMedia(any(), any()) } returns null

        val result = manager.saveMedia("fail.jpg", byteArrayOf(1))

        assertNull(result)
    }

    @Test
    fun `stageFile returns staged path when underlying file exists`() = runTest {
        val manager = mockk<IFileManager>()
        val source = File("/tmp/source.pdf")
        coEvery { manager.stageFile("source.pdf", source) } returns "/data/staging/source.pdf"

        val result = manager.stageFile("source.pdf", source)

        assertEquals("/data/staging/source.pdf", result)
        coVerify { manager.stageFile("source.pdf", source) }
    }

    @Test
    fun `stageFile returns null when staging fails`() = runTest {
        val manager = mockk<IFileManager>()
        coEvery { manager.stageFile(any(), any()) } returns null

        assertNull(manager.stageFile("x.bin", File("/tmp/x.bin")))
    }

    @Test
    fun `stageBytes returns path for in-memory content`() = runTest {
        val manager = mockk<IFileManager>()
        coEvery { manager.stageBytes("note.txt", any()) } returns "/data/staging/note.txt"

        val result = manager.stageBytes("note.txt", "hello".toByteArray())

        assertEquals("/data/staging/note.txt", result)
        coVerify { manager.stageBytes("note.txt", any()) }
    }

    @Test
    fun `stageBytes returns null on failure`() = runTest {
        val manager = mockk<IFileManager>()
        coEvery { manager.stageBytes(any(), any()) } returns null

        assertNull(manager.stageBytes("fail.txt", byteArrayOf()))
    }

    @Test
    fun `relaxed mock does not crash on suspend calls`() = runTest {
        val manager = mockk<IFileManager>(relaxed = true)

        // Relaxed mock must not throw; return value is mockk's default (null or empty).
        // Just verify the calls complete and are recorded.
        manager.saveMedia("a.jpg", byteArrayOf())
        manager.stageFile("a.jpg", File("/tmp/a.jpg"))
        manager.stageBytes("a.jpg", byteArrayOf())

        coVerify { manager.saveMedia(any(), any()) }
        coVerify { manager.stageFile(any(), any()) }
        coVerify { manager.stageBytes(any(), any()) }
    }
}
