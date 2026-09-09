package com.p2p.meshify.core.network.lan

import io.mockk.mockk
import java.net.Socket
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocketFactoryTest {

    private val factory = SocketFactory()

    @org.junit.Before
    fun setUp() {
        io.mockk.mockkStatic(android.util.Log::class)
        io.mockk.every { android.util.Log.e(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.e(any<String>(), any<String>(), any()) } returns 0
        io.mockk.every { android.util.Log.w(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.w(any<String>(), any<String>(), any()) } returns 0
        io.mockk.every { android.util.Log.d(any<String>(), any<String>()) } returns 0
        io.mockk.every { android.util.Log.i(any<String>(), any<String>()) } returns 0
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(android.util.Log::class)
    }

    @Test
    fun isSocketValid_null_returnsFalse() {
        assertFalse(factory.isSocketValid(null))
    }

    @Test
    fun isSocketValid_disconnectedSocket_returnsFalse() {
        val sock = mockk<Socket>(relaxed = true)
        io.mockk.every { sock.isConnected } returns false
        io.mockk.every { sock.isClosed } returns false
        assertFalse(factory.isSocketValid(sock))
    }

    @Test
    fun isSocketValid_closedSocket_returnsFalse() {
        val sock = mockk<Socket>(relaxed = true)
        io.mockk.every { sock.isConnected } returns true
        io.mockk.every { sock.isClosed } returns true
        assertFalse(factory.isSocketValid(sock))
    }

    @Test
    fun isSocketValid_connectedAndOpen_returnsTrue() {
        val sock = mockk<Socket>(relaxed = true)
        io.mockk.every { sock.isConnected } returns true
        io.mockk.every { sock.isClosed } returns false
        assertTrue(factory.isSocketValid(sock))
    }

    @Test
    fun closeSocket_null_doesNotThrow() {
        factory.closeSocket(null)
    }

    @Test
    fun closeSocket_whenCloseThrows_doesNotThrow() {
        val sock = mockk<Socket>(relaxed = true)
        io.mockk.every { sock.isInputShutdown } returns false
        io.mockk.every { sock.isOutputShutdown } returns false
        io.mockk.every { sock.isClosed } returns false
        io.mockk.every { sock.shutdownInput() } throws RuntimeException("fail")
        io.mockk.every { sock.shutdownOutput() } throws RuntimeException("fail")
        io.mockk.every { sock.close() } throws RuntimeException("fail")
        factory.closeSocket(sock)
    }

    @Test
    fun closeSocket_alreadyClosed_skipsClose() {
        val sock = mockk<Socket>(relaxed = true)
        io.mockk.every { sock.isInputShutdown } returns true
        io.mockk.every { sock.isOutputShutdown } returns true
        io.mockk.every { sock.isClosed } returns true
        factory.closeSocket(sock)
        io.mockk.verify(exactly = 0) { sock.close() }
    }

    @Test
    fun configureSocket_setsKeepAliveAndTimeout() {
        val sock = mockk<Socket>(relaxed = true)
        factory.configureSocket(sock, readTimeout = 5000L)
        io.mockk.verify { sock.soTimeout = 5000 }
        io.mockk.verify { sock.keepAlive = true }
    }

    @Test
    fun configureSocket_defaultTimeout() {
        val sock = mockk<Socket>(relaxed = true)
        factory.configureSocket(sock)
        io.mockk.verify { sock.keepAlive = true }
    }

    @Test
    fun createServerSocket_invalidPort_throws() {
        var threw = false
        try {
            factory.createServerSocket(-1)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun createClientSocket_unreachable_throws() {
        var threw = false
        try {
            factory.createClientSocket("192.0.2.1", port = 19999)
        } catch (e: Exception) {
            threw = true
        }
        assertTrue(threw)
    }
}
