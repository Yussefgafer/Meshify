package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.domain.repository.IFileManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Unit tests for [FileManagerImpl].
 *
 * Why no Robolectric here:
 * `FileManagerImpl` only touches `Context.getFilesDir()` — a single File
 * accessor. The class under test doesn't need Android resources, services, or
 * `Looper`, so we drive it with a mockk `Context` pointing at a JUnit temp dir
 * (fast and deterministic). Note: the project compiles test bytecode to
 * class-file v65 (via compileOptions VERSION_21 in this module's
 * build.gradle.kts); Robolectric is available here but adds no value for a
 * pure-file-access test, so we avoid it. Same approach as
 * `WifiPermissionCheckerTest.contextGranting(...)`.
 *
 * What this covers:
 * - `saveMedia` writes bytes to `filesDir/media/`, returns the absolute path,
 *   and rejects names that escape the media directory (path-traversal defense).
 * - `stageBytes` writes into `filesDir/staging/`, returning the absolute path.
 * - `stageFile` copies a source file into staging. If the sanitized name
 *   escapes, [IFileManager.stageFile] catches the thrown exception and
 *   returns null.
 *
 * Note: `sanitizeFileName` is private; the traversal tests target it via the
 * public API. Names containing `/`, `\`, or `..` are folded into a random UUID
 * by the sanitizer; only names whose canonical path escapes the media root
 * even after sanitization are rejected outright by `saveMedia`'s
 * defense-in-depth check.
 */
class FileManagerImplTest {

    private lateinit var filesDir: File
    private lateinit var context: Context
    private lateinit var mediaDir: File
    private lateinit var stagingDir: File

    @Before
    fun setUp() {
        // JUnit temp dir — auto-cleaned in @After.
        filesDir = Files.createTempDirectory("filemanager-impl-test").toFile()
        // Mockk Context whose only relevant accessor is getFilesDir().
        context = mockk()
        every { context.filesDir } returns filesDir

        // init {} in FileManagerImpl creates these.
        mediaDir = File(filesDir, "media")
        stagingDir = File(filesDir, IFileManager.STAGING_DIR_NAME)
    }

    @After
    fun tearDown() {
        filesDir.deleteRecursively()
    }

    @Test
    fun `saveMedia — writes bytes to media directory`() = runTest {
        val fm = FileManagerImpl(context)
        val path = fm.saveMedia("photo.jpg", byteArrayOf(1, 2, 3, 4))

        assertNotNull(path)
        val written = File(path!!)
        assertTrue("file must exist at $path", written.exists())
        assertTrue(
            "file must live under mediaDir",
            written.canonicalPath.startsWith(mediaDir.canonicalPath)
        )
        assertEquals(4L, written.length())
        assertEquals(byteArrayOf(1, 2, 3, 4).toList(), written.readBytes().toList())
    }

    @Test
    fun `saveMedia — strips forward slashes from name`() = runTest {
        val fm = FileManagerImpl(context)
        val path = fm.saveMedia("sub/dir/photo.png", byteArrayOf(7))

        assertNotNull(path)
        val written = File(path!!)
        assertTrue("file must exist at $path", written.exists())
        // After sanitization, the file must NOT contain a subdirectory.
        assertEquals(
            "filename must be flat (no subdir)",
            mediaDir.canonicalPath,
            written.parentFile?.canonicalPath
        )
    }

    @Test
    fun `saveMedia — defends against traversal attempts`() = runTest {
        val fm = FileManagerImpl(context)
        // Sanitizer folds `..` pairs and slashes into a UUID; the only way the
        // canonical path can escape mediaDir is if a leftover `../` segment
        // survives sanitization (e.g. `..../foo` reduces to `../foo`).
        // saveMedia's defense-in-depth check returns null in that case.
        val path = fm.saveMedia("../../../../etc/passwd", ByteArray(0))

        if (path != null) {
            val written = File(path)
            assertTrue(
                "even when sanitized, must stay inside mediaDir: ${written.canonicalPath}",
                written.canonicalPath.startsWith(mediaDir.canonicalPath)
            )
        }
        // null is also a valid outcome (defense-in-depth short-circuit).
    }

    @Test
    fun `saveMedia — empty bytes still creates an empty file`() = runTest {
        val fm = FileManagerImpl(context)
        val path = fm.saveMedia("empty.bin", ByteArray(0))
        assertNotNull(path)
        assertEquals(0L, File(path!!).length())
    }

    @Test
    fun `stageBytes — writes into staging directory`() = runTest {
        val fm = FileManagerImpl(context)
        val path = fm.stageBytes("payload.dat", byteArrayOf(0x42, 0x43))

        assertNotNull(path)
        val written = File(path!!)
        assertTrue("file must exist at $path", written.exists())
        assertTrue(
            "file must live under stagingDir",
            written.canonicalPath.startsWith(stagingDir.canonicalPath)
        )
        assertEquals(byteArrayOf(0x42, 0x43).toList(), written.readBytes().toList())
    }

    @Test
    fun `stageFile — copies source into staging`() = runTest {
        val src = Files.createTempFile("source", ".bin").toFile().apply {
            writeBytes(byteArrayOf(9, 9, 9))
        }
        try {
            val fm = FileManagerImpl(context)
            val path = fm.stageFile("copied.bin", src)

            assertNotNull(path)
            val written = File(path!!)
            assertTrue(written.exists())
            assertTrue(
                "copied file must live under stagingDir",
                written.canonicalPath.startsWith(stagingDir.canonicalPath)
            )
            assertEquals(3L, written.length())
            assertEquals(byteArrayOf(9, 9, 9).toList(), written.readBytes().toList())
        } finally {
            src.delete()
        }
    }

    @Test
    fun `stageFile — returns null when source is missing`() = runTest {
        val missing = File(filesDir, "does-not-exist.bin")
        val fm = FileManagerImpl(context)
        // copyTo throws NoSuchFileException — stageFile's try/catch returns null.
        val path = fm.stageFile("ghost.bin", missing)
        assertNull(path)
    }

    @Test
    fun `init — creates media and staging directories under filesDir`() {
        // Construct the manager; its init block creates the two directories.
        FileManagerImpl(context)
        assertTrue(mediaDir.exists())
        assertTrue(mediaDir.isDirectory)
        assertTrue(stagingDir.exists())
        assertTrue(stagingDir.isDirectory)
    }
}
