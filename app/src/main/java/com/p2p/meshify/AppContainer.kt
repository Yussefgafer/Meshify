package com.p2p.meshify

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.data.repository.FileManagerImpl
import com.p2p.meshify.core.data.repository.SettingsRepository
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.network.lan.LanTransportImpl
import com.p2p.meshify.core.network.lan.SocketManager
import com.p2p.meshify.core.network.service.MessageQueueService
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppContainer(private val context: Context) {

    // Container scope with lifecycle management
    private val containerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

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

        // Collect events with proper lifecycle management
        containerScope.launch {
            lanTransport.events.collect { event ->
                when (event) {
                    is com.p2p.meshify.core.network.base.TransportEvent.PayloadReceived -> {
                        Logger.d("AppContainer -> Received payload from ${event.deviceId}, type=${event.payload.type}")
                        chatRepository.handleIncomingPayload(event.deviceId, event.payload)
                    }
                    is com.p2p.meshify.core.network.base.TransportEvent.DeviceDiscovered -> {
                        Logger.i("AppContainer -> Device discovered: ${event.deviceId} at ${event.address}")
                    }
                    is com.p2p.meshify.core.network.base.TransportEvent.DeviceLost -> {
                        Logger.w("AppContainer -> Device lost: ${event.deviceId}")
                    }
                    else -> {
                        Logger.d("AppContainer -> Transport event: ${event::class.simpleName}")
                    }
                }
            }
        }
    }
    
    /**
     * Cleanup all resources when application is terminating.
     * Call this from Application.onTerminate() for testing or when app is shutting down.
     */
    fun cleanup() {
        Log.d("AppContainer", "Starting cleanup...")
        containerScope.cancel() // Cancel all coroutines
        socketManager.stopListening()
        // lanTransport.stop() is suspend, so we can't call it here directly
        // It will be stopped when socketManager stops
        Log.d("AppContainer", "Cleanup completed")
    }
}
