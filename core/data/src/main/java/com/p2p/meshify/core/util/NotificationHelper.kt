package com.p2p.meshify.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Base64
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.p2p.meshify.core.data.local.entity.MessageEntity
import java.security.KeyStore
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey

/**
 * Helper class for managing notifications.
 */
class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID_MESSAGES = "meshify_messages"
        const val CHANNEL_ID_CONNECTIONS = "meshify_connections"
        const val KEY_TEXT_REPLY = "text_reply"
        private const val REPLY_SECRET_KEY_ALIAS = "meshify_reply_key"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ROTATION_INTERVAL_MS = 30 * 24 * 60 * 60 * 1000L // 30 days
        private const val KEY_TIMESTAMP_KEY = "meshify_key_timestamp"
        private const val PREFS_NAME = "notification_helper"
    }

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val messagesChannel = NotificationChannel(
                CHANNEL_ID_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming message notifications"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }

            val connectionsChannel = NotificationChannel(
                CHANNEL_ID_CONNECTIONS,
                "Connections",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Peer connection/disconnection notifications"
            }

            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(messagesChannel, connectionsChannel))
        }
    }

    /**
     * Get or create the HMAC secret key from Android Keystore.
     * This key is used to sign reply intents to prevent unauthorized replies.
     * Keys are automatically rotated every 30 days for enhanced security.
     */
    private fun getReplySecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val keyTimestamp = prefs.getLong(KEY_TIMESTAMP_KEY, 0L)

        // Rotate key if older than rotation interval
        if (System.currentTimeMillis() - keyTimestamp > KEY_ROTATION_INTERVAL_MS) {
            keyStore.deleteEntry(REPLY_SECRET_KEY_ALIAS)
            Logger.d("NotificationHelper -> Rotated reply secret key (age: ${(System.currentTimeMillis() - keyTimestamp) / 1000 / 60 / 60 / 24} days)")
        }

        // Check if key already exists
        val existingEntry = keyStore.getEntry(REPLY_SECRET_KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existingEntry?.secretKey != null) {
            return existingEntry.secretKey
        }

        // Generate new key
        val keyGenerator = KeyGenerator.getInstance("HmacSHA256", KEYSTORE_PROVIDER).apply {
            init(256)
        }
        return keyGenerator.generateKey().also {
            keyStore.setEntry(
                REPLY_SECRET_KEY_ALIAS,
                KeyStore.SecretKeyEntry(it),
                null
            )
            prefs.edit().putLong(KEY_TIMESTAMP_KEY, System.currentTimeMillis()).apply()
            Logger.d("NotificationHelper -> Generated new reply secret key")
        }
    }

    /**
     * Generate HMAC signature for reply intent.
     * This signature prevents unauthorized intents from sending replies.
     *
     * @param chatId The chat ID to sign
     * @param timestamp The timestamp to include in the signature (prevents replay attacks)
     * @return Base64-encoded HMAC signature
     */
    fun generateReplySignature(chatId: String, timestamp: Long): String {
        val secretKey = getReplySecretKey()
        val mac = Mac.getInstance("HmacSHA256").apply { init(secretKey) }
        // Use delimiter to prevent collision attacks
        val data = "$chatId:$timestamp"
        val signature = mac.doFinal(data.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(signature, Base64.NO_WRAP)
    }

    /**
     * Verify HMAC signature for reply intent.
     *
     * @param chatId The chat ID that was signed
     * @param signature The Base64-encoded signature to verify
     * @param timestamp The timestamp that was signed
     * @return true if signature is valid, false otherwise
     */
    fun verifyReplySignature(chatId: String, signature: String, timestamp: Long): Boolean {
        return try {
            val expectedSignature = generateReplySignature(chatId, timestamp)
            // Compare decoded bytes, not Base64 strings
            val signatureBytes = Base64.decode(signature, Base64.NO_WRAP)
            val expectedBytes = Base64.decode(expectedSignature, Base64.NO_WRAP)
            // Use constant-time comparison on raw bytes
            java.security.MessageDigest.isEqual(signatureBytes, expectedBytes)
        } catch (e: Exception) {
            Logger.e("NotificationHelper -> Signature verification failed", e)
            false
        }
    }

    /**
     * Create a notification with reply action for incoming messages.
     * Includes HMAC signature to prevent unauthorized replies.
     */
    fun showMessageNotification(senderName: String, message: MessageEntity) {
        val timestamp = System.currentTimeMillis()
        val signature = generateReplySignature(message.chatId, timestamp)

        // Create reply intent with HMAC signature for security
        val replyIntent = Intent("com.p2p.meshify.REPLY_ACTION").apply {
            putExtra("chat_id", message.chatId)
            putExtra("signature", signature)
            putExtra("timestamp", timestamp)
        }

        // Create PendingIntent for reply action
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            message.chatId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create remote input for inline reply
        val remoteInput = androidx.core.app.RemoteInput.Builder(KEY_TEXT_REPLY).apply {
            setLabel("Type your reply...")
        }.build()

        // Create reply action
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        // Note: Intent will be set by the app module via setter injection
        // to avoid circular dependency
        val chatIntent = Intent("com.p2p.meshify.OPEN_CHAT").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_peer_id", message.chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, message.chatId.hashCode(), chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID_MESSAGES)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(senderName)
            .setContentText(message.text ?: "[Image]")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .addAction(replyAction)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(message.chatId.hashCode(), notification)
        } catch (e: SecurityException) {
            Logger.e("NotificationHelper -> Permission denied for notification")
        }
    }
}
