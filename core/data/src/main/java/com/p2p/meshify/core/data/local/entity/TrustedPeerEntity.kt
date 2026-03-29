package com.p2p.meshify.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.p2p.meshify.domain.security.model.TrustLevel

/**
 * Trusted peer stored in TOFU (Trust On First Use) database.
 * Contains peer's public key fingerprint and trust metadata.
 */
@Entity(tableName = "trusted_peers")
data class TrustedPeerEntity(
    @PrimaryKey val peerId: String,              // SHA-256(pubKey) in Base64
    val displayName: String,
    val publicKeyHex: String,                    // DER-encoded public key, hex
    val firstSeenAt: Long,                       // Unix millis
    val lastSeenAt: Long,
    val trustLevel: String,                      // TrustLevel enum ordinal
    val oobVerified: Boolean = false             // Was this verified via QR/SAS?
) {
    fun toDomainModel(): com.p2p.meshify.domain.security.model.TrustedPeer {
        return com.p2p.meshify.domain.security.model.TrustedPeer(
            peerId = peerId,
            displayName = displayName,
            publicKeyHex = publicKeyHex,
            firstSeenAt = firstSeenAt,
            lastSeenAt = lastSeenAt,
            trustLevel = TrustLevel.valueOf(trustLevel),
            oobVerified = oobVerified
        )
    }
    
    companion object {
        fun fromDomainModel(model: com.p2p.meshify.domain.security.model.TrustedPeer): TrustedPeerEntity {
            return TrustedPeerEntity(
                peerId = model.peerId,
                displayName = model.displayName,
                publicKeyHex = model.publicKeyHex,
                firstSeenAt = model.firstSeenAt,
                lastSeenAt = model.lastSeenAt,
                trustLevel = model.trustLevel.name,
                oobVerified = model.oobVerified
            )
        }
    }
}
