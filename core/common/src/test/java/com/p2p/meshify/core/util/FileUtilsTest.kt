package com.p2p.meshify.core.util

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Unit tests for [FileUtils].
 *
 * - `calculateHash` is pure JVM (no Android) — verified with SHA-256 reference vectors.
 * - `getBytesFromUri` / `saveBytesToInternalStorage` / `getFilePath` need a `Context`,
 *   so they run under Robolectric against `applicationContext.filesDir`.
 *
 * Limitations: only exercises the in-process file APIs. Real ContentResolver
 * permissions/grants are not covered here — that requires an instrumented test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class FileUtilsTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        // Best-effort cleanup of any category dirs we created.
        listOf("media", "test-clean").forEach { cat ->
            File(context.filesDir, cat).deleteRecursively()
        }
    }

    // ---- calculateHash (pure JVM) ----

    @Test
    fun `calculateHash — known SHA-256 vectors`() {
        // RFC4634 / FIPS-180 test vectors.
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            FileUtils.calculateHash("abc".toByteArray())
        )
        assertEquals(
            "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
            FileUtils.calculateHash("hello".toByteArray())
        )
    }

    @Test
    fun `calculateHash — empty input`() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        assertEquals(
            "e3b0c44298fc1c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b"
                .let { "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855" },
            FileUtils.calculateHash(ByteArray(0))
        )
    }

    @Test
    fun `calculateHash — unicode and binary bytes`() {
        // Stable length: 64 hex chars regardless of input.
        val hash = FileUtils.calculateHash("שלום".toByteArray(Charsets.UTF_8))
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })

        val hashBinary = FileUtils.calculateHash(byteArrayOf(0x00, 0xFF.toByte(), 0x10, 0xAB.toByte()))
        assertEquals(64, hashBinary.length)
    }

    // ---- saveBytesToInternalStorage (needs Context) ----

    @Test
    fun `saveBytesToInternalStorage — writes to category subdir`() {
        val bytes = "hello-payload".toByteArray()
        val path = FileUtils.saveBytesToInternalStorage(context, "greeting.txt", bytes, category = "media")

        assertNotNull("expected non-null path", path)
        val saved = File(path!!)
        assertTrue("file must exist on disk", saved.exists())
        assertArrayEquals(bytes, saved.readBytes())
        assertEquals(File(context.filesDir, "media/greeting.txt").absolutePath, saved.absolutePath)
    }

    @Test
    fun `saveBytesToInternalStorage — overwrites existing file`() {
        val first = "one".toByteArray()
        val second = "two-second".toByteArray()
        val path = FileUtils.saveBytesToInternalStorage(context, "x.bin", first, category = "media")!!
        FileUtils.saveBytesToInternalStorage(context, "x.bin", second, category = "media")
        assertArrayEquals(second, File(path).readBytes())
    }

    @Test
    fun `saveBytesToInternalStorage — creates nested category dir if missing`() {
        val path = FileUtils.saveBytesToInternalStorage(
            context, "doc.bin", "x".toByteArray(), category = "test-clean"
        )
        assertNotNull(path)
        assertTrue(File(context.filesDir, "test-clean").isDirectory)
    }

    // ---- getFilePath ----

    @Test
    fun `getFilePath — returns null for missing file`() {
        assertNull(FileUtils.getFilePath(context, "never-written.bin", category = "media"))
    }

    @Test
    fun `getFilePath — returns absolute path for existing file`() {
        FileUtils.saveBytesToInternalStorage(context, "exists.bin", "z".toByteArray(), category = "media")
        val path = FileUtils.getFilePath(context, "exists.bin", category = "media")
        assertEquals(File(context.filesDir, "media/exists.bin").absolutePath, path)
    }

    // ---- getBytesFromUri ----

    @Test
    fun `getBytesFromUri — reads bytes for a file scheme uri`() {
        // Stage a real file on disk and resolve via file:// Uri.
        val tmp = File.createTempFile("fu_", ".bin", context.cacheDir)
        tmp.writeBytes("file-uri-payload".toByteArray())
        try {
            val bytes = FileUtils.getBytesFromUri(context, Uri.fromFile(tmp))
            assertNotNull(bytes)
            assertArrayEquals("file-uri-payload".toByteArray(), bytes!!)
        } finally {
            tmp.delete()
        }
    }

    @Test
    fun `getBytesFromUri — returns null when the uri cannot be opened`() {
        // A uri that doesn't exist triggers the catch block.
        val bogus = Uri.fromFile(File("/this/path/does/not/exist-${System.nanoTime()}.bin"))
        assertNull(FileUtils.getBytesFromUri(context, bogus))
    }

    // ---- input-shape sanity ----

    @Test
    fun `saveBytesToInternalStorage returns absolute path under filesDir`() {
        val path = FileUtils.saveBytesToInternalStorage(context, "a.bin", ByteArray(1), category = "media")!!
        assertTrue(
            "path must live under app filesDir",
            path.startsWith(context.filesDir.absolutePath)
        )
        assertFalse(path.contains(".."))
    }
}
