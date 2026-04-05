package com.p2p.meshify.core.data.repository

import android.content.Context
import com.p2p.meshify.core.common.R
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.entity.ChatEntity
import com.p2p.meshify.core.data.local.entity.MessageEntity
import com.p2p.meshify.core.data.local.entity.MessageStatus
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.model.DeleteRequest
import com.p2p.meshify.domain.model.DeleteType
import com.p2p.meshify.domain.model.AppConstants
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.model.MessageType
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.ReactionUpdate
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.core.common.util.HexUtil
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.data.security.impl.EcdhSessionManager
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.domain.security.model.MessageEnvelope
import com.p2p.meshify.domain.security.model.SecurityEvent
import kotlinx.serialization.encodeToString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import java.io.Closeable
import java.io.File
import java.util.UUID

/**
 * ChatRepositoryImpl - Facade pattern for chat operations.
 *
 * This class now delegates to specialized repositories following Single Responsibility Principle:
 * - [messageRepository]: Sending/receiving messages
 * - [chatManagementRepository]: Chat CRUD operations
 * - [pendingMessageRepository]: Pending message queue management
 * - [messageAttachmentRepository]: Message attachments (albums)
 * - [reactionRepository]: Message reactions
 *
 * This facade maintains backward compatibility with existing code while improving maintainability.
 */
class ChatRepositoryImpl(
    private val context: android.content.Context,
    private val stringProvider: StringResourceProvider,
    private val database: MeshifyDatabase,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val pendingMessageDao: com.p2p.meshify.core.data.local.dao.PendingMessageDao,
    private val transportManager: TransportManager,
    private val fileManager: IFileManager,
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: ISettingsRepository,
    private val peerIdentity: PeerIdentityRepository,
    private val messageCrypto: MessageEnvelopeCrypto,
    private val ecdhSessionManager: EcdhSessionManager,
    private val sessionKeyStore: EncryptedSessionKeyStore
) : IChatRepository, Closeable {

    // BUG FIX #1: Validate context is Application Context to prevent memory leaks
    init {
        require(context.applicationContext != null) {
            "Context must be Application Context to prevent memory leaks"
        }
    }

    // Specialized repositories
    private val messageRepository: MessageRepository = MessageRepository(
        database = database,
        messageDao = messageDao,
        chatDao = chatDao,
        pendingMessageDao = pendingMessageDao,
        transportManager = transportManager,
        fileManager = fileManager,
        settingsRepository = settingsRepository
    )

    private val chatManagementRepository: ChatManagementRepository = ChatManagementRepository(
        context = context,
        chatDao = chatDao,
        messageDao = messageDao
    )

    private val pendingMessageRepository: PendingMessageRepository = PendingMessageRepository(
        pendingMessageDao = pendingMessageDao,
        messageDao = messageDao,
        transportManager = transportManager,
        settingsRepository = settingsRepository
    )

    private val messageAttachmentRepository: MessageAttachmentRepository = MessageAttachmentRepository(
        messageDao = messageDao,
        fileManager = fileManager
    )

    private val reactionRepository: ReactionRepository = ReactionRepository(
        messageDao = messageDao,
        transportManager = transportManager,
        settingsRepository = settingsRepository
    )

    private val repositoryJob = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + repositoryJob)
    private val payloadMutex = Mutex()
    private val sessionMutex = Mutex()

    // Security events flow for UI notification
    private val _securityEvents = MutableSharedFlow<SecurityEvent>(replay = 0)
    override val securityEvents: SharedFlow<SecurityEvent> = _securityEvents.asSharedFlow()

    companion object {
        private const val PAYLOAD_HANDLING_TIMEOUT_MS = 30000L // 30 seconds
    }

    // Public DAO access methods for ViewModels (backward compatibility)
    fun getAllChats(): Flow<List<ChatEntity>> = chatManagementRepository.getAllChats()
    fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageRepository.getMessages(chatId)
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>> =
        messageRepository.getMessagesPaged(chatId, limit, offset)

    // Online peers flow (backward compatibility)
    override val onlinePeers: Flow<Set<String>> = transportManager.getAllEventsFlow()
        .map { event ->
            transportManager.getAllTransports().flatMap { it.onlinePeers.value }.toSet()
        }
    override val typingPeers: Flow<Set<String>> = transportManager.getAllEventsFlow()
        .map { event ->
            transportManager.getAllTransports().flatMap { it.typingPeers.value }.toSet()
        }

    // ==================== Message Sending ====================

    override suspend fun sendMessage(
        peerId: String,
        peerName: String,
        text: String,
        replyToId: String?
    ): Result<Unit> {
        // SECURITY: Encryption is mandatory - abort if session cannot be established
        // No plaintext fallback is allowed
        val sessionKeyInfo = getOrEstablishSessionKey(peerId)
            ?: return Result.failure(SecurityException("Secure session required - cannot send plaintext to $peerId"))

        // Encrypt the plaintext message
        val envelope = messageCrypto.encrypt(
            plaintext = text.toByteArray(Charsets.UTF_8),
            recipientId = peerId,
            sessionKey = sessionKeyInfo.sessionKey
        )

        // Serialize the envelope to bytes
        val envelopeBytes = serializeEnvelope(envelope)

        // Send as ENCRYPTED_MESSAGE payload type
        return sendEncryptedPayload(peerId, peerName, envelopeBytes, replyToId)
    }

    override suspend fun sendImage(
        peerId: String,
        peerName: String,
        imageBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        return messageRepository.sendImageMessage(peerId, peerName, imageBytes, extension, replyToId)
    }

    override suspend fun sendVideo(
        peerId: String,
        peerName: String,
        videoBytes: ByteArray,
        extension: String,
        replyToId: String?
    ): Result<Unit> {
        return messageRepository.sendVideoMessage(peerId, peerName, videoBytes, extension, replyToId)
    }

    override suspend fun sendFileWithProgress(
        messageId: String,
        peerId: String,
        peerName: String,
        file: File,
        fileType: MessageType,
        caption: String,
        replyToId: String?,
        progressCallback: ((Int) -> Unit)?
    ): Result<Unit> {
        return messageRepository.sendFileWithProgress(
            messageId = messageId,
            peerId = peerId,
            peerName = peerName,
            file = file,
            fileType = fileType,
            caption = caption,
            replyToId = replyToId,
            progressCallback = progressCallback
        )
    }

    override suspend fun sendGroupedMessage(
        peerId: String,
        peerName: String,
        caption: String,
        attachments: List<Pair<ByteArray, MessageType>>,
        replyToId: String?
    ): Result<Unit> {
        if (attachments.isEmpty()) return Result.failure(Exception("No attachments"))

        val messageId: String = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()

        // Create parent message with caption
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = caption.ifBlank { null },
            mediaPath = null,
            type = if (attachments.all { it.second == MessageType.VIDEO }) MessageType.VIDEO else MessageType.IMAGE,
            timestamp = timestamp,
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId,
            groupId = messageId
        )

        // Save chat
        val cleanName = parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, caption.ifBlank { "[Album]" }, timestamp))

        // Save message
        messageDao.insertMessage(message)

        // Save attachments
        val saveResult = messageAttachmentRepository.saveAttachments(messageId, attachments)
        if (saveResult.isFailure) {
            return Result.failure(saveResult.exceptionOrNull() ?: Exception("Failed to save attachments"))
        }

        // Send first attachment as representative
        val firstAttachmentBytes = attachments.firstOrNull()?.first
            ?: return Result.failure(Exception("No attachments to send"))
        
        return messageRepository.sendFileMessage(
            peerId = peerId,
            peerName = peerName,
            fileBytes = firstAttachmentBytes,
            fileName = "Album: $caption",
            fileType = message.type,
            replyToId = replyToId
        )
    }

    // ==================== Chat Management ====================

    override suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(Exception("Message not found"))

        return try {
            if (deleteType == DeleteType.DELETE_FOR_ME) {
                messageDao.markAsDeletedForMe(messageId)
            } else {
                messageDao.markAsDeletedForEveryone(messageId, System.currentTimeMillis(), message.senderId)
                
                // Send delete request to peer
                val request = DeleteRequest(messageId, deleteType, message.senderId)
                val payload = Payload(
                    senderId = message.senderId,
                    type = Payload.PayloadType.DELETE_REQUEST,
                    data = Json.encodeToString(request).toByteArray()
                )
                val transport = transportManager.selectBestTransport(message.chatId).firstOrNull()
                    ?: return Result.failure(Exception("No available transport"))
                transport.sendPayload(message.chatId, payload)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to delete message: $messageId", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteChat(peerId: String) {
        chatManagementRepository.deleteChat(peerId)
    }

    override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
        val message = messageDao.getMessageById(messageId)
            ?: return Result.failure(Exception("Message not found"))

        // Validate target list is not empty
        if (targetPeerIds.isEmpty()) {
            return Result.failure(Exception("No target peers specified"))
        }

        return try {
            val results = coroutineScope {
                targetPeerIds.map { peerId ->
                    async {
                        try {
                            val chat = chatDao.getChatById(peerId)
                            val forwardContext = buildForwardContext(message)
                            var success = false

                            if (message.type == MessageType.TEXT && message.text != null) {
                                success = forwardTextMessage(message, peerId, forwardContext)
                            } else {
                                success = forwardMediaContext(message, peerId, forwardContext)
                            }

                            // Update chat last message ONLY if successful
                            if (success) {
                                updateChatLastMessage(peerId, chat, forwardContext)
                            }

                            Pair(peerId, if (success) Result.success(Unit) else Result.failure(Exception("Forward failed")))
                        } catch (e: Exception) {
                            Logger.e("ChatRepository -> Failed to forward message to $peerId", e)
                            Pair(peerId, Result.failure<Unit>(e))
                        }
                    }
                }.awaitAll()
            }

            val failures = results.filter { it.second.isFailure }
            if (failures.isNotEmpty()) {
                Logger.e("ChatRepository -> Forward completed with ${failures.size} failures out of ${results.size}")
                Result.failure(Exception("${failures.size} of ${results.size} forwards failed"))
            } else {
                Logger.d("ChatRepository -> All ${results.size} forwards succeeded")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to forward message: $messageId", e)
            Result.failure(e)
        }
    }

    private suspend fun forwardTextMessage(
        message: MessageEntity,
        peerId: String,
        forwardContext: String
    ): Boolean {
        // SECURITY: Forwarded messages MUST be encrypted - no plaintext fallback
        val sessionKeyInfo = getOrEstablishSessionKey(peerId)
            ?: run {
                Logger.e("ChatRepository -> Cannot forward: no secure session with $peerId")
                return false
            }

        val newMessage = MessageEntity(
            id = UUID.randomUUID().toString(),
            chatId = peerId,
            senderId = message.senderId,
            text = forwardContext,
            mediaPath = null,
            type = MessageType.TEXT,
            timestamp = System.currentTimeMillis(),
            isFromMe = true,
            status = MessageStatus.SENDING,
            replyToId = null,
            groupId = null
        )

        messageDao.insertMessage(newMessage)

        val transport = transportManager.selectBestTransport(peerId).firstOrNull()
        if (transport == null) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            Logger.w("ChatRepository -> No transport available for forwarding to $peerId")
            return false
        }

        // Encrypt the forwarded message
        val envelope = try {
            messageCrypto.encrypt(
                plaintext = forwardContext.toByteArray(Charsets.UTF_8),
                recipientId = peerId,
                sessionKey = sessionKeyInfo.sessionKey
            )
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to encrypt forwarded message for $peerId", e)
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            return false
        }

        // Serialize and send as ENCRYPTED_MESSAGE
        val envelopeBytes = serializeEnvelope(envelope)
        val payload = Payload(
            id = newMessage.id,
            senderId = settingsRepository.getDeviceId(),
            timestamp = newMessage.timestamp,
            type = Payload.PayloadType.ENCRYPTED_MESSAGE,
            data = envelopeBytes
        )

        return try {
            val sendResult = transport.sendPayload(peerId, payload)
            if (sendResult.isSuccess) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)
                Logger.d("ChatRepository -> Forwarded message ${message.id} to $peerId")
                true
            } else {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.e("ChatRepository -> Failed to forward message to $peerId")
                false
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
            Logger.e("ChatRepository -> Failed to forward message to $peerId", e)
            false
        }
    }

    private suspend fun forwardMediaContext(
        message: MessageEntity,
        peerId: String,
        forwardContext: String
    ): Boolean {
        return try {
            // Step 1: Validate media path
            val mediaPath = message.mediaPath
            if (mediaPath == null) {
                Logger.e("ChatRepository -> Cannot forward media: mediaPath is null")
                return false
            }

            // Step 2: Read media file from disk
            val mediaFile = File(mediaPath)
            if (!mediaFile.exists()) {
                Logger.e("ChatRepository -> Media file does not exist: ${mediaFile.name}")
                return false
            }

            // CRITICAL: Validate file size (100MB limit to prevent OOM)
            val fileSize = mediaFile.length()
            val maxFileSize = AppConstants.MAX_FILE_SIZE_BYTES
            if (fileSize > maxFileSize) {
                val maxFileSizeMb = AppConstants.MAX_FILE_SIZE_BYTES / 1024 / 1024
                Logger.e("ChatRepository -> File size exceeds ${maxFileSizeMb}MB limit: ${fileSize / 1024 / 1024}MB")
                return false
            }

            // Step 3: Read media bytes on IO dispatcher
            val mediaBytes = withContext(Dispatchers.IO) {
                mediaFile.readBytes()
            }

            // SECURITY: Forwarded media MUST be encrypted - no plaintext fallback
            val sessionKeyInfo = getOrEstablishSessionKey(peerId)
                ?: run {
                    Logger.e("ChatRepository -> Cannot forward: no secure session with $peerId")
                    return false
                }

            // Step 4: Create new message entity
            val newMessage = MessageEntity(
                id = UUID.randomUUID().toString(),
                chatId = peerId,
                senderId = message.senderId,
                text = forwardContext,
                mediaPath = null,
                type = message.type,
                timestamp = System.currentTimeMillis(),
                isFromMe = true,
                status = MessageStatus.SENDING,
                replyToId = null,
                groupId = null
            )

            // Step 5: Save to database
            messageDao.insertMessage(newMessage)

            // Step 6: Get transport
            val transport = transportManager.selectBestTransport(peerId).firstOrNull()
            if (transport == null) {
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.w("ChatRepository -> No transport available for forwarding to ${peerId.take(8)}")
                return false
            }

            // Step 7: Encrypt the media bytes
            val envelope = try {
                messageCrypto.encrypt(
                    plaintext = mediaBytes,
                    recipientId = peerId,
                    sessionKey = sessionKeyInfo.sessionKey
                )
            } catch (e: Exception) {
                Logger.e("ChatRepository -> Failed to encrypt forwarded media for ${peerId.take(8)}", e)
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                return false
            }

            // Step 8: Serialize and send as ENCRYPTED_MESSAGE
            val envelopeBytes = serializeEnvelope(envelope)
            val payload = Payload(
                id = newMessage.id,
                senderId = settingsRepository.getDeviceId(),
                timestamp = newMessage.timestamp,
                type = Payload.PayloadType.ENCRYPTED_MESSAGE,
                data = envelopeBytes
            )

            // Step 9: Send payload with try-catch
            val sendResult = try {
                transport.sendPayload(peerId, payload)
            } catch (e: Exception) {
                Logger.e("ChatRepository -> sendPayload threw exception for ${peerId.take(8)}", e)
                Result.failure(e)
            }

            return if (sendResult.isSuccess) {
                // Step 10: Save media file for the new message BEFORE updating status
                val fileName = "forwarded_${newMessage.id}.${mediaFile.extension}"
                val savedPath = withContext(Dispatchers.IO) {
                    fileManager.saveMedia(fileName, mediaBytes)
                }

                // Step 11: Update database with saved path
                if (savedPath != null) {
                    messageDao.insertMessage(newMessage.copy(mediaPath = savedPath))
                }

                // Step 12: Update status to SENT (AFTER file is saved)
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.SENT)

                Logger.d("ChatRepository -> Forwarded media message ${message.id.take(8)} to ${peerId.take(8)}")
                true
            } else {
                // Step 13: Update status to QUEUED
                messageDao.updateMessageStatus(newMessage.id, MessageStatus.QUEUED)
                Logger.e("ChatRepository -> Failed to forward media message to ${peerId.take(8)}")
                false
            }
        } catch (e: java.io.IOException) {
            Logger.e("ChatRepository -> IO error forwarding media to ${peerId.take(8)}: ${e.message}", e)
            false
        } catch (e: SecurityException) {
            Logger.e("ChatRepository -> Security error forwarding media to ${peerId.take(8)}: ${e.message}", e)
            false
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to forward media context to ${peerId.take(8)}", e)
            false
        }
    }

    private suspend fun updateChatLastMessage(
        peerId: String,
        existingChat: ChatEntity?,
        forwardContext: String
    ) {
        if (existingChat != null) {
            chatDao.insertChat(existingChat.copy(
                lastMessage = forwardContext,
                lastTimestamp = System.currentTimeMillis()
            ))
        } else {
            chatDao.insertChat(ChatEntity(
                peerId = peerId,
                peerName = "${AppConstants.DEFAULT_PEER_NAME_PREFIX}${peerId.take(4)}",
                lastMessage = forwardContext,
                lastTimestamp = System.currentTimeMillis()
            ))
        }
    }

    /**
     * Builds context string for forwarded message.
     */
    private fun buildForwardContext(original: MessageEntity): String {
        val timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(original.timestamp))
        val unknown = stringProvider.getString(R.string.forward_context_unknown)

        return when (original.type) {
            MessageType.TEXT -> {
                val preview = original.text?.take(100) ?: ""
                stringProvider.getString(R.string.forward_context_text, timestamp, preview)
            }
            MessageType.IMAGE -> stringProvider.getString(R.string.forward_context_image, timestamp)
            MessageType.VIDEO -> stringProvider.getString(R.string.forward_context_video, timestamp)
            MessageType.AUDIO -> stringProvider.getString(R.string.forward_context_audio, timestamp)
            MessageType.FILE -> stringProvider.getString(R.string.forward_context_file, original.text ?: unknown, timestamp)
            MessageType.DOCUMENT -> stringProvider.getString(R.string.forward_context_document, timestamp)
            MessageType.ARCHIVE -> stringProvider.getString(R.string.forward_context_archive, original.text ?: unknown, timestamp)
            MessageType.APK -> stringProvider.getString(R.string.forward_context_apk, original.text ?: unknown, timestamp)
        }
    }

    // ==================== Reactions ====================

    override suspend fun addReaction(messageId: String, reaction: String?): Result<Unit> {
        return reactionRepository.addReaction(messageId, reaction)
    }

    // ==================== Encryption & Session Management ====================

    /**
     * Get existing session key or establish new session via ECDH handshake.
     *
     * Flow:
     * 1. Check cache for existing session (protected by mutex)
     * 2. If no session, trigger handshake via TransportManager (protected by mutex)
     * 3. Release mutex BEFORE polling to prevent deadlock
     * 4. Poll for session establishment outside mutex
     * 5. Return cached session if established, null on failure
     *
     * Thread Safety: Uses sessionMutex to prevent race condition where multiple
     * coroutines could simultaneously check for session (both find null) and then
     * both send HANDSHAKE, resulting in duplicate sessions and wasted resources.
     *
     * BUG FIX #4: Mutex is now released before polling to prevent deadlock risk.
     *
     * @param peerId the peer's ID
     * @return SessionKeyInfo if session exists or was established, null on failure
     */
    private suspend fun getOrEstablishSessionKey(peerId: String): EncryptedSessionKeyStore.SessionKeyInfo? {
        // Step 1 & 2: Check cache and send handshake (protected by mutex)
        val handshakeSent = sessionMutex.withLock {
            // Check if we already have a session (inside lock to prevent race condition)
            sessionKeyStore.getSessionKey(peerId)?.let { existing ->
                Logger.d("ChatRepository", "Using cached session key for ${peerId.take(8)}...")
                return@withLock true // Session already exists
            }

            // No session - trigger handshake via transport layer
            Logger.d("ChatRepository", "No session for ${peerId.take(8)}... - triggering handshake")

            // Get our identity info for handshake
            val myId = settingsRepository.getDeviceId()
            val identityPubKeyHex = peerIdentity.getPublicKeyHex()
            val displayName = withContext(Dispatchers.IO) {
                settingsRepository.displayName.firstOrNull() ?: "Unknown"
            }
            val avatarHash = withContext(Dispatchers.IO) {
                settingsRepository.avatarHash.firstOrNull()
            }

            // Generate ephemeral keypair for forward secrecy
            val ephemeralKeypair = ecdhSessionManager.generateEphemeralKeypair()
            val ephemeralPubKeyHex = HexUtil.toHex(ephemeralKeypair.public.encoded)
            val nonce = ecdhSessionManager.generateNonce()
            val nonceHex = HexUtil.toHex(nonce)

            // Build handshake payload
            val handshake = Handshake(
                version = 2,
                name = displayName,
                avatarHash = avatarHash,
                identityPubKeyHex = identityPubKeyHex,
                ephemeralPubKeyHex = ephemeralPubKeyHex,
                nonceHex = nonceHex,
                timestamp = System.currentTimeMillis()
            )

            val handshakePayload = Payload(
                senderId = myId,
                type = Payload.PayloadType.HANDSHAKE,
                data = Json.encodeToString(handshake).toByteArray()
            )

            // Send handshake via transport
            val transport = transportManager.selectBestTransport(peerId).firstOrNull()
            if (transport == null) {
                Logger.e("No transport available for handshake with ${peerId.take(8)}...", tag = "ChatRepository")
                return@withLock false
            }

            val sendResult = transport.sendPayload(peerId, handshakePayload)
            if (sendResult.isFailure) {
                Logger.e("Failed to send handshake to ${peerId.take(8)}...: ${sendResult.exceptionOrNull()?.message}", tag = "ChatRepository")
                return@withLock false
            }

            Logger.d("ChatRepository", "Handshake sent to ${peerId.take(8)}... - waiting for response")
            true // Handshake sent successfully
        } // Mutex RELEASED here - before polling

        // Step 3: Poll for session establishment OUTSIDE mutex to prevent deadlock
        if (!handshakeSent) {
            return null
        }

        val session = pollForSessionEstablishment(peerId)
        if (session != null) {
            Logger.d("ChatRepository", "Session established with ${peerId.take(8)}... via handshake")
        } else {
            Logger.w("ChatRepository", "Timeout waiting for handshake response from ${peerId.take(8)}...")
        }
        return session
    }

    /**
     * Polls for session establishment after handshake was sent.
     * Must be called OUTSIDE of sessionMutex to prevent deadlock.
     *
     * @param peerId the peer's ID
     * @return SessionKeyInfo if session was established, null on timeout
     */
    private suspend fun pollForSessionEstablishment(peerId: String): EncryptedSessionKeyStore.SessionKeyInfo? {
        val pollStartTime = System.currentTimeMillis()
        val pollTimeoutMs = 5000L
        val pollIntervalMs = 100L

        while ((System.currentTimeMillis() - pollStartTime) < pollTimeoutMs) {
            delay(pollIntervalMs)
            sessionKeyStore.getSessionKey(peerId)?.let { session ->
                Logger.d("ChatRepository", "Session established with ${peerId.take(8)}... via handshake")
                return session
            }
        }

        Logger.w("ChatRepository", "Timeout waiting for handshake response from ${peerId.take(8)}...")
        return null
    }

    /**
     * Establish session key from peer's handshake (called during handshake response).
     * 
     * This is the RESPONDER flow:
     * 1. Receive initiator's handshake with identity key, ephemeral key, and nonce
     * 2. Validate TOFU - ABORT if peer's identity key changed
     * 3. Generate our own ephemeral keypair and nonce
     * 4. Compute ECDH shared secret
     * 5. Derive session key via HKDF with concatenated salt
     * 6. Cache the session for future encryption/decryption
     * 
     * @param peerId the peer's ID
     * @param peerIdentityPubKeyHex peer's long-term identity public key (hex)
     * @param peerEphemeralPubKeyHex peer's ephemeral session public key (hex)
     * @param peerNonceHex peer's nonce (hex)
     * @return true if session was established successfully, false on TOFU violation
     */
    internal suspend fun establishSessionFromHandshake(
        peerId: String,
        peerIdentityPubKeyHex: String,
        peerEphemeralPubKeyHex: String,
        peerNonceHex: String
    ): Boolean {
        try {
            // Step 1: TOFU Validation - CRITICAL: ABORT on violation
            val tofuResult = sessionKeyStore.validatePeerPublicKey(peerId, peerIdentityPubKeyHex)
            if (tofuResult == false) {
                // TOFU VIOLATION: Peer's identity key changed!
                Logger.e(
                    "TOFU VIOLATION: Peer identity key changed! Aborting session establishment.",
                    tag = "ChatRepository"
                )
                // Do NOT establish session - require user confirmation
                return false
            }

            // Step 2: Convert hex to bytes
            val peerIdentityPubKeyBytes = peerIdentityPubKeyHex.hexToByteArray()
            val peerEphemeralPubKeyBytes = peerEphemeralPubKeyHex.hexToByteArray()
            val peerNonce = peerNonceHex.hexToByteArray()

            // Step 3: Generate our own ephemeral keypair and nonce
            val myEphemeralKeyPair = ecdhSessionManager.generateEphemeralKeypair()
            val myNonce = ecdhSessionManager.generateNonce()

            // Step 4: Derive session key using ECDH + HKDF
            // Both parties will compute: sharedSecret = ECDH(ephemeralPriv, peerEphemeralPub)
            // Then derive: sessionKey = HKDF(sharedSecret, salt=peerNonce||myNonce, info="Meshify-Session-v2")
            val sessionKey = ecdhSessionManager.deriveSessionKeyFromPeer(
                peerEphemeralPubKeyBytes = peerEphemeralPubKeyBytes,
                peerNonce = peerNonce,
                myEphemeralKeyPair = myEphemeralKeyPair,
                myNonce = myNonce
            )

            // Step 5: Cache the session key (encrypted at rest)
            sessionKeyStore.putSessionKey(peerId, sessionKey, peerIdentityPubKeyHex)

            // Step 6: Zero ephemeral private key (forward secrecy)
            ecdhSessionManager.zeroPrivateKey(myEphemeralKeyPair.private.encoded)

            Logger.d("ChatRepository", "Session established with peer ${peerId.take(8)}... (responder)")
            return true
        } catch (e: Exception) {
            Logger.e("Failed to establish session with ${peerId.take(8)}...", e, "ChatRepository")
            return false
        }
    }

    /**
     * Initiate session with peer (called when sending first message).
     *
     * This is the INITIATOR flow:
     * 1. Generate ephemeral keypair and nonce
     * 2. Send handshake with identity key, ephemeral key, and nonce
     * 3. Receive peer's handshake response
     * 4. Finalize session key with concatenated salt (in finalizeSession)
     *
     * @param peerId the peer's ID
     * @return LocalSession containing ephemeral keys and nonce (sessionKey is placeholder)
     */
    internal suspend fun initiateSession(peerId: String): EcdhSessionManager.LocalSession? {
        try {
            // Generate ephemeral session (no ECDH performed yet)
            val session = ecdhSessionManager.createEphemeralSession()

            return session
        } catch (e: Exception) {
            Logger.e("Failed to initiate session with ${peerId.take(8)}...", e, "ChatRepository")
            return null
        }
    }

    /**
     * Finalize session key after receiving peer's handshake response.
     * 
     * @param peerId the peer's ID
     * @param peerEphemeralPubKeyHex peer's ephemeral public key (hex)
     * @param peerNonceHex peer's nonce (hex)
     * @param myEphemeralPrivateKey our ephemeral private key (from initiateSession)
     * @param myNonce our nonce (from initiateSession)
     * @param peerIdentityPubKeyHex peer's identity public key (for TOFU)
     * @return true if session finalized successfully
     */
    internal suspend fun finalizeSession(
        peerId: String,
        peerEphemeralPubKeyHex: String,
        peerNonceHex: String,
        myEphemeralPrivateKey: ByteArray,
        myNonce: ByteArray,
        peerIdentityPubKeyHex: String
    ): Boolean {
        try {
            // TOFU validation
            val tofuResult = sessionKeyStore.validatePeerPublicKey(peerId, peerIdentityPubKeyHex)
            if (tofuResult == false) {
                Logger.e(
                    "TOFU VIOLATION: Peer identity key changed! Aborting.",
                    tag = "ChatRepository"
                )
                return false
            }

            // Convert hex to bytes
            val peerEphemeralPubKeyBytes = peerEphemeralPubKeyHex.hexToByteArray()
            val peerNonce = peerNonceHex.hexToByteArray()

            // Finalize session key with concatenated salt
            val sessionKey = ecdhSessionManager.finalizeSessionKey(
                peerEphemeralPubKeyBytes = peerEphemeralPubKeyBytes,
                peerNonce = peerNonce,
                myEphemeralPrivateKey = myEphemeralPrivateKey,
                myNonce = myNonce
            )

            // Cache the session
            sessionKeyStore.putSessionKey(peerId, sessionKey, peerIdentityPubKeyHex)

            // Zero ephemeral private key (forward secrecy)
            ecdhSessionManager.zeroPrivateKey(myEphemeralPrivateKey)

            Logger.d("ChatRepository", "Session finalized with peer ${peerId.take(8)}... (initiator)")
            return true
        } catch (e: Exception) {
            Logger.e("Failed to finalize session with ${peerId.take(8)}...", e, "ChatRepository")
            return false
        }
    }

    /**
     * Send encrypted payload to peer.
     * @param peerId destination peer ID
     * @param peerName destination peer name
     * @param encryptedData encrypted envelope bytes
     * @param replyToId optional reply-to message ID
     * @return Result of send operation
     */
    private suspend fun sendEncryptedPayload(
        peerId: String,
        peerName: String,
        encryptedData: ByteArray,
        replyToId: String?
    ): Result<Unit> {
        val messageId = UUID.randomUUID().toString()
        val myId = settingsRepository.getDeviceId()
        val timestamp = System.currentTimeMillis()

        // Create message entity (status will be updated after send)
        val message = MessageEntity(
            id = messageId,
            chatId = peerId,
            senderId = myId,
            text = "[Encrypted]", // Placeholder - actual content is encrypted
            mediaPath = null,
            type = MessageType.TEXT,
            timestamp = timestamp,
            isFromMe = true,
            status = MessageStatus.QUEUED,
            replyToId = replyToId
        )

        // Save chat
        val cleanName = parseName(peerName)
        chatDao.insertChat(ChatEntity(peerId, cleanName, "[Encrypted]", timestamp))

        // Save message
        messageDao.insertMessage(message)

        // Check if peer is online
        val isOnline = transportManager.getAllTransports().any { it.onlinePeers.value.contains(peerId) }

        if (!isOnline) {
            Logger.w("ChatRepository -> Peer $peerId offline, queuing encrypted message")
            pendingMessageDao.insert(
                com.p2p.meshify.core.data.local.entity.PendingMessageEntity(
                    id = messageId,
                    recipientId = peerId,
                    recipientName = cleanName,
                    content = "[Encrypted]",
                    type = MessageType.TEXT
                )
            )
            return Result.success(Unit)
        }

        // Create encrypted payload
        val payload = Payload(
            id = messageId,
            senderId = myId,
            timestamp = timestamp,
            type = Payload.PayloadType.ENCRYPTED_MESSAGE,
            data = encryptedData
        )

        // Send with timeout — supports multi-path (send on all transports, success if ANY succeeds)
        return try {
            messageDao.updateMessageStatus(messageId, MessageStatus.SENDING)
            val transports = transportManager.selectBestTransport(peerId)
            if (transports.isEmpty()) {
                Result.failure(Exception("No available transport"))
            } else {
                // Multi-path: send on all transports, succeed if ANY succeeds
                val results = transports.map { transport ->
                    runCatching {
                        withTimeout(30000L) {
                            transport.sendPayload(peerId, payload)
                        }
                    }
                }

                val firstSuccess = results.find { it.isSuccess }
                if (firstSuccess != null) {
                    messageDao.updateMessageStatus(messageId, MessageStatus.SENT)
                    val transportNames = transports.joinToString("+") { it.transportName }
                    Logger.d("ChatRepository -> Encrypted message sent to $peerId via [$transportNames]")
                    Result.success(Unit)
                } else {
                    messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
                    val lastError = results.lastOrNull()?.exceptionOrNull()
                    Logger.e("ChatRepository -> Failed to send encrypted message to $peerId on all transports: ${lastError?.message}")
                    // Queue for retry
                    pendingMessageDao.insert(
                        com.p2p.meshify.core.data.local.entity.PendingMessageEntity(
                            id = messageId,
                            recipientId = peerId,
                            recipientName = cleanName,
                            content = "[Encrypted]",
                            type = MessageType.TEXT
                        )
                    )
                    // Emit security event to notify UI
                    scope.launch {
                        try {
                            _securityEvents.emit(
                                SecurityEvent.MessageSendFailed(
                                    messageId = messageId,
                                    peerId = peerId,
                                    reason = lastError?.message ?: "Unknown send error"
                                )
                            )
                        } catch (emitError: Exception) {
                            Logger.e(
                                message = "Failed to emit security event for message $messageId: ${emitError.message}",
                                throwable = emitError,
                                tag = "ChatRepository"
                            )
                        }
                    }
                    Result.failure(lastError ?: Exception("All transports failed"))
                }
            }
        } catch (e: Exception) {
            messageDao.updateMessageStatus(messageId, MessageStatus.FAILED)
            Logger.e("ChatRepository -> Exception sending encrypted message", e)
            Result.failure(e)
        }
    }

    /**
     * Serialize MessageEnvelope to bytes for transmission.
     */
    private fun serializeEnvelope(envelope: MessageEnvelope): ByteArray {
        // Format: [senderIdLen:2][senderId][recipientIdLen:2][recipientId][nonceLen:2][nonce][timestamp:8][ivLen:2][iv][ciphertextLen:4][ciphertext][sigLen:2][signature]
        val senderIdBytes = envelope.senderId.toByteArray(Charsets.UTF_8)
        val recipientIdBytes = envelope.recipientId.toByteArray(Charsets.UTF_8)

        return buildPacket(
            senderIdBytes,
            recipientIdBytes,
            envelope.nonce,
            envelope.timestamp,
            envelope.iv,
            envelope.ciphertext,
            envelope.signature
        )
    }

    /**
     * Deserialize MessageEnvelope from bytes.
     */
    private fun deserializeEnvelope(data: ByteArray): MessageEnvelope {
        return parsePacket(data)
    }

    /**
     * Build binary packet from envelope fields.
     */
    private fun buildPacket(
        senderIdBytes: ByteArray,
        recipientIdBytes: ByteArray,
        nonce: ByteArray,
        timestamp: Long,
        iv: ByteArray,
        ciphertext: ByteArray,
        signature: ByteArray
    ): ByteArray {
        val totalSize = 2 + senderIdBytes.size +
                2 + recipientIdBytes.size +
                2 + nonce.size +
                8 + // timestamp as Long
                2 + iv.size +
                4 + ciphertext.size +
                2 + signature.size

        val buffer = java.nio.ByteBuffer.allocate(totalSize).apply {
            putShort(senderIdBytes.size.toShort())
            put(senderIdBytes)
            putShort(recipientIdBytes.size.toShort())
            put(recipientIdBytes)
            putShort(nonce.size.toShort())
            put(nonce)
            putLong(timestamp)
            putShort(iv.size.toShort())
            put(iv)
            putInt(ciphertext.size.toInt())
            put(ciphertext)
            putShort(signature.size.toShort())
            put(signature)
        }
        return buffer.array()
    }

    /**
     * Parse binary packet to MessageEnvelope.
     */
    private fun parsePacket(data: ByteArray): MessageEnvelope {
        val buffer = java.nio.ByteBuffer.wrap(data)

        val senderIdLen = buffer.short.toInt()
        val senderIdBytes = ByteArray(senderIdLen)
        buffer.get(senderIdBytes)
        val senderId = String(senderIdBytes, Charsets.UTF_8)

        val recipientIdLen = buffer.short.toInt()
        val recipientIdBytes = ByteArray(recipientIdLen)
        buffer.get(recipientIdBytes)
        val recipientId = String(recipientIdBytes, Charsets.UTF_8)

        val nonceLen = buffer.short.toInt()
        val nonce = ByteArray(nonceLen)
        buffer.get(nonce)

        val timestamp = buffer.long

        val ivLen = buffer.short.toInt()
        val iv = ByteArray(ivLen)
        buffer.get(iv)

        val ciphertextLen = buffer.int
        val ciphertext = ByteArray(ciphertextLen)
        buffer.get(ciphertext)

        val sigLen = buffer.short.toInt()
        val signature = ByteArray(sigLen)
        buffer.get(signature)

        return MessageEnvelope(
            senderId = senderId,
            recipientId = recipientId,
            nonce = nonce,
            timestamp = timestamp,
            iv = iv,
            ciphertext = ciphertext,
            signature = signature
        )
    }

    /**
     * Get stored public key for a peer.
     * This is a simplified implementation - in production, you'd store peer keys in a database.
     * @param peerId the peer's ID
     * @return peer's public key in hex format, or null if not found
     */
    private suspend fun getPeerPublicKey(peerId: String): String? {
        // For now, we retrieve from session store if available
        // In a full implementation, this would query a persistent peer key store
        return sessionKeyStore.getSessionKey(peerId)?.peerPublicKeyHex
    }

    // ==================== System Commands ====================

    override suspend fun sendSystemCommand(peerId: String, command: String) {
        val myId = settingsRepository.getDeviceId()
        val payload = Payload(
            senderId = myId,
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = command.toByteArray()
        )
        val transport = transportManager.selectBestTransport(peerId).firstOrNull()
            ?: throw IllegalStateException("No available transport for peer: $peerId")
        transport.sendPayload(peerId, payload)
    }

    // ==================== Incoming Payload Handling ====================

    override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
        try {
            withTimeout(PAYLOAD_HANDLING_TIMEOUT_MS) {
                payloadMutex.withLock {
                    processPayload(peerId, payload)
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            Logger.e("ChatRepository -> handleIncomingPayload timeout after ${PAYLOAD_HANDLING_TIMEOUT_MS}ms", e)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> handleIncomingPayload failed", e)
        }
    }

    /**
     * Process incoming payload from peer.
     *
     * Structure:
     * - ENCRYPTED_MESSAGE: Decrypts message, saves to database, sends ACK on success
     * - HANDSHAKE: Processes V2/V1 handshake, establishes encrypted session via ECDH
     * - SYSTEM_CONTROL, DELETE_REQUEST, REACTION: Basic command processing
     * - Unhandled types (TEXT, FILE, VIDEO, DELIVERY_ACK, AVATAR_REQUEST, AVATAR_RESPONSE): Rejected
     *
     * SECURITY NOTE: Only ENCRYPTED_MESSAGE payload type is accepted for message content.
     * Plaintext messages (TEXT, FILE, VIDEO) are REJECTED to prevent downgrade attacks.
     * All communication MUST be encrypted via end-to-end encryption.
     *
     * @param peerId the peer's ID
     * @param payload the payload to process
     */
    private suspend fun processPayload(peerId: String, payload: Payload) {
        Logger.d("ChatRepository -> Processing payload from $peerId, type=${payload.type}")

        when (payload.type) {
            Payload.PayloadType.SYSTEM_CONTROL -> handleSystemCommand(peerId, payload)
            Payload.PayloadType.DELETE_REQUEST -> handleDeleteRequest(peerId, payload)
            Payload.PayloadType.REACTION -> handleReaction(peerId, payload)
            Payload.PayloadType.ENCRYPTED_MESSAGE -> handleEncryptedMessage(peerId, payload)
            Payload.PayloadType.HANDSHAKE -> handleHandshake(peerId, payload)
            // SECURITY: Plaintext payload types are REJECTED to prevent downgrade attacks
            Payload.PayloadType.TEXT -> rejectPayload("TEXT", peerId)
            Payload.PayloadType.FILE -> rejectPayload("FILE", peerId)
            Payload.PayloadType.VIDEO -> rejectPayload("VIDEO", peerId)
            // Unimplemented payload types - logged for future implementation
            Payload.PayloadType.DELIVERY_ACK -> Logger.w("ChatRepository -> Unimplemented payload type: DELIVERY_ACK from $peerId")
            Payload.PayloadType.AVATAR_REQUEST -> Logger.w("ChatRepository -> Unimplemented payload type: AVATAR_REQUEST from $peerId")
            Payload.PayloadType.AVATAR_RESPONSE -> Logger.w("ChatRepository -> Unimplemented payload type: AVATAR_RESPONSE from $peerId")
            // NOTE: No else branch - compiler error if new PayloadType added without explicit handling
        }
    }

    /**
     * Handle SYSTEM_CONTROL payload (ACK messages).
     */
    private suspend fun handleSystemCommand(peerId: String, payload: Payload) {
        val command = String(payload.data)
        if (command.startsWith("ACK_")) {
            val messageId = command.removePrefix("ACK_")
            Logger.d("ChatRepository -> Received ACK for message $messageId")
            messageDao.updateMessageStatus(messageId, MessageStatus.DELIVERED)
        }
    }

    /**
     * Handle DELETE_REQUEST payload.
     */
    private suspend fun handleDeleteRequest(peerId: String, payload: Payload) {
        val req = Json.decodeFromString<DeleteRequest>(String(payload.data))
        Logger.d("ChatRepository -> Received delete request for message ${req.messageId}")
        messageDao.markAsDeletedForEveryone(req.messageId, req.deletedAt, req.deletedBy)
    }

    /**
     * Handle REACTION payload.
     */
    private suspend fun handleReaction(peerId: String, payload: Payload) {
        val update = Json.decodeFromString<ReactionUpdate>(String(payload.data))
        Logger.d("ChatRepository -> Received reaction ${update.reaction} for message ${update.messageId}")
        messageDao.updateReaction(update.messageId, update.reaction)
    }

    /**
     * Reject unsupported payload types with a warning log.
     */
    private fun rejectPayload(type: String, peerId: String) {
        Logger.w("ChatRepository -> REJECTED: Plaintext $type from $peerId (downgrade attack prevented)")
    }

    /**
     * Handle ENCRYPTED_MESSAGE payload — decrypt, validate, save, and send ACK.
     */
    private suspend fun handleEncryptedMessage(peerId: String, payload: Payload) {
        try {
            // Deserialize envelope
            val envelope = deserializeEnvelope(payload.data)

            // Get session key for this peer
            val sessionKeyInfo = sessionKeyStore.getSessionKey(peerId)
                ?: throw SecurityException("No session key for peer $peerId - cannot decrypt message")

            // Get sender's public key from session store
            val senderPublicKeyHex = getPeerPublicKey(peerId)
                ?: throw SecurityException("Unknown peer $peerId - public key not found")

            val senderPublicKeyBytes = senderPublicKeyHex.hexToByteArray()

            // Decrypt the message
            val plaintext = messageCrypto.decrypt(
                envelope = envelope,
                senderPublicKeyBytes = senderPublicKeyBytes,
                sessionKey = sessionKeyInfo.sessionKey
            )

            // Process decrypted message
            val text = String(plaintext, Charsets.UTF_8)
            // SECURITY: Never log decrypted message content - use non-sensitive logging only
            Logger.d("ChatRepository", "Message decrypted successfully from ${peerId.take(8)}...")

            // Check Result before sending ACK - only send ACK after confirmed save
            val saveResult = saveIncomingMessage(peerId, text, null, MessageType.TEXT, payload.timestamp, payload.id)

            if (saveResult.isSuccess) {
                // Only send ACK after confirmed save
                sendSystemCommand(payload.senderId, "ACK_${payload.id}")
            } else {
                // Log the failure and do NOT send ACK
                Logger.e(
                    message = "Failed to save incoming message from ${peerId.take(8)}: ${saveResult.exceptionOrNull()?.message}",
                    tag = "ChatRepository"
                )

                // Emit security event so UI knows save failed
                scope.launch {
                    try {
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Database save failed: ${saveResult.exceptionOrNull()?.message}"
                            )
                        )
                    } catch (emitError: Exception) {
                        Logger.e(
                            message = "Failed to emit security event for DB save failure: ${emitError.message}",
                            throwable = emitError,
                            tag = "ChatRepository"
                        )
                    }
                }

                // Do NOT send ACK - sender should retry
            }

        } catch (e: SecurityException) {
            // SECURITY: Log decryption failures as potential attacks
            Logger.e(
                "SECURITY: Decryption failed for message from ${peerId.take(8)}...: ${e.message}",
                e,
                "ChatRepository"
            )

            // Save placeholder message so user sees decryption failure in chat
            val saveResult = saveIncomingMessage(
                peerId = peerId,
                text = context.getString(R.string.error_decryption_failed),
                mediaPath = null,
                type = MessageType.TEXT,
                timestamp = payload.timestamp,
                messageId = "decryption_failed_${UUID.randomUUID()}"
            )

            // Notify UI layer about the decryption failure
            scope.launch {
                try {
                    if (saveResult.isFailure) {
                        // Emit security event for save failure (this is a decryption/processing failure, not TOFU)
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Failed to save decryption error placeholder: ${saveResult.exceptionOrNull()?.message}"
                            )
                        )
                    } else {
                        // Only emit decryption event if save succeeded
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = e.message ?: "Unknown decryption error"
                            )
                        )
                    }
                } catch (emitError: Exception) {
                    Logger.e(
                        message = "Failed to emit security event for decryption failure: ${emitError.message}",
                        throwable = emitError,
                        tag = "ChatRepository"
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to process encrypted message from ${peerId.take(8)}...", e, "ChatRepository")

            // Save placeholder message so user sees processing failure in chat
            val saveResult = saveIncomingMessage(
                peerId = peerId,
                text = context.getString(R.string.error_message_processing_failed),
                mediaPath = null,
                type = MessageType.TEXT,
                timestamp = payload.timestamp,
                messageId = "processing_failed_${UUID.randomUUID()}"
            )

            // Notify UI layer about the processing error
            scope.launch {
                try {
                    if (saveResult.isFailure) {
                        // Emit security event for save failure (this is a decryption/processing failure, not TOFU)
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Failed to save processing error placeholder: ${saveResult.exceptionOrNull()?.message}"
                            )
                        )
                    } else {
                        // Only emit decryption event if save succeeded
                        _securityEvents.emit(
                            SecurityEvent.DecryptionFailed(
                                peerId = peerId,
                                reason = "Message processing error: ${e.message ?: "Unknown error"}"
                            )
                        )
                    }
                } catch (emitError: Exception) {
                    Logger.e(
                        message = "Failed to emit security event for processing error: ${emitError.message}",
                        throwable = emitError,
                        tag = "ChatRepository"
                    )
                }
            }
        }
    }

    /**
     * Handle HANDSHAKE payload — establish encrypted session via ECDH.
     */
    private suspend fun handleHandshake(peerId: String, payload: Payload) {
        try {
            val handshake = Json.decodeFromString<Handshake>(String(payload.data))
            val cleanName = parseName(handshake.name)
            chatDao.insertChat(ChatEntity(payload.senderId, cleanName, "Connected", payload.timestamp))

            // Check for V2 protocol fields (ephemeral key exchange)
            val identityPubKeyHex = handshake.identityPubKeyHex
            val ephemeralPubKeyHex = handshake.ephemeralPubKeyHex
            val nonceHex = handshake.nonceHex

            if (!identityPubKeyHex.isNullOrBlank() &&
                !ephemeralPubKeyHex.isNullOrBlank() &&
                !nonceHex.isNullOrBlank()) {

                Logger.d("ChatRepository", "Handshake V2 from ${peerId.take(8)}... with ephemeral key exchange")

                // Establish encrypted session using V2 protocol (responder flow)
                val sessionEstablished = establishSessionFromHandshake(
                    peerId = peerId,
                    peerIdentityPubKeyHex = identityPubKeyHex,
                    peerEphemeralPubKeyHex = ephemeralPubKeyHex,
                    peerNonceHex = nonceHex
                )

                if (sessionEstablished) {
                    Logger.d("ChatRepository", "Encrypted V2 session established with ${peerId.take(8)}...")
                } else {
                    Logger.e("TOFU VIOLATION: Session establishment aborted for ${peerId.take(8)}...", tag = "ChatRepository")
                    // Do NOT send response - TOFU violation requires user intervention
                    return
                }
            } else if (!identityPubKeyHex.isNullOrBlank()) {
                // V1 protocol (identity key only, no forward secrecy)
                Logger.w("ChatRepository", "Handshake V1 from ${peerId.take(8)}... (no forward secrecy)")
                // For backward compatibility, could establish V1 session here
                // But we prefer V2 only
            } else {
                // SECURITY: ABORT handshake - missing public keys means no encryption possible
                Logger.e("Handshake from ${peerId.take(8)}... missing public keys - REJECTING", tag = "ChatRepository")
                return  // DO NOT establish session - encryption is mandatory
            }

            // Retry pending messages in background
            scope.launch {
                try {
                    retryPendingMessages(payload.senderId)
                } catch (e: Exception) {
                    Logger.e(
                        message = "Failed to retry pending messages for ${payload.senderId.take(8)}: ${e.message}",
                        throwable = e,
                        tag = "ChatRepository"
                    )
                }
            }
        } catch (e: Exception) {
            Logger.e("Failed to process handshake from ${peerId.take(8)}...", e, "ChatRepository")
        }
    }

    private suspend fun saveIncomingMessage(
        peerId: String,
        text: String?,
        mediaPath: String?,
        type: MessageType,
        timestamp: Long,
        messageId: String
    ): Result<Unit> {
        return try {
            val existingChat = chatDao.getChatById(peerId)
            val finalName = if (existingChat != null) parseName(existingChat.peerName) else "${AppConstants.DEFAULT_PEER_NAME_PREFIX}${peerId.take(4)}"
            chatDao.insertChat(ChatEntity(peerId, finalName, text ?: "[Media]", timestamp))
            val message = MessageEntity(
                id = messageId,
                chatId = peerId,
                senderId = peerId,
                text = text,
                mediaPath = mediaPath,
                type = type,
                timestamp = timestamp,
                isFromMe = false,
                status = MessageStatus.SENT
            )
            messageDao.insertMessage(message)
            notificationHelper.showMessageNotification(finalName, message)
            Result.success(Unit)
        } catch (e: Exception) {
            Logger.e("ChatRepository -> Failed to save incoming message", e)
            Result.failure(e)
        }
    }

    private fun parseName(raw: String): String {
        return if (raw.contains("name")) {
             try { Json.decodeFromString<Handshake>(raw).name } catch(e:Exception) { raw.take(20) }
        } else raw.removePrefix("HELO_")
    }

    // ==================== Pending Messages ====================

    override suspend fun retryPendingMessages(peerId: String): Result<Unit> {
        return pendingMessageRepository.retryPendingMessages(peerId)
    }

    // ==================== Attachments (Backward Compatibility) ====================

    suspend fun getMessageAttachments(messageId: String): List<com.p2p.meshify.core.data.local.entity.MessageAttachmentEntity> {
        return messageAttachmentRepository.getAttachmentsForMessage(messageId)
    }

    override fun close() {
        repositoryJob.cancel()
        Logger.d("ChatRepositoryImpl -> Repository scope cancelled")
    }
}
