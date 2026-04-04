package com.p2p.meshify

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.p2p.meshify.core.data.local.MeshifyDatabase
import com.p2p.meshify.core.data.repository.ChatRepositoryImpl
import com.p2p.meshify.core.data.repository.FileManagerImpl
import com.p2p.meshify.core.data.repository.PeerTrustStore
import com.p2p.meshify.core.data.repository.SettingsRepository
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
import com.p2p.meshify.core.network.base.TransportEvent
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

    val database: MeshifyDatabase by lazy {
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

    // Security Components
    val peerIdentity: PeerIdentityManagerImpl by lazy {
        PeerIdentityManagerImpl()
    }

    val nonceCache: InMemoryNonceCache by lazy {
        InMemoryNonceCache()
    }

    val peerTrustStore: PeerTrustStore by lazy {
        PeerTrustStore(database.trustedPeerDao())
    }

    val ecdhSessionManager: EcdhSessionManager by lazy {
        EcdhSessionManager()
    }

    val messageCrypto: MessageEnvelopeCrypto by lazy {
        MessageEnvelopeCrypto(peerIdentity, nonceCache)
    }

    // ✅ Shared EncryptedSessionKeyStore - single instance for all components
    val sessionKeyStore: EncryptedSessionKeyStore by lazy {
        EncryptedSessionKeyStore(context)
    }

    // String Resource Provider for core:data module
    val stringResourceProvider: StringResourceProvider by lazy {
        AndroidStringResourceProvider(context)
    }

    // Wi-Fi State Checker
    val wifiStateChecker: WifiStateChecker by lazy {
        WifiStateCheckerImpl(context)
    }

    // ✅ Transport Manager - manages all transport protocols (LAN, BT, WiFi-Direct, DHT)
    val transportManager: TransportManager by lazy {
        TransportManager.createDefault(context, settingsRepository, peerIdentity, sessionKeyStore)
    }

    val chatRepository: ChatRepositoryImpl by lazy {
        ChatRepositoryImpl(
            context = context,
            stringProvider = stringResourceProvider,
            database = database,
            chatDao = database.chatDao(),
            messageDao = database.messageDao(),
            pendingMessageDao = database.pendingMessageDao(),
            transportManager = transportManager,
            fileManager = fileManager,
            notificationHelper = notificationHelper,
            settingsRepository = settingsRepository,
            peerIdentity = peerIdentity,
            messageCrypto = messageCrypto,
            ecdhSessionManager = ecdhSessionManager,
            sessionKeyStore = sessionKeyStore
        )
    }

    init {
        // Start all transports
        containerScope.launch {
            transportManager.startAllTransports()
        }

        // Start discovery on all transports
        containerScope.launch {
            transportManager.startDiscoveryOnAll()
        }

        // Collect events from ALL transports (merged flow)
        containerScope.launch {
            transportManager.getAllEventsFlow().collect { event ->
                when (event) {
                    is TransportEvent.PayloadReceived -> {
                        Logger.d("AppContainer -> Received payload from ${event.deviceId}, type=${event.payload.type}")
                        chatRepository.handleIncomingPayload(event.deviceId, event.payload)
                    }
                    is TransportEvent.DeviceDiscovered -> {
                        Logger.i("AppContainer -> Device discovered: ${event.deviceId} at ${event.address} via ${event.rssi} dBm")
                    }
                    is TransportEvent.DeviceLost -> {
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

        // Stop all transports
        containerScope.launch {
            transportManager.stopAllTransports()
        }

        // Close chat repository to cancel its internal scope
        chatRepository.close()

        Log.d("AppContainer", "Cleanup completed")
    }
}
