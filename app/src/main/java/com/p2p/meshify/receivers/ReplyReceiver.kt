package com.p2p.meshify.receivers

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.p2p.meshify.MeshifyApp
import com.p2p.meshify.core.common.util.RateLimiter
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.core.util.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/**
 * Secure BroadcastReceiver for handling inline reply actions from notifications.
 *
 * SECURITY FEATURES:
 * 1. HMAC Signature Validation - Prevents unauthorized intents
 * 2. Timestamp Validation - Prevents replay attacks (5-minute window)
 * 3. ChatId Validation - Ensures chat exists before sending
 * 4. Rate Limiting - Prevents flooding (10 replies/minute)
 * 5. Message Validation - Validates length and sanitizes content
 * 6. Proper Error Handling - Try/catch around all async operations
 * 7. Result Checking - Verifies sendMessage() result before showing success
 * 8. User Feedback - Success/error notifications with retry option
 */
class ReplyReceiver : BroadcastReceiver() {
    
    companion object {
        private const val SIGNATURE_MAX_AGE_MS = 5 * 60 * 1000L // 5 minutes

        // Rate limiter: 10 replies per minute per chat, max 10000 identifiers to prevent memory exhaustion
        private val replyRateLimiter = RateLimiter(
            maxRequests = 10,
            windowMs = 60 * 1000L,
            maxIdentifiers = 10000
        )

        /**
         * Validate Base64 signature format.
         * @param str The string to validate
         * @return true if valid Base64, false otherwise
         */
        private fun isValidBase64(str: String): Boolean {
            return try {
                Base64.decode(str, Base64.NO_WRAP)
                true
            } catch (e: IllegalArgumentException) {
                false
            }
        }

        /**
         * Schedule retry with exponential backoff for failed replies.
         * @param context Application context
         * @param chatId The chat identifier
         * @param message The message text to retry sending
         * @param retryCount Current retry attempt (0-2)
         */
        private fun scheduleRetry(context: Context, chatId: String, message: String, retryCount: Int = 0) {
            // Exponential backoff: 1s, 2s, 4s, capped at 30 seconds
            val delayMs = minOf(30000L, 1000L * (1L shl retryCount))

            CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                delay(delayMs)

                try {
                    val app = context.applicationContext as MeshifyApp
                    val repository = app.container.chatRepository
                    val chatDao = app.container.database.chatDao()
                    val chat = chatDao.getChatById(chatId)

                    if (chat != null) {
                        val result = repository.sendMessage(chatId, chat.peerName, message)
                        if (result.isFailure && retryCount < 3) {
                            scheduleRetry(context, chatId, message, retryCount + 1)
                        } else if (result.isFailure) {
                            // Final failure after 3 retries
                            Logger.e("ReplyReceiver -> Failed after 3 retries for chat")
                        }
                    }
                } catch (e: Exception) {
                    if (retryCount < 3) {
                        scheduleRetry(context, chatId, message, retryCount + 1)
                    } else {
                        Logger.e("ReplyReceiver -> Retry exhausted after exception", e)
                    }
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.p2p.meshify.REPLY_ACTION") {
            Logger.d("ReplyReceiver -> Invalid action: ${intent.action}")
            return
        }

        // Extract intent data
        val chatId = intent.getStringExtra("chat_id")
        val signature = intent.getStringExtra("signature")
        val timestamp = intent.getLongExtra("timestamp", 0L)

        // Extract reply text early so it can be preserved in error notifications
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

        // Validate required fields
        if (chatId.isNullOrBlank()) {
            Logger.e("ReplyReceiver -> Missing chat_id")
            showReplyErrorNotification(context, "Invalid chat", null, replyText)
            return
        }

        if (signature.isNullOrBlank()) {
            Logger.e("ReplyReceiver -> Missing signature")
            showReplyErrorNotification(context, "Invalid reply", null, replyText)
            return
        }

        // Validate signature is valid Base64 format
        if (!isValidBase64(signature)) {
            Logger.e("ReplyReceiver -> Invalid Base64 signature format")
            showReplyErrorNotification(context, "Unauthorized reply", null, replyText)
            return
        }

        // Validate timestamp (prevent replay attacks AND future timestamps)
        val now = System.currentTimeMillis()
        val age = now - timestamp

        // Reject future timestamps (clock manipulation attack)
        if (age < 0) {
            Logger.e("ReplyReceiver -> Future timestamp detected, rejecting")
            showReplyErrorNotification(context, "Invalid timestamp", null, replyText)
            return
        }

        // Reject expired timestamps
        if (age > SIGNATURE_MAX_AGE_MS) {
            Logger.e("ReplyReceiver -> Expired timestamp, age: ${age}ms")
            showReplyErrorNotification(context, "Reply expired, please try again", chatId, replyText)
            return
        }

        // Validate signature using NotificationHelper
        val notificationHelper = NotificationHelper(context)
        if (!notificationHelper.verifyReplySignature(chatId, signature, timestamp)) {
            Logger.e("ReplyReceiver -> Signature verification failed") // No chatId for security
            showReplyErrorNotification(context, "Unauthorized reply", null, replyText)
            return
        }

        // Validate chatId format
        if (chatId.length > 256) {
            Logger.e("ReplyReceiver -> Invalid chatId format: length ${chatId.length}")
            showReplyErrorNotification(context, "Invalid chat", null, replyText)
            return
        }

        // Validate chat exists in database
        // TODO: Extract chat validation to repository layer - Direct DAO access violates clean architecture
        // Tracked in issue #XXX
        val app = context.applicationContext as MeshifyApp
        val chatDao = app.container.database.chatDao()
        val chat: com.p2p.meshify.core.data.local.entity.ChatEntity? = runCatching {
            runBlocking {
                withTimeout(2000) { // 2 second timeout to prevent ANR
                    chatDao.getChatById(chatId)
                }
            }
        }.getOrNull()

        if (chat == null) {
            Logger.e("ReplyReceiver -> Chat not found") // No chatId for security
            showReplyErrorNotification(context, "Chat not found", chatId, replyText)
            return
        }

        // Rate limiting check
        if (!replyRateLimiter.allowRequest(chatId)) {
            Logger.w("ReplyReceiver -> Rate limit exceeded") // No chatId for security
            showReplyErrorNotification(context, "Too many replies, please wait", chatId, replyText)
            return
        }

        if (replyText.isNullOrBlank()) {
            Logger.e("ReplyReceiver -> Empty reply text")
            showReplyErrorNotification(context, "Reply cannot be empty", chatId, replyText)
            return
        }

        // Validate message length
        if (replyText.length > 10000) {
            Logger.e("ReplyReceiver -> Message too long: ${replyText.length} chars")
            showReplyErrorNotification(context, "Message too long (max 10000 characters)", chatId, replyText)
            return
        }

        // Sanitize message (remove control characters, trim whitespace)
        val sanitizedText = replyText.trim()
            .replace(Regex("[\\x00-\\x1F\\x7F]"), "")

        if (sanitizedText.isBlank()) {
            Logger.e("ReplyReceiver -> Message empty after sanitization")
            showReplyErrorNotification(context, "Invalid message content", chatId, replyText)
            return
        }

        // Send the reply with proper error handling
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = app.container.chatRepository

                val result = repository.sendMessage(chatId, chat.peerName, sanitizedText)

                when {
                    result.isSuccess -> {
                        Logger.d("ReplyReceiver -> Reply sent successfully")
                        showReplySuccessNotification(context, sanitizedText)
                    }
                    result.isFailure -> {
                        val error = result.exceptionOrNull()
                        val errorMessage = when (error) {
                            is java.net.UnknownHostException,
                            is java.net.SocketTimeoutException,
                            is java.io.IOException -> "Network error, please check connection"
                            is java.security.GeneralSecurityException -> "Security error, please reconnect"
                            null -> "Failed to send reply"
                            else -> error.message ?: "Failed to send reply"
                        }
                        Logger.e("ReplyReceiver -> Send failed: $errorMessage")
                        showReplyErrorNotification(context, errorMessage, chatId, sanitizedText)
                        // Schedule retry with exponential backoff
                        scheduleRetry(context, chatId, sanitizedText)
                    }
                }
            } catch (e: Exception) {
                Logger.e("ReplyReceiver -> Exception sending reply", e)
                val errorMessage = when (e) {
                    is java.net.UnknownHostException,
                    is java.net.SocketTimeoutException,
                    is java.io.IOException -> "Network error, please check connection"
                    is java.security.GeneralSecurityException -> "Security error, please reconnect"
                    else -> e.message ?: "Error: Unknown error"
                }
                showReplyErrorNotification(context, errorMessage, chatId, sanitizedText)
                // Schedule retry with exponential backoff
                scheduleRetry(context, chatId, sanitizedText)
            }
        }
    }

    /**
     * Show success notification after reply is sent.
     */
    private fun showReplySuccessNotification(context: Context, replyText: String) {
        val previewText = if (replyText.length > 50) {
            "${replyText.take(50)}..."
        } else {
            replyText
        }

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Reply Sent")
            .setContentText("Reply: $previewText")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            Logger.e("ReplyReceiver -> Permission denied for success notification")
        }
    }

    /**
     * Show error notification when reply fails, with retry option.
     * @param context Application context
     * @param error Error message to display
     * @param chatId Chat identifier (optional, for retry navigation)
     * @param replyText Original reply text to preserve for retry (optional)
     */
    private fun showReplyErrorNotification(
        context: Context,
        error: String,
        chatId: String?,
        replyText: String? = null
    ) {
        val retryIntent = Intent(context, com.p2p.meshify.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chat_peer_id", it) }
            replyText?.let { putExtra("reply_text", it) } // Preserve reply text for retry
            putExtra("retry-action", true)
        }

        val retryPendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            retryIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Reply Failed")
            .setContentText(error)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(retryPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_send,
                "Retry",
                retryPendingIntent
            )
            .build()

        try {
            NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notification)
        } catch (e: SecurityException) {
            Logger.e("ReplyReceiver -> Permission denied for error notification")
        }
    }
}
