package com.p2p.meshify.domain.security.interfaces

/**
 * Manages peer identity — generates and stores EC keypair in Android Keystore,
 * signs challenges, and verifies peer signatures.
 */
interface PeerIdentityRepository {
    /**
     * Initialize peer identity — generates EC keypair in Keystore if not exists.
     * @return peerId (Base64(SHA-256(DER-encoded public key)))
     */
    suspend fun initializeIdentity(): String
    
    /**
     * Get the current peer ID.
     * @throws IllegalStateException if identity not initialized
     */
    suspend fun getPeerId(): String
    
    /**
     * Get raw public key bytes (DER-encoded X.509).
     */
    suspend fun getPublicKeyBytes(): ByteArray
    
    /**
     * Get public key as hex string for fingerprint display.
     */
    suspend fun getPublicKeyHex(): String
    
    /**
     * Sign a challenge (e.g., handshake payload) with identity private key.
     * @param challenge data to sign
     * @return ECDSA signature (DER-encoded)
     */
    suspend fun signChallenge(challenge: ByteArray): ByteArray
    
    /**
     * Verify a peer's signature using their raw public key bytes.
     * @param publicKeyBytes peer's DER-encoded public key
     * @param challenge original challenge data
     * @param signature peer's ECDSA signature
     * @return true if signature is valid
     */
    suspend fun verifySignature(
        publicKeyBytes: ByteArray,
        challenge: ByteArray,
        signature: ByteArray
    ): Boolean
    
    /**
     * Reset identity — deletes keypair from Keystore.
     * Use with caution — user will get a new peerId.
     */
    suspend fun resetIdentity()
}
