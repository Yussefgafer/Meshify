package com.p2p.meshify.core.data.repository.interfaces

import com.p2p.meshify.domain.model.Payload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import com.p2p.meshify.domain.security.model.SecurityEvent

/**
 * Service interface for processing incoming payloads from peers.
 * Handles decryption, command processing, reactions, and handshake flows.
 */
interface IPayloadProcessingService {

    /**
     * Flow of security events that should be shown to the user.
     */
    val securityEvents: SharedFlow<SecurityEvent>

    /**
     * Process an incoming payload from a peer.
     * Dispatches to the appropriate handler based on payload type.
     *
     * @param peerId the source peer's ID
     * @param payload the payload to process
     */
    suspend fun processPayload(peerId: String, payload: Payload)

    /**
     * Handle a system command payload (e.g., ACK messages).
     *
     * @param peerId the source peer's ID
     * @param payload the payload containing the command
     */
    suspend fun handleSystemCommand(peerId: String, payload: Payload)

    /**
     * Handle a delete request payload.
     *
     * @param peerId the source peer's ID
     * @param payload the payload containing the delete request
     */
    suspend fun handleDeleteRequest(peerId: String, payload: Payload)

    /**
     * Handle a reaction payload.
     *
     * @param peerId the source peer's ID
     * @param payload the payload containing the reaction update
     */
    suspend fun handleReaction(peerId: String, payload: Payload)

    /**
     * Handle an encrypted message payload.
     * Decrypts, validates, saves, and sends ACK.
     *
     * @param peerId the source peer's ID
     * @param payload the payload containing encrypted message bytes
     */
    suspend fun handleEncryptedMessage(peerId: String, payload: Payload)

    /**
     * Handle a handshake payload.
     * Establishes encrypted session via ECDH.
     *
     * @param peerId the source peer's ID
     * @param payload the payload containing handshake data
     */
    suspend fun handleHandshake(peerId: String, payload: Payload)

    /**
     * Send a system command to a peer.
     *
     * @param peerId the destination peer's ID
     * @param command the command string to send
     */
    suspend fun sendSystemCommand(peerId: String, command: String)

    /**
     * Retry pending messages for a peer.
     *
     * @param peerId the destination peer's ID
     * @return Result of retry operation
     */
    suspend fun retryPendingMessages(peerId: String): Result<Unit>
}
