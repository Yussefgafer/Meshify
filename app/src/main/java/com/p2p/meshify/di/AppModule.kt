package com.p2p.meshify.di

import android.content.Context
import androidx.room.Room
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.repository.PeerTrustStore
import com.p2p.meshify.core.data.security.impl.EcdhSessionManager
import com.p2p.meshify.core.data.security.impl.InMemoryNonceCache
import com.p2p.meshify.core.data.security.impl.MessageEnvelopeCrypto
import com.p2p.meshify.core.data.security.impl.PeerIdentityManagerImpl
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.core.common.util.AndroidStringResourceProvider
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.WifiStateCheckerImpl
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeshifyDatabase {
        return Room.databaseBuilder(context, MeshifyDatabase::class.java, "meshify.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    @Provides
    @Singleton
    fun provideSettingsRepository(@ApplicationContext context: Context): ISettingsRepository {
        return com.p2p.meshify.core.data.repository.SettingsRepository(context)
    }

    @Provides
    @Singleton
    fun provideFileManager(@ApplicationContext context: Context): IFileManager {
        return com.p2p.meshify.core.data.repository.FileManagerImpl(context)
    }

    @Provides
    @Singleton
    fun provideNotificationHelper(@ApplicationContext context: Context): NotificationHelper {
        return NotificationHelper(context).apply { createNotificationChannels() }
    }

    @Provides
    @Singleton
    fun providePeerIdentity(): PeerIdentityRepository {
        return PeerIdentityManagerImpl()
    }

    @Provides
    @Singleton
    fun provideNonceCache(): InMemoryNonceCache {
        return InMemoryNonceCache()
    }

    @Provides
    @Singleton
    fun providePeerTrustStore(database: MeshifyDatabase): PeerTrustStore {
        return PeerTrustStore(database.trustedPeerDao())
    }

    @Provides
    @Singleton
    fun provideEcdhSessionManager(): EcdhSessionManager {
        return EcdhSessionManager()
    }

    @Provides
    @Singleton
    fun provideMessageCrypto(
        peerIdentity: PeerIdentityRepository,
        nonceCache: InMemoryNonceCache
    ): MessageEnvelopeCrypto {
        return MessageEnvelopeCrypto(peerIdentity as PeerIdentityManagerImpl, nonceCache)
    }

    @Provides
    @Singleton
    fun provideSessionKeyStore(@ApplicationContext context: Context): EncryptedSessionKeyStore {
        return EncryptedSessionKeyStore(context)
    }

    @Provides
    @Singleton
    fun provideStringResourceProvider(@ApplicationContext context: Context): StringResourceProvider {
        return AndroidStringResourceProvider(context)
    }

    @Provides
    @Singleton
    fun provideWifiStateChecker(@ApplicationContext context: Context): WifiStateChecker {
        return WifiStateCheckerImpl(context)
    }

    @Provides
    @Singleton
    fun provideTransportManager(
        @ApplicationContext context: Context,
        settingsRepository: ISettingsRepository,
        peerIdentity: PeerIdentityRepository,
        sessionKeyStore: EncryptedSessionKeyStore
    ): TransportManager {
        return TransportManager.createDefault(
            context,
            settingsRepository,
            peerIdentity,
            sessionKeyStore
        )
    }
}
