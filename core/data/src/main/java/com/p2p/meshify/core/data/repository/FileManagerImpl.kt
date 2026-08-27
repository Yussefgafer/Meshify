package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.util.Logger
import java.util.UUID
import com.p2p.meshify.domain.repository.IFileManager
import java.io.File
import java.io.FileOutputStream

/**
 * Implementation of IFileManager using Android Context.
 */
class FileManagerImpl(private val context: Context) : IFileManager {

    private val mediaDir: File
    private val stagingDir: File

    init {
        mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
        stagingDir = File(context.filesDir, IFileManager.STAGING_DIR_NAME)
        if (!stagingDir.exists()) {
            stagingDir.mkdirs()
        }
    }

    override suspend fun saveMedia(fileName: String, data: ByteArray): String? {
        return try {
            val safeName = sanitizeFileName(fileName)
            val mediaFile = File(mediaDir, safeName)

            // Defense in depth: never resolve outside mediaDir.
            val mediaDirPath = mediaDir.canonicalPath + File.separator
            if (!mediaFile.canonicalPath.startsWith(mediaDirPath)) {
                Logger.e("FileManagerImpl -> Rejected unsafe file name: $fileName")
                return null
            }

            FileOutputStream(mediaFile).use {
                it.write(data)
            }
            mediaFile.absolutePath
        } catch (e: Exception) {
            Logger.e("FileManagerImpl -> Save Failed: $fileName", e)
            null
        }
    }

    /**
     * Copies [source] into the private staging directory so an offline-queued
     * message can deliver real bytes on retry. Caller supplies a unique
     * [fileName] (typically "<messageId>.<ext>").
     */
    override suspend fun stageFile(fileName: String, source: File): String? {
        return try {
            val stagedFile = resolveStagingTarget(fileName)
            source.copyTo(stagedFile, overwrite = true)
            stagedFile.absolutePath
        } catch (e: Exception) {
            Logger.e("FileManagerImpl -> Stage Failed: $fileName", e)
            null
        }
    }

    override suspend fun stageBytes(fileName: String, data: ByteArray): String? {
        return try {
            val stagedFile = resolveStagingTarget(fileName)
            FileOutputStream(stagedFile).use {
                it.write(data)
            }
            stagedFile.absolutePath
        } catch (e: Exception) {
            Logger.e("FileManagerImpl -> Stage Failed: $fileName", e)
            null
        }
    }

    /**
     * Defense in depth: never resolve outside stagingDir.
     */
    private fun resolveStagingTarget(fileName: String): File {
        if (!stagingDir.exists()) {
            stagingDir.mkdirs()
        }
        val stagedFile = File(stagingDir, sanitizeFileName(fileName))
        val stagingDirPath = stagingDir.canonicalPath + File.separator
        if (!stagedFile.canonicalPath.startsWith(stagingDirPath)) {
            throw IllegalArgumentException("Unsafe staged file name: $fileName")
        }
        return stagedFile
    }

    /**
     * Strips path separators and parent-directory segments from [fileName].
     * If anything changed (or the result would be empty), falls back to a
     * random UUID name preserving the original extension.
     */
    private fun sanitizeFileName(fileName: String): String {
        val cleaned = fileName.replace("/", "").replace("\\", "").replace("..", "")
        return if (cleaned == fileName && cleaned.isNotBlank()) {
            cleaned
        } else {
            // Extension derived from the sanitized text so it cannot reintroduce separators.
            val idx = cleaned.lastIndexOf('.')
            val ext = if (idx >= 0) cleaned.substring(idx) else ""
            UUID.randomUUID().toString() + ext
        }
    }

}
