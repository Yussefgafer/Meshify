package com.p2p.meshify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.core.view.WindowCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.network.service.MeshForegroundService
import com.p2p.meshify.ui.navigation.MeshifyNavHost
import com.p2p.meshify.ui.theme.MD3EFontFamilies
import com.p2p.meshify.ui.theme.MeshifyTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Main entry point of the Meshify application.
 * Integrates MD3E settings for dynamic theming.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            Logger.i("MainActivity -> All permissions granted")
            startAppService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // ✅ FIX 1: Log onCreate start
        Logger.d("MainActivity -> onCreate START")
        
        // ✅ FIX 2: Enable edge-to-edge FIRST (before any heavy work)
        Logger.d("MainActivity -> Calling enableEdgeToEdge()")
        enableEdgeToEdge()
        
        // ✅ FIX 2.1: Explicitly control system bars appearance to prevent re-draw loop
        // This prevents SurfaceSyncGroup timeout on Transsion/Mediatek devices
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.isAppearanceLightStatusBars = false
        windowInsetsController.isAppearanceLightNavigationBars = false
        
        Logger.d("MainActivity -> enableEdgeToEdge() completed + WindowCompat configured")

        // ✅ FIX 3: Request permissions asynchronously (non-blocking)
        Logger.d("MainActivity -> Starting permission check")
        checkAndRequestPermissions()

        // ✅ FIX 4: Get app container (lazy-initialized in Application)
        Logger.d("MainActivity -> Getting AppContainer")
        val appContainer = (application as MeshifyApp).container
        Logger.d("MainActivity -> AppContainer retrieved")

        setContent {
            Logger.d("MainActivity -> setContent START")

            // Collect all MD3E settings
            val themeMode by appContainer.settingsRepository.themeMode.collectAsState(initial = com.p2p.meshify.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by appContainer.settingsRepository.dynamicColorEnabled.collectAsState(initial = true)
            val motionPreset by appContainer.settingsRepository.motionPreset.collectAsState(initial = MotionPreset.STANDARD)
            val motionScale by appContainer.settingsRepository.motionScale.collectAsState(initial = 1.0f)
            val fontFamilyPreset by appContainer.settingsRepository.fontFamilyPreset.collectAsState(initial = FontFamilyPreset.ROBOTO)
            val shapeStyle by appContainer.settingsRepository.shapeStyle.collectAsState(initial = com.p2p.meshify.domain.model.ShapeStyle.CIRCLE)
            val bubbleStyle by appContainer.settingsRepository.bubbleStyle.collectAsState(initial = com.p2p.meshify.domain.model.BubbleStyle.ROUNDED)
            val visualDensity by appContainer.settingsRepository.visualDensity.collectAsState(initial = 1.0f)

            // ✅ FIX: Ready state to prevent startup deadlock
            var isReady by remember { mutableStateOf(false) }

            // ✅ FIX: Warm up heavy components BEFORE showing UI
            LaunchedEffect(Unit) {
                Logger.d("MainActivity -> Starting background initialization")
                try {
                    withContext(Dispatchers.IO) {
                        // Force initialization of heavy components
                        appContainer.chatRepository
                        appContainer.lanTransport
                    }
                    isReady = true
                    Logger.d("MainActivity -> Background initialization COMPLETE - UI ready")
                } catch (e: Exception) {
                    Logger.e("MainActivity -> Background initialization FAILED: ${e.message}")
                    isReady = true // Still show UI even if init fails
                }
            }

            // ✅ FIX: Create navController OUTSIDE the conditional to avoid recomposition issues
            val navController = rememberNavController()

            // ✅ FIX 5: Log theme config
            LaunchedEffect(themeMode, dynamicColor, fontFamilyPreset, visualDensity) {
                Logger.d("MeshifyTheme -> Config: DarkMode=$themeMode, DynamicColor=$dynamicColor, Font=$fontFamilyPreset, Density=${String.format("%.2f", visualDensity)}x")
            }

            MeshifyTheme(
                themeMode = themeMode.name,
                dynamicColor = dynamicColor,
                motionPreset = motionPreset,
                motionScale = motionScale,
                fontFamily = MD3EFontFamilies.getFontFamily(fontFamilyPreset),
                shapeStyle = shapeStyle,
                bubbleStyle = bubbleStyle,
                visualDensity = visualDensity
            ) {
                Logger.d("MainActivity -> MeshifyTheme composed")

                // ✅ FIX: Show solid background ONLY until ready - prevents black screen
                if (!isReady) {
                    Logger.d("MainActivity -> Showing solid background while initializing...")
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    )
                } else {
                    // ✅ FIX 7: Simplified root Surface with fallback solid background
                    // This satisfies OpenGL EGL configuration immediately
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        Logger.d("MainActivity -> Rendering MeshifyNavHost")
                        MeshifyNavHost(
                            context = this@MainActivity,
                            navController = navController,
                            appContainer = appContainer
                        )
                    }
                }
            }
            Logger.d("MainActivity -> setContent COMPLETE")
        }
        
        Logger.d("MainActivity -> onCreate COMPLETE")
    }

    private fun startAppService() {
        Logger.d("MainActivity -> Starting MeshForegroundService")
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
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            Logger.d("MainActivity -> Requesting ${missing.size} permissions")
            // Launch async - doesn't block main thread
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            Logger.d("MainActivity -> All permissions already granted")
            startAppService()
        }
    }
}
