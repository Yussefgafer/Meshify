package com.p2p.meshify.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EncryptResultTest {

    @Test
    fun success_equality_isContentBased() {
        val a = EncryptResult.Success(byteArrayOf(1, 2, 3))
        val b = EncryptResult.Success(byteArrayOf(1, 2, 3))
        val c = EncryptResult.Success(byteArrayOf(1, 2, 4))
        assertEquals(a, b)
        assertNotEquals(a, c)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun success_emptyArray_equality() {
        val a = EncryptResult.Success(ByteArray(0))
        val b = EncryptResult.Success(ByteArray(0))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun success_notEqualToDifferentVariant() {
        val s = EncryptResult.Success(byteArrayOf(1))
        assertFalse(s.equals(EncryptResult.PublicKeyUnavailable))
        assertFalse(s.equals(EncryptResult.PeerDoesNotSupportEncryption))
        assertFalse(s.equals("string"))
        assertFalse(s.equals(null))
    }

    @Test
    fun success_sameInstance_equalsItself() {
        val s = EncryptResult.Success(byteArrayOf(9, 9))
        assertEquals(s, s)
    }

    @Test
    fun dataObjects_areSingletons() {
        assertSame(EncryptResult.PublicKeyUnavailable, EncryptResult.PublicKeyUnavailable)
        assertSame(EncryptResult.PeerDoesNotSupportEncryption, EncryptResult.PeerDoesNotSupportEncryption)
        assertTrue(EncryptResult.PublicKeyUnavailable is EncryptResult)
        assertTrue(EncryptResult.PeerDoesNotSupportEncryption is EncryptResult)
        assertTrue(EncryptResult.Success(byteArrayOf(1)) is EncryptResult)
    }

    @Test
    fun sealedWhen_isExhaustive() {
        fun describe(r: EncryptResult): String = when (r) {
            is EncryptResult.Success -> "success:${r.ciphertext.size}"
            EncryptResult.PublicKeyUnavailable -> "unavailable"
            EncryptResult.PeerDoesNotSupportEncryption -> "unsupported"
        }
        assertEquals("success:2", describe(EncryptResult.Success(byteArrayOf(1, 2))))
        assertEquals("unavailable", describe(EncryptResult.PublicKeyUnavailable))
        assertEquals("unsupported", describe(EncryptResult.PeerDoesNotSupportEncryption))
    }
}
