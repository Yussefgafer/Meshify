package com.p2p.meshify.domain.security.model

/**
 * Methods for Out-Of-Band (OOB) identity verification.
 *
 * OOB verification prevents Man-In-The-Middle (MITM) attacks during the first session
 * by allowing users to verify their peer's identity through a separate communication channel.
 *
 * - QR: Scan peer's QR code containing their public key fingerprint
 * - SAS: Compare Short Authentication Strings (6-character codes)
 * - NFC: Tap devices together to exchange fingerprints via NFC
 */
enum class OobVerificationMethod {
    /** QR Code scanning — user scans peer's QR containing key fingerprint */
    QR,

    /** Short Authentication String comparison — user compares 6-char codes */
    SAS,

    /** Near Field Communication — devices exchange fingerprints via NFC tap */
    NFC
}
