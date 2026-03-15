package com.p2p.meshify.core.util

import android.webkit.MimeTypeMap
import java.io.File
import java.util.Locale

/**
 * MIME Type detection utility.
 * Supports all common file types.
 */
object MimeTypeDetector {
    
    private val mimeTypeMap = MimeTypeMap.getSingleton()
    
    /**
     * Gets MIME type from file extension.
     */
    fun getMimeTypeFromExtension(extension: String): String {
        val ext = extension.lowercase(Locale.getDefault()).trimStart('.')
        return mimeTypeMap.getMimeTypeFromExtension(ext) 
            ?: getMimeTypeFromExtensionManual(ext)
    }
    
    /**
     * Gets MIME type from file path.
     */
    fun getMimeTypeFromPath(filePath: String): String {
        val file = File(filePath)
        val extension = file.extension
        return getMimeTypeFromExtension(extension)
    }
    
    /**
     * Gets file extension from path.
     */
    fun getExtensionFromPath(filePath: String): String {
        val file = File(filePath)
        return file.extension.lowercase(Locale.getDefault())
    }
    
    /**
     * Checks if file type is supported.
     */
    fun isSupportedType(extension: String): Boolean {
        val ext = extension.lowercase(Locale.getDefault()).trimStart('.')
        return SUPPORTED_EXTENSIONS.contains(ext)
    }
    
    /**
     * Gets human-readable type name.
     */
    fun getReadableTypeName(extension: String): String {
        val ext = extension.lowercase(Locale.getDefault()).trimStart('.')
        return when (ext) {
            in IMAGE_EXTENSIONS -> "Image"
            in VIDEO_EXTENSIONS -> "Video"
            in AUDIO_EXTENSIONS -> "Audio"
            in DOCUMENT_EXTENSIONS -> "Document"
            in ARCHIVE_EXTENSIONS -> "Archive"
            "apk" -> "APK"
            else -> "File"
        }
    }
    
    /**
     * Manual MIME type mapping for common extensions.
     */
    private fun getMimeTypeFromExtensionManual(ext: String): String {
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "svg" -> "image/svg+xml"
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "avi" -> "video/x-msvideo"
            "webm" -> "video/webm"
            "mov" -> "video/quicktime"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            "flac" -> "audio/flac"
            "ogg" -> "audio/ogg"
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "zip" -> "application/zip"
            "rar" -> "application/vnd.rar"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            else -> "application/octet-stream"
        }
    }
    
    // Supported extensions
    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")
    private val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "avi", "webm", "mov", "flv")
    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")
    private val DOCUMENT_EXTENSIONS = setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp")
    private val ARCHIVE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "bz2")
    
    val SUPPORTED_EXTENSIONS = IMAGE_EXTENSIONS + VIDEO_EXTENSIONS + AUDIO_EXTENSIONS + 
                               DOCUMENT_EXTENSIONS + ARCHIVE_EXTENSIONS + setOf("apk", "txt")
}
