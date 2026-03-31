package com.p2p.meshify.core.network

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream

/**
 * File reader that emits upload progress via StateFlow and/or callback.
 * Used to track file upload progress in the UI.
 *
 * This class reads a file in chunks and emits progress updates.
 * It's designed to work with the existing Payload-based transport system.
 *
 * Progress is emitted as integers from 0 to 100, representing percentage complete.
 * Only emits when progress changes by >= 1% to avoid excessive updates.
 */
class ProgressFileReader(
    private val file: File,
    private val progressCallback: ((Int) -> Unit)? = null
) {

    private val _progressFlow = MutableStateFlow(0)
    val progressFlow: StateFlow<Int> = _progressFlow.asStateFlow()

    /**
     * Reads the entire file and returns the bytes.
     * Progress is emitted via progressFlow and progressCallback during reading.
     */
    fun readBytesWithProgress(): ByteArray {
        val fileSize = file.length()
        if (fileSize == 0L) {
            _progressFlow.value = 100
            progressCallback?.invoke(100)
            return ByteArray(0)
        }

        val buffer = ByteArray(BUFFER_SIZE)
        val outputStream = java.io.ByteArrayOutputStream(fileSize.toInt())
        var uploaded: Long = 0
        var lastEmittedProgress = -1

        FileInputStream(file).use { inputStream ->
            while (true) {
                val read = inputStream.read(buffer)
                if (read == -1) break

                outputStream.write(buffer, 0, read)
                uploaded += read

                val progress = calculateProgress(uploaded, fileSize)
                lastEmittedProgress = emitProgress(progress, lastEmittedProgress, _progressFlow, progressCallback)
            }
        }

        // Ensure we reach 100%
        _progressFlow.value = 100
        progressCallback?.invoke(100)

        return outputStream.toByteArray()
    }

    /**
     * Resets the progress to 0 for reuse.
     */
    fun reset() {
        _progressFlow.value = 0
    }

    companion object {
        private const val BUFFER_SIZE = 8192 // 8KB - balances smoothness vs performance

        /**
         * Calculates the upload progress as a percentage.
         *
         * @param uploaded The number of bytes uploaded so far
         * @param total The total file size in bytes
         * @return Progress percentage (0-100)
         */
        private fun calculateProgress(uploaded: Long, total: Long): Int {
            return ((uploaded / total.toDouble()) * 100).toInt()
        }

        /**
         * Emits progress update if the progress has increased.
         * Only emits when progress changes by >= 1% to avoid excessive updates.
         *
         * @param progress The current progress percentage
         * @param lastEmittedProgress The last emitted progress percentage
         * @param progressFlow The StateFlow to emit progress updates to
         * @param progressCallback Optional callback to invoke with progress updates
         * @return The updated last emitted progress value
         */
        private fun emitProgress(
            progress: Int,
            lastEmittedProgress: Int,
            progressFlow: MutableStateFlow<Int>,
            progressCallback: ((Int) -> Unit)?
        ): Int {
            if (progress > lastEmittedProgress) {
                progressFlow.value = progress
                progressCallback?.invoke(progress)
                return progress
            }
            return lastEmittedProgress
        }
    }
}
