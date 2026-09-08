package com.p2p.meshify.di

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.p2p.meshify.core.common.security.SimplePeerIdProvider
import com.p2p.meshify.core.common.util.StringResourceProvider
import com.p2p.meshify.core.crypto.CryptoModule
import com.p2p.meshify.core.crypto.DeviceKeysetManager
import com.p2p.meshify.core.crypto.MessageCipher
import com.p2p.meshify.core.crypto.PeerPublicKeyStore
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.local.dao.ChatDao
import com.p2p.meshify.core.data.local.dao.MessageDao
import com.p2p.meshify.core.data.local.dao.PendingMessageDao
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.network.TransportManager
import com.p2p.meshify.core.network.ble.BleTransportImpl
import com.p2p.meshify.core.network.WifiStateCheckerImpl
import com.p2p.meshify.core.util.NotificationHelper
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.domain.repository.IFileManager
import com.p2p.meshify.domain.repository.ISettingsRepository
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
/**
 * DI graph smoke: runs every @Provides method in the order Hilt resolves them,
 * against a real Robolectric Context. Catches regressions that Dagger's
 * compile-time check cannot — a provider constructor that throws at runtime, a
 * @Provides returning null, or swapped arguments silently producing a mis-wired
 * graph.
 *
 * Hilt itself validates the graph at compile time (the app builds), so the
 * value here is runtime instantiation, not binding resolution.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [33])
class DiGraphSmokeTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `all provider methods construct non-null instances`() {
        // ---- Crypto chain (core:crypto) ----
        val keysetManager = CryptoModule.provideDeviceKeysetManager(context)
        val peerPublicKeyStore = CryptoModule.providePeerPublicKeyStore()
        val messageCipher: MessageCipher =
            CryptoModule.provideMessageCipher(keysetManager, peerPublicKeyStore)
        assertNotNull(keysetManager)
        assertNotNull(peerPublicKeyStore)
        assertNotNull(messageCipher)

        // ---- AppModule ----
        val settingsRepository: ISettingsRepository = AppModule.provideSettingsRepository(context)
        val peerIdProvider = AppModule.providePeerIdProvider(context)
        val stringProvider: StringResourceProvider = AppModule.provideStringResourceProvider(context)
        val database: MeshifyDatabase = AppModule.provideDatabase(context)
        val fileManager: IFileManager = AppModule.provideFileManager(context)
        val notificationHelper = AppModule.provideNotificationHelper(context)
        val wifiStateChecker = AppModule.provideWifiStateChecker(context)
        assertNotNull(settingsRepository)
        assertNotNull(peerIdProvider)
        assertNotNull(stringProvider)
        assertNotNull(database)
        assertNotNull(fileManager)
        assertNotNull(notificationHelper)
        assertNotNull(wifiStateChecker)

        // ---- NetworkModule ----
        val bleTransport: BleTransportImpl = NetworkModule.provideBleTransport(
            context, settingsRepository, peerIdProvider, peerPublicKeyStore
        )
        assertNotNull(bleTransport)

        // ---- TransportManager (AppModule) — depends on the crypto chain ----
        val transportManager: TransportManager = AppModule.provideTransportManager(
            context, settingsRepository, peerIdProvider, messageCipher, peerPublicKeyStore
        )
        assertNotNull(transportManager)

        // ---- RepositoryModule DAOs ----
        val chatDao: ChatDao = RepositoryModule.provideChatDao(database)
        val messageDao: MessageDao = RepositoryModule.provideMessageDao(database)
        val pendingMessageDao: PendingMessageDao = RepositoryModule.providePendingMessageDao(database)
        assertNotNull(chatDao)
        assertNotNull(messageDao)
        assertNotNull(pendingMessageDao)

        // ---- ChatRepositoryImpl (heaviest node: wires the whole graph) ----
        val chatRepository: ChatRepositoryImpl = RepositoryModule.provideChatRepository(
            context = context,
            stringProvider = stringProvider,
            database = database,
            chatDao = chatDao,
            messageDao = messageDao,
            pendingMessageDao = pendingMessageDao,
            transportManager = transportManager,
            fileManager = fileManager,
            notificationHelper = notificationHelper,
            settingsRepository = settingsRepository,
            messageCipher = messageCipher
        )
        val iChatRepository: IChatRepository =
            RepositoryModule.provideChatRepositoryAsInterface(chatRepository)
        assertNotNull(chatRepository)
        assertNotNull(iChatRepository)
        assertSame("Interface binding must be the same singleton instance", chatRepository, iChatRepository)

        // ---- Sanity: the default hub is wired with the LAN transport only ----
        assertNotNull("LAN transport must be registered by createDefault", transportManager.getTransport("lan"))
        assertTrue(transportManager.getTransport("ble") == null)
    }
}