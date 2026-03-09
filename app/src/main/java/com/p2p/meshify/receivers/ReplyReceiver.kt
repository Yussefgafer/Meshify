package com.p2p.meshify.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.p2p.meshify.MeshifyApp
import com.p2p.meshify.core.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.p2p.meshify.REPLY_ACTION") return

        val chatId = intent.getStringExtra("chat_id") ?: return
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

        if (!replyText.isNullOrBlank()) {
            val app = context.applicationContext as MeshifyApp
            val repository = app.container.chatRepository

            CoroutineScope(Dispatchers.IO).launch {
                repository.sendMessage(chatId, "Peer", replyText)

                val updatedNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID_MESSAGES)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("Reply Sent")
                    .setContentText(replyText)
                    .build()

                try {
                    NotificationManagerCompat.from(context).notify(chatId.hashCode(), updatedNotification)
                } catch (e: SecurityException) { }
            }
        }
    }
}
