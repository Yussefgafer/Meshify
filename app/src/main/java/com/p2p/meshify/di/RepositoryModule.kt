package com.p2p.meshify.di

import android.content.Context
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideChatDao(database: MeshifyDatabase): ChatDao = database.chatDao()

    @Provides
    @Singleton
    fun provideMessageDao(database: MeshifyDatabase): MessageDao = database.messageDao()

    @Provides
    @Singleton
    fun providePendingMessageDao(database: MeshifyDatabase): PendingMessageDao = database.pendingMessageDao()

    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext context: Context,
        stringProvider: StringResourceProvider,
        database: MeshifyDatabase,
        chatDao: ChatDao,
        messageDao: MessageDao,
        pendingMessageDao: PendingMessageDao,
        transportManager: TransportManager,
        fileManager: IFileManager,
        notificationHelper: NotificationHelper,
        settingsRepository: ISettingsRepository
    ): ChatRepositoryImpl {
        return ChatRepositoryImpl(
            context = context,
            stringProvider = stringProvider,
            database = database,
            chatDao = chatDao,
            messageDao = messageDao,
            pendingMessageDao = pendingMessageDao,
            transportManager = transportManager,
            fileManager = fileManager,
            notificationHelper = notificationHelper,
            settingsRepository = settingsRepository
        )
    }

    @Provides
    @Singleton
    fun provideChatRepositoryAsInterface(repo: ChatRepositoryImpl): IChatRepository = repo
}
