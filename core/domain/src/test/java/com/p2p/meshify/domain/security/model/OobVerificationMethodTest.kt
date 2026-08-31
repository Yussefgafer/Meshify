package com.p2p.meshify.domain.security.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [OobVerificationMethod].
 *
 * The three out-of-band verification methods (QR / SAS / NFC) are the full set
 * used to prevent MITM during the first session. This suite pins the enum set
 * and name stability (the names are referenced by UI/screens and must not
 * silently change).
 */
class OobVerificationMethodTest {

    @Test
    fun `method set is exactly QR SAS NFC`() {
        val methods = OobVerificationMethod.values()
        assertEquals(3, methods.size)
        assertEquals(OobVerificationMethod.QR, methods[0])
        assertEquals(OobVerificationMethod.SAS, methods[1])
        assertEquals(OobVerificationMethod.NFC, methods[2])
    }

    @Test
    fun `each method has a non-empty stable name`() {
        OobVerificationMethod.values().forEach {
            assertTrue("expected non-empty name for $it", it.name.isNotEmpty())
        }
        assertEquals("QR", OobVerificationMethod.QR.name)
        assertEquals("SAS", OobVerificationMethod.SAS.name)
        assertEquals("NFC", OobVerificationMethod.NFC.name)
    }
}
