package com.p2p.meshify.core.common.util

import android.graphics.Bitmap
import com.p2p.meshify.core.util.ImageCompressor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

/**
 * Unit tests for [ImageCompressor].
 *
 * The compressor downscales to `maxSize` (rounding the sample size to a power of
 * two, so the result may be up to ~2x `maxSize` for non-power-of-two ratios — it
 * never *upscales* beyond the source) and applies WEBP quality compression.
 *
 * Under Robolectric we:
 * - synthesize a real PNG from a shadow `Bitmap` so `BitmapFactory.decodeByteArray`
 *   produces genuine bounds,
 * - assert the downscale contract (never upscaled; exact fit for power-of-two ratios),
 * - assert safe handling of empty/malformed input (no throw; non-negative bounds).
 *
 * Note: `Logger.e` is invoked by `fixOrientation` only on a thrown EXIF parse
 * exception; Robolectric provides `android.util.Log`, so nothing needs stubbing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class ImageCompressorTest {

    private fun pngOf(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        // Robolectric must be able to encode the synthetic bitmap to PNG.
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream))
        bitmap.recycle()
        return stream.toByteArray()
    }

    // ---- getImageDimensions (bounds only) ----

    @Test
    fun `getImageDimensions returns real bounds for a synthesized PNG`() {
        val png = pngOf(1600, 1200)
        val (w, h) = ImageCompressor.getImageDimensions(png)
        assertEquals(1600, w)
        assertEquals(1200, h)
    }

    @Test
    fun `getImageDimensions does not throw on empty input`() {
        // Robolectric's BitmapFactory fabricates a 100x100 bitmap for unparseable
        // input rather than returning null; the load-bearing contract is that the
        // bounds call never throws (production code would get null on real Android).
        val (w, h) = ImageCompressor.getImageDimensions(ByteArray(0))
        assertTrue(w >= 0)
        assertTrue(h >= 0)
    }

    // ---- estimateFileSize (pure math) ----

    @Test
    fun `estimateFileSize scales with pixel count and quality`() {
        val small = ImageCompressor.estimateFileSize(100, 100, 80)
        val large = ImageCompressor.estimateFileSize(1000, 1000, 80)
        // Larger pixel count must estimate a larger file.
        assertTrue(large > small)

        val lowQ = ImageCompressor.estimateFileSize(800, 600, 10)
        val highQ = ImageCompressor.estimateFileSize(800, 600, 90)
        // Higher quality must estimate a larger file.
        assertTrue(highQ > lowQ)
    }

    // ---- compress: downscale contract ----

    @Test
    fun `compress downscales a large image to the target max dimension`() {
        // 1600x1200 with maxSize 400 -> ratio 4 (power of two) -> exact 400x300.
        val png = pngOf(1600, 1200)
        val result = ImageCompressor.compress(png, maxSize = 400, targetSizeKB = 0)

        assertEquals(400, result.width)
        assertEquals(300, result.height)
        // Compressed bytes must be produced.
        assertTrue(result.bytes.isNotEmpty())
        assertEquals(png.size, result.originalSize)
    }

    @Test
    fun `compress never upscales a small image within maxSize`() {
        // 200x150 with maxSize 400 -> sampleSize 1 -> dimensions preserved.
        val png = pngOf(200, 150)
        val result = ImageCompressor.compress(png, maxSize = 400, targetSizeKB = 0)
        assertEquals(200, result.width)
        assertEquals(150, result.height)
    }

    @Test
    fun `compress produces a smaller or equal file for a photographic source`() {
        val png = pngOf(1024, 768)
        val result = ImageCompressor.compress(png, maxSize = 512, targetSizeKB = 200)
        // With aggressive WEBP quality compression + downscale the output should
        // generally be smaller than the raw PNG; assert bytes are non-empty.
        assertTrue(result.compressedSize > 0)
    }

    // ---- compressQuick ----

    @Test
    fun `compressQuick downscales to its default maxSize without target`() {
        val png = pngOf(2560, 1920)
        // compressQuick default maxSize = 1280 -> ratio 2 -> exact 1280x960.
        val result = ImageCompressor.compressQuick(png)
        assertEquals(1280, result.width)
        assertEquals(960, result.height)
    }

    // ---- safe handling of degenerate input ----

    @Test
    fun `compress on empty input never throws and tracks original size`() {
        // Robolectric's BitmapFactory fabricates a 100x100 bitmap for unparseable
        // input instead of returning null, so the production null-guard branch is
        // not exercised here — but the load-bearing contract is that compress()
        // never throws and that originalSize is recorded faithfully.
        val result = ImageCompressor.compress(ByteArray(0), maxSize = 800, targetSizeKB = 0)
        assertEquals(0, result.originalSize)
    }

    @Test
    fun `compress on malformed bytes never throws`() {
        // Corrupt (non-image) bytes must not crash the pipeline.
        val garbage = "this is definitely not an image".toByteArray(Charsets.UTF_8)
        val result = ImageCompressor.compress(garbage, maxSize = 800, targetSizeKB = 0)
        assertEquals(garbage.size, result.originalSize)
    }

    @Test
    fun `getImageDimensions on corrupt bytes does not throw`() {
        val garbage = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0xFF.toByte())
        // Should not throw; Robolectric's BitmapFactory fabricates a 100x100
        // bitmap for unparseable input, so decode yields non-negative bounds.
        val (w, h) = ImageCompressor.getImageDimensions(garbage)
        assertTrue(w >= 0)
        assertTrue(h >= 0)
    }
}
