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
import androidx.lifecycle.lifecycleScope
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
import com.p2p.meshify.feature.realdevicetesting.ui.RealDeviceTestingViewModel
import com.p2p.meshify.feature.realdevicetesting.ui.RealDeviceTestScreen
import com.p2p.meshify.feature.onboarding.WelcomeScreen
import com.p2p.meshify.feature.onboarding.WelcomeViewModel
import com.p2p.meshify.feature.onboarding.PermissionSummaryDialog
import com.p2p.meshify.feature.onboarding.PermissionRequestCard
import com.p2p.meshify.feature.onboarding.SkipConfirmationDialog
import com.p2p.meshify.feature.onboarding.PermissionDefinitions
import com.p2p.meshify.feature.onboarding.PermissionRequestResult
import com.p2p.meshify.core.domain.interfaces.WifiStateChecker
import com.p2p.meshify.core.data.local.MeshifyDatabase
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Delay to allow the permission card exit animation to complete before advancing.
 * This is a known workaround because Compose's AnimatedVisibility does not expose
 * an onComplete callback that can be observed from a LaunchedEffect.
 * The values are conservative estimates that work on most devices.
 */
private const val PERMISSION_EXIT_ANIMATION_DELAY_MS = 800L

/**
 * Delay for auto-advancing when a permission is already granted.
 * Allows the user to see the "Granted" state briefly before moving on.
 */
private const val PERMISSION_ALREADY_GRANTED_DISPLAY_DELAY_MS = 600L

/**
 * Main entry point of the Meshify application.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var database: MeshifyDatabase

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

    // Callback for onboarding permission requests
    var permissionResultCallback: ((Map<String, Boolean>) -> Unit)? = null
        internal set

    private val onboardingPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionResultCallback?.invoke(results)
        permissionResultCallback = null
    }

    /**
     * Request specific permissions from the onboarding flow.
     * Returns results via the callback.
     */
    private fun requestSpecificPermissions(permissions: List<String>) {
        if (permissions.isEmpty()) return
        permissionResultCallback = null // Reset
        onboardingPermissionLauncher.launch(permissions.toTypedArray())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MeshifyApp

        // Only request permissions immediately if onboarding was already completed.
        // Otherwise, permissions will be requested after the onboarding flow.
        lifecycleScope.launch {
            val completed = try {
                app.settingsRepository.hasCompletedOnboarding.first()
            } catch (e: Exception) {
                true // fallback: assume completed to avoid blocking app startup
            }
            if (completed) {
                checkAndRequestPermissions()
            }
        }

        // Prevent screenshots and screen recording of sensitive chat data
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        setContent {
            val settingsRepo = app.settingsRepository
            val themeMode by settingsRepo.themeMode.collectAsState(initial = com.p2p.meshify.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by settingsRepo.dynamicColorEnabled.collectAsState(initial = true)
            val motionPreset by settingsRepo.motionPreset.collectAsState(initial = MotionPreset.STANDARD)
            val motionScale by settingsRepo.motionScale.collectAsState(initial = 1.0f)
            val fontFamilyPreset by settingsRepo.fontFamilyPreset.collectAsState(initial = FontFamilyPreset.ROBOTO)
            val shapeStyle by settingsRepo.shapeStyle.collectAsState(initial = com.p2p.meshify.domain.model.ShapeStyle.CIRCLE)
            val bubbleStyle by settingsRepo.bubbleStyle.collectAsState(initial = com.p2p.meshify.domain.model.BubbleStyle.ROUNDED)
            val visualDensity by settingsRepo.visualDensity.collectAsState(initial = 1.0f)
            val seedColorInt by settingsRepo.seedColor.collectAsState(initial = 0xFF006D68.toInt())

            var isReady by remember { mutableStateOf(false) }
            var startDestination by remember { mutableStateOf<Screen?>(null) }
            val navController = rememberNavController()

            LaunchedEffect(Unit) {
                try {
                    withContext(Dispatchers.IO) {
                        app.chatRepository
                        app.transportManager

                        // Check if onboarding was completed
                        val completed = app.settingsRepository.hasCompletedOnboarding.first()
                        startDestination = if (completed) Screen.Home else Screen.Onboarding
                    }
                    isReady = true
                } catch (e: Exception) {
                    startDestination = Screen.Home // fallback
                    isReady = true
                }
            }

            val seedColor = remember(seedColorInt) { Color(seedColorInt) }
            val premiumHaptics = rememberPremiumHaptics(settingsRepo)

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
                                val effectiveStart = startDestination ?: Screen.Home

                                MeshifyNavHost(
                                    navController = navController,
                                    startDestination = effectiveStart,
                                    onHomeRoute = {
                                        val homeViewModel: RecentChatsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return RecentChatsViewModel(app.chatRepository) as T
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
                                                    return DiscoveryViewModel(app.transportManager, app.wifiStateChecker) as T
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
                                        // Use Hilt to create the ViewModel with SavedStateHandle.
                                        // peerId and peerName are read from SavedStateHandle by the ViewModel.
                                        val savedStateHandle = androidx.lifecycle.SavedStateHandle()
                                        savedStateHandle.set("peerId", peerId)
                                        savedStateHandle.set("peerName", peerName ?: "Peer")
                                        val chatViewModel: ChatViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            key = peerId,
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return ChatViewModel(
                                                        context = context,
                                                        savedStateHandle = savedStateHandle,
                                                        repository = app.chatRepository
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
                                                    return SettingsViewModel(app.settingsRepository) as T
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
                                        val developerViewModel: DeveloperViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = object : androidx.lifecycle.ViewModelProvider.Factory {
                                                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                                                    @Suppress("UNCHECKED_CAST")
                                                    return DeveloperViewModel(
                                                        chatDao = database.chatDao(),
                                                        messageDao = database.messageDao()
                                                    ) as T
                                                }
                                            }
                                        )
                                        DeveloperScreen(
                                            viewModel = developerViewModel,
                                            onBackClick = { navController.popBackStack() },
                                            onRealDeviceTestingClick = { navController.navigate(Screen.RealDeviceTesting) },
                                            onResetOnboardingClick = {
                                                lifecycleScope.launch {
                                                    app.settingsRepository.resetOnboardingCompleted()
                                                }
                                                navController.navigate(Screen.Onboarding) {
                                                    popUpTo(Screen.Home) { inclusive = true }
                                                }
                                            }
                                        )
                                    },
                                    onRealDeviceTestingRoute = {
                                        val realDeviceTestViewModel: RealDeviceTestingViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                                            factory = RealDeviceTestingViewModel.factory(
                                                context = context,
                                                chatRepository = app.chatRepository,
                                                database = database
                                            )
                                        )
                                        RealDeviceTestScreen(
                                            viewModel = realDeviceTestViewModel,
                                            onNavigateBack = { navController.popBackStack() }
                                        )
                                    },
                                    onOnboardingRoute = {
                                        OnboardingRoute(
                                            activity = this@MainActivity,
                                            settingsRepository = app.settingsRepository,
                                            onNavigateToHome = {
                                                navController.navigate(Screen.Home) {
                                                    popUpTo(Screen.Onboarding) { inclusive = true }
                                                }
                                            },
                                            onRequestPermissions = { perms ->
                                                requestSpecificPermissions(perms)
                                            }
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

/**
 * Onboarding flow composable.
 * 3 pages: Welcome → How It Works → Permissions.
 * Handles language switching, permission requests, and navigation.
 */
@Composable
private fun OnboardingRoute(
    activity: MainActivity,
    settingsRepository: com.p2p.meshify.domain.repository.ISettingsRepository,
    onNavigateToHome: () -> Unit,
    onRequestPermissions: (List<String>) -> Unit
) {
    val onboardingViewModel: WelcomeViewModel = hiltViewModel()
    val permissions = PermissionDefinitions.getPermissions()
    val scope = rememberCoroutineScope()

    // Hoist LocalContext to top level
    val context = LocalContext.current

    // Language: "en" or "ar"
    var currentLang by remember { mutableStateOf("en") }

    // Permission flow state
    var isPermissionFlowActive by remember { mutableStateOf(false) }
    var currentPermissionIndex by remember { mutableStateOf(0) }
    val permissionResults = remember { mutableStateMapOf<String, PermissionRequestResult>() }
    var advanceTrigger by remember { mutableIntStateOf(0) }
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showSkipConfirm by remember { mutableStateOf(false) }

    // Wire permission result callback from Activity — single persistent callback
    DisposableEffect(activity) {
        val callback: (Map<String, Boolean>) -> Unit = { results ->
            val currentPerm = permissions.getOrNull(currentPermissionIndex)
            if (currentPerm != null) {
                val isGranted = results.values.all { it }
                permissionResults[currentPerm.id] = if (isGranted) {
                    PermissionRequestResult.Granted
                } else {
                    val wasDeniedPermanently = currentPerm.androidPermissions.any { p ->
                        results[p] == false && !activity.shouldShowRequestPermissionRationale(p)
                    }
                    if (wasDeniedPermanently) {
                        PermissionRequestResult.DeniedPermanently
                    } else {
                        PermissionRequestResult.Denied
                    }
                }
                advanceTrigger++
            }
        }
        activity.permissionResultCallback = callback
        onDispose {
            activity.permissionResultCallback = null
        }
    }

    // WelcomeScreen
    WelcomeScreen(
        viewModel = onboardingViewModel,
        currentLang = currentLang,
        onLangChange = { newLang ->
            currentLang = newLang
        },
        onNextClick = {
            // Page 3 "Get Started" → start permission flow
            isPermissionFlowActive = true
            currentPermissionIndex = 0
            onboardingViewModel.startPermissionFlow()
        },
        onSkipClick = {
            if (isPermissionFlowActive) {
                showSkipConfirm = true
            } else {
                scope.launch {
                    settingsRepository.setOnboardingCompleted()
                }
                onNavigateToHome()
            }
        }
    )

    // Auto-advance after permission result
    LaunchedEffect(advanceTrigger) {
        if (advanceTrigger > 0) {
            kotlinx.coroutines.delay(PERMISSION_EXIT_ANIMATION_DELAY_MS)
            currentPermissionIndex++
        }
    }

    // Permission flow: show cards one by one
    if (isPermissionFlowActive && currentPermissionIndex < permissions.size) {
        val perm = permissions[currentPermissionIndex]

        // Check if already granted
        val alreadyGranted = perm.androidPermissions.all { pid ->
            android.content.pm.PackageManager.PERMISSION_GRANTED ==
                context.checkSelfPermission(pid)
        }

        if (alreadyGranted) {
            LaunchedEffect(perm.id) {
                permissionResults[perm.id] = PermissionRequestResult.Granted
                advanceTrigger++
                kotlinx.coroutines.delay(PERMISSION_ALREADY_GRANTED_DISPLAY_DELAY_MS)
                currentPermissionIndex++
            }
        } else {
            PermissionRequestCard(
                permission = perm,
                onAllowClick = {
                    onRequestPermissions(perm.androidPermissions)
                },
                onDenyClick = {
                    permissionResults[perm.id] = PermissionRequestResult.Denied
                    currentPermissionIndex++
                },
                onRequestDismiss = {
                    isPermissionFlowActive = false
                    showSummaryDialog = true
                }
            )
        }
    }

    // Show summary when all permissions processed
    if (showSummaryDialog) {
        val grantedCount = permissionResults.count { it.value == PermissionRequestResult.Granted }
        PermissionSummaryDialog(
            grantedCount = grantedCount,
            totalCount = permissions.size,
            permissionResults = permissionResults.toMap(),
            onStartClick = {
                scope.launch {
                    settingsRepository.setOnboardingCompleted()
                }
                onNavigateToHome()
            },
            onDismiss = {
                showSummaryDialog = false
            }
        )
    }

    // Skip confirmation
    if (showSkipConfirm) {
        SkipConfirmationDialog(
            onStayClick = { showSkipConfirm = false },
            onLeaveClick = {
                showSkipConfirm = false
                isPermissionFlowActive = false
                scope.launch {
                    settingsRepository.setOnboardingCompleted()
                }
                onNavigateToHome()
            }
        )
    }
}
