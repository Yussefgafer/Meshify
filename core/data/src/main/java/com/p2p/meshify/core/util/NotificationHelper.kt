package com.p2p.meshify.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.p2p.meshify.core.data.local.entity.MessageEntity

/**
 * Helper class for managing notifications.
 */
class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID_MESSAGES = "meshify_messages"
        const val CHANNEL_ID_CONNECTIONS = "meshify_connections"
        const val KEY_TEXT_REPLY = "text_reply"
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

    fun showMessageNotification(senderName: String, message: MessageEntity) {
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
            .build()

        try {
            NotificationManagerCompat.from(context).notify(message.chatId.hashCode(), notification)
        } catch (e: SecurityException) {
            Logger.e("NotificationHelper -> Permission denied for notification")
        }
    }
}
