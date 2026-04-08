package com.p2p.meshify.core.data.repository.interfaces

import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore

/**
 * Service interface for ECDH session key management.
 * Handles session establishment, validation, and key lifecycle.
 */
interface ISessionManagementService {

    /**
     * Get existing session key or establish new session via ECDH handshake.
     *
     * @param peerId the peer's ID
     * @return SessionKeyInfo if session exists or was established, null on failure
     */
    suspend fun getOrEstablishSessionKey(peerId: String): EncryptedSessionKeyStore.SessionKeyInfo?

    /**
     * Establish session key from peer's handshake (responder flow).
     *
     * @param peerId the peer's ID
     * @param peerIdentityPubKeyHex peer's long-term identity public key (hex)
     * @param peerEphemeralPubKeyHex peer's ephemeral session public key (hex)
     * @param peerNonceHex peer's nonce (hex)
     * @return true if session was established successfully, false on TOFU violation
     */
    suspend fun establishSessionFromHandshake(
        peerId: String,
        peerIdentityPubKeyHex: String,
        peerEphemeralPubKeyHex: String,
        peerNonceHex: String
    ): Boolean

    /**
     * Initiate session with peer (initiator flow).
     *
     * @param peerId the peer's ID
     * @return LocalSession containing ephemeral keys and nonce, null on failure
     */
    suspend fun initiateSession(peerId: String): com.p2p.meshify.core.data.security.impl.EcdhSessionManager.LocalSession?

    /**
     * Finalize session key after receiving peer's handshake response.
     *
     * @param peerId the peer's ID
     * @param peerEphemeralPubKeyHex peer's ephemeral public key (hex)
     * @param peerNonceHex peer's nonce (hex)
     * @param myEphemeralPrivateKey our ephemeral private key
     * @param myNonce our nonce
     * @param peerIdentityPubKeyHex peer's identity public key (for TOFU)
     * @return true if session finalized successfully
     */
    suspend fun finalizeSession(
        peerId: String,
        peerEphemeralPubKeyHex: String,
        peerNonceHex: String,
        myEphemeralPrivateKey: ByteArray,
        myNonce: ByteArray,
        peerIdentityPubKeyHex: String
    ): Boolean
}
