package com.p2p.meshify.core.common.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class HexUtilTest {

    @Test
    fun toHex_roundTripsPositiveBytes() {
        val bytes = byteArrayOf(1, 2, 10, 16)
        val hex = HexUtil.toHex(bytes)
        assertEquals("01020a10", hex)
        val decoded = with(HexUtil) { hex.hexToByteArray() }
        assertArrayEquals(bytes, decoded)
    }

    @Test
    fun hexToByteArray_emptyString_returnsEmptyArray() {
        val decoded = with(HexUtil) { "".hexToByteArray() }
        assertArrayEquals(byteArrayOf(), decoded)
    }

    @Test
    fun hexToByteArray_oddLength_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException::class.java) {
            with(HexUtil) { "abc".hexToByteArray() }
        }
    }

    @Test
    fun hexToByteArray_isCaseInsensitive() {
        val decoded = with(HexUtil) { "AB".hexToByteArray() }
        assertArrayEquals(byteArrayOf(0xAB.toByte()), decoded)
    }

    @Test
    fun hexToByteArray_decodesFfToNegativeByte() {
        val decoded = with(HexUtil) { "ff".hexToByteArray() }
        assertArrayEquals(byteArrayOf(0xff.toByte()), decoded)
    }

    @Test
    fun toHexPrefix_takesFirstNBytes() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        assertEquals("010203", HexUtil.toHexPrefix(bytes, 3))
        assertEquals("01020304", HexUtil.toHexPrefix(bytes))
    }

    @Test
    fun toFingerprint_uppercasesAndColonSeparates() {
        val bytes = byteArrayOf(10, 11, 12)
        assertEquals("0A:0B:0C", HexUtil.toFingerprint(bytes))
    }

    @Test
    fun toFingerprintSpaced_uppercasesAndSpaceSeparates() {
        val bytes = byteArrayOf(10, 11)
        assertEquals("0A 0B", HexUtil.toFingerprintSpaced(bytes))
    }
}
