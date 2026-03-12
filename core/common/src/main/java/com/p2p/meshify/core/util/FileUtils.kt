package com.p2p.meshify.core.util

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

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
            Log.e("FileUtils", "Failed to read bytes from URI", e)
            null
        }
    }

    /**
     * Calculates the SHA-256 hash of a ByteArray.
     * Used for content-addressable storage (avatars, media).
     */
    fun calculateHash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Saves ByteArray to a local file in app files directory under a specific category folder.
     * Uses internal storage for privacy and security.
     */
    fun saveBytesToInternalStorage(
        context: Context,
        fileName: String,
        data: ByteArray,
        category: String = "media"
    ): String? {
        return try {
            val dir = File(context.filesDir, category)
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, fileName)
            FileOutputStream(file).use {
                it.write(data)
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e("FileUtils", "Save Failed to $category/$fileName", e)
            null
        }
    }

    /**
     * Checks if a file exists in the specified category folder.
     */
    fun getFilePath(context: Context, fileName: String, category: String = "media"): String? {
        val file = File(File(context.filesDir, category), fileName)
        return if (file.exists()) file.absolutePath else null
    }
}
