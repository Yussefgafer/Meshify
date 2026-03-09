package com.p2p.meshify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.domain.model.FontFamilyPreset
import com.p2p.meshify.domain.model.MotionPreset
import com.p2p.meshify.network.service.MeshForegroundService
import com.p2p.meshify.ui.components.PremiumNoiseTexture
import com.p2p.meshify.ui.navigation.MeshifyNavDisplay
import com.p2p.meshify.ui.theme.MD3EFontFamilies
import com.p2p.meshify.ui.theme.MeshifyTheme
import com.p2p.meshify.ui.hooks.rememberPremiumHaptics
import com.p2p.meshify.ui.hooks.LocalPremiumHaptics
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
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAndRequestPermissions()
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
                        appContainer.lanTransport
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
                                MeshifyNavDisplay(context = this@MainActivity, navController = navController, appContainer = appContainer)
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
        } else {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissionLauncher.launch(missing.toTypedArray()) else startAppService()
    }
}
