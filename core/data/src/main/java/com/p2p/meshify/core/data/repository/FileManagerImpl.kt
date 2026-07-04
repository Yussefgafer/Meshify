package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.repository.IFileManager
import java.io.File
import java.io.FileOutputStream

/**
 * Implementation of IFileManager using Android Context.
 */
class FileManagerImpl(private val context: Context) : IFileManager {

    private val mediaDir: File

    init {
        mediaDir = File(context.filesDir, "media")
        if (!mediaDir.exists()) {
            mediaDir.mkdirs()
        }
    }

    override suspend fun saveMedia(fileName: String, data: ByteArray): String? {
        return try {
            val mediaFile = File(mediaDir, fileName)
            FileOutputStream(mediaFile).use {
                it.write(data)
            }
            mediaFile.absolutePath
        } catch (e: Exception) {
            Logger.e("FileManagerImpl -> Save Failed: $fileName", e)
            null
        }
    }

    override fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }
}
