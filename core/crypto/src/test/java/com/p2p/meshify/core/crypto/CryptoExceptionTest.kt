package com.p2p.meshify.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CryptoExceptionTest {

    @Test
    fun cryptoException_isException_andCarriesMessageAndCause() {
        val cause = IllegalStateException("cause")
        val ex = CryptoException("outer", cause)
        assertTrue(ex is Exception)
        assertEquals("outer", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun cryptoException_withoutCause_hasNullCause() {
        val ex = CryptoException("msg")
        assertEquals("msg", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun cryptoDecryptionException_isCryptoException() {
        val cause = RuntimeException("decrypt fail")
        val ex = CryptoDecryptionException("decrypt msg", cause)
        assertTrue(ex is CryptoException)
        assertTrue(ex is Exception)
        assertEquals("decrypt msg", ex.message)
        assertSame(cause, ex.cause)
    }

    @Test
    fun cryptoDecryptionException_withoutCause_hasNullCause() {
        val ex = CryptoDecryptionException("decrypt only")
        assertEquals("decrypt only", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun peerUnsupportedEncryptionException_defaultMessageIncludesPeerId() {
        val ex = PeerUnsupportedEncryptionException("peer-123")
        assertTrue(ex is CryptoException)
        assertEquals("peer-123", ex.peerId)
        assertEquals("Peer peer-123 does not support encryption", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun peerUnsupportedEncryptionException_customMessagePreserved() {
        val ex = PeerUnsupportedEncryptionException("peer-xyz", "custom")
        assertEquals("peer-xyz", ex.peerId)
        assertEquals("custom", ex.message)
    }

    @Test
    fun peerPublicKeyUnavailableException_defaultMessageIncludesPeerId() {
        val ex = PeerPublicKeyUnavailableException("peer-abc")
        assertTrue(ex is CryptoException)
        assertEquals("peer-abc", ex.peerId)
        assertEquals("Encryption key for peer peer-abc is not yet available", ex.message)
        assertNull(ex.cause)
    }

    @Test
    fun peerPublicKeyUnavailableException_customMessagePreserved() {
        val ex = PeerPublicKeyUnavailableException("peer-abc", "pending")
        assertEquals("pending", ex.message)
        assertEquals("peer-abc", ex.peerId)
    }

    @Test
    fun hierarchy_allCryptoExceptionsCanBeCaughtAsCryptoException() {
        val variants: List<CryptoException> = listOf(
            CryptoException("a"),
            CryptoDecryptionException("b"),
            PeerUnsupportedEncryptionException("p1"),
            PeerPublicKeyUnavailableException("p2")
        )
        variants.forEach { ex ->
            var caught = false
            try {
                throw ex
            } catch (e: CryptoException) {
                caught = true
                assertSame(ex, e)
            }
            assertTrue("should be catchable as CryptoException: $ex", caught)
        }
    }

    @Test
    fun cryptoException_causeChainingPreservedThroughHierarchy() {
        val root = IllegalArgumentException("root")
        val decryptEx = CryptoDecryptionException("wrap", root)
        val crypto: CryptoException = decryptEx
        assertSame(root, crypto.cause)
        assertTrue(crypto is CryptoDecryptionException)
    }
}
