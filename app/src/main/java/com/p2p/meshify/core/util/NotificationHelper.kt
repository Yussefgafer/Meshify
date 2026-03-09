package com.p2p.meshify.core.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.p2p.meshify.MainActivity
import com.p2p.meshify.R
import com.p2p.meshify.data.local.entity.MessageEntity
import com.p2p.meshify.receivers.ReplyReceiver

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_ID_MESSAGES = "meshify_messages"
        const val CHANNEL_ID_CONNECTIONS = "meshify_connections"
        const val KEY_TEXT_REPLY = "key_text_reply"
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
        val chatIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("chat_peer_id", message.chatId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context, message.chatId.hashCode(), chatIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyAction = createReplyAction(message)

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

    private fun createReplyAction(message: MessageEntity): NotificationCompat.Action {
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Type your reply...")
            .build()

        val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
            action = "com.p2p.meshify.REPLY_ACTION"
            putExtra("message_id", message.id)
            putExtra("chat_id", message.chatId)
            putExtra("peer_name", "Peer") // Fallback
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            message.chatId.hashCode(),
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        return NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()
    }
}
