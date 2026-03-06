package com.p2p.meshify.core.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object FileUtils {
    /**
     * Reads bytes from a URI. 
     */
    fun getBytesFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { 
                it.readBytes()
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Saves ByteArray to a local file in app cache and returns the absolute path.
     */
    fun saveBytesToInternalStorage(context: Context, fileName: String, data: ByteArray): String? {
        return try {
            val dir = File(context.filesDir, "media")
            if (!dir.exists()) dir.mkdirs()
            
            val file = File(dir, fileName)
            FileOutputStream(file).use { 
                it.write(data)
            }
            file.absolutePath
        } catch (e: Exception) {
            Logger.e("FileUtils -> Save Failed", e)
            null
        }
    }
}
