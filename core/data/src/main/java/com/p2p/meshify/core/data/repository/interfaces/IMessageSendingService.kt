package com.p2p.meshify.core.data.repository.interfaces

import com.p2p.meshify.domain.model.MessageType
import java.io.File

/**
 * Service interface for sending messages to peers.
 * Handles text, media, grouped attachments, and file uploads with progress.
 */
interface IMessageSendingService {

    /**
     * Send an encrypted text message to a peer.
     *
     * @param peerId destination peer ID
     * @param peerName destination peer name
     * @param text plaintext message content
     * @param replyToId optional reply-to message ID
     * @return Result of send operation
     */
    suspend fun sendMessage(
        peerId: String,
        peerName: String,
        text: String,
        replyToId: String?
    ): Result<Unit>

    /**
     * Send an image message to a peer.
     *
     * @param peerId destination peer ID
     * @param peerName destination peer name
     * @param imageBytes raw image bytes
     * @param extension file extension
     * @param replyToId optional reply-to message ID
     * @return Result of send operation
     */
    suspend fun sendImage(
        peerId: String,
        peerName: String,
        imageBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit>

    /**
     * Send a video message to a peer.
     *
     * @param peerId destination peer ID
     * @param peerName destination peer name
     * @param videoBytes raw video bytes
     * @param extension file extension
     * @param replyToId optional reply-to message ID
     * @return Result of send operation
     */
    suspend fun sendVideo(
        peerId: String,
        peerName: String,
        videoBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit>

    /**
     * Send a grouped message with multiple attachments (album).
     *
     * @param peerId destination peer ID
     * @param peerName destination peer name
     * @param caption album caption
     * @attachments list of (bytes, type) pairs
     * @param replyToId optional reply-to message ID
     * @return Result of send operation
     */
    suspend fun sendGroupedMessage(
        peerId: String,
        peerName: String,
        caption: String,
        attachments: List<Pair<ByteArray, MessageType>>,
        replyToId: String?
    ): Result<Unit>

    /**
     * Send a file with progress tracking.
     *
     * @param messageId unique ID for this message
     * @param peerId destination peer ID
     * @param peerName destination peer name
     * @param file the file to send
     * @param fileType type of file
     * @param caption optional caption
     * @param replyToId optional reply-to message ID
     * @param progressCallback optional progress callback (0-100)
     * @return Result of send operation
     */
    suspend fun sendFileWithProgress(
        messageId: String,
        peerId: String,
        peerName: String,
        file: File,
        fileType: MessageType,
        caption: String,
        replyToId: String?,
        progressCallback: ((Int) -> Unit)?
    ): Result<Unit>

    /**
     * Forward a message to one or more target peers.
     *
     * @param messageId the message to forward
     * @param targetPeerIds list of target peer IDs
     * @return Result with count of successes/failures
     */
    suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit>
}
