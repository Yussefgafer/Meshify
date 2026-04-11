package com.p2p.meshify.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.repository.PeerTrustStore
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.core.common.util.AndroidStringResourceProvider
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.WifiStateCheckerImpl
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.util.NotificationHelper
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
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MeshifyDatabase {
        val migration5to6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE chats ADD COLUMN unreadCount INTEGER NOT NULL DEFAULT 0")
            }
        }

        return Room.databaseBuilder(context, MeshifyDatabase::class.java, "meshify.db")
            .addMigrations(migration5to6)
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
    fun providePeerIdProvider(@ApplicationContext context: Context): SimplePeerIdProvider {
        return SimplePeerIdProvider(context)
    }

    @Provides
    @Singleton
    fun providePeerTrustStore(database: MeshifyDatabase): PeerTrustStore {
        return PeerTrustStore(database.trustedPeerDao())
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
        peerIdProvider: SimplePeerIdProvider
    ): TransportManager {
        return TransportManager.createDefault(
            context,
            settingsRepository,
            peerIdProvider
        )
    }
}
