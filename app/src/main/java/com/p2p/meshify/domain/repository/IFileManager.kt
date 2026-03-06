package com.p2p.meshify.domain.repository

/**
 * Domain interface for file operations to decouple from Android Context.
 */
interface IFileManager {
    suspend fun saveMedia(fileName: String, data: ByteArray): String?
    fun getAppVersion(): String
}
