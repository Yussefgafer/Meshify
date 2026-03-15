package com.p2p.meshify.domain.model

/**
 * File type data with icon identifier and display info.
 * Icons are mapped in the UI layer.
 */
data class FileTypeData(
    val iconId: String, // Icon identifier for UI layer to resolve
    val label: String,
    val color: Long // ARGB color
)

/**
 * Extension to get file type data.
 */
fun MessageType.getFileTypeData(): FileTypeData {
    return when (this) {
        MessageType.TEXT -> FileTypeData(
            iconId = "description",
            label = "Text",
            color = 0xFF4285F4 // Blue
        )
        MessageType.IMAGE -> FileTypeData(
            iconId = "image",
            label = "Image",
            color = 0xFF34A853 // Green
        )
        MessageType.VIDEO -> FileTypeData(
            iconId = "movie",
            label = "Video",
            color = 0xFFEA4335 // Red
        )
        MessageType.AUDIO -> FileTypeData(
            iconId = "music_note",
            label = "Audio",
            color = 0xFFFBBC05 // Yellow
        )
        MessageType.DOCUMENT -> FileTypeData(
            iconId = "description",
            label = "Document",
            color = 0xFF4285F4 // Blue
        )
        MessageType.ARCHIVE -> FileTypeData(
            iconId = "folder_zip",
            label = "Archive",
            color = 0xFF9AA0A6 // Gray
        )
        MessageType.APK -> FileTypeData(
            iconId = "android",
            label = "APK",
            color = 0xFF34A853 // Green
        )
        MessageType.FILE -> FileTypeData(
            iconId = "insert_drive_file",
            label = "File",
            color = 0xFF9AA0A6 // Gray
        )
    }
}
