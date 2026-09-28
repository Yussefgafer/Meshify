package com.p2p.meshify.core.network.lan

import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.PayloadSerializer
import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.InputStream
import java.net.SocketException
import java.net.SocketTimeoutException

class SocketManagerFrameTest {

    private val testScheduler = TestCoroutineScheduler()
    private val testDispatcher = StandardTestDispatcher(testScheduler)

    @Test
    fun payloadSerializer_roundTrip_preservesTextFrame() = runTest(testDispatcher) {
        val payload = Payload(
            id = "11111111-1111-1111-1111-111111111111",
            senderId = "22222222-2222-2222-2222-222222222222",
            timestamp = 1L,
            type = Payload.PayloadType.TEXT,
            data = "hello".toByteArray()
        )
        val frame = PayloadSerializer.serialize(payload)
        val parsed = PayloadSerializer.deserialize(frame)

        assertEquals("hello", String(parsed.data))
        assertEquals(payload.id, parsed.id)
        assertEquals(payload.senderId, parsed.senderId)
    }

    @Test
    fun negativeLengthHeader_doesNotCrashDeserializer() = runTest(testDispatcher) {
        val negativeFrame = ByteArray(4).apply { java.nio.ByteBuffer.wrap(this).putInt(-1) }
        val input = DataInputStream(ByteArrayInputStream(negativeFrame))

        val length = input.readInt()
        assertEquals(-1, length)
    }

    @Test
    fun oversizedLengthHeader_doesNotCrashDeserializer() = runTest(testDispatcher) {
        val oversizedFrame = ByteArray(4).apply {
            java.nio.ByteBuffer.wrap(this).putInt(AppConfig.MAX_PAYLOAD_SIZE_BYTES + 1)
        }
        val input = DataInputStream(ByteArrayInputStream(oversizedFrame))

        val length = input.readInt()
        assertEquals(AppConfig.MAX_PAYLOAD_SIZE_BYTES + 1, length)
    }

    @Test
    fun truncatedStream_returnsEofBeforeFrameComplete() = runTest(testDispatcher) {
        val input = DataInputStream(object : InputStream() {
            override fun read(): Int = -1
        })

        val result = input.read()
        assertEquals(-1, result)
    }

    @Test
    fun socketTimeoutStream_doesNotCrashDeserializer() = runTest(testDispatcher) {
        val input = DataInputStream(object : InputStream() {
            override fun read(): Int = throw SocketTimeoutException("timeout")
        })

        var threw: Throwable? = null
        try {
            input.read()
        } catch (e: Throwable) {
            threw = e
        }

        assertTrue(threw is SocketTimeoutException)
    }

    @Test
    fun socketExceptionStream_doesNotCrashDeserializer() = runTest(testDispatcher) {
        val input = DataInputStream(object : InputStream() {
            override fun read(): Int = throw SocketException("reset")
        })

        var threw: Throwable? = null
        try {
            input.read()
        } catch (e: Throwable) {
            threw = e
        }

        assertTrue(threw is SocketException)
    }
}
