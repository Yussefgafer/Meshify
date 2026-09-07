package com.p2p.meshify.core.network.ble

import android.content.Context
import android.bluetooth.BluetoothAdapter
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class BleScannerTest {

    private lateinit var scanner: BleScanner

    @Before
    fun setUp() {
        val context = mockk<Context>(relaxed = true)
        val adapter = mockk<BluetoothAdapter>(relaxed = true)
        scanner = BleScanner(context, adapter)
    }

    @Test
    fun extractPeerId_fullUuid_returnsTrimmedString() {
        // Full UUID branch requires >= 16 bytes
        val peerId = "peer-1234567890ab"
        val data = peerId.toByteArray(Charsets.UTF_8)

        val result = scanner.extractPeerId(data)

        assertEquals(peerId, result)
    }

    @Test
    fun extractPeerId_fullUuid_trimsWhitespace() {
        val data = "  peer-with-spaces-and-padding      \n".toByteArray(Charsets.UTF_8)

        val result = scanner.extractPeerId(data)

        assertEquals("peer-with-spaces-and-padding", result)
    }

    @Test
    fun extractPeerId_blankString_returnsNull() {
        val data = "                 ".toByteArray(Charsets.UTF_8)

        val result = scanner.extractPeerId(data)

        assertNull(result)
    }

    @Test
    fun extractPeerId_8ByteCompressed_returnsBleHex() {
        // 8 zero bytes keep the test independent of JVM byte-order mapping
        val data = ByteArray(8)

        val result = scanner.extractPeerId(data)

        assertNotNull(result)
        assertFalse(result.isNullOrEmpty())
        assert(result!!.startsWith("ble_"))
        assertEquals(16, result.length - 4)
    }

    @Test
    fun extractPeerId_shortArray_returnsNull() {
        val data = "short".toByteArray(Charsets.UTF_8)

        val result = scanner.extractPeerId(data)

        assertNull(result)
    }

    @Test
    fun extractPeerId_emptyArray_returnsNull() {
        val result = scanner.extractPeerId(ByteArray(0))
        assertNull(result)
    }

    @Test
    fun extractPeerId_exact16Bytes_fullUuid() {
        val peerId = "1234567890abcdef"
        val data = peerId.toByteArray(Charsets.UTF_8)

        val result = scanner.extractPeerId(data)

        assertEquals(peerId, result)
    }
}
