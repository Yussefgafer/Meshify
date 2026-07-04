package com.p2p.meshify.domain.security.model

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for OobVerificationMethod enum.
 */
class OobVerificationMethodTest {

    @Test
    fun `OobVerificationMethod contains all expected values`() {
        val expected = listOf(
            OobVerificationMethod.QR,
            OobVerificationMethod.SAS,
            OobVerificationMethod.NFC
        )

        assertEquals(expected.size, OobVerificationMethod.values().size)
        assertTrue(OobVerificationMethod.values().toList().containsAll(expected))
    }

    @Test
    fun `OobVerificationMethod values have unique names`() {
        val names = OobVerificationMethod.values().map { it.name }
        assertEquals(names.toSet().size, names.size)
    }

    @Test
    fun `QR has ordinal 0`() {
        assertEquals(0, OobVerificationMethod.QR.ordinal)
    }

    @Test
    fun `SAS has ordinal 1`() {
        assertEquals(1, OobVerificationMethod.SAS.ordinal)
    }

    @Test
    fun `NFC has ordinal 2`() {
        assertEquals(2, OobVerificationMethod.NFC.ordinal)
    }

    @Test
    fun `valueOf returns QR for QR`() {
        assertEquals(OobVerificationMethod.QR, OobVerificationMethod.valueOf("QR"))
    }

    @Test
    fun `valueOf returns SAS for SAS`() {
        assertEquals(OobVerificationMethod.SAS, OobVerificationMethod.valueOf("SAS"))
    }

    @Test
    fun `valueOf returns NFC for NFC`() {
        assertEquals(OobVerificationMethod.NFC, OobVerificationMethod.valueOf("NFC"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf throws for invalid name`() {
        OobVerificationMethod.valueOf("INVALID")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `valueOf throws for lowercase name`() {
        OobVerificationMethod.valueOf("qr")
    }

    @Test
    fun `QR name returns QR`() {
        assertEquals("QR", OobVerificationMethod.QR.name)
    }

    @Test
    fun `SAS name returns SAS`() {
        assertEquals("SAS", OobVerificationMethod.SAS.name)
    }

    @Test
    fun `NFC name returns NFC`() {
        assertEquals("NFC", OobVerificationMethod.NFC.name)
    }
}
