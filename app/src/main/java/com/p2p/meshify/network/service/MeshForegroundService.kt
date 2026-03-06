package com.p2p.meshify.network.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.p2p.meshify.MeshifyApp
import com.p2p.meshify.R
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.repository.IChatRepository
import com.p2p.meshify.network.base.TransportEvent
import kotlinx.coroutines.*

/**
 * Foreground Service to keep the mesh network alive.
 * Refactored to depend on IChatRepository interface.
 */
class MeshForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var multicastLock: WifiManager.MulticastLock? = null
    
    private lateinit var chatRepository: IChatRepository

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
            val transport = app.container.lanTransport
            
            // Listen for incoming payloads globally
            launch {
                transport.events.collect { event ->
                    if (event is TransportEvent.PayloadReceived) {
                        // Corrected: PayloadReceived has 'deviceId' not 'peerId'
                        chatRepository.handleIncomingPayload(event.deviceId, event.payload)
                    }
                }
            }
            
            transport.start()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.i("Service -> onStartCommand")
        createNotificationChannel()
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_mesh_active))
            .setContentText(getString(R.string.service_searching_peers))
            .setSmallIcon(R.drawable.ic_launcher_foreground) 
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
        multicastLock?.let { if (it.isHeld) it.release() }
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
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
