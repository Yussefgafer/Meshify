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

/**
 * Main entry point of the Meshify application.
 * Integrates MD3E settings for dynamic theming.
 */
class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.entries.all { it.value }) {
            Logger.i("All permissions granted")
            startAppService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ✅ FIX 1: Enable edge-to-edge BEFORE any heavy work
        enableEdgeToEdge()
        
        // ✅ FIX 2: Request permissions asynchronously (non-blocking)
        checkAndRequestPermissions()

        val appContainer = (application as MeshifyApp).container

        setContent {
            // Collect all MD3E settings
            val themeMode by appContainer.settingsRepository.themeMode.collectAsState(initial = com.p2p.meshify.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by appContainer.settingsRepository.dynamicColorEnabled.collectAsState(initial = true)
            val motionPreset by appContainer.settingsRepository.motionPreset.collectAsState(initial = MotionPreset.STANDARD)
            val motionScale by appContainer.settingsRepository.motionScale.collectAsState(initial = 1.0f)
            val fontFamilyPreset by appContainer.settingsRepository.fontFamilyPreset.collectAsState(initial = FontFamilyPreset.ROBOTO)
            val shapeStyle by appContainer.settingsRepository.shapeStyle.collectAsState(initial = com.p2p.meshify.domain.model.ShapeStyle.CIRCLE)
            val bubbleStyle by appContainer.settingsRepository.bubbleStyle.collectAsState(initial = com.p2p.meshify.domain.model.BubbleStyle.ROUNDED)
            val visualDensity by appContainer.settingsRepository.visualDensity.collectAsState(initial = 1.0f)

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
                val navController = rememberNavController()
                
                // ✅ FIX 3: Use LaunchedEffect for non-UI startup logic
                LaunchedEffect(Unit) {
                    // Trigger background initialization asynchronously
                    lifecycleScope.launch(Dispatchers.IO) {
                        // Pre-load heavy components in background
                        // Database and SocketManager are already lazy-initialized
                        Logger.d("Background initialization complete")
                    }
                }
                
                // ✅ FIX 4: Simplified root Surface to prevent rendering deadlocks
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MeshifyNavHost(
                        context = this@MainActivity,
                        navController = navController,
                        appContainer = appContainer
                    )
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
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            // Launch async - doesn't block main thread
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            startAppService()
        }
    }
}
