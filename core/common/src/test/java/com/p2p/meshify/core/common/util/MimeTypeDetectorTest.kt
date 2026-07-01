package com.p2p.meshify.core.util

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Unit tests for MimeTypeDetector.
 * Tests cover known MIME types, unknown extensions, path parsing,
 * readable type names, and edge cases.
 */
@RunWith(RobolectricTestRunner::class)
class MimeTypeDetectorTest {

    // ============================================================================
    // SECTION 1: KNOWN IMAGE EXTENSIONS
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns image jpeg for jpg`() {
        // Given
        val extension = "jpg"

        // When
        val mime = MimeTypeDetector.getMimeTypeFromExtension(extension)

        // Then
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun `getMimeTypeFromExtension returns image jpeg for jpeg`() {
        assertEquals("image/jpeg", MimeTypeDetector.getMimeTypeFromExtension("jpeg"))
    }

    @Test
    fun `getMimeTypeFromExtension returns image png for png`() {
        assertEquals("image/png", MimeTypeDetector.getMimeTypeFromExtension("png"))
    }

    @Test
    fun `getMimeTypeFromExtension returns image gif for gif`() {
        assertEquals("image/gif", MimeTypeDetector.getMimeTypeFromExtension("gif"))
    }

    @Test
    fun `getMimeTypeFromExtension returns image webp for webp`() {
        assertEquals("image/webp", MimeTypeDetector.getMimeTypeFromExtension("webp"))
    }

    @Test
    fun `getMimeTypeFromExtension returns image bmp for bmp`() {
        assertEquals("image/bmp", MimeTypeDetector.getMimeTypeFromExtension("bmp"))
    }

    @Test
    fun `getMimeTypeFromExtension returns image svg for svg`() {
        assertEquals("image/svg+xml", MimeTypeDetector.getMimeTypeFromExtension("svg"))
    }

    // ============================================================================
    // SECTION 2: KNOWN VIDEO EXTENSIONS
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns video mp4 for mp4`() {
        assertEquals("video/mp4", MimeTypeDetector.getMimeTypeFromExtension("mp4"))
    }

    @Test
    fun `getMimeTypeFromExtension returns video x-matroska for mkv`() {
        assertEquals("video/x-matroska", MimeTypeDetector.getMimeTypeFromExtension("mkv"))
    }

    @Test
    fun `getMimeTypeFromExtension returns video x-msvideo for avi`() {
        assertEquals("video/x-msvideo", MimeTypeDetector.getMimeTypeFromExtension("avi"))
    }

    @Test
    fun `getMimeTypeFromExtension returns video webm for webm`() {
        assertEquals("video/webm", MimeTypeDetector.getMimeTypeFromExtension("webm"))
    }

    @Test
    fun `getMimeTypeFromExtension returns video quicktime for mov`() {
        assertEquals("video/quicktime", MimeTypeDetector.getMimeTypeFromExtension("mov"))
    }

    // ============================================================================
    // SECTION 3: KNOWN AUDIO EXTENSIONS
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns audio mpeg for mp3`() {
        assertEquals("audio/mpeg", MimeTypeDetector.getMimeTypeFromExtension("mp3"))
    }

    @Test
    fun `getMimeTypeFromExtension returns audio wav for wav`() {
        assertEquals("audio/wav", MimeTypeDetector.getMimeTypeFromExtension("wav"))
    }

    @Test
    fun `getMimeTypeFromExtension returns audio aac for aac`() {
        assertEquals("audio/aac", MimeTypeDetector.getMimeTypeFromExtension("aac"))
    }

    @Test
    fun `getMimeTypeFromExtension returns audio flac for flac`() {
        assertEquals("audio/flac", MimeTypeDetector.getMimeTypeFromExtension("flac"))
    }

    @Test
    fun `getMimeTypeFromExtension returns audio ogg for ogg`() {
        assertEquals("audio/ogg", MimeTypeDetector.getMimeTypeFromExtension("ogg"))
    }

    // ============================================================================
    // SECTION 4: KNOWN DOCUMENT EXTENSIONS
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns application pdf for pdf`() {
        assertEquals("application/pdf", MimeTypeDetector.getMimeTypeFromExtension("pdf"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application msword for doc`() {
        assertEquals("application/msword", MimeTypeDetector.getMimeTypeFromExtension("doc"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application vnd openxmlformats for docx`() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            MimeTypeDetector.getMimeTypeFromExtension("docx")
        )
    }

    @Test
    fun `getMimeTypeFromExtension returns application vnd ms-excel for xls`() {
        assertEquals("application/vnd.ms-excel", MimeTypeDetector.getMimeTypeFromExtension("xls"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application vnd openxmlformats spreadsheet for xlsx`() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            MimeTypeDetector.getMimeTypeFromExtension("xlsx")
        )
    }

    @Test
    fun `getMimeTypeFromExtension returns application vnd ms-powerpoint for ppt`() {
        assertEquals("application/vnd.ms-powerpoint", MimeTypeDetector.getMimeTypeFromExtension("ppt"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application vnd openxmlformats presentation for pptx`() {
        assertEquals(
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            MimeTypeDetector.getMimeTypeFromExtension("pptx")
        )
    }

    // ============================================================================
    // SECTION 5: KNOWN ARCHIVE EXTENSIONS
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns application zip for zip`() {
        assertEquals("application/zip", MimeTypeDetector.getMimeTypeFromExtension("zip"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application vnd rar for rar`() {
        assertEquals("application/vnd.rar", MimeTypeDetector.getMimeTypeFromExtension("rar"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application x-7z-compressed for 7z`() {
        assertEquals("application/x-7z-compressed", MimeTypeDetector.getMimeTypeFromExtension("7z"))
    }

    @Test
    fun `getMimeTypeFromExtension returns application x-tar for tar`() {
        assertEquals("application/x-tar", MimeTypeDetector.getMimeTypeFromExtension("tar"))
    }

    // ============================================================================
    // SECTION 6: APK AND TEXT
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns application vnd android package for apk`() {
        assertEquals(
            "application/vnd.android.package-archive",
            MimeTypeDetector.getMimeTypeFromExtension("apk")
        )
    }

    @Test
    fun `getMimeTypeFromExtension returns text plain for txt`() {
        assertEquals("text/plain", MimeTypeDetector.getMimeTypeFromExtension("txt"))
    }

    // ============================================================================
    // SECTION 7: UNKNOWN AND EDGE CASE EXTENSIONS
    // ============================================================================

    @Test
    fun `getMimeTypeFromExtension returns octet stream for unknown extension`() {
        // Given
        val unknown = "xyz123"

        // When
        val mime = MimeTypeDetector.getMimeTypeFromExtension(unknown)

        // Then
        assertEquals("application/octet-stream", mime)
    }

    @Test
    fun `getMimeTypeFromExtension handles empty extension`() {
        // Given
        val empty = ""

        // When
        val mime = MimeTypeDetector.getMimeTypeFromExtension(empty)

        // Then
        assertEquals("application/octet-stream", mime)
    }

    @Test
    fun `getMimeTypeFromExtension handles extension with leading dot`() {
        // Given
        val extension = ".jpg"

        // When
        val mime = MimeTypeDetector.getMimeTypeFromExtension(extension)

        // Then
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun `getMimeTypeFromExtension is case insensitive`() {
        // When/Then
        assertEquals("image/jpeg", MimeTypeDetector.getMimeTypeFromExtension("JPG"))
        assertEquals("image/jpeg", MimeTypeDetector.getMimeTypeFromExtension("Jpg"))
        assertEquals("image/png", MimeTypeDetector.getMimeTypeFromExtension("PNG"))
        assertEquals("video/mp4", MimeTypeDetector.getMimeTypeFromExtension("MP4"))
        assertEquals("application/pdf", MimeTypeDetector.getMimeTypeFromExtension("PDF"))
    }

    @Test
    fun `getMimeTypeFromExtension handles extension with trailing spaces as unknown`() {
        assertEquals("application/octet-stream", MimeTypeDetector.getMimeTypeFromExtension("jpg "))
    }

    // ============================================================================
    // SECTION 8: PATH-BASED DETECTION
    // ============================================================================

    @Test
    fun `getMimeTypeFromPath extracts extension and returns correct MIME`() {
        // Given
        val path = "/storage/emulated/0/DCIM/photo.jpg"

        // When
        val mime = MimeTypeDetector.getMimeTypeFromPath(path)

        // Then
        assertEquals("image/jpeg", mime)
    }

    @Test
    fun `getMimeTypeFromPath handles file without extension`() {
        // Given
        val path = "/storage/emulated/0/file_without_extension"

        // When
        val mime = MimeTypeDetector.getMimeTypeFromPath(path)

        // Then
        assertEquals("application/octet-stream", mime)
    }

    @Test
    fun `getMimeTypeFromPath handles path with multiple dots`() {
        // Given
        val path = "/storage/downloads/archive.tar.gz"

        // When
        val mime = MimeTypeDetector.getMimeTypeFromPath(path)

        // Then - gets the last extension after final dot
        // "gz" falls through to octet-stream since it's not in the manual MIME map
        assertEquals("application/octet-stream", mime)
    }

    @Test
    fun `getMimeTypeFromPath handles empty path`() {
        // Given
        val path = ""

        // When
        val mime = MimeTypeDetector.getMimeTypeFromPath(path)

        // Then - empty path has no extension
        assertEquals("application/octet-stream", mime)
    }

    // ============================================================================
    // SECTION 9: EXTENSION FROM PATH
    // ============================================================================

    @Test
    fun `getExtensionFromPath extracts extension correctly`() {
        // Given
        val path = "/path/to/document.pdf"

        // When
        val ext = MimeTypeDetector.getExtensionFromPath(path)

        // Then
        assertEquals("pdf", ext)
    }

    @Test
    fun `getExtensionFromPath returns lowercase extension`() {
        // Given
        val path = "/path/to/Photo.JPG"

        // When
        val ext = MimeTypeDetector.getExtensionFromPath(path)

        // Then
        assertEquals("jpg", ext)
    }

    @Test
    fun `getExtensionFromPath returns empty for no extension`() {
        // Given
        val path = "/path/to/noext"

        // When
        val ext = MimeTypeDetector.getExtensionFromPath(path)

        // Then
        assertEquals("", ext)
    }

    // ============================================================================
    // SECTION 10: SUPPORTED TYPE CHECK
    // ============================================================================

    @Test
    fun `isSupportedType returns true for known extensions`() {
        // When/Then
        assertTrue("jpg should be supported", MimeTypeDetector.isSupportedType("jpg"))
        assertTrue("png should be supported", MimeTypeDetector.isSupportedType("png"))
        assertTrue("mp4 should be supported", MimeTypeDetector.isSupportedType("mp4"))
        assertTrue("mp3 should be supported", MimeTypeDetector.isSupportedType("mp3"))
        assertTrue("pdf should be supported", MimeTypeDetector.isSupportedType("pdf"))
        assertTrue("zip should be supported", MimeTypeDetector.isSupportedType("zip"))
        assertTrue("apk should be supported", MimeTypeDetector.isSupportedType("apk"))
        assertTrue("txt should be supported", MimeTypeDetector.isSupportedType("txt"))
    }

    @Test
    fun `isSupportedType returns false for unknown extensions`() {
        // When/Then
        assertFalse("xyz should not be supported", MimeTypeDetector.isSupportedType("xyz"))
        assertFalse("exe should not be supported", MimeTypeDetector.isSupportedType("exe"))
        assertFalse("dll should not be supported", MimeTypeDetector.isSupportedType("dll"))
    }

    @Test
    fun `isSupportedType handles empty extension`() {
        assertFalse("empty should not be supported", MimeTypeDetector.isSupportedType(""))
    }

    @Test
    fun `isSupportedType is case insensitive`() {
        assertTrue(MimeTypeDetector.isSupportedType("JPG"))
        assertTrue(MimeTypeDetector.isSupportedType("PDF"))
        assertTrue(MimeTypeDetector.isSupportedType("APK"))
    }

    // ============================================================================
    // SECTION 11: READABLE TYPE NAMES
    // ============================================================================

    @Test
    fun `getReadableTypeName returns Image for image extensions`() {
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("jpg"))
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("png"))
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("gif"))
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("webp"))
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("bmp"))
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("svg"))
    }

    @Test
    fun `getReadableTypeName returns Video for video extensions`() {
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("mp4"))
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("mkv"))
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("avi"))
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("webm"))
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("mov"))
    }

    @Test
    fun `getReadableTypeName returns Audio for audio extensions`() {
        assertEquals("Audio", MimeTypeDetector.getReadableTypeName("mp3"))
        assertEquals("Audio", MimeTypeDetector.getReadableTypeName("wav"))
        assertEquals("Audio", MimeTypeDetector.getReadableTypeName("aac"))
        assertEquals("Audio", MimeTypeDetector.getReadableTypeName("flac"))
        assertEquals("Audio", MimeTypeDetector.getReadableTypeName("ogg"))
    }

    @Test
    fun `getReadableTypeName returns Document for document extensions`() {
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("pdf"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("doc"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("docx"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("xls"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("xlsx"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("ppt"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("pptx"))
    }

    @Test
    fun `getReadableTypeName returns Archive for archive extensions`() {
        assertEquals("Archive", MimeTypeDetector.getReadableTypeName("zip"))
        assertEquals("Archive", MimeTypeDetector.getReadableTypeName("rar"))
        assertEquals("Archive", MimeTypeDetector.getReadableTypeName("7z"))
        assertEquals("Archive", MimeTypeDetector.getReadableTypeName("tar"))
    }

    @Test
    fun `getReadableTypeName returns APK for apk extension`() {
        assertEquals("APK", MimeTypeDetector.getReadableTypeName("apk"))
    }

    @Test
    fun `getReadableTypeName returns File for unknown extensions`() {
        assertEquals("File", MimeTypeDetector.getReadableTypeName("xyz"))
        assertEquals("File", MimeTypeDetector.getReadableTypeName("exe"))
        assertEquals("File", MimeTypeDetector.getReadableTypeName(""))
    }

    @Test
    fun `getReadableTypeName is case insensitive`() {
        assertEquals("Image", MimeTypeDetector.getReadableTypeName("JPG"))
        assertEquals("Video", MimeTypeDetector.getReadableTypeName("MP4"))
        assertEquals("Document", MimeTypeDetector.getReadableTypeName("PDF"))
        assertEquals("APK", MimeTypeDetector.getReadableTypeName("APK"))
    }
}
