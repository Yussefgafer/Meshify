package com.p2p.meshify.domain.security.model

/**
 * Represents Out-Of-Band (OOB) verification data for identity verification.
 *
 * OOB verification is critical for preventing MITM attacks in the first session.
 * When two users establish an encrypted connection, they should verify each other's
 * identity through a separate channel (scanning QR, comparing SAS codes, or NFC tap).
 *
 * @property fingerprint SHA-256 hash of the user's public key (hex-encoded, 64 chars)
 * @property shortAuthString 6-character authentication string for visual comparison
 * @property qrCodeData JSON-encoded verification data for QR code generation
 * @property method The verification method used (QR, SAS, or NFC)
 * @property isVerified Whether the peer's identity has been successfully verified
 * @property verifiedAt Timestamp when verification occurred (epoch millis), null if not verified
 */
data class OobVerificationData(
    val fingerprint: String,
    val shortAuthString: String,
    val qrCodeData: String,
    val method: OobVerificationMethod,
    val isVerified: Boolean = false,
    val verifiedAt: Long? = null
) {
    companion object {
        /** Generate a 6-character Short Authentication String from a fingerprint */
        fun generateSas(fingerprint: String): String {
            require(fingerprint.length == 64) { "Fingerprint must be 64 hex chars" }
            // Take first 6 chars, convert to uppercase alphabetic only
            return fingerprint.take(6)
                .map { char ->
                    val digit = char.digitToInt(16)
                    // Map 0-15 to A-P (avoid confusing chars like O/0, I/1)
                    ('A' + digit).toString()
                }
                .joinToString("")
                .take(6)
        }

        /** Create QR code data JSON from fingerprint */
        fun createQrData(fingerprint: String, displayName: String): String {
            return """{"fp":"$fingerprint","name":"$displayName","v":1}"""
        }

        /** Parse QR code data into OobVerificationData */
        fun fromQrData(qrData: String, myFingerprint: String): OobVerificationData? {
            // Simple JSON parsing (no external library needed for this simple format)
            val fpMatch = Regex("\"fp\":\"([a-f0-9]{64})\"").find(qrData)
            val nameMatch = Regex("\"name\":\"([^\"]+)\"").find(qrData)

            if (fpMatch == null) return null

            val peerFingerprint = fpMatch.groupValues[1]

            return OobVerificationData(
                fingerprint = peerFingerprint,
                shortAuthString = generateSas(peerFingerprint),
                qrCodeData = qrData,
                method = OobVerificationMethod.QR,
                isVerified = false,
                verifiedAt = null
            )
        }

        /** Check if two fingerprints match (identity verification) */
        fun verifyFingerprint(expected: String, actual: String): Boolean {
            return expected.equals(actual, ignoreCase = true)
        }

        /** Check if two SAS codes match */
        fun verifySas(expected: String, actual: String): Boolean {
            return expected.equals(actual, ignoreCase = true)
        }
    }
}
