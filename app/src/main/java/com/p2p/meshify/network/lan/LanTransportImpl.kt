package com.p2p.meshify.network.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.network.base.IMeshTransport
import com.p2p.meshify.network.base.TransportEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections.newSetFromMap

/**
 * Professional LAN Transport with Reactive Visibility and Peer Tracking.
 */
class LanTransportImpl(
    private val context: Context,
    private val socketManager: SocketManager,
    private val settingsRepository: ISettingsRepository
) : IMeshTransport {

    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 128)
    override val events = _events.asSharedFlow()

    private val peerMap = ConcurrentHashMap<String, String>()

    // Dead Peer Detection: track consecutive send failures per peer
    private val failedSendCounts = ConcurrentHashMap<String, AtomicInteger>()

    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    val onlinePeers: StateFlow<Set<String>> = _onlinePeers.asStateFlow()

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    val typingPeers: StateFlow<Set<String>> = _typingPeers.asStateFlow()

    // Thread-safe set using ConcurrentHashMap-backed set
    private val resolvingPeers = newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Discovery enabled flag - controls watchdog restart behavior
    @Volatile
    private var discoveryEnabled = false

    // Dead peer threshold: remove peer after this many consecutive failures
    companion object {
        private const val MAX_FAILURES_BEFORE_REMOVAL = 3
    }
    
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null
    private var visibilityJob: Job? = null

    override suspend fun start() {
        val myId = settingsRepository.getDeviceId()
        
        Logger.i("LanEngine -> Starting. MyID: $myId")
        
        // REACTIVE VISIBILITY: Register/Unregister service based on settings
        visibilityJob?.cancel()
        visibilityJob = scope.launch {
            combine(
                settingsRepository.isNetworkVisible,
                settingsRepository.displayName
            ) { visible, name -> visible to name }
                .collect { (visible, name) ->
                    if (visible) {
                        registerService(myId, name)
                    } else {
                        unregisterService()
                    }
                }
        }
        
        scope.launch {
            socketManager.incomingPayloads.collect { (address, payload) ->
                val senderId = payload.senderId
                peerMap[senderId] = address
                updateOnlinePeers()
                
                when (payload.type) {
                    Payload.PayloadType.SYSTEM_CONTROL -> handleSystemCommand(senderId, String(payload.data))
                    Payload.PayloadType.HANDSHAKE -> handleHandshake(senderId, address, payload)
                    else -> _events.emit(TransportEvent.PayloadReceived(senderId, payload))
                }
            }
        }
        
        scope.launch { socketManager.startListening() }
        
        // Start discovery immediately, then let watchdog handle periodic restarts
        scope.launch { startDiscovery() }
        
        startWatchdog()
    }

    private fun handleSystemCommand(senderId: String, command: String) {
        when (command) {
            "TYPING_ON" -> _typingPeers.update { it + senderId }
            "TYPING_OFF" -> _typingPeers.update { it - senderId }
        }
    }

    private suspend fun handleHandshake(senderId: String, address: String, payload: Payload) {
        val name = String(payload.data).removePrefix("HELO_")
        if (!peerMap.containsKey(senderId)) {
            // Estimate RSSI from connection quality (simulated for LAN)
            // In real WiFi Direct scenarios, this would come from WiFiManager
            val estimatedRssi = estimateRssiFromAddress(address)
            _events.emit(TransportEvent.DeviceDiscovered(senderId, name, address, estimatedRssi))
            val myName = settingsRepository.displayName.first()
            sendPayload(senderId, Payload(
                senderId = settingsRepository.getDeviceId(),
                type = Payload.PayloadType.HANDSHAKE,
                data = "HELO_$myName".toByteArray()
            ))
        }
        _events.emit(TransportEvent.PayloadReceived(senderId, payload))
    }

    /**
     * Estimate RSSI from IP address proximity (simulated for LAN transport).
     * Real WiFi Direct implementations should use actual WiFi signal strength.
     */
    private fun estimateRssiFromAddress(address: String): Int {
        // Simulated RSSI: -40 to -80 dBm range
        // In production, this should come from WiFiManager.calculateSignalLevel()
        return -55 // Default to good signal for LAN peers
    }

    private fun updateOnlinePeers() {
        _onlinePeers.value = peerMap.keys.toSet()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                delay(AppConfig.DISCOVERY_SCAN_INTERVAL_MS)
                // Only restart if discovery is still enabled
                if (discoveryEnabled) {
                    stopDiscovery()
                    startDiscovery()
                } else {
                    break // Exit loop if discovery was disabled
                }
            }
        }
    }

    override suspend fun stop() {
        discoveryEnabled = false
        visibilityJob?.cancel()
        watchdogJob?.cancel()
        unregisterService()
        stopDiscovery()
        socketManager.stopListening()
        scope.cancel()
    }

    override suspend fun startDiscovery() {
        if (discoveryListener != null) return
        
        // Mark discovery as enabled
        discoveryEnabled = true
        discoveryListener = createDiscoveryListener()
        try {
            nsdManager.discoverServices(AppConfig.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            Logger.d("NSD -> Discovery started")
        } catch (e: Exception) {
            Logger.e("NSD -> Discovery Start Failed", e)
            discoveryListener = null
        }
    }

    override suspend fun stopDiscovery() {
        // Mark discovery as disabled - prevents watchdog restart
        discoveryEnabled = false
        
        try {
            discoveryListener?.let {
                nsdManager.stopServiceDiscovery(it)
            }
        } catch (e: Exception) {
            Logger.e("NSD -> Stop Discovery Failed", e)
        } finally {
            discoveryListener = null
            resolvingPeers.clear()
        }
        
        Logger.d("NSD -> Discovery stopped")
    }

    override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
        val ipAddress = peerMap[targetDeviceId]
            ?: return Result.failure(Exception("Peer offline"))

        val result = socketManager.sendPayload(ipAddress, payload)

        // Dead Peer Detection: track consecutive failures
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            // Only count network-related failures (timeout, connection refused)
            if (exception is SocketTimeoutException || exception is ConnectException) {
                val failureCount = failedSendCounts.getOrPut(targetDeviceId) { AtomicInteger(0) }
                val count = failureCount.incrementAndGet()

                Logger.w("LanTransport -> Send failed to $targetDeviceId (count: $count)")

                // Remove peer if failures exceed threshold
                if (count >= MAX_FAILURES_BEFORE_REMOVAL) {
                    Logger.w("LanTransport -> Marking peer $targetDeviceId as dead after $count failures")
                    peerMap.remove(targetDeviceId)
                    failedSendCounts.remove(targetDeviceId)
                    _typingPeers.update { it - targetDeviceId }
                    _onlinePeers.update { it - targetDeviceId }
                    scope.launch {
                        _events.emit(TransportEvent.DeviceLost(targetDeviceId))
                    }
                }
            }
        } else {
            // Reset failure count on success
            failedSendCounts.remove(targetDeviceId)
        }

        return result
    }

    private fun registerService(uuid: String, name: String) {
        unregisterService() // Ensure old one is gone
        
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "Meshify_${uuid}"
            serviceType = AppConfig.SERVICE_TYPE
            port = AppConfig.DEFAULT_PORT
        }
        
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Logger.i("NSD -> Local Service Registered: ${info.serviceName}")
            }
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Logger.e("NSD -> Registration Failed: $errorCode")
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        
        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        } catch (e: Exception) {
            Logger.e("NSD -> Register Service Failed", e)
        }
    }

    private fun unregisterService() {
        registrationListener?.let {
            try { nsdManager.unregisterService(it) } catch (e: Exception) {}
            registrationListener = null
        }
    }

    private fun createDiscoveryListener() = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String) {}
        override fun onServiceFound(serviceInfo: NsdServiceInfo) {
            val name = serviceInfo.serviceName
            if (name.startsWith("Meshify_") && !resolvingPeers.contains(name)) {
                resolvingPeers.add(name)
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, createResolveListener())
            }
        }
        override fun onServiceLost(serviceInfo: NsdServiceInfo) {
            val name = serviceInfo.serviceName
            resolvingPeers.remove(name)
            if (name.startsWith("Meshify_")) {
                val peerId = name.removePrefix("Meshify_")
                peerMap.remove(peerId)
                _typingPeers.update { it - peerId }
                updateOnlinePeers()
                scope.launch { _events.emit(TransportEvent.DeviceLost(peerId)) }
            }
        }
        override fun onDiscoveryStopped(regType: String) {}
        override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
            Logger.e("NSD -> Start Discovery Failed: $errorCode")
            discoveryListener = null
        }
        override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
            discoveryListener = null
        }
    }

    private fun createResolveListener() = object : NsdManager.ResolveListener {
        override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
            resolvingPeers.remove(info.serviceName)
        }
        override fun onServiceResolved(info: NsdServiceInfo) {
            @Suppress("DEPRECATION")
            val address = info.host.hostAddress ?: return
            val name = info.serviceName
            resolvingPeers.remove(name)
            val peerId = name.removePrefix("Meshify_")

            scope.launch {
                val myId = settingsRepository.getDeviceId()
                if (peerId == myId) return@launch
                peerMap[peerId] = address
                updateOnlinePeers()
                // Estimate RSSI for discovered peer
                val estimatedRssi = estimateRssiFromAddress(address)
                _events.emit(TransportEvent.DeviceDiscovered(peerId, "Peer_${peerId.take(4)}", address, estimatedRssi))
            }
        }
    }
}
