package com.p2p.meshify.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Unit tests for [MimeTypeDetector].
 *
 * Two code paths are exercised:
 * - Robolectric's `MimeTypeMap` lookup (jvm-known extensions).
 * - Manual fallback inside `getMimeTypeFromExtensionManual` for extensions the
 *   platform map does not carry (mkv, flac, docx, apk, etc.).
 *
 * The manual fallback also documents the corrected APK behavior:
 * `getMimeTypeFromExtension("apk")` returns `application/vnd.android.package-archive`
 * (it would have been the platform map for `apk` anyway, but we cover the manual
 * branch so a future refactor cannot silently drop the APK mapping).
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class MimeTypeDetectorTest {

    // ---- getMimeTypeFromExtension: extension-only ----

    @Test
    fun `getMimeTypeFromExtension — strips leading dot and lowercases`() {
        // ".PNG" -> "png" -> "image/png" (manual fallback covers png)
        assertEquals("image/png", MimeTypeDetector.getMimeTypeFromExtension(".PNG"))
    }

    @Test
    fun `getMimeTypeFromExtension — common extensions map to expected types`() {
        // Manual fallback entries.
        assertEquals("image/jpeg", MimeTypeDetector.getMimeTypeFromExtension("jpg"))
        assertEquals("image/jpeg", MimeTypeDetector.getMimeTypeFromExtension("jpeg"))
        assertEquals("video/mp4", MimeTypeDetector.getMimeTypeFromExtension("mp4"))
        assertEquals("audio/mpeg", MimeTypeDetector.getMimeTypeFromExtension("mp3"))
        assertEquals("application/pdf", MimeTypeDetector.getMimeTypeFromExtension("pdf"))
        assertEquals("text/plain", MimeTypeDetector.getMimeTypeFromExtension("txt"))
    }

    @Test
    fun `getMimeTypeFromExtension — apk returns the android archive mime`() {
        // Critical: APK must be detected so the chat layer knows what it is shipping.
        assertEquals(
            "application/vnd.android.package-archive",
            MimeTypeDetector.getMimeTypeFromExtension("apk")
        )
    }

    @Test
    fun `getMimeTypeFromExtension — unsupported extension falls back to octet-stream`() {
        assertEquals(
            "application/octet-stream",
            MimeTypeDetector.getMimeTypeFromExtension("zzznotreal")
        )
    }

    @Test
    fun `getMimeTypeFromExtension — empty string falls back to octet-stream`() {
        assertEquals(
            "application/octet-stream",
            MimeTypeDetector.getMimeTypeFromExtension("")
        )
    }

    // ---- getMimeTypeFromPath ----

    @Test
    fun `getMimeTypeFromPath — derives extension from path`() {
        assertEquals("image/png", MimeTypeDetector.getMimeTypeFromPath("/tmp/foo/bar.png"))
        assertEquals("video/mp4", MimeTypeDetector.getMimeTypeFromPath("a/b/c.MP4"))
    }

    @Test
    fun `getMimeTypeFromPath — no extension returns octet-stream`() {
        assertEquals(
            "application/octet-stream",
            MimeTypeDetector.getMimeTypeFromPath("/tmp/noext")
        )
    }

    // ---- getExtensionFromPath ----

    @Test
    fun `getExtensionFromPath — returns lowercased extension only`() {
        assertEquals("png", MimeTypeDetector.getExtensionFromPath("/a/b/c.PNG"))
        assertEquals("mp4", MimeTypeDetector.getExtensionFromPath("foo.MP4"))
    }

    @Test
    fun `getExtensionFromPath — no extension returns empty string`() {
        assertEquals("", MimeTypeDetector.getExtensionFromPath("/a/b/noext"))
    }

    // ---- isSupportedType ----

    @Test
    fun `isSupportedType — common categories are supported`() {
        assertTrue(MimeTypeDetector.isSupportedType("jpg"))
        assertTrue(MimeTypeDetector.isSupportedType("mp4"))
        assertTrue(MimeTypeDetector.isSupportedType("mp3"))
        assertTrue(MimeTypeDetector.isSupportedType("pdf"))
        assertTrue(MimeTypeDetector.isSupportedType("apk"))
        assertTrue(MimeTypeDetector.isSupportedType("txt"))
        // Leading dot tolerated.
        assertTrue(MimeTypeDetector.isSupportedType(".PDF"))
    }

    @Test
    fun `isSupportedType — unknown extension is not supported`() {
        assertFalse(MimeTypeDetector.isSupportedType("not-a-real-type"))
        assertFalse(MimeTypeDetector.isSupportedType(""))
    }

    // ---- getReadableTypeName ----

    @Test
    fun `getReadableTypeName — buckets to a category`() {
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("jpg"))
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("png"))
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("mp4"))
        assertEquals("Audio", MimeTypeDetector.getReadableTypeName("mp3"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("pdf"))
        assertEquals("Archive", MimeTypeDetector.getReadableTypeName("zip"))
        assertEquals("APK", MimeTypeDetector.getReadableTypeName("apk"))
        assertEquals("File", MimeTypeDetector.getReadableTypeName("unknown"))
    }
}
