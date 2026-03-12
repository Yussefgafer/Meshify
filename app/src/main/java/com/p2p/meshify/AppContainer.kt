package com.p2p.meshify

import android.content.Context
import androidx.room.Room
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.data.repository.FileManagerImpl
import com.p2p.meshify.core.data.repository.SettingsRepository
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.lan.LanTransportImpl
import com.p2p.meshify.core.network.lan.SocketManager
import com.p2p.meshify.core.network.service.MessageQueueService
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {

    private val database: MeshifyDatabase by lazy {
        Room.databaseBuilder(context, MeshifyDatabase::class.java, "meshify.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    val settingsRepository: ISettingsRepository by lazy {
        SettingsRepository(context)
    }

    val fileManager: IFileManager by lazy {
        FileManagerImpl(context)
    }

    val notificationHelper: NotificationHelper by lazy {
        NotificationHelper(context).apply { createNotificationChannels() }
    }

    private val socketManager: SocketManager by lazy {
        SocketManager()
    }

    val lanTransport: IMeshTransport by lazy {
        LanTransportImpl(context, socketManager, settingsRepository)
    }

    val chatRepository: IChatRepository by lazy {
        ChatRepositoryImpl(
            database.chatDao(),
            database.messageDao(),
            database.pendingMessageDao(),
            lanTransport,
            fileManager,
            notificationHelper,
            settingsRepository
        )
    }

    private val messageQueueService: MessageQueueService by lazy {
        MessageQueueService(chatRepository, lanTransport)
    }

    init {
        messageQueueService.start()
        CoroutineScope(Dispatchers.IO).launch {
            lanTransport.events.collect { event ->
                if (event is com.p2p.meshify.core.network.base.TransportEvent.PayloadReceived) {
                    chatRepository.handleIncomingPayload(event.deviceId, event.payload)
                }
            }
        }
    }
}
