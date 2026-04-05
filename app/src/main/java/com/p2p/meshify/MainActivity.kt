package com.p2p.meshify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.service.MeshForegroundService
import com.p2p.meshify.core.ui.components.PremiumNoiseTexture
import com.p2p.meshify.core.ui.navigation.MeshifyNavHost
import com.p2p.meshify.core.ui.navigation.Screen
import com.p2p.meshify.core.ui.theme.MD3EFontFamilies
import com.p2p.meshify.core.ui.theme.MeshifyTheme
import com.p2p.meshify.core.ui.hooks.rememberPremiumHaptics
import com.p2p.meshify.core.ui.hooks.LocalPremiumHaptics
import com.p2p.meshify.feature.home.RecentChatsScreen
import com.p2p.meshify.feature.home.RecentChatsViewModel
import com.p2p.meshify.feature.discovery.DiscoveryScreen
import com.p2p.meshify.feature.discovery.DiscoveryViewModel
import com.p2p.meshify.feature.chat.ChatScreen
import com.p2p.meshify.feature.chat.ChatViewModel
import com.p2p.meshify.feature.settings.SettingsScreen
import com.p2p.meshify.feature.settings.SettingsViewModel
import com.p2p.meshify.feature.settings.DeveloperScreen
import com.p2p.meshify.feature.settings.DeveloperViewModel
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Main entry point of the Meshify application.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            Logger.i("MainActivity -> All permissions granted")
            startAppService()
        } else {
            Logger.w("MainActivity -> Some permissions denied")
            // App still works with LAN-only mode; BLE features will be limited
            startAppService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()

        // Prevent screenshots and screen recording of sensitive chat data
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        val appContainer = (application as MeshifyApp).container

        setContent {
            val themeMode by appContainer.settingsRepository.themeMode.collectAsState(initial = com.p2p.meshify.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by appContainer.settingsRepository.dynamicColorEnabled.collectAsState(initial = true)
            val motionPreset by appContainer.settingsRepository.motionPreset.collectAsState(initial = MotionPreset.STANDARD)
            val motionScale by appContainer.settingsRepository.motionScale.collectAsState(initial = 1.0f)
            val fontFamilyPreset by appContainer.settingsRepository.fontFamilyPreset.collectAsState(initial = FontFamilyPreset.ROBOTO)
            val shapeStyle by appContainer.settingsRepository.shapeStyle.collectAsState(initial = com.p2p.meshify.domain.model.ShapeStyle.CIRCLE)
            val bubbleStyle by appContainer.settingsRepository.bubbleStyle.collectAsState(initial = com.p2p.meshify.domain.model.BubbleStyle.ROUNDED)
            val visualDensity by appContainer.settingsRepository.visualDensity.collectAsState(initial = 1.0f)
            val seedColorInt by appContainer.settingsRepository.seedColor.collectAsState(initial = 0xFF006D68.toInt())

            var isReady by remember { mutableStateOf(false) }
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                try {
                    withContext(Dispatchers.IO) {
                        appContainer.chatRepository
                        appContainer.transportManager
                    }
                    isReady = true
                } catch (e: Exception) {
                    isReady = true
                }
            }

            val seedColor = remember(seedColorInt) { Color(seedColorInt) }
            val premiumHaptics = rememberPremiumHaptics(appContainer.settingsRepository)

            MeshifyTheme(
                themeMode = themeMode.name,
                dynamicColor = dynamicColor,
                motionPreset = motionPreset,
                motionScale = motionScale,
                fontFamily = MD3EFontFamilies.getFontFamily(fontFamilyPreset),
                shapeStyle = shapeStyle,
                bubbleStyle = bubbleStyle,
                visualDensity = visualDensity,
                seedColor = seedColor
            ) {
                CompositionLocalProvider(LocalPremiumHaptics provides premiumHaptics) {
                    val context = LocalContext.current
                    val isDark = MaterialTheme.colorScheme.surface.toArgb().let { colorInt ->
                        val r = (colorInt shr 16 and 0xff) / 255.0
                        val g = (colorInt shr 8 and 0xff) / 255.0
                        val b = (colorInt and 0xff) / 255.0
                        (0.2126 * r + 0.7152 * g + 0.0722 * b) < 0.5
                    }

                    SideEffect {
                        enableEdgeToEdge(
                            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark },
                            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { isDark }
                        )
                    }

                    if (!isReady) {
                        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                    } else {
                        Box(modifier = Modifier.fillMaxSize()) {
                            // High-end tactile feel
                            PremiumNoiseTexture(alpha = 0.03f)

                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Transparent
                            ) {
                                MeshifyNavHost(
                                    navController = navController,
                                    onHomeRoute = {
                                        val homeViewModel: RecentChatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return RecentChatsViewModel(appContainer.chatRepository) as T
                                                }
                                            }
                                        )
                                        RecentChatsScreen(
                                            viewModel = homeViewModel,
                                            onChatClick = { chat -> navController.navigate(Screen.Chat(chat.peerId, chat.peerName)) },
                                            onDiscoverClick = { navController.navigate(Screen.Discovery) },
                                            onSettingsClick = { navController.navigate(Screen.Settings) }
                                        )
                                    },
                                    onDiscoveryRoute = {
                                        val discoveryViewModel: DiscoveryViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return DiscoveryViewModel(appContainer.transportManager, appContainer.wifiStateChecker) as T
                                                }
                                            }
                                        )
                                        DiscoveryScreen(
                                            viewModel = discoveryViewModel,
                                            onPeerClick = { peer -> navController.navigate(Screen.Chat(peer.id, peer.name)) },
                                            onSettingsClick = { navController.navigate(Screen.Settings) }
                                        )
                                    },
                                    onChatRoute = { peerId, peerName ->
                                        val chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            key = peerId,
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return ChatViewModel(
                                                        context = context,
                                                        peerId = peerId,
                                                        peerName = peerName ?: "Peer",
                                                        repository = appContainer.chatRepository,
                                                        transportTypeProvider = {
                                                            val transports = appContainer.transportManager.selectBestTransport(peerId)
                                                            val hasLan = transports.any { it.transportName == "lan" }
                                                            val hasBle = transports.any { it.transportName == "ble" }
                                                            when {
                                                                hasLan && hasBle -> com.p2p.meshify.domain.model.TransportType.BOTH
                                                                hasBle -> com.p2p.meshify.domain.model.TransportType.BLE
                                                                else -> com.p2p.meshify.domain.model.TransportType.LAN
                                                            }
                                                        }
                                                    ) as T
                                                }
                                            }
                                        )
                                        ChatScreen(
                                            viewModel = chatViewModel,
                                            peerId = peerId,
                                            peerName = peerName ?: "Peer",
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    },
                                    onSettingsRoute = {
                                        val settingsViewModel: SettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return SettingsViewModel(appContainer.settingsRepository) as T
                                                }
                                            }
                                        )
                                        SettingsScreen(
                                            viewModel = settingsViewModel,
                                            onBackClick = { navController.popBackStack() },
                                            onDeveloperModeClick = { navController.navigate(Screen.Developer) }
                                        )
                                    },
                                    onDeveloperRoute = {
                                        val devDb = (application as MeshifyApp).container.database
                                        val developerViewModel: DeveloperViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return DeveloperViewModel(
                                                        chatDao = devDb.chatDao(),
                                                        messageDao = devDb.messageDao()
                                                    ) as T
                                                }
                                            }
                                        )
                                        DeveloperScreen(
                                            viewModel = developerViewModel,
                                            onBackClick = { navController.popBackStack() }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun startAppService() {
        MeshForegroundService.start(this)
    }

    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_MULTICAST_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        // Android 12+ (API 31): Request BLE runtime permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(Manifest.permission.BLUETOOTH_CONNECT)
            permissions.add(Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            startAppService()
        }
    }
}
