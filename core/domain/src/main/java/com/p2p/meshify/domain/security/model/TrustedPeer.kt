package com.p2p.meshify.domain.security.model

/**
 * Trusted peer stored in TOFU (Trust On First Use) store.
 * Contains peer's public key fingerprint and trust metadata.
 */
data class TrustedPeer(
    val peerId: String,              // SHA-256(pubKey) in Base64
    val displayName: String,
    val publicKeyHex: String,        // DER-encoded public key, hex
    val firstSeenAt: Long,           // Unix millis
    val lastSeenAt: Long,
    val trustLevel: TrustLevel,
    val oobVerified: Boolean = false // Was this verified via QR or SAS?
)

/**
 * Trust level for a peer.
 */
enum class TrustLevel {
    /** Never seen before — will become TOFU on first connection */
    UNKNOWN,
    
    /** Trusted on first use — user has not manually verified */
    TOFU,
    
    /** Out-of-band verified — user confirmed fingerprint via QR/SAS */
    OOB_VERIFIED,
    
    /** Explicitly rejected — user blocked this peer */
    REJECTED
}
