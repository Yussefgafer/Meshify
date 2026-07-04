package com.p2p.meshify.di

import android.content.Context
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.core.network.ble.BleTransportImpl
import com.p2p.meshify.domain.repository.ISettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    fun provideBleTransport(
        @ApplicationContext context: Context,
        settingsRepository: ISettingsRepository,
        peerIdProvider: SimplePeerIdProvider
    ): BleTransportImpl {
        val peerId = peerIdProvider.getPeerId()
        val deviceName = runBlocking {
            settingsRepository.displayName.first()
        }
        return BleTransportImpl(context, settingsRepository, peerId, deviceName)
    }
}
