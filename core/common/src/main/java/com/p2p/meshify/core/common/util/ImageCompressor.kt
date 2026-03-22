package com.p2p.meshify.core.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Image compression utility for reducing image size before sending.
 * 
 * Features:
 * - Resize to max dimensions
 * - Quality compression
 * - EXIF orientation preservation
 * - Target file size optimization
 */
object ImageCompressor {
    
    /**
     * Compression result.
     */
    data class CompressionResult(
        val bytes: ByteArray,
        val originalSize: Int,
        val compressedSize: Int,
        val compressionRatio: Double,
        val width: Int,
        val height: Int
    )
    
    /**
     * Compress image with smart optimization.
     *
     * @param imageBytes Original image bytes
     * @param maxSize Max dimension (width or height)
     * @param targetSizeKB Target file size in KB (0 = no target)
     * @return CompressionResult with compressed bytes
     */
    fun compress(
        imageBytes: ByteArray,
        maxSize: Int = 1920, // Full HD width
        targetSizeKB: Int = 500 // Target 500KB
    ): CompressionResult {
        // Decode bitmap bounds
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

        val originalWidth = options.outWidth
        val originalHeight = options.outHeight
        val originalSize = imageBytes.size

        // Calculate sample size
        val sampleSize = calculateSampleSize(originalWidth, originalHeight, maxSize)

        // Decode with sample size
        options.inJustDecodeBounds = false
        options.inSampleSize = sampleSize
        // ✅ CRITICAL FIX: Enable inMutable for better memory reuse
        options.inMutable = false

        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
            ?: return CompressionResult(imageBytes, originalSize, originalSize, 0.0, 0, 0)

        var orientedBitmap: Bitmap? = null
        var outputStream: ByteArrayOutputStream? = null

        return try {
            orientedBitmap = fixOrientation(bitmap, imageBytes)
            
            // ✅ CRITICAL FIX: Pre-calculate estimated output size to avoid excessive resizing
            // Estimate: width * height * 3 bytes / compression ratio
            val estimatedSize = (orientedBitmap.width * orientedBitmap.height * 3) / 10
            // Cap at 2MB initial size to prevent excessive memory allocation
            val initialBufferSize = estimatedSize.coerceAtMost(2 * 1024 * 1024)
            
            outputStream = ByteArrayOutputStream(initialBufferSize)

            // First pass: compress with 85% quality
            var quality = 85
            orientedBitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)

            // Second pass: reduce quality if needed to meet target size
            if (targetSizeKB > 0) {
                val targetBytes = targetSizeKB * 1024
                var currentSize = outputStream.size()

                while (currentSize > targetBytes && quality > 10) {
                    outputStream.reset()
                    quality -= 5
                    orientedBitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)
                    currentSize = outputStream.size()
                }
            }

            val compressedBytes = outputStream.toByteArray()
            val compressedSize = compressedBytes.size
            val compressionRatio = (1.0 - (compressedSize.toDouble() / originalSize)) * 100

            CompressionResult(
                bytes = compressedBytes,
                originalSize = originalSize,
                compressedSize = compressedSize,
                compressionRatio = compressionRatio,
                width = orientedBitmap.width,
                height = orientedBitmap.height
            )
        } finally {
            // ✅ CRITICAL FIX: Ensure memory is freed even in case of exception
            // Close output stream first
            outputStream?.close()

            // ✅ Recycle bitmaps in reverse order of creation
            // Recycle oriented bitmap first (if different from original)
            if (orientedBitmap != null && orientedBitmap != bitmap && !orientedBitmap.isRecycled) {
                orientedBitmap.recycle()
            }
            // Then recycle original bitmap
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }

            // ✅ REMOVED: System.gc() was causing 5-50ms stutters
            // Bitmap.recycle() is sufficient - GC will run naturally
        }
    }
    
    /**
     * Quick compress without target size.
     */
    fun compressQuick(
        imageBytes: ByteArray,
        maxSize: Int = 1280, // HD width
        quality: Int = 80
    ): CompressionResult {
        return compress(imageBytes, maxSize, 0)
    }
    
    /**
     * Calculate sample size for downscaling.
     */
    private fun calculateSampleSize(width: Int, height: Int, maxSize: Int): Int {
        if (width <= maxSize && height <= maxSize) {
            return 1
        }
        
        val sampleSize = max(1, max(width, height) / maxSize)
        
        // Round to power of 2 for better performance
        return Integer.highestOneBit(sampleSize)
    }
    
    /**
     * Fix image orientation from EXIF.
     */
    private fun fixOrientation(bitmap: Bitmap, imageBytes: ByteArray): Bitmap {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(imageBytes))
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
                else -> return bitmap
            }
            
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (e: Exception) {
            bitmap
        }
    }
    
    /**
     * Get image dimensions without loading full bitmap.
     */
    fun getImageDimensions(imageBytes: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)
        return options.outWidth to options.outHeight
    }
    
    /**
     * Estimate file size after compression.
     */
    fun estimateFileSize(width: Int, height: Int, quality: Int): Int {
        // Rough estimate: pixels * quality factor * format factor
        val pixels = width * height
        val qualityFactor = quality / 100.0
        val webpFactor = 0.3 // WebP is ~70% smaller than JPEG
        
        return (pixels * qualityFactor * webpFactor * 3).toInt() // 3 bytes per pixel
    }
}
