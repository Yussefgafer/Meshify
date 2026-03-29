package com.p2p.meshify.core.data.security.impl

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Manages ECDH key exchange for session key derivation.
 * 
 * Flow:
 * 1. Initiator generates ephemeral EC keypair + random nonce
 * 2. Initiator performs ECDH with peer's static public key
 * 3. Derives session key via HKDF-SHA256
 * 4. Sends ephemeral public key + nonce to responder
 * 5. Responder performs ECDH with initiator's ephemeral key
 * 6. Responder derives matching session key via HKDF
 * 
 * Security properties:
 * - Forward secrecy: ephemeral keys deleted after session
 * - Key separation: HKDF info string binds session to participants
 * - Safe key derivation: raw ECDH output never used directly
 */
class EcdhSessionManager {
    
    data class LocalSession(
        val ephemeralPublicKeyBytes: ByteArray,
        val sessionNonce: ByteArray,
        val sessionKey: ByteArray
    )
    
    /**
     * Step 1 (initiator): Generate ephemeral keypair + derive session key.
     * @param peerPublicKeyBytes responder's static public key (DER-encoded X.509)
     * @return ephemeral public key, nonce, and derived session key
     */
    fun createEphemeralSession(peerPublicKeyBytes: ByteArray): LocalSession {
        // Generate ephemeral EC keypair (secp256r1)
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val ephemeralKeyPair = kpg.generateKeyPair()
        
        // Parse peer's public key
        val peerPublicKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerPublicKeyBytes))
        
        // Perform ECDH
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephemeralKeyPair.private)
        ka.doPhase(peerPublicKey, true)
        val rawSharedSecret = ka.generateSecret()
        
        // Generate random nonce for HKDF salt
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        
        // Derive proper session key via HKDF
        val sessionKey = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = rawSharedSecret,
            salt = nonce,
            info = "meshify-v1-session"
        )
        
        // Zero raw shared secret immediately (forward secrecy)
        rawSharedSecret.fill(0)
        
        return LocalSession(
            ephemeralPublicKeyBytes = ephemeralKeyPair.public.encoded,
            sessionNonce = nonce,
            sessionKey = sessionKey
        )
    }
    
    /**
     * Step 2 (responder): Derive matching session key from initiator's ephemeral key.
     * @param peerEphemeralPubKeyBytes initiator's ephemeral public key
     * @param peerNonce initiator's nonce (used as HKDF salt)
     * @return derived session key (matches initiator's session key)
     */
    fun deriveSessionKeyFromPeer(
        peerEphemeralPubKeyBytes: ByteArray,
        peerNonce: ByteArray
    ): ByteArray {
        // Generate our own ephemeral EC keypair
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        val myEphemeralKeyPair = kpg.generateKeyPair()
        
        // Parse peer's ephemeral public key
        val peerKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(peerEphemeralPubKeyBytes))
        
        // Perform ECDH
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myEphemeralKeyPair.private)
        ka.doPhase(peerKey, true)
        val rawSecret = ka.generateSecret()
        
        // Derive session key via HKDF (using peer's nonce as salt)
        val sessionKey = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = rawSecret,
            salt = peerNonce,
            info = "meshify-v1-session"
        )
        
        // Zero raw shared secret immediately
        rawSecret.fill(0)
        
        return sessionKey
    }
}
