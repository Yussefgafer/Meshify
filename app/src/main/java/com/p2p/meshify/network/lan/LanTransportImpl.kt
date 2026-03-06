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
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

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
    
    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    val onlinePeers: StateFlow<Set<String>> = _onlinePeers.asStateFlow()

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    val typingPeers: StateFlow<Set<String>> = _typingPeers.asStateFlow()

    private val resolvingPeers = mutableSetOf<String>()
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
            _events.emit(TransportEvent.DeviceDiscovered(senderId, name, address))
            val myName = settingsRepository.displayName.first()
            sendPayload(senderId, Payload(
                senderId = settingsRepository.getDeviceId(),
                type = Payload.PayloadType.HANDSHAKE,
                data = "HELO_$myName".toByteArray()
            ))
        }
        _events.emit(TransportEvent.PayloadReceived(senderId, payload))
    }

    private fun updateOnlinePeers() {
        _onlinePeers.value = peerMap.keys.toSet()
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = scope.launch {
            while (isActive) {
                startDiscovery()
                delay(AppConfig.DISCOVERY_SCAN_INTERVAL_MS)
            }
        }
    }

    override suspend fun stop() {
        visibilityJob?.cancel()
        watchdogJob?.cancel()
        unregisterService()
        stopDiscovery()
        socketManager.stopListening()
        scope.cancel()
    }

    override suspend fun startDiscovery() {
        if (discoveryListener != null) return 
        discoveryListener = createDiscoveryListener()
        try {
            nsdManager.discoverServices(AppConfig.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Logger.e("NSD -> Discovery Start Failed", e)
        }
    }

    private fun stopDiscovery() {
        discoveryListener?.let {
            try { nsdManager.stopServiceDiscovery(it) } catch (e: Exception) { }
            discoveryListener = null
        }
    }

    override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
        val ipAddress = peerMap[targetDeviceId] ?: return Result.failure(Exception("Peer offline"))
        return socketManager.sendPayload(ipAddress, payload)
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
            val address = info.host.hostAddress ?: return
            val name = info.serviceName
            resolvingPeers.remove(name)
            val peerId = name.removePrefix("Meshify_")
            
            scope.launch {
                val myId = settingsRepository.getDeviceId()
                if (peerId == myId) return@launch 
                peerMap[peerId] = address
                updateOnlinePeers()
                _events.emit(TransportEvent.DeviceDiscovered(peerId, "Peer_${peerId.take(4)}", address))
            }
        }
    }
}
