package com.p2p.meshify.core.data.repository.impl

import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.core.common.util.HexUtil.hexToByteArray
import com.p2p.meshify.core.data.security.impl.EcdhSessionManager
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.data.repository.interfaces.ISessionManagementService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Implementation of session key management via ECDH handshake.
 * Handles both initiator and responder flows.
 *
 * Simplified: removed PeerIdentityRepository — identity public key is
 * no longer included in handshakes or session key derivation.
 */
class SessionManagementServiceImpl(
    private val settingsRepository: ISettingsRepository,
    private val messageCrypto: com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto,
    private val ecdhSessionManager: EcdhSessionManager,
    private val sessionKeyStore: EncryptedSessionKeyStore,
    private val transportManager: TransportManager
) : ISessionManagementService {

    override suspend fun getOrEstablishSessionKey(peerId: String): EncryptedSessionKeyStore.SessionKeyInfo? {
        val handshakeSent = sessionMutex.withLock {
            sessionKeyStore.getSessionKey(peerId)?.let {
                Logger.d("SessionManagementService", "Using cached session key for ${peerId.take(8)}...")
                return@withLock true
            }

            Logger.d("SessionManagementService", "No session for ${peerId.take(8)}... - triggering handshake")

            val myId = settingsRepository.getDeviceId()
            val displayName = withContext(Dispatchers.IO) {
                settingsRepository.displayName.firstOrNull() ?: "Unknown"
            }
            val avatarHash = withContext(Dispatchers.IO) {
                settingsRepository.avatarHash.firstOrNull()
            }

            val ephemeralKeypair = ecdhSessionManager.generateEphemeralKeypair()
            val ephemeralPubKeyHex = com.p2p.meshify.core.common.util.HexUtil.toHex(ephemeralKeypair.public.encoded)
            val nonce = ecdhSessionManager.generateNonce()
            val nonceHex = com.p2p.meshify.core.common.util.HexUtil.toHex(nonce)

            val handshake = Handshake(
                version = 2,
                name = displayName,
                avatarHash = avatarHash,
                identityPubKeyHex = null, // No identity key in simplified protocol
                ephemeralPubKeyHex = ephemeralPubKeyHex,
                nonceHex = nonceHex,
                timestamp = System.currentTimeMillis()
            )

            val handshakePayload = Payload(
                senderId = myId,
                type = Payload.PayloadType.HANDSHAKE,
                data = Json.encodeToString(handshake).toByteArray()
            )

            val transport = transportManager.selectBestTransport(peerId).firstOrNull()
            if (transport == null) {
                Logger.e("No transport available for handshake with ${peerId.take(8)}...", tag = "SessionManagementService")
                return@withLock false
            }

            val sendResult = transport.sendPayload(peerId, handshakePayload)
            if (sendResult.isFailure) {
                Logger.e("Failed to send handshake to ${peerId.take(8)}...: ${sendResult.exceptionOrNull()?.message}", tag = "SessionManagementService")
                return@withLock false
            }

            Logger.d("SessionManagementService", "Handshake sent to ${peerId.take(8)}... - waiting for response")
            true
        }

        if (!handshakeSent) return null

        return waitForSessionEstablishment(peerId)
    }

    /**
     * Wait for the peer's handshake response to establish a session.
     *
     * After sending our handshake, we must wait for the peer to respond. Their response
     * triggers `establishSessionFromHandshake()` which stores the session key.
     * Since EncryptedSessionKeyStore has no reactive flow, we poll with exponential
     * backoff instead of fixed intervals to reduce CPU wake-ups and battery drain.
     */
    private suspend fun waitForSessionEstablishment(peerId: String): EncryptedSessionKeyStore.SessionKeyInfo? {
        return withTimeoutOrNull(5000L) {
            var delayMs = 50L
            val maxDelayMs = 500L

            repeat(20) { attempt ->
                delay(delayMs)
                sessionKeyStore.getSessionKey(peerId)?.let { session ->
                    Logger.d("SessionManagementService", "Session established with ${peerId.take(8)}... via handshake")
                    return@withTimeoutOrNull session
                }
                // Exponential backoff with ceiling to avoid excessive polling
                delayMs = kotlin.math.min(delayMs * 2, maxDelayMs)
            }
            null
        }
    }

    override suspend fun establishSessionFromHandshake(
        peerId: String,
        peerEphemeralPubKeyHex: String,
        peerNonceHex: String
    ): Boolean {
        try {
            val peerEphemeralPubKeyBytes = peerEphemeralPubKeyHex.hexToByteArray()
            val peerNonce = peerNonceHex.hexToByteArray()

            val myEphemeralKeyPair = ecdhSessionManager.generateEphemeralKeypair()
            val myNonce = ecdhSessionManager.generateNonce()

            val sessionKey = ecdhSessionManager.deriveSessionKeyFromPeer(
                peerEphemeralPubKeyBytes = peerEphemeralPubKeyBytes,
                peerNonce = peerNonce,
                myEphemeralKeyPair = myEphemeralKeyPair,
                myNonce = myNonce
            )

            sessionKeyStore.putSessionKey(peerId, sessionKey, "")
            ecdhSessionManager.zeroPrivateKey(myEphemeralKeyPair.private.encoded)

            Logger.d("SessionManagementService", "Session established with peer ${peerId.take(8)}... (responder)")
            return true
        } catch (e: Exception) {
            Logger.e("Failed to establish session with ${peerId.take(8)}...", e, "SessionManagementService")
            return false
        }
    }

    override suspend fun initiateSession(peerId: String): EcdhSessionManager.LocalSession? {
        try {
            val session = ecdhSessionManager.createEphemeralSession()
            return session
        } catch (e: Exception) {
            Logger.e("Failed to initiate session with ${peerId.take(8)}...", e, "SessionManagementService")
            return null
        }
    }

    override suspend fun finalizeSession(
        peerId: String,
        peerEphemeralPubKeyHex: String,
        peerNonceHex: String,
        myEphemeralPrivateKey: ByteArray,
        myNonce: ByteArray
    ): Boolean {
        try {
            val peerEphemeralPubKeyBytes = peerEphemeralPubKeyHex.hexToByteArray()
            val peerNonce = peerNonceHex.hexToByteArray()

            val sessionKey = ecdhSessionManager.finalizeSessionKey(
                peerEphemeralPubKeyBytes = peerEphemeralPubKeyBytes,
                peerNonce = peerNonce,
                myEphemeralPrivateKey = myEphemeralPrivateKey,
                myNonce = myNonce
            )

            sessionKeyStore.putSessionKey(peerId, sessionKey, "")
            ecdhSessionManager.zeroPrivateKey(myEphemeralPrivateKey)

            Logger.d("SessionManagementService", "Session finalized with peer ${peerId.take(8)}... (initiator)")
            return true
        } catch (e: Exception) {
            Logger.e("Failed to finalize session with ${peerId.take(8)}...", e, "SessionManagementService")
            return false
        }
    }

    private val sessionMutex = Mutex()
}
