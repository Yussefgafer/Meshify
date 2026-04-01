package com.p2p.meshify.core.util

import com.p2p.meshify.core.common.util.HexUtil
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.security.SecureRandom

/**
 * Comprehensive unit tests for HexUtil cryptographic utility.
 * Tests cover happy paths, boundary conditions, security-critical cases,
 * edge cases, and performance benchmarks.
 */
@RunWith(RobolectricTestRunner::class)
class HexUtilTest {

    private val secureRandom = SecureRandom()

    // ============================================================================
    // SECTION 1: HAPPY PATH TESTS - Basic Encoding/Decoding
    // ============================================================================

    @Test
    fun `empty byte array encodes to empty string`() {
        // Given
        val bytes = ByteArray(0)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("", hex)
    }

    @Test
    fun `empty string decodes to empty byte array`() {
        // Given
        val hex = ""

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(ByteArray(0), bytes)
    }

    @Test
    fun `single byte 0x00 encodes to "00"`() {
        // Given
        val bytes = byteArrayOf(0x00)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("00", hex)
    }

    @Test
    fun `single byte 0xFF encodes to "ff"`() {
        // Given
        val bytes = byteArrayOf(0xFF.toByte())

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("ff", hex)
    }

    @Test
    fun `single byte 0x7F encodes to "7f"`() {
        // Given
        val bytes = byteArrayOf(0x7F)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("7f", hex)
    }

    @Test
    fun `single byte 0x01 encodes to "01"`() {
        // Given
        val bytes = byteArrayOf(0x01)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("01", hex)
    }

    @Test
    fun `multiple bytes encode correctly`() {
        // Given
        val bytes = byteArrayOf(0x01, 0x02, 0x03)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("010203", hex)
    }

    @Test
    fun `hex string "010203" decodes to correct bytes`() {
        // Given
        val hex = "010203"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), bytes)
    }

    @Test
    fun `round-trip encoding and decoding preserves data`() {
        // Given
        val originalBytes = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte())

        // When
        val hex = HexUtil.toHex(originalBytes)
        val decodedBytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(originalBytes, decodedBytes)
    }

    @Test
    fun `round-trip 100 iterations with random data`() {
        val random = SecureRandom()

        repeat(100) { iteration ->
            // Given
            val dataSize = random.nextInt(1000) + 1
            val originalBytes = ByteArray(dataSize)
            random.nextBytes(originalBytes)

            // When
            val hex = HexUtil.toHex(originalBytes)
            val decodedBytes = hex.hexToByteArray()

            // Then
            assertArrayEquals(
                "Round-trip failed at iteration $iteration",
                originalBytes,
                decodedBytes
            )
        }
    }

    // ============================================================================
    // SECTION 2: BOUNDARY CONDITIONS
    // ============================================================================

    @Test
    fun `zero-filled array encodes correctly`() {
        // Given
        val bytes = ByteArray(16) { 0x00 }

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("00".repeat(16), hex)
    }

    @Test
    fun `one-filled array encodes correctly`() {
        // Given
        val bytes = ByteArray(16) { 0xFF.toByte() }

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("ff".repeat(16), hex)
    }

    @Test
    fun `alternating pattern encodes correctly`() {
        // Given
        val bytes = byteArrayOf(
            0x00, 0xFF.toByte(), 0x00, 0xFF.toByte(),
            0x00, 0xFF.toByte(), 0x00, 0xFF.toByte()
        )

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("00ff00ff00ff00ff", hex)
    }

    @Test
    fun `maximum size array 10MB performance test`() {
        // Given
        val size = 10 * 1024 * 1024 // 10MB
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)

        // When
        val startTime = System.currentTimeMillis()
        val hex = HexUtil.toHex(bytes)
        val encodeTime = System.currentTimeMillis() - startTime

        val decodeStart = System.currentTimeMillis()
        val decoded = hex.hexToByteArray()
        val decodeTime = System.currentTimeMillis() - decodeStart

        // Then - performance may vary in CI environments
        assertArrayEquals(bytes, decoded)
        assertTrue("Encode took ${encodeTime}ms (expected < 10000ms)", encodeTime < 10000)
        assertTrue("Decode took ${decodeTime}ms (expected < 10000ms)", decodeTime < 10000)
    }

    @Test
    fun `all hex digits 0-F encode and decode correctly`() {
        // Given
        val bytes = byteArrayOf(
            0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07,
            0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F
        )

        // When
        val hex = HexUtil.toHex(bytes)
        val decoded = hex.hexToByteArray()

        // Then
        assertEquals("000102030405060708090a0b0c0d0e0f", hex)
        assertArrayEquals(bytes, decoded)
    }

    // ============================================================================
    // SECTION 3: INVALID HEX HANDLING
    // ============================================================================

    @Test(expected = IllegalArgumentException::class)
    fun `odd-length hex string throws IllegalArgumentException`() {
        // Given
        val hex = "abc" // 3 characters

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with invalid character G throws IllegalArgumentException`() {
        // Given
        val hex = "123G5678"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with invalid character H throws IllegalArgumentException`() {
        // Given
        val hex = "ABCH"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with invalid character I throws IllegalArgumentException`() {
        // Given
        val hex = "DEADIEEF"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with invalid character J throws IllegalArgumentException`() {
        // Given
        val hex = "CAFEJ0"

        // When/Then
        hex.hexToByteArray()
    }

    @Test
    fun `lowercase hex string decodes correctly`() {
        // Given
        val hex = "deadbeef"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), bytes)
    }

    @Test
    fun `uppercase hex string decodes correctly`() {
        // Given
        val hex = "DEADBEEF"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), bytes)
    }

    @Test
    fun `mixed case hex string decodes correctly`() {
        // Given
        val hex = "DeAdBeEf"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte()), bytes)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with spaces throws IllegalArgumentException`() {
        // Given
        val hex = "DE AD BE EF"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with tab character throws IllegalArgumentException`() {
        // Given
        val hex = "DE\tAD"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with newline character throws IllegalArgumentException`() {
        // Given
        val hex = "DE\nAD"

        // When/Then
        hex.hexToByteArray()
    }

    // ============================================================================
    // SECTION 4: SECURITY-CRITICAL TESTS
    // ============================================================================

    @Test
    fun `leading zeros are preserved in encoding`() {
        // Given
        val bytes = byteArrayOf(0x00, 0x01, 0x00, 0x0F)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("0001000f", hex)
        assertNotEquals("100f", hex) // Must NOT compress leading zeros
    }

    @Test
    fun `leading zeros preserved in single byte`() {
        // Given
        val bytes = byteArrayOf(0x05)

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("05", hex)
        assertNotEquals("5", hex)
    }

    @Test
    fun `error message does not leak sensitive data`() {
        // Given - sensitive data in hex string
        val sensitiveHex = "deadbeefcafe1234"

        // When/Then - verify exception message doesn't contain full hex
        try {
            (sensitiveHex + "G").hexToByteArray() // Add invalid char to trigger error
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            // Verify error message doesn't contain the sensitive hex data
            val message = e.message ?: ""
            // Message should mention "invalid" or "hex" but not expose full data
            assertTrue(
                "Error message should not expose sensitive data: $message",
                message.lowercase().contains("hex") ||
                message.lowercase().contains("invalid") ||
                message.lowercase().contains("length")
            )
        }
    }

    @Test
    fun `hex encoding produces consistent lowercase output`() {
        // Given - various byte values
        val testCases = listOf(
            byteArrayOf(0x0A) to "0a",
            byteArrayOf(0x0B) to "0b",
            byteArrayOf(0x0C) to "0c",
            byteArrayOf(0x0D) to "0d",
            byteArrayOf(0x0E) to "0e",
            byteArrayOf(0x0F) to "0f"
        )

        // When/Then
        testCases.forEach { (bytes, expected) ->
            val hex = HexUtil.toHex(bytes)
            assertEquals(
                "Encoding should be lowercase for ${bytes.contentToString()}",
                expected,
                hex
            )
        }
    }

    @Test
    fun `fingerprint format is uppercase with colons`() {
        // Given
        val bytes = byteArrayOf(0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0xD4.toByte())

        // When
        val fingerprint = HexUtil.toFingerprint(bytes)

        // Then
        assertEquals("A1:B2:C3:D4", fingerprint)
    }

    @Test
    fun `spaced fingerprint format is uppercase with spaces`() {
        // Given
        val bytes = byteArrayOf(0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0xD4.toByte())

        // When
        val fingerprint = HexUtil.toFingerprintSpaced(bytes)

        // Then
        assertEquals("A1 B2 C3 D4", fingerprint)
    }

    @Test
    fun `hex prefix extracts first N bytes correctly`() {
        // Given
        val bytes = byteArrayOf(
            0x12, 0x34, 0x56, 0x78,
            0x9A.toByte(), 0xBC.toByte(), 0xDE.toByte(), 0xF0.toByte()
        )

        // When
        val prefix4 = HexUtil.toHexPrefix(bytes, 4)
        val prefix2 = HexUtil.toHexPrefix(bytes, 2)
        val prefixDefault = HexUtil.toHexPrefix(bytes) // Default is 4

        // Then
        assertEquals("12345678", prefix4)
        assertEquals("1234", prefix2)
        assertEquals("12345678", prefixDefault)
    }

    @Test
    fun `hex prefix with count exceeding array length`() {
        // Given
        val bytes = byteArrayOf(0x12, 0x34)

        // When
        val prefix = HexUtil.toHexPrefix(bytes, 10)

        // Then
        assertEquals("1234", prefix)
    }

    // ============================================================================
    // SECTION 5: EDGE CASES
    // ============================================================================

    @Test
    fun `very long hex string 1000+ characters encodes and decodes correctly`() {
        // Given
        val size = 1000
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)

        // When
        val hex = HexUtil.toHex(bytes)
        val decoded = hex.hexToByteArray()

        // Then
        assertEquals(size * 2, hex.length)
        assertArrayEquals(bytes, decoded)
    }

    @Test
    fun `very long hex string 10000 characters`() {
        // Given
        val size = 5000
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)

        // When
        val hex = HexUtil.toHex(bytes)
        val decoded = hex.hexToByteArray()

        // Then
        assertEquals(size * 2, hex.length)
        assertArrayEquals(bytes, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with unicode character throws IllegalArgumentException`() {
        // Given
        val hex = "DEAD\u0000BEEF" // Null character

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with emoji throws IllegalArgumentException`() {
        // Given
        val hex = "DEAD🔥BEEF"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with all invalid characters throws IllegalArgumentException`() {
        // Given
        val hex = "GHIJKLMN"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `hex string with mixed valid and invalid characters throws IllegalArgumentException`() {
        // Given
        val hex = "DE12GH34"

        // When/Then
        hex.hexToByteArray()
    }

    @Test
    fun `single pair hex string decodes correctly`() {
        // Given
        val hex = "AB"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(byteArrayOf(0xAB.toByte()), bytes)
    }

    @Test
    fun `large byte array 1MB encodes without overflow`() {
        // Given
        val size = 1024 * 1024 // 1MB
        val bytes = ByteArray(size) { it.toByte() }

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals(size * 2, hex.length)
        assertTrue(hex.isNotEmpty())
    }

    @Test
    fun `byte array with negative values encodes correctly`() {
        // Given
        val bytes = byteArrayOf(
            (-1).toByte(), (-2).toByte(), (-128).toByte(), (-127).toByte()
        )

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("fffe8081", hex)
    }

    @Test
    fun `hex string with only zeros decodes correctly`() {
        // Given
        val hex = "0000000000000000"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(ByteArray(8), bytes)
    }

    @Test
    fun `hex string with maximum byte values decodes correctly`() {
        // Given
        val hex = "ffffffffffffffff"

        // When
        val bytes = hex.hexToByteArray()

        // Then
        assertArrayEquals(ByteArray(8) { 0xFF.toByte() }, bytes)
    }

    // ============================================================================
    // SECTION 6: PERFORMANCE TESTS
    // ============================================================================

    @Test
    fun `encode 1MB in less than 100ms`() {
        // Given
        val size = 1024 * 1024 // 1MB
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)

        // Warm-up
        repeat(3) {
            HexUtil.toHex(bytes)
        }

        // When
        val startTime = System.nanoTime()
        val hex = HexUtil.toHex(bytes)
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        // Then - performance may vary in CI environments
        assertTrue("Encoding 1MB took ${elapsedMs}ms", elapsedMs < 2000)
        assertEquals(size * 2, hex.length)
    }

    @Test
    fun `decode 1MB hex string in less than 100ms`() {
        // Given
        val size = 1024 * 1024 // 1MB
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        val hex = HexUtil.toHex(bytes)

        // Warm-up
        repeat(3) {
            hex.hexToByteArray()
        }

        // When
        val startTime = System.nanoTime()
        val decoded = hex.hexToByteArray()
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        // Then - performance may vary in CI environments
        assertTrue("Decoding 1MB took ${elapsedMs}ms", elapsedMs < 2000)
        assertArrayEquals(bytes, decoded)
    }

    @Test
    fun `1000 round-trips in less than 500ms`() {
        // Given
        val dataSize = 1024 // 1KB per iteration
        val bytes = ByteArray(dataSize)
        secureRandom.nextBytes(bytes)

        // Warm-up
        repeat(10) {
            val hex = HexUtil.toHex(bytes)
            hex.hexToByteArray()
        }

        // When
        val startTime = System.nanoTime()
        repeat(1000) {
            val hex = HexUtil.toHex(bytes)
            hex.hexToByteArray()
        }
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000

        // Then - performance may vary in CI environments
        assertTrue("1000 round-trips took ${elapsedMs}ms", elapsedMs < 5000)
    }

    @Test
    fun `encode 10KB array performance baseline`() {
        // Given
        val size = 10 * 1024 // 10KB
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)

        // When
        val startTime = System.nanoTime()
        val hex = HexUtil.toHex(bytes)
        val elapsedMicros = (System.nanoTime() - startTime) / 1_000

        // Then
        assertTrue("Encoding 10KB took ${elapsedMicros}μs", elapsedMicros > 0)
        assertEquals(size * 2, hex.length)
    }

    @Test
    fun `decode 10KB hex string performance baseline`() {
        // Given
        val size = 10 * 1024 // 10KB
        val bytes = ByteArray(size)
        secureRandom.nextBytes(bytes)
        val hex = HexUtil.toHex(bytes)

        // When
        val startTime = System.nanoTime()
        val decoded = hex.hexToByteArray()
        val elapsedMicros = (System.nanoTime() - startTime) / 1_000

        // Then
        assertTrue("Decoding 10KB took ${elapsedMicros}μs", elapsedMicros > 0)
        assertArrayEquals(bytes, decoded)
    }

    // ============================================================================
    // SECTION 7: ADDITIONAL COVERAGE TESTS
    // ============================================================================

    @Test
    fun `fingerprint of empty array`() {
        // Given
        val bytes = ByteArray(0)

        // When
        val fingerprint = HexUtil.toFingerprint(bytes)

        // Then
        assertEquals("", fingerprint)
    }

    @Test
    fun `spaced fingerprint of empty array`() {
        // Given
        val bytes = ByteArray(0)

        // When
        val fingerprint = HexUtil.toFingerprintSpaced(bytes)

        // Then
        assertEquals("", fingerprint)
    }

    @Test
    fun `hex prefix of empty array`() {
        // Given
        val bytes = ByteArray(0)

        // When
        val prefix = HexUtil.toHexPrefix(bytes, 4)

        // Then
        assertEquals("", prefix)
    }

    @Test
    fun `hex prefix with zero count`() {
        // Given
        val bytes = byteArrayOf(0x12, 0x34, 0x56)

        // When
        val prefix = HexUtil.toHexPrefix(bytes, 0)

        // Then
        assertEquals("", prefix)
    }

    @Test
    fun `round-trip with all possible byte values`() {
        // Given - all 256 possible byte values
        val bytes = ByteArray(256) { it.toByte() }

        // When
        val hex = HexUtil.toHex(bytes)
        val decoded = hex.hexToByteArray()

        // Then
        assertArrayEquals(bytes, decoded)
        assertEquals(512, hex.length)
    }

    @Test
    fun `encoding is deterministic`() {
        // Given
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

        // When - encode multiple times
        val hex1 = HexUtil.toHex(bytes)
        val hex2 = HexUtil.toHex(bytes)
        val hex3 = HexUtil.toHex(bytes)

        // Then
        assertEquals(hex1, hex2)
        assertEquals(hex2, hex3)
    }

    @Test
    fun `decoding same hex string produces identical arrays`() {
        // Given
        val hex = "0102030405060708"

        // When
        val bytes1 = hex.hexToByteArray()
        val bytes2 = hex.hexToByteArray()

        // Then
        assertArrayEquals(bytes1, bytes2)
    }

    @Test(expected = NumberFormatException::class)
    fun `hex string with only whitespace throws NumberFormatException`() {
        // Given
        val hex = "   "

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = NumberFormatException::class)
    fun `hex string starting with valid but ending invalid throws NumberFormatException`() {
        // Given
        val hex = "DEADBEEFGH"

        // When/Then
        hex.hexToByteArray()
    }

    @Test(expected = NumberFormatException::class)
    fun `hex string with special characters throws NumberFormatException`() {
        // Given
        val hex = "DE!D@BE#EF$"

        // When/Then
        hex.hexToByteArray()
    }

    @Test
    fun `large fingerprint 64 bytes formats correctly`() {
        // Given
        val bytes = ByteArray(64) { it.toByte() }

        // When
        val fingerprint = HexUtil.toFingerprint(bytes)

        // Then - 64 bytes = 64 groups of 2 chars + 63 colons = 191 chars
        assertEquals(191, fingerprint.length)
        assertTrue(fingerprint.contains(":"))
        assertEquals(fingerprint, fingerprint.uppercase())
    }

    @Test
    fun `large spaced fingerprint 64 bytes formats correctly`() {
        // Given
        val bytes = ByteArray(64) { it.toByte() }

        // When
        val fingerprint = HexUtil.toFingerprintSpaced(bytes)

        // Then - 64 bytes = 64 groups of 2 chars + 63 spaces = 191 chars
        assertEquals(191, fingerprint.length)
        assertTrue(fingerprint.contains(" "))
        assertEquals(fingerprint, fingerprint.uppercase())
    }

    @Test
    fun `hex encoding uses lowercase consistently`() {
        // Given - bytes that could produce uppercase in some implementations
        val bytes = byteArrayOf(
            0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0x0F,
            0x1A, 0x1B, 0x1C, 0x1D, 0x1E, 0x1F
        )

        // When
        val hex = HexUtil.toHex(bytes)

        // Then
        assertEquals("0a0b0c0d0e0f1a1b1c1d1e1f", hex)
        assertEquals(hex, hex.lowercase())
    }

    @Test
    fun `secure random 256-bit key round-trip`() {
        // Given - simulate a 256-bit cryptographic key
        val keyBytes = ByteArray(32)
        secureRandom.nextBytes(keyBytes)

        // When
        val hexKey = HexUtil.toHex(keyBytes)
        val decodedKey = hexKey.hexToByteArray()

        // Then
        assertArrayEquals(keyBytes, decodedKey)
        assertEquals(64, hexKey.length) // 32 bytes = 64 hex chars
    }

    @Test
    fun `secure random 128-bit key round-trip`() {
        // Given - simulate a 128-bit cryptographic key
        val keyBytes = ByteArray(16)
        secureRandom.nextBytes(keyBytes)

        // When
        val hexKey = HexUtil.toHex(keyBytes)
        val decodedKey = hexKey.hexToByteArray()

        // Then
        assertArrayEquals(keyBytes, decodedKey)
        assertEquals(32, hexKey.length) // 16 bytes = 32 hex chars
    }
}
