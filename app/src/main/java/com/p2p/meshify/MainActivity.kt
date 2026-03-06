package com.p2p.meshify

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController
import com.p2p.meshify.core.util.Logger
import com.p2p.meshify.network.service.MeshForegroundService
import com.p2p.meshify.ui.navigation.MeshifyNavHost
import com.p2p.meshify.ui.theme.MeshifyTheme

/**
 * Main entry point of the Meshify application.
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
        checkAndRequestPermissions()

        val appContainer = (application as MeshifyApp).container

        setContent {
            val themeMode by appContainer.settingsRepository.themeMode.collectAsState(initial = com.p2p.meshify.domain.repository.ThemeMode.SYSTEM)
            val dynamicColor by appContainer.settingsRepository.dynamicColorEnabled.collectAsState(initial = true)

            MeshifyTheme(
                themeMode = themeMode.name,
                dynamicColor = dynamicColor
            ) {
                val navController = rememberNavController()
                MeshifyNavHost(
                    context = this@MainActivity,
                    navController = navController,
                    appContainer = appContainer
                )
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
            requestPermissionLauncher.launch(missing.toTypedArray())
        } else {
            startAppService()
        }
    }
}
