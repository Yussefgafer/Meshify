package com.p2p.meshify.core.crypto

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    @Provides
    @Singleton
    fun provideDeviceKeysetManager(
        @ApplicationContext context: Context
    ): DeviceKeysetManager = DeviceKeysetManager(context)

    @Provides
    @Singleton
    fun providePeerPublicKeyStore(): PeerPublicKeyStore = PeerPublicKeyStore()

    @Provides
    @Singleton
    fun provideMessageCipher(
        deviceKeysetManager: DeviceKeysetManager,
        peerPublicKeyStore: PeerPublicKeyStore
    ): MessageCipher = TinkMessageCipher(deviceKeysetManager, peerPublicKeyStore)
}
