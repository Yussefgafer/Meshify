package com.p2p.meshify

import android.content.Context
import androidx.room.Room
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

    private val database: MeshifyDatabase by lazy {
        Room.databaseBuilder(
            context,
            MeshifyDatabase::class.java,
            "meshify_db"
        ).fallbackToDestructiveMigration().build()
    }

    private val socketManager: SocketManager by lazy {
        SocketManager()
    }

    val fileManager: IFileManager by lazy {
        FileManagerImpl(context)
    }

    val settingsRepository: ISettingsRepository by lazy {
        SettingsRepository(context)
    }

    val lanTransport: IMeshTransport by lazy {
        LanTransportImpl(context, socketManager, settingsRepository)
    }

    // Main Repository
    val chatRepository: IChatRepository by lazy {
        ChatRepositoryImpl(
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            meshTransport = lanTransport,
            settingsRepository = settingsRepository,
            fileManager = fileManager
        )
    }

    // USE CASES
    val getMessagesUseCase by lazy { com.p2p.meshify.domain.usecase.GetMessagesUseCase(chatRepository) }
    val sendMessageUseCase by lazy { com.p2p.meshify.domain.usecase.SendMessageUseCase(chatRepository) }
    val deleteMessagesUseCase by lazy { com.p2p.meshify.domain.usecase.DeleteMessagesUseCase(chatRepository) }
}
