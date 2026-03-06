package com.p2p.meshify.data.repository

import android.content.Context
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.repository.IFileManager

class FileManagerImpl(private val context: Context) : IFileManager {
    override suspend fun saveMedia(fileName: String, data: ByteArray): String? {
        return FileUtils.saveBytesToInternalStorage(context, fileName, data)
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
