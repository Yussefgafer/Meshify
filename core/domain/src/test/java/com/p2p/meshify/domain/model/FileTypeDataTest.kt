package com.p2p.meshify.domain.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for FileTypeData data class and MessageType.getFileTypeData() extension.
 */
class FileTypeDataTest {

    // --- FileTypeData construction ---

    @Test
    fun `FileTypeData constructed with all params returns correct values`() {
        val data = FileTypeData(
            iconId = "image",
            label = "Image",
            color = 0xFF34A853
        )

        assertEquals("image", data.iconId)
        assertEquals("Image", data.label)
        assertEquals(0xFF34A853, data.color)
    }

    // --- FileTypeData copy / equals / hashCode ---

    @Test
    fun `FileTypeData copy creates equal instance with no overrides`() {
        val data = FileTypeData(iconId = "a", label = "A", color = 0xFF000000)
        assertEquals(data, data.copy())
    }

    @Test
    fun `FileTypeData copy overrides specified field`() {
        val data = FileTypeData(iconId = "a", label = "A", color = 0xFF000000)
        val modified = data.copy(label = "B")
        assertEquals("B", modified.label)
        assertEquals("a", modified.iconId)
    }

    @Test
    fun `FileTypeData equals returns true for same values`() {
        val a = FileTypeData(iconId = "x", label = "X", color = 0xFF123456)
        val b = FileTypeData(iconId = "x", label = "X", color = 0xFF123456)
        assertEquals(a, b)
    }

    @Test
    fun `FileTypeData equals returns false for different iconId`() {
        val a = FileTypeData(iconId = "a", label = "X", color = 0xFF123456)
        val b = FileTypeData(iconId = "b", label = "X", color = 0xFF123456)
        assertNotEquals(a, b)
    }

    @Test
    fun `FileTypeData hashCode is consistent for equal instances`() {
        val a = FileTypeData(iconId = "x", label = "X", color = 0xFF123456)
        val b = FileTypeData(iconId = "x", label = "X", color = 0xFF123456)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `FileTypeData toString contains iconId and label`() {
        val data = FileTypeData(iconId = "music_note", label = "Audio", color = 0xFFFBBC05)
        val str = data.toString()
        assertTrue(str.contains("music_note"))
        assertTrue(str.contains("Audio"))
        assertTrue(str.contains("color="))
    }

    // --- getFileTypeData for each MessageType ---

    @Test
    fun `TEXT getFileTypeData returns correct values`() {
        val data = MessageType.TEXT.getFileTypeData()
        assertEquals("description", data.iconId)
        assertEquals("Text", data.label)
        assertEquals(0xFF4285F4, data.color)
    }

    @Test
    fun `IMAGE getFileTypeData returns correct values`() {
        val data = MessageType.IMAGE.getFileTypeData()
        assertEquals("image", data.iconId)
        assertEquals("Image", data.label)
        assertEquals(0xFF34A853, data.color)
    }

    @Test
    fun `VIDEO getFileTypeData returns correct values`() {
        val data = MessageType.VIDEO.getFileTypeData()
        assertEquals("movie", data.iconId)
        assertEquals("Video", data.label)
        assertEquals(0xFFEA4335, data.color)
    }

    @Test
    fun `AUDIO getFileTypeData returns correct values`() {
        val data = MessageType.AUDIO.getFileTypeData()
        assertEquals("music_note", data.iconId)
        assertEquals("Audio", data.label)
        assertEquals(0xFFFBBC05, data.color)
    }

    @Test
    fun `DOCUMENT getFileTypeData returns correct values`() {
        val data = MessageType.DOCUMENT.getFileTypeData()
        assertEquals("description", data.iconId)
        assertEquals("Document", data.label)
        assertEquals(0xFF4285F4, data.color)
    }

    @Test
    fun `ARCHIVE getFileTypeData returns correct values`() {
        val data = MessageType.ARCHIVE.getFileTypeData()
        assertEquals("folder_zip", data.iconId)
        assertEquals("Archive", data.label)
        assertEquals(0xFF9AA0A6, data.color)
    }

    @Test
    fun `APK getFileTypeData returns correct values`() {
        val data = MessageType.APK.getFileTypeData()
        assertEquals("android", data.iconId)
        assertEquals("APK", data.label)
        assertEquals(0xFF34A853, data.color)
    }

    @Test
    fun `FILE getFileTypeData returns correct values`() {
        val data = MessageType.FILE.getFileTypeData()
        assertEquals("insert_drive_file", data.iconId)
        assertEquals("File", data.label)
        assertEquals(0xFF9AA0A6, data.color)
    }

    // --- Color values are proper ARGB ---

    @Test
    fun `all getFileTypeData colors have full alpha channel`() {
        val types = MessageType.values()
        for (type in types) {
            val data = type.getFileTypeData()
            val alpha = (data.color shr 24) and 0xFF
            assertEquals("$type should have full alpha", 0xFF, alpha.toInt())
        }
    }

    @Test
    fun `all getFileTypeData labels are non-empty`() {
        val types = MessageType.values()
        for (type in types) {
            val data = type.getFileTypeData()
            assertTrue("$type label should not be blank", data.label.isNotBlank())
        }
    }

    @Test
    fun `all getFileTypeData iconIds are non-empty`() {
        val types = MessageType.values()
        for (type in types) {
            val data = type.getFileTypeData()
            assertTrue("$type iconId should not be blank", data.iconId.isNotBlank())
        }
    }
}
