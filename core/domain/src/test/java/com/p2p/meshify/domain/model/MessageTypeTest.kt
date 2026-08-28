package com.p2p.meshify.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageTypeTest {

    @Test
    fun fromExtension_mapsKnownExtensions() {
        assertEquals(MessageType.IMAGE, MessageType.fromExtension("jpg"))
        assertEquals(MessageType.IMAGE, MessageType.fromExtension("png"))
        assertEquals(MessageType.IMAGE, MessageType.fromExtension(".PNG")) // leading dot stripped, lowercased
        assertEquals(MessageType.VIDEO, MessageType.fromExtension("mp4"))
        assertEquals(MessageType.AUDIO, MessageType.fromExtension("mp3"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromExtension("pdf"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromExtension("docx"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromExtension("xlsx"))
        assertEquals(MessageType.ARCHIVE, MessageType.fromExtension("zip"))
        assertEquals(MessageType.APK, MessageType.fromExtension("apk"))
    }

    @Test
    fun fromExtension_unknownFallsBackToFile() {
        assertEquals(MessageType.FILE, MessageType.fromExtension("txt")) // TEXT is excluded from the search
        assertEquals(MessageType.FILE, MessageType.fromExtension("unknownxyz"))
        assertEquals(MessageType.FILE, MessageType.fromExtension(""))
    }

    @Test
    fun fromMimeType_mapsKnownMimeTypes() {
        assertEquals(MessageType.IMAGE, MessageType.fromMimeType("image/png"))
        assertEquals(MessageType.VIDEO, MessageType.fromMimeType("video/mp4"))
        assertEquals(MessageType.AUDIO, MessageType.fromMimeType("audio/mpeg"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/pdf"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/msword"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/vnd.ms-excel"))
        assertEquals(MessageType.DOCUMENT, MessageType.fromMimeType("application/vnd.ms-powerpoint"))
        assertEquals(MessageType.ARCHIVE, MessageType.fromMimeType("application/zip"))
        assertEquals(MessageType.TEXT, MessageType.fromMimeType("text/plain"))
        assertEquals(MessageType.FILE, MessageType.fromMimeType("application/octet-stream"))
        assertEquals(MessageType.FILE, MessageType.fromMimeType("weird/type"))
    }

    @Test
    fun fromMimeType_apkMimeMapsToApk() {
        assertEquals(MessageType.APK, MessageType.fromMimeType("application/vnd.android.package-archive"))
    }
}
