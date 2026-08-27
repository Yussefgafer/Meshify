package com.p2p.meshify.domain.repository

/**
 * Domain interface for file operations to decouple from Android Context.
 */
interface IFileManager {
    suspend fun saveMedia(fileName: String, data: ByteArray): String?

    /**
     * Copies [source] into a private staging directory (filesDir/staging) so a
     * message queued while its recipient is offline can still deliver the real
     * bytes on retry. Returns the staged file's absolute path, or null on failure.
     */
    suspend fun stageFile(fileName: String, source: java.io.File): String?

    /**
     * Same contract as [stageFile], for content that only exists in memory.
     */
    suspend fun stageBytes(fileName: String, data: ByteArray): String?

    companion object {
        const val STAGING_DIR_NAME = "staging"
    }
}
