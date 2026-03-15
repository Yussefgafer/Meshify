package com.p2p.meshify.domain.model

/**
 * Message/File type for all supported file types.
 * Includes MIME type detection and icon mapping.
 */
enum class MessageType(
    val mimeType: String,
    val extension: List<String>
) {
    TEXT("text/plain", listOf("txt")),
    IMAGE("image/*", listOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")),
    VIDEO("video/*", listOf("mp4", "mkv", "avi", "webm", "mov", "flv")),
    AUDIO("audio/*", listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")),
    DOCUMENT("application/*", listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp")),
    ARCHIVE("application/zip", listOf("zip", "rar", "7z", "tar", "gz", "bz2")),
    APK("application/vnd.android.package-archive", listOf("apk")),
    FILE("application/octet-stream", listOf("*")); // Generic fallback

    companion object {
        /**
         * Detects file type from extension.
         */
        fun fromExtension(extension: String): MessageType {
            val ext = extension.lowercase().trimStart('.')
            return values().find { type ->
                type != TEXT && type != FILE && ext in type.extension
            } ?: FILE
        }

        /**
         * Detects file type from MIME type.
         */
        fun fromMimeType(mimeType: String): MessageType {
            val mime = mimeType.lowercase()
            return when {
                mime.startsWith("image/") -> IMAGE
                mime.startsWith("video/") -> VIDEO
                mime.startsWith("audio/") -> AUDIO
                mime.contains("pdf") -> DOCUMENT
                mime.contains("word") -> DOCUMENT
                mime.contains("excel") -> DOCUMENT
                mime.contains("powerpoint") -> DOCUMENT
                mime.contains("zip") || mime.contains("compressed") -> ARCHIVE
                mime.contains("apk") -> APK
                mime.startsWith("text/") -> TEXT
                else -> FILE
            }
        }
    }
}
