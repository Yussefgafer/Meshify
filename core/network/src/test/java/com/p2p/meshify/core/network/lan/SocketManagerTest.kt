package com.p2p.meshify.core.network.lan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for SocketManager — safe tests that avoid startListening()
 * (which blocks on ServerSocket.accept and cannot be unit-tested without
 * refactoring the accept loop into a coroutine-friendly design).
 *
 * Integration testing of the full listen→receive path is covered by
 * feature:real-device-testing on real hardware.
 */
class SocketManagerTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun stopListening_beforeStart_doesNotThrow() = runTest {
        val manager = SocketManager(ioDispatcher = testDispatcher)
        manager.stopListening()
    }

    @Test
    fun stopListening_calledTwice_isIdempotent() = runTest {
        val manager = SocketManager(ioDispatcher = testDispatcher)
        manager.stopListening()
        manager.stopListening()
    }

    @Test
    fun registerKnownPeer_isNoOp() = runTest {
        val manager = SocketManager(ioDispatcher = testDispatcher)
        manager.registerKnownPeer("peer1", "192.168.1.100")
        manager.registerKnownPeer("peer2", "192.168.1.101")
    }

    @Test
    fun setKeepAliveSenderId_doesNotThrow() = runTest {
        val manager = SocketManager(ioDispatcher = testDispatcher)
        manager.setKeepAliveSenderId("test_device_id")
    }
}
