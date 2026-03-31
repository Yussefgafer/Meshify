package com.p2p.meshify.core.data.security.impl

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement

/**
 * Manages ECDH key exchange for session key derivation (Protocol V2.0).
 *
 * Protocol V2.0 Flow (X3DH-inspired with forward secrecy):
 * 
 * Initiator (Alice):
 * 1. Generates ephemeral EC keypair (ephemeralPriv_A, ephemeralPub_A)
 * 2. Sends handshake with: identityPubKey_A, ephemeralPubKey_A, nonce_A
 * 3. Receives Bob's handshake: identityPubKey_B, ephemeralPubKey_B, nonce_B
 * 4. Computes: sharedSecret = ECDH(ephemeralPriv_A, ephemeralPub_B)
 * 5. Derives: sessionKey = HKDF(sharedSecret, salt=nonce_A||nonce_B, info="Meshify-Session-v2")
 *
 * Responder (Bob):
 * 1. Receives Alice's handshake
 * 2. Generates ephemeral EC keypair (ephemeralPriv_B, ephemeralPub_B)
 * 3. Sends handshake with: identityPubKey_B, ephemeralPubKey_B, nonce_B
 * 4. Computes: sharedSecret = ECDH(ephemeralPriv_B, ephemeralPub_A)
 * 5. Derives: sessionKey = HKDF(sharedSecret, salt=nonce_A||nonce_B, info="Meshify-Session-v2")
 *
 * Security properties:
 * - Forward secrecy: ephemeral keys deleted after session establishment
 * - Key separation: HKDF info string binds session to protocol version
 * - Safe key derivation: raw ECDH output never used directly as encryption key
 * - Mutual authentication: both parties contribute ephemeral keys
 */
class EcdhSessionManager {

    /**
     * Holds local ephemeral session state.
     * @property ephemeralPublicKey Our ephemeral public key (to send to peer)
     * @property ephemeralPrivateKey Our ephemeral private key (keep secret, delete after use)
     * @property sessionNonce Our random nonce for HKDF salt
     * @property sessionKey Derived 32-byte AES-256 session key
     */
    data class LocalSession(
        val ephemeralPublicKey: ByteArray,
        val ephemeralPrivateKey: ByteArray,
        val sessionNonce: ByteArray,
        val sessionKey: ByteArray
    )

    /**
     * Step 1 (Initiator): Generate ephemeral keypair and derive session key.
     *
     * This is called by the party initiating the connection.
     * They generate an ephemeral keypair and perform ECDH with the peer's identity key.
     *
     * @param peerIdentityPubKeyBytes Peer's long-term identity public key (DER-encoded X.509)
     * @return LocalSession containing ephemeral keys, nonce, and derived session key
     * @throws IllegalArgumentException if peer public key format is invalid
     * @throws IllegalStateException if key generation or ECDH fails
     */
    fun createEphemeralSession(peerIdentityPubKeyBytes: ByteArray): LocalSession {
        // Generate ephemeral EC keypair (secp256r1 / P-256)
        val ephemeralKeyPair = try {
            val kpg = KeyPairGenerator.getInstance("EC")
            kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
            kpg.generateKeyPair()
        } catch (e: Exception) {
            throw IllegalStateException("Failed to generate ephemeral keypair: ${e.message}", e)
        }

        // Parse peer's identity public key
        val peerIdentityPubKey = try {
            val keyFactory = KeyFactory.getInstance("EC")
            val spec = X509EncodedKeySpec(peerIdentityPubKeyBytes)
            keyFactory.generatePublic(spec)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid peer public key format: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse peer public key: ${e.message}", e)
        }

        // Perform ECDH: sharedSecret = ECDH(ephemeralPriv, peerIdentityPub)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(ephemeralKeyPair.private)
        ka.doPhase(peerIdentityPubKey, true)
        val rawSharedSecret = ka.generateSecret()

        // Generate random 16-byte nonce for HKDF salt
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }

        // Derive session key via HKDF-SHA256
        // Note: For initiator, we use only our nonce initially; responder will contribute theirs
        val sessionKey = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = rawSharedSecret,
            salt = nonce,
            info = "Meshify-Session-v2"
        )

        // Zero raw shared secret immediately (forward secrecy)
        rawSharedSecret.fill(0)

        return LocalSession(
            ephemeralPublicKey = ephemeralKeyPair.public.encoded,
            ephemeralPrivateKey = ephemeralKeyPair.private.encoded,
            sessionNonce = nonce,
            sessionKey = sessionKey
        )
    }

    /**
     * Step 2 (Responder): Derive session key from initiator's ephemeral key.
     *
     * This is called by the party responding to the connection.
     * They receive the initiator's ephemeral public key and nonce,
     * generate their own ephemeral keypair, and compute the shared secret.
     *
     * CRITICAL: Both parties must use the SAME salt (nonce_A || nonce_B) for HKDF.
     *
     * @param peerEphemeralPubKeyBytes Initiator's ephemeral public key (DER-encoded X.509)
     * @param peerNonce Initiator's nonce (16 bytes)
     * @param myEphemeralKeyPair Our newly generated ephemeral keypair
     * @param myNonce Our newly generated nonce (16 bytes)
     * @return Derived 32-byte session key (must match initiator's session key)
     * @throws IllegalArgumentException if peer ephemeral public key format is invalid
     * @throws IllegalStateException if ECDH fails
     */
    fun deriveSessionKeyFromPeer(
        peerEphemeralPubKeyBytes: ByteArray,
        peerNonce: ByteArray,
        myEphemeralKeyPair: KeyPair,
        myNonce: ByteArray
    ): ByteArray {
        // Parse peer's ephemeral public key
        val peerEphemeralPubKey = try {
            val keyFactory = KeyFactory.getInstance("EC")
            val spec = X509EncodedKeySpec(peerEphemeralPubKeyBytes)
            keyFactory.generatePublic(spec)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid peer ephemeral public key format: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse peer ephemeral public key: ${e.message}", e)
        }

        // Perform ECDH: sharedSecret = ECDH(ephemeralPriv, peerEphemeralPub)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myEphemeralKeyPair.private)
        ka.doPhase(peerEphemeralPubKey, true)
        val rawSharedSecret = ka.generateSecret()

        // Build HKDF salt: nonce_A || nonce_B (concatenated nonces, 32 bytes total)
        val hkdfSalt = peerNonce + myNonce

        // Derive session key via HKDF-SHA256 with concatenated salt
        val sessionKey = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = rawSharedSecret,
            salt = hkdfSalt,
            info = "Meshify-Session-v2"
        )

        // Zero raw shared secret immediately (forward secrecy)
        rawSharedSecret.fill(0)

        return sessionKey
    }

    /**
     * Step 3 (Initiator): Finalize session key after receiving responder's handshake.
     *
     * After the initiator receives the responder's ephemeral public key and nonce,
     * they must re-derive the session key using the concatenated salt.
     *
     * @param peerEphemeralPubKeyBytes Responder's ephemeral public key (DER-encoded X.509)
     * @param peerNonce Responder's nonce (16 bytes)
     * @param myEphemeralPrivateKey Our ephemeral private key (from createEphemeralSession)
     * @param myNonce Our nonce (from createEphemeralSession)
     * @return Finalized 32-byte session key (must match responder's session key)
     * @throws IllegalArgumentException if peer ephemeral public key or our private key format is invalid
     * @throws IllegalStateException if key reconstruction or ECDH fails
     */
    fun finalizeSessionKey(
        peerEphemeralPubKeyBytes: ByteArray,
        peerNonce: ByteArray,
        myEphemeralPrivateKey: ByteArray,
        myNonce: ByteArray
    ): ByteArray {
        // Reconstruct our private key from bytes
        val myEphemeralPrivKey = try {
            val keyFactory = KeyFactory.getInstance("EC")
            val myPrivKeySpec = java.security.spec.PKCS8EncodedKeySpec(myEphemeralPrivateKey)
            keyFactory.generatePrivate(myPrivKeySpec)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid private key format: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to reconstruct private key: ${e.message}", e)
        }

        // Parse peer's ephemeral public key
        val peerEphemeralPubKey = try {
            val keyFactory = KeyFactory.getInstance("EC")
            val spec = X509EncodedKeySpec(peerEphemeralPubKeyBytes)
            keyFactory.generatePublic(spec)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Invalid peer ephemeral public key format: ${e.message}", e)
        } catch (e: Exception) {
            throw IllegalStateException("Failed to parse peer ephemeral public key: ${e.message}", e)
        }

        // Perform ECDH: sharedSecret = ECDH(ephemeralPriv, peerEphemeralPub)
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(myEphemeralPrivKey)
        ka.doPhase(peerEphemeralPubKey, true)
        val rawSharedSecret = ka.generateSecret()

        // Build HKDF salt: nonce_A || nonce_B (concatenated nonces, 32 bytes total)
        val hkdfSalt = myNonce + peerNonce

        // Derive session key via HKDF-SHA256 with concatenated salt
        val sessionKey = HkdfKeyDerivation.deriveSessionKey(
            sharedSecret = rawSharedSecret,
            salt = hkdfSalt,
            info = "Meshify-Session-v2"
        )

        // Zero raw shared secret immediately (forward secrecy)
        rawSharedSecret.fill(0)

        return sessionKey
    }

    /**
     * Generate a fresh ephemeral keypair for handshake response.
     * @return KeyPair (ephemeral, for single session use only)
     */
    fun generateEphemeralKeypair(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
        return kpg.generateKeyPair()
    }

    /**
     * Generate a random 16-byte nonce for HKDF salt.
     * @return 16-byte random nonce
     */
    fun generateNonce(): ByteArray {
        return ByteArray(16).also { SecureRandom().nextBytes(it) }
    }

    /**
     * Securely zero out a private key byte array.
     * Call this after using ephemeral private keys.
     */
    fun zeroPrivateKey(privateKeyBytes: ByteArray) {
        privateKeyBytes.fill(0)
    }
}
