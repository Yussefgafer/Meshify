package com.p2p.meshify

import android.content.Context
import androidx.room.Room
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.data.local.MeshifyDatabase
import com.p2p.meshify.data.repository.ChatRepositoryImpl
import com.p2p.meshify.data.repository.FileManagerImpl
import com.p2p.meshify.data.repository.SettingsRepository
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.network.base.IMeshTransport
import com.p2p.meshify.network.lan.LanTransportImpl
import com.p2p.meshify.network.lan.SocketManager

/**
 * Manual Dependency Injection Container.
 * Refactored for Clean Architecture.
 */
class AppContainer(private val context: Context) {
    
    init {
        Logger.d("AppContainer -> Creating AppContainer")
    }

    private val database: MeshifyDatabase by lazy {
        Logger.d("AppContainer -> Initializing Database (lazy)")
        val db = Room.databaseBuilder(
            context,
            MeshifyDatabase::class.java,
            "meshify_db"
        ).fallbackToDestructiveMigration().build()
        Logger.d("AppContainer -> Database initialized SUCCESS")
        db
    }

    private val socketManager: SocketManager by lazy {
        Logger.d("AppContainer -> Initializing SocketManager (lazy)")
        val sm = SocketManager()
        Logger.d("AppContainer -> SocketManager initialized SUCCESS")
        sm
    }

    val fileManager: IFileManager by lazy {
        Logger.d("AppContainer -> Initializing FileManager (lazy)")
        val fm = FileManagerImpl(context)
        Logger.d("AppContainer -> FileManager initialized SUCCESS")
        fm
    }

    val settingsRepository: ISettingsRepository by lazy {
        Logger.d("AppContainer -> Initializing SettingsRepository (lazy)")
        val sr = SettingsRepository(context)
        Logger.d("AppContainer -> SettingsRepository initialized SUCCESS")
        sr
    }

    val lanTransport: IMeshTransport by lazy {
        Logger.d("AppContainer -> Initializing LanTransport (lazy)")
        val transport = LanTransportImpl(context, socketManager, settingsRepository)
        Logger.d("AppContainer -> LanTransport initialized SUCCESS")
        transport
    }

    // Main Repository
    val chatRepository: IChatRepository by lazy {
        Logger.d("AppContainer -> Initializing ChatRepository (lazy)")
        val repo = ChatRepositoryImpl(
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            meshTransport = lanTransport,
            settingsRepository = settingsRepository,
            fileManager = fileManager
        )
        Logger.d("AppContainer -> ChatRepository initialized SUCCESS")
        repo
    }

    // USE CASES
    val getMessagesUseCase by lazy {
        Logger.d("AppContainer -> Initializing GetMessagesUseCase (lazy)")
        com.p2p.meshify.domain.usecase.GetMessagesUseCase(chatRepository)
    }
    
    val sendMessageUseCase by lazy {
        Logger.d("AppContainer -> Initializing SendMessageUseCase (lazy)")
        com.p2p.meshify.domain.usecase.SendMessageUseCase(chatRepository)
    }
    
    val deleteMessagesUseCase by lazy {
        Logger.d("AppContainer -> Initializing DeleteMessagesUseCase (lazy)")
        com.p2p.meshify.domain.usecase.DeleteMessagesUseCase(chatRepository)
    }
}
