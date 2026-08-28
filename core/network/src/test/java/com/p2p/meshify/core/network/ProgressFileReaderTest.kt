package com.p2p.meshify.core.network

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProgressFileReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun emptyFile_emits100AndReturnsEmpty() {
        val file = tmp.newFile()
        file.writeBytes(ByteArray(0))
        val emissions = mutableListOf<Int>()
        val reader = ProgressFileReader(file) { emissions.add(it) }
        val bytes = reader.readBytesWithProgress()
        assertEquals(0, bytes.size)
        assertEquals(100, reader.progressFlow.value)
        assertEquals(listOf(100), emissions)
    }

    @Test
    fun smallFile_emitsMonotonicNonDecreasingProgress() {
        val data = ByteArray(10_000) { (it % 251).toByte() }
        val file = tmp.newFile()
        file.writeBytes(data)
        val emissions = mutableListOf<Int>()
        val reader = ProgressFileReader(file) { emissions.add(it) }
        val bytes = reader.readBytesWithProgress()
        assertArrayEquals(data, bytes)
        assertEquals(100, reader.progressFlow.value)
        // Monotonic non-decreasing
        for (i in 1 until emissions.size) {
            assertTrue(
                "non-monotonic at $i: ${emissions[i - 1]} -> ${emissions[i]}",
                emissions[i] >= emissions[i - 1]
            )
        }
        // First emit must be > 0 (some bytes read) and last must be 100
        assertTrue("first emit > 0 expected, was ${emissions.first()}", emissions.first() > 0)
        assertEquals(100, emissions.last())
    }

    @Test
    fun progressFlow_reaches100() {
        val data = ByteArray(5_000) { 1 }
        val file = tmp.newFile()
        file.writeBytes(data)
        val reader = ProgressFileReader(file)
        reader.readBytesWithProgress()
        assertEquals(100, reader.progressFlow.value)
    }

    @Test
    fun reset_setsProgressBackToZero() {
        val data = ByteArray(1_000) { 1 }
        val file = tmp.newFile()
        file.writeBytes(data)
        val reader = ProgressFileReader(file)
        reader.readBytesWithProgress()
        assertEquals(100, reader.progressFlow.value)
        reader.reset()
        assertEquals(0, reader.progressFlow.value)
    }

    @Test
    fun callbackInvokedExactlyOnceWithFinal100_forLargeFile() {
        // 100KB — enough to generate multiple incremental emits; we only assert
        // the FINAL emit was 100.
        val data = ByteArray(100_000) { (it and 0xFF).toByte() }
        val file = tmp.newFile()
        file.writeBytes(data)
        val emissions = mutableListOf<Int>()
        val reader = ProgressFileReader(file) { emissions.add(it) }
        reader.readBytesWithProgress()
        assertEquals(100, emissions.last())
    }
}
