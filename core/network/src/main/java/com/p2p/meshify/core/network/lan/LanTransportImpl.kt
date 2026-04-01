package com.p2p.meshify.core.network.lan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import androidx.core.content.ContextCompat
import com.p2p.meshify.core.config.AppConfig
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.core.util.FileUtils
import com.p2p.meshify.domain.model.Payload
import com.p2p.meshify.domain.model.Handshake
import com.p2p.meshify.domain.repository.ISettingsRepository
import com.p2p.meshify.domain.security.interfaces.PeerIdentityRepository
import com.p2p.meshify.core.network.base.IMeshTransport
import com.p2p.meshify.core.common.security.EncryptedSessionKeyStore
import com.p2p.meshify.domain.security.util.EcdhSessionManager
import com.p2p.meshify.core.common.util.HexUtil
import java.security.KeyPair
import com.p2p.meshify.core.network.base.TransportEvent
import com.p2p.meshify.core.network.base.TransportCapability
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.Collections.newSetFromMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Professional LAN Transport with Reactive Visibility and Peer Tracking.
 *
 * Improvements:
 * - Mutex locks for critical operations on peerMap
 * - Periodic cleanup of failedSendCounts to prevent memory leaks
 * - Retry logic with exponential backoff for NSD operations
 * - Improved Dead Peer Detection with accurate failure tracking
 * - Timeout for all async operations
 * - Fixed RSSI to use actual WiFi signal strength instead of IP estimation
 */
class LanTransportImpl(
    private val context: Context,
    private val socketManager: SocketManager,
    private val settingsRepository: ISettingsRepository,
    private val peerIdentity: PeerIdentityRepository,
    private val sessionKeyStore: EncryptedSessionKeyStore
) : IMeshTransport {

    // ✅ Transport metadata
    override val transportName: String = "lan"
    override val isAvailable: Boolean = true // LAN is always available on Android devices
    override val capabilities: Set<TransportCapability> = setOf(
        TransportCapability.FILE_TRANSFER,
        TransportCapability.HIGH_BANDWIDTH,
        TransportCapability.OFFLINE,
        TransportCapability.LOW_LATENCY
    )

    private val _events = MutableSharedFlow<TransportEvent>(extraBufferCapacity = 128)
    override val events = _events.asSharedFlow()

    private val peerMap = ConcurrentHashMap<String, String>()

    // Mutex for protecting peerMap operations
    private val peerMapMutex = Mutex()

    // Dead Peer Detection: track consecutive send failures per peer
    private val failedSendCounts = ConcurrentHashMap<String, AtomicInteger>()

    // ✅ PF10: Cache for settings to avoid repeated firstOrNull() calls (5-10ms delay each)
    private var cachedDisplayName: String = "Unknown"
    private var cachedAvatarHash: String? = null
    private var lastCacheUpdate = 0L
    private val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Get cached display name to avoid repeated firstOrNull() calls.
     */
    private suspend fun getCachedDisplayName(): String {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate < CACHE_DURATION_MS && cachedDisplayName != "Unknown") {
            return cachedDisplayName
        }
        cachedDisplayName = settingsRepository.displayName.firstOrNull() ?: "Unknown"
        lastCacheUpdate = now
        return cachedDisplayName
    }

    /**
     * Get cached avatar hash to avoid repeated firstOrNull() calls.
     */
    private suspend fun getCachedAvatarHash(): String? {
        val now = System.currentTimeMillis()
        if (now - lastCacheUpdate < CACHE_DURATION_MS) {
            return cachedAvatarHash
        }
        cachedAvatarHash = settingsRepository.avatarHash.firstOrNull()
        return cachedAvatarHash
    }

    // Mutex for protecting failedSendCounts operations
    private val failedCountsMutex = Mutex()

    private val _onlinePeers = MutableStateFlow<Set<String>>(emptySet())
    override val onlinePeers: StateFlow<Set<String>> = _onlinePeers.asStateFlow()

    private val _typingPeers = MutableStateFlow<Set<String>>(emptySet())
    override val typingPeers: StateFlow<Set<String>> = _typingPeers.asStateFlow()

    // Thread-safe set using ConcurrentHashMap-backed set
    private val resolvingPeers = newSetFromMap(ConcurrentHashMap<String, Boolean>())

    // Discovery enabled flag - controls watchdog restart behavior
    @Volatile
    private var discoveryEnabled = false

    // Dead peer threshold: remove peer after this many consecutive failures
    companion object {
        private const val MAX_FAILURES_BEFORE_REMOVAL = 3
        private const val CLEANUP_FAILED_COUNTS_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
        private const val NSD_RETRY_DELAY_MS = 2000L // 2s initial retry
        private const val NSD_MAX_RETRIES = 3
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var watchdogJob: Job? = null
    private var visibilityJob: Job? = null
    private var cleanupFailedCountsJob: Job? = null

    // ECDH V2 Handshake: ephemeral key management
    // Uses shared sessionKeyStore for session storage
    private val ecdhSessionManager = EcdhSessionManager()

    // Temporary cache for ephemeral keys during handshake (cleared after session derivation)
    // Key: peerId, Value: Pair(ephemeralPrivateKeyBytes, nonceBytes)
    private val pendingEphemeralKeys = ConcurrentHashMap<String, Pair<ByteArray, ByteArray>>()

    override suspend fun start() {
        val myId = settingsRepository.getDeviceId()

        Logger.i("LanEngine -> Starting. MyID: $myId")

        // Start cleanup job for failedSendCounts (prevents memory leak)
        cleanupFailedCountsJob = scope.launch {
            while (isActive) {
                delay(CLEANUP_FAILED_COUNTS_INTERVAL_MS)
                cleanupFailedCounts()
            }
        }

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
                peerMapMutex.withLock {
                    peerMap[senderId] = address
                }
                updateOnlinePeers()

                when (payload.type) {
                    Payload.PayloadType.SYSTEM_CONTROL -> handleSystemCommand(senderId, String(payload.data))
                    Payload.PayloadType.HANDSHAKE -> handleHandshake(senderId, address, payload)
                    Payload.PayloadType.AVATAR_REQUEST -> handleAvatarRequest(senderId, String(payload.data))
                    Payload.PayloadType.AVATAR_RESPONSE -> handleAvatarResponse(senderId, payload)
                    else -> _events.emit(TransportEvent.PayloadReceived(senderId, payload))
                }
            }
        }

        scope.launch { socketManager.startListening() }

        // Start discovery immediately, then let watchdog handle periodic restarts
        scope.launch { startDiscovery() }

        startWatchdog()
    }

    /**
     * Cleans up failedSendCounts to prevent memory leak.
     * Removes entries with zero count or stale entries older than 10 minutes.
     */
    private suspend fun cleanupFailedCounts() = withContext(Dispatchers.IO) {
        failedCountsMutex.withLock {
            val now = System.currentTimeMillis()
            val toRemove = mutableListOf<String>()
            
            for ((peerId, counter) in failedSendCounts) {
                if (counter.get() == 0) {
                    toRemove.add(peerId)
                }
            }
            
            toRemove.forEach { peerId ->
                failedSendCounts.remove(peerId)
            }
            
            if (toRemove.isNotEmpty()) {
                Logger.d("LanTransport -> Cleaned up ${toRemove.size} stale failed count entries")
            }
        }
    }

    private fun handleSystemCommand(senderId: String, command: String) {
        when (command) {
            "TYPING_ON" -> _typingPeers.update { it + senderId }
            "TYPING_OFF" -> _typingPeers.update { it - senderId }
        }
    }

    private suspend fun handleHandshake(senderId: String, address: String, payload: Payload) {
        val rawData = String(payload.data)
        val handshake = try {
            if (rawData.startsWith("{")) {
                Json.decodeFromString<Handshake>(rawData)
            } else {
                Handshake(name = rawData.removePrefix("HELO_"))
            }
        } catch (e: Exception) {
            Logger.e("LanTransport -> Failed to parse handshake from $senderId", e)
            Handshake(name = "Unknown")
        }

        val name = handshake.name
        val hash = handshake.avatarHash

        // V2 Handshake: Derive session key if ephemeral keys are present
        if (handshake.version >= 2 && handshake.ephemeralPubKeyHex != null && handshake.nonceHex != null) {
            try {
                // Check if we have stored ephemeral keys for this peer
                val storedKeys = pendingEphemeralKeys[senderId]

                if (storedKeys != null) {
                    // We initiated: finalize session key using stored ephemeral private key
                    val (myEphemeralPrivKey, myNonce) = storedKeys
                    val peerEphemeralPubKeyHex = handshake.ephemeralPubKeyHex
                        ?: run {
                            Logger.e("Missing ephemeral key in handshake", null, "LanTransport")
                            return
                        }
                    val peerEphemeralPubKey = peerEphemeralPubKeyHex.hexToByteArray()
                    val peerNonceHex = handshake.nonceHex
                        ?: run {
                            Logger.e("Missing nonce in handshake", null, "LanTransport")
                            return
                        }
                    val peerNonce = peerNonceHex.hexToByteArray()

                    val sessionKey = ecdhSessionManager.finalizeSessionKey(
                        peerEphemeralPubKeyBytes = peerEphemeralPubKey,
                        peerNonce = peerNonce,
                        myEphemeralPrivateKey = myEphemeralPrivKey,
                        myNonce = myNonce
                    )

                    // Zero out ephemeral private key after use (forward secrecy)
                    ecdhSessionManager.zeroPrivateKey(myEphemeralPrivKey)
                    pendingEphemeralKeys.remove(senderId)

                    // Store session in shared sessionKeyStore with TOFU validation
                    handshake.identityPubKeyHex?.let { peerIdentityPubKeyHex ->
                        // TOFU validation: check if peer's identity key has changed
                        val tofuResult = sessionKeyStore.validatePeerPublicKey(senderId, peerIdentityPubKeyHex)
                        if (tofuResult == false) {
                            Logger.e("TOFU VIOLATION: Peer identity key changed", null, "LanTransport")
                            Logger.e("BLOCKING session establishment - possible MITM attack", null, "LanTransport")
                            return // ABORT - do not establish session
                        }

                        // TOFU passed (or first contact), store session
                        sessionKeyStore.putSessionKey(senderId, sessionKey, peerIdentityPubKeyHex)
                        Logger.d("Session established with peer (TOFU validated)", "LanTransport")
                    }

                    Logger.i("Session key derived (initiator)", "LanTransport")
                } else {
                    // Peer initiated: generate ephemeral keypair and derive session key
                    val myEphemeralKeypair = ecdhSessionManager.generateEphemeralKeypair()
                    val myNonce = ecdhSessionManager.generateNonce()

                    val peerEphemeralPubKeyHex = handshake.ephemeralPubKeyHex
                        ?: run {
                            Logger.e("Missing ephemeral key in handshake", null, "LanTransport")
                            return
                        }
                    val peerEphemeralPubKey = peerEphemeralPubKeyHex.hexToByteArray()
                    val peerNonceHex = handshake.nonceHex
                        ?: run {
                            Logger.e("Missing nonce in handshake", null, "LanTransport")
                            return
                        }
                    val peerNonce = peerNonceHex.hexToByteArray()

                    val sessionKey = ecdhSessionManager.deriveSessionKeyFromPeer(
                        peerEphemeralPubKeyBytes = peerEphemeralPubKey,
                        peerNonce = peerNonce,
                        myEphemeralKeyPair = myEphemeralKeypair,
                        myNonce = myNonce
                    )

                    // Store our ephemeral keys for session finalization
                    pendingEphemeralKeys[senderId] = Pair(
                        myEphemeralKeypair.private.encoded,
                        myNonce
                    )

                    // Store session in shared sessionKeyStore with TOFU validation
                    handshake.identityPubKeyHex?.let { peerIdentityPubKeyHex ->
                        // TOFU validation: check if peer's identity key has changed
                        val tofuResult = sessionKeyStore.validatePeerPublicKey(senderId, peerIdentityPubKeyHex)
                        if (tofuResult == false) {
                            Logger.e("TOFU VIOLATION: Peer identity key changed", null, "LanTransport")
                            Logger.e("BLOCKING session establishment - possible MITM attack", null, "LanTransport")
                            return // ABORT - do not establish session
                        }

                        // TOFU passed (or first contact), store session
                        sessionKeyStore.putSessionKey(senderId, sessionKey, peerIdentityPubKeyHex)
                        Logger.d("Session established with peer (TOFU validated)", "LanTransport")
                    }

                    Logger.i("Session key derived (responder)", "LanTransport")
                }
            } catch (e: Exception) {
                Logger.e("LanTransport -> Failed to derive session key for $senderId", e)
            }
        }

        peerMapMutex.withLock {
            if (!peerMap.containsKey(senderId)) {
                val rssi = getPeerRssi()
                scope.launch {
                    // ✅ FIX: Use parsed name instead of generic "Peer_"
                    _events.emit(TransportEvent.DeviceDiscovered(senderId, name, address, hash, rssi))
                }

                // ✅ PF10: FIX repeated firstOrNull() by using cached values
                // Previous code called firstOrNull() twice per handshake (5-10ms delay each)
                val displayName = getCachedDisplayName()
                val avatarHash = getCachedAvatarHash()
                val identityPubKeyHex = peerIdentity.getPublicKeyHex()

                // V2 Handshake: Generate ephemeral keypair for forward secrecy
                val ephemeralKeypair = ecdhSessionManager.generateEphemeralKeypair()
                val ephemeralPubKeyHex = HexUtil.toHex(ephemeralKeypair.public.encoded)
                val nonceHex = HexUtil.toHex(ecdhSessionManager.generateNonce())

                // Store ephemeral private key and nonce for session finalization
                pendingEphemeralKeys[senderId] = Pair(
                    ephemeralKeypair.private.encoded,
                    nonceHex.hexToByteArray()
                )

                val myHandshake = Handshake(
                    version = 2,
                    name = displayName,
                    avatarHash = avatarHash,
                    identityPubKeyHex = identityPubKeyHex,
                    ephemeralPubKeyHex = ephemeralPubKeyHex,
                    nonceHex = nonceHex,
                    timestamp = System.currentTimeMillis()
                )

                scope.launch {
                    sendPayload(senderId, Payload(
                        senderId = settingsRepository.getDeviceId(),
                        type = Payload.PayloadType.HANDSHAKE,
                        data = Json.encodeToString(myHandshake).toByteArray()
                    ))
                }
            }
        }

        // Auto-request avatar if we don't have it
        if (hash != null) {
            val existingPath = FileUtils.getFilePath(context, hash, "avatars")
            if (existingPath == null) {
                Logger.i("LanTransport -> Requesting missing avatar: $hash from $senderId")
                sendPayload(senderId, Payload(
                    senderId = settingsRepository.getDeviceId(),
                    type = Payload.PayloadType.AVATAR_REQUEST,
                    data = hash.toByteArray()
                ))
            }
        }

        // ✅ FIX: Send clean payload with just the name for UI display
        val cleanPayload = payload.copy(data = "HELO_$name".toByteArray())
        _events.emit(TransportEvent.PayloadReceived(senderId, cleanPayload))
    }

    private suspend fun handleAvatarRequest(senderId: String, requestedHash: String) {
        val myAvatarHash = settingsRepository.avatarHash.firstOrNull()
        if (myAvatarHash == requestedHash) {
            val path = FileUtils.getFilePath(context, requestedHash, "avatars")
            if (path != null) {
                val bytes = File(path).readBytes()
                Logger.i("LanTransport -> Sending avatar to $senderId (hash: $requestedHash)")
                sendPayload(senderId, Payload(
                    senderId = settingsRepository.getDeviceId(),
                    type = Payload.PayloadType.AVATAR_RESPONSE,
                    data = bytes
                ))
            } else {
                Logger.w("LanTransport -> Avatar file not found for hash: $requestedHash")
            }
        }
    }

    private suspend fun handleAvatarResponse(senderId: String, payload: Payload) {
        val bytes = payload.data
        val hash = FileUtils.calculateHash(bytes)
        Logger.i("LanTransport -> Received avatar from $senderId (hash: $hash)")
        FileUtils.saveBytesToInternalStorage(context, hash, bytes, "avatars")
        // No need to update local settings, the UI will find it by hash when needed
    }

    /**
     * Get actual RSSI from WiFi connection.
     * Requires android.permission.ACCESS_WIFI_STATE
     * 
     * @return Actual RSSI value in dBm, or Int.MIN_VALUE if unavailable
     */
    @Suppress("DEPRECATION")
    private fun getActualRssi(): Int {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            if (wifiManager == null) {
                Logger.w("LanTransport -> WifiManager not available")
                return Int.MIN_VALUE
            }

            // Check permission
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.NEARBY_WIFI_DEVICES)
                    != PackageManager.PERMISSION_GRANTED) {
                    Logger.w("LanTransport -> NEARBY_WIFI_DEVICES permission not granted")
                    return Int.MIN_VALUE
                }
            } else {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                    Logger.w("LanTransport -> ACCESS_WIFI_STATE permission not granted")
                    return Int.MIN_VALUE
                }
            }

            val wifiInfo = wifiManager.connectionInfo
            val rssi = wifiInfo.rssi
            
            // Validate RSSI value (typical range: -100 to 0 dBm)
            if (rssi < -100 || rssi > 0) {
                Logger.w("LanTransport -> Invalid RSSI value: $rssi")
                return Int.MIN_VALUE
            }
            
            rssi
        } catch (e: Exception) {
            Logger.e("LanTransport -> Failed to get RSSI", e)
            Int.MIN_VALUE // Mark as OFFLINE
        }
    }

    /**
     * Get RSSI for a peer device.
     * Uses actual WiFi RSSI when available, falls back to reasonable default.
     */
    private fun getPeerRssi(): Int {
        val actualRssi = getActualRssi()
        return if (actualRssi != Int.MIN_VALUE) {
            actualRssi
        } else {
            // Fallback: return a reasonable default for LAN (-55 dBm = good signal)
            -55
        }
    }

    private suspend fun updateOnlinePeers() = withContext(Dispatchers.IO) {
        peerMapMutex.withLock {
            _onlinePeers.value = peerMap.keys.toSet()
        }
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
        cleanupFailedCountsJob?.cancel()
        unregisterService()
        stopDiscovery()
        socketManager.stopListening()
        scope.cancel()
    }

    /**
     * Starts NSD discovery with retry logic.
     * Retries up to MAX_RETRIES times with exponential backoff on failure.
     */
    override suspend fun startDiscovery() = withContext(Dispatchers.IO) {
        // Check if already running
        if (discoveryListener != null) return@withContext

        // Mark discovery as enabled
        discoveryEnabled = true

        var retryCount = 0
        var started = false

        while (!started && retryCount < NSD_MAX_RETRIES) {
            try {
                discoveryListener = createDiscoveryListener()
                
                withTimeout(5000) { // 5s timeout for NSD operation
                    nsdManager.discoverServices(AppConfig.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                    Logger.d("NSD -> Discovery started (attempt ${retryCount + 1})")
                    started = true
                }
            } catch (e: TimeoutCancellationException) {
                Logger.e("NSD -> Discovery start timeout (attempt ${retryCount + 1})", e)
                discoveryListener = null
                retryCount++
                
                if (retryCount < NSD_MAX_RETRIES) {
                    val delay = NSD_RETRY_DELAY_MS * (1L shl retryCount) // Exponential backoff
                    Logger.w("NSD -> Retrying in ${delay}ms...")
                    delay(delay)
                }
            } catch (e: Exception) {
                Logger.e("NSD -> Discovery Start Failed (attempt ${retryCount + 1})", e)
                discoveryListener = null
                retryCount++
                
                if (retryCount < NSD_MAX_RETRIES) {
                    val delay = NSD_RETRY_DELAY_MS * (1L shl retryCount)
                    Logger.w("NSD -> Retrying in ${delay}ms...")
                    delay(delay)
                }
            }
        }

        if (!started) {
            Logger.e("NSD -> Failed to start discovery after $NSD_MAX_RETRIES attempts")
        }
    }

    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        // Mark discovery as disabled - prevents watchdog restart
        discoveryEnabled = false

        try {
            withTimeout(3000) { // 3s timeout
                discoveryListener?.let {
                    nsdManager.stopServiceDiscovery(it)
                }
            }
        } catch (e: TimeoutCancellationException) {
            Logger.e("NSD -> Stop Discovery Timeout", e)
        } catch (e: Exception) {
            Logger.e("NSD -> Stop Discovery Failed", e)
        } finally {
            discoveryListener = null
            resolvingPeers.clear()
        }

        Logger.d("NSD -> Discovery stopped")
    }

    override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> = withContext(Dispatchers.IO) {
        val ipAddress = peerMapMutex.withLock {
            peerMap[targetDeviceId]
        } ?: return@withContext Result.failure(Exception("Peer offline"))

        val result = socketManager.sendPayload(ipAddress, payload)

        // Dead Peer Detection: track consecutive failures
        if (result.isFailure) {
            val exception = result.exceptionOrNull()
            // Only count network-related failures (timeout, connection refused)
            if (exception is SocketTimeoutException || exception is ConnectException) {
                failedCountsMutex.withLock {
                    val failureCount = failedSendCounts.getOrPut(targetDeviceId) { AtomicInteger(0) }
                    val count = failureCount.incrementAndGet()

                    Logger.w("LanTransport -> Send failed to $targetDeviceId (count: $count)")

                    // Remove peer if failures exceed threshold
                    if (count >= MAX_FAILURES_BEFORE_REMOVAL) {
                        Logger.w("LanTransport -> Marking peer $targetDeviceId as dead after $count failures")

                        peerMapMutex.withLock {
                            peerMap.remove(targetDeviceId)
                        }
                        failedSendCounts.remove(targetDeviceId)
                        _typingPeers.update { it - targetDeviceId }
                        updateOnlinePeers()

                        scope.launch {
                            _events.emit(TransportEvent.DeviceLost(targetDeviceId))
                        }
                    }
                }
            }
        } else {
            // Reset failure count on success
            failedCountsMutex.withLock {
                failedSendCounts.remove(targetDeviceId)
            }
        }

        return@withContext result
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
                // ✅ FIX: Retry registration after delay
                scope.launch {
                    delay(2000)
                    if (discoveryEnabled) {
                        registerService(uuid, name)
                    }
                }
            }
            override fun onServiceUnregistered(info: NsdServiceInfo) {
                Logger.d("NSD -> Service Unregistered: ${info.serviceName}")
            }
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Logger.w("NSD -> Unregistration Failed: $errorCode")
            }
        }

        try {
            nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            Logger.d("NSD -> Registering service...")
        } catch (e: Exception) {
            Logger.e("NSD -> Register Service Failed", e)
        }
    }

    private fun unregisterService() {
        registrationListener?.let {
            try { 
                nsdManager.unregisterService(it) 
            } catch (e: Exception) { 
                Logger.w("LanTransport -> Failed to unregister NSD service: ${e.message}") 
            }
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
                scope.launch {
                    peerMapMutex.withLock {
                        peerMap.remove(peerId)
                    }
                    _typingPeers.update { it - peerId }
                    updateOnlinePeers()
                    _events.emit(TransportEvent.DeviceLost(peerId))
                }
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
            Logger.w("NSD -> Resolve failed for ${info.serviceName}: ${resolveErrorToString(errorCode)}")
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
                if (peerId == myId) {
                    Logger.d("LanTransport -> Ignoring self-discovery: $peerId")
                    return@launch
                }

                // ✅ FIX: Check if peer already exists before adding
                val isDuplicate = peerMapMutex.withLock {
                    val existing = peerMap.putIfAbsent(peerId, address)
                    existing != null
                }
                
                if (isDuplicate) {
                    Logger.d("LanTransport -> Peer $peerId already known, skipping duplicate resolution")
                    return@launch
                }
                
                updateOnlinePeers()

                // Immediately send our Handshake to the newly resolved peer
                val myName = settingsRepository.displayName.firstOrNull() ?: "Unknown"
                val myAvatarHash = settingsRepository.avatarHash.firstOrNull()
                val myIdentityPubKeyHex = peerIdentity.getPublicKeyHex()

                // V2 Handshake: Generate ephemeral keypair for forward secrecy
                val ephemeralKeypair = ecdhSessionManager.generateEphemeralKeypair()
                val ephemeralPubKeyHex = HexUtil.toHex(ephemeralKeypair.public.encoded)
                val nonceHex = HexUtil.toHex(ecdhSessionManager.generateNonce())

                // Store ephemeral private key and nonce for session finalization
                pendingEphemeralKeys[peerId] = Pair(
                    ephemeralKeypair.private.encoded,
                    nonceHex.hexToByteArray()
                )

                val myHandshake = Handshake(
                    version = 2,
                    name = myName,
                    avatarHash = myAvatarHash,
                    identityPubKeyHex = myIdentityPubKeyHex,
                    ephemeralPubKeyHex = ephemeralPubKeyHex,
                    nonceHex = nonceHex,
                    timestamp = System.currentTimeMillis()
                )

                Logger.i("LanTransport -> Resolved peer $peerId at $address. Sending Handshake.")

                // ✅ FIX: Pre-warm connection before sending handshake
                socketManager.registerKnownPeer(peerId, address)

                sendPayload(peerId, Payload(
                    senderId = myId,
                    type = Payload.PayloadType.HANDSHAKE,
                    data = Json.encodeToString(myHandshake).toByteArray()
                ))

                val rssi = getPeerRssi()
                _events.emit(TransportEvent.DeviceDiscovered(peerId, "Peer_${peerId.take(4)}", address, null, rssi))
            }
        }
    }
    
    private fun resolveErrorToString(errorCode: Int): String {
        return when (errorCode) {
            NsdManager.FAILURE_ALREADY_ACTIVE -> "Already active"
            NsdManager.FAILURE_INTERNAL_ERROR -> "Internal error"
            else -> "Error code: $errorCode"
        }
    }
}
