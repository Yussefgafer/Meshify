package com.p2p.meshify.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.repository.IChatRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.TimeoutCancellationException
import com.p2p.meshify.MeshifyApp

/**
 * Foreground Service to keep the mesh network alive.
 * Refactored to depend on IChatRepository interface.
 */
class MeshForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val shutdownScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var multicastLock: WifiManager.MulticastLock? = null

    private lateinit var chatRepository: IChatRepository
    private var transportStarted = false

    override fun onCreate() {
        super.onCreate()
        Logger.i("Service -> onCreate")

        val app = application as MeshifyApp
        chatRepository = app.container.chatRepository

        acquireMulticastLock()
        startMeshNetwork()
    }

    private fun startMeshNetwork() {
        serviceScope.launch {
            val app = application as MeshifyApp
            val transportManager = app.container.transportManager

            // Listen for incoming payloads globally
            launch {
                transportManager.getAllEventsFlow().collect { event ->
                    if (event is TransportEvent.PayloadReceived) {
                        // Corrected: PayloadReceived has 'deviceId' not 'peerId'
                        chatRepository.handleIncomingPayload(event.deviceId, event.payload)
                    }
                }
            }

            transportManager.startAllTransports()
            transportStarted = true
        }
    }

    /**
     * Deterministic shutdown - stops transport BEFORE cancelling scope.
     * Uses runBlocking with timeout to prevent ANR if stopAllTransports() hangs.
     */
    private fun stopMeshNetworkDeterministic() {
        if (!transportStarted) return

        Logger.i("Service -> Stopping transport deterministically...")

        // Use runBlocking with timeout to prevent ANR
        runBlocking(Dispatchers.IO) {
            try {
                withTimeout(3000L) { // 3 second timeout
                    val app = application as MeshifyApp
                    app.container.transportManager.stopAllTransports()
                }
                transportStarted = false
                Logger.i("Service -> Transport stopped successfully")
            } catch (e: TimeoutCancellationException) {
                Logger.e("Service -> Transport stop timed out after 3s, forcing shutdown")
                transportStarted = false
            } catch (e: Exception) {
                Logger.e("Service -> Failed to stop transport", e)
                transportStarted = false
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("Service -> onStartCommand")
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(com.p2p.meshify.core.common.R.string.service_mesh_active))
            .setContentText(getString(com.p2p.meshify.core.common.R.string.service_searching_peers))
            .setSmallIcon(com.p2p.meshify.core.common.R.drawable.ic_launcher_foreground)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        return START_STICKY
    }

    private fun acquireMulticastLock() {
        val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifi.createMulticastLock("MeshifyLock").apply {
            setReferenceCounted(true)
            acquire()
        }
        Logger.d("Service -> Multicast Lock Acquired")
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.i("Service -> onDestroy")
        // 1. Stop transport FIRST (deterministic, blocking with timeout)
        stopMeshNetworkDeterministic()
        // 2. Release multicast lock
        multicastLock?.let { if (it.isHeld) it.release() }
        // 3. Cancel coroutine scopes
        shutdownScope.cancel()
        serviceScope.cancel()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Logger.i("Service -> onTaskRemoved - App removed from recents")
        // 1. Stop transport FIRST (deterministic, blocking with timeout)
        stopMeshNetworkDeterministic()
        // 2. Release multicast lock
        multicastLock?.let { if (it.isHeld) it.release() }
        // 3. Cancel coroutine scopes
        shutdownScope.cancel()
        serviceScope.cancel()
        // 4. Stop the service
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(com.p2p.meshify.core.common.R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "mesh_service_channel"
        private const val NOTIFICATION_ID = 101

        fun start(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MeshForegroundService::class.java)
            context.stopService(intent)
        }
    }
}
