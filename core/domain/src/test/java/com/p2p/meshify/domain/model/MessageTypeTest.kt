package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for MessageType enum and its companion methods.
 */
class MessageTypeTest {

    // --- Enum values ---

    @Test
    fun `MessageType contains all expected values`() {
        val expected = listOf(
            MessageType.TEXT,
            MessageType.IMAGE,
            MessageType.VIDEO,
            MessageType.AUDIO,
            MessageType.DOCUMENT,
            MessageType.ARCHIVE,
            MessageType.APK,
            MessageType.FILE
        )

        assertEquals(expected.size, MessageType.values().size)
        assertTrue(MessageType.values().toList().containsAll(expected))
    }

    @Test
    fun `MessageType values have unique names`() {
        val names = MessageType.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `MessageType IMAGE has correct mimeType`() {
        assertEquals("image/*", MessageType.IMAGE.mimeType)
    }

    @Test
    fun `MessageType VIDEO has correct mimeType`() {
        assertEquals("video/*", MessageType.VIDEO.mimeType)
    }

    @Test
    fun `MessageType AUDIO has correct mimeType`() {
        assertEquals("audio/*", MessageType.AUDIO.mimeType)
    }

    @Test
    fun `MessageType TEXT has correct mimeType`() {
        assertEquals("text/plain", MessageType.TEXT.mimeType)
    }

    @Test
    fun `MessageType DOCUMENT has correct mimeType`() {
        assertEquals("application/*", MessageType.DOCUMENT.mimeType)
    }

    @Test
    fun `MessageType ARCHIVE has correct mimeType`() {
        assertEquals("application/zip", MessageType.ARCHIVE.mimeType)
    }

    @Test
    fun `MessageType APK has correct mimeType`() {
        assertEquals("application/vnd.android.package-archive", MessageType.APK.mimeType)
    }

    @Test
    fun `MessageType FILE has correct mimeType`() {
        assertEquals("application/octet-stream", MessageType.FILE.mimeType)
    }

    // --- Extensions ---

    @Test
    fun `IMAGE has jpg jpeg png gif webp bmp svg extensions`() {
        assertEquals(
            listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"),
            MessageType.IMAGE.extension
        )
    }

    @Test
    fun `VIDEO has mp4 mkv avi webm mov flv extensions`() {
        assertEquals(
            listOf("mp4", "mkv", "avi", "webm", "mov", "flv"),
            MessageType.VIDEO.extension
        )
    }

    @Test
    fun `AUDIO has mp3 wav aac flac ogg m4a wma extensions`() {
        assertEquals(
            listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma"),
            MessageType.AUDIO.extension
        )
    }

    @Test
    fun `FILE has wildcard extension`() {
        assertEquals(listOf("*"), MessageType.FILE.extension)
    }

    // --- fromExtension ---

    @Test
    fun `fromExtension returns IMAGE for jpg`() {
        assertEquals(MessageType.IMAGE, MessageType.fromExtension("jpg"))
    }

    @Test
    fun `fromExtension returns IMAGE for png`() {
        assertEquals(MessageType.IMAGE, MessageType.fromExtension("png"))
    }

    @Test
    fun `fromExtension returns VIDEO for mp4`() {
        assertEquals(MessageType.VIDEO, MessageType.fromExtension("mp4"))
    }

    @Test
    fun `fromExtension returns AUDIO for mp3`() {
        assertEquals(MessageType.AUDIO, MessageType.fromExtension("mp3"))
    }

    @Test
    fun `fromExtension returns DOCUMENT for pdf`() {
        assertEquals(MessageType.DOCUMENT, MessageType.fromExtension("pdf"))
    }

    @Test
    fun `fromExtension returns DOCUMENT for docx`() {
        assertEquals(MessageType.DOCUMENT, MessageType.fromExtension("docx"))
    }

    @Test
    fun `fromExtension returns ARCHIVE for zip`() {
        assertEquals(MessageType.ARCHIVE, MessageType.fromExtension("zip"))
    }

    @Test
    fun `fromExtension returns ARCHIVE for rar`() {
        assertEquals(MessageType.ARCHIVE, MessageType.fromExtension("rar"))
    }

    @Test
    fun `fromExtension returns APK for apk`() {
        assertEquals(MessageType.APK, MessageType.fromExtension("apk"))
    }

    @Test
    fun `fromExtension returns FILE for unknown extension`() {
        assertEquals(MessageType.FILE, MessageType.fromExtension("xyz"))
    }

    @Test
    fun `fromExtension handles leading dot`() {
        assertEquals(MessageType.IMAGE, MessageType.fromExtension(".jpg"))
    }

    @Test
    fun `fromExtension is case insensitive`() {
        assertEquals(MessageType.IMAGE, MessageType.fromExtension("JPG"))
        assertEquals(MessageType.IMAGE, MessageType.fromExtension("Png"))
    }

    @Test
    fun `fromExtension returns FILE for txt because TEXT is excluded from extension matching`() {
        assertEquals(MessageType.FILE, MessageType.fromExtension("txt"))
    }

    // --- fromMimeType ---

    @Test
    fun `fromMimeType returns IMAGE for image slash`() {
        assertEquals(MessageType.IMAGE, MessageType.fromMimeType("image/jpeg"))
        assertEquals(MessageType.IMAGE, MessageType.fromMimeType("image/png"))
        assertEquals(MessageType.IMAGE, MessageType.fromMimeType("image/gif"))
    }

    @Test
    fun `fromMimeType returns VIDEO for video slash`() {
        assertEquals(MessageType.VIDEO, MessageType.fromMimeType("video/mp4"))
        assertEquals(MessageType.VIDEO, MessageType.fromMimeType("video/webm"))
    }

    @Test
    fun `fromMimeType returns AUDIO for audio slash`() {
        assertEquals(MessageType.AUDIO, MessageType.fromMimeType("audio/mpeg"))
        assertEquals(MessageType.AUDIO, MessageType.fromMimeType("audio/wav"))
    }

    @Test
    fun `fromMimeType returns DOCUMENT for pdf`() {
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/pdf"))
    }

    @Test
    fun `fromMimeType returns DOCUMENT for word types`() {
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/msword"))
        assertEquals(
            MessageType.DOCUMENT,
            MessageType.fromMimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
        )
    }

    @Test
    fun `fromMimeType returns DOCUMENT for excel types`() {
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/vnd.ms-excel"))
    }

    @Test
    fun `fromMimeType returns DOCUMENT for powerpoint types`() {
        assertEquals(
            MessageType.DOCUMENT,
            MessageType.fromMimeType("application/vnd.ms-powerpoint")
        )
    }

    @Test
    fun `fromMimeType returns ARCHIVE for zip`() {
        assertEquals(MessageType.ARCHIVE, MessageType.fromMimeType("application/zip"))
    }

    @Test
    fun `fromMimeType returns ARCHIVE for compressed`() {
        assertEquals(MessageType.ARCHIVE, MessageType.fromMimeType("application/x-compressed"))
    }

    @Test
    fun `fromMimeType returns FILE for apk mime since 'apk' is not a substring of the registered mime type`() {
        assertEquals(MessageType.FILE, MessageType.fromMimeType("application/vnd.android.package-archive"))
    }

    @Test
    fun `fromMimeType returns TEXT for text slash`() {
        assertEquals(MessageType.TEXT, MessageType.fromMimeType("text/plain"))
        assertEquals(MessageType.TEXT, MessageType.fromMimeType("text/html"))
    }

    @Test
    fun `fromMimeType returns FILE for unknown mime type`() {
        assertEquals(MessageType.FILE, MessageType.fromMimeType("application/octet-stream"))
    }

    @Test
    fun `fromMimeType is case insensitive`() {
        assertEquals(MessageType.IMAGE, MessageType.fromMimeType("IMAGE/JPEG"))
    }

    // --- name property ---

    @Test
    fun `MessageType name returns correct enum name`() {
        assertEquals("TEXT", MessageType.TEXT.name)
        assertEquals("IMAGE", MessageType.IMAGE.name)
        assertEquals("VIDEO", MessageType.VIDEO.name)
        assertEquals("AUDIO", MessageType.AUDIO.name)
        assertEquals("DOCUMENT", MessageType.DOCUMENT.name)
        assertEquals("ARCHIVE", MessageType.ARCHIVE.name)
        assertEquals("APK", MessageType.APK.name)
        assertEquals("FILE", MessageType.FILE.name)
    }

    @Test
    fun `MessageType valueOf returns correct types`() {
        assertEquals(MessageType.TEXT, MessageType.valueOf("TEXT"))
        assertEquals(MessageType.IMAGE, MessageType.valueOf("IMAGE"))
        assertEquals(MessageType.VIDEO, MessageType.valueOf("VIDEO"))
        assertEquals(MessageType.AUDIO, MessageType.valueOf("AUDIO"))
        assertEquals(MessageType.DOCUMENT, MessageType.valueOf("DOCUMENT"))
        assertEquals(MessageType.ARCHIVE, MessageType.valueOf("ARCHIVE"))
        assertEquals(MessageType.APK, MessageType.valueOf("APK"))
        assertEquals(MessageType.FILE, MessageType.valueOf("FILE"))
    }
}
