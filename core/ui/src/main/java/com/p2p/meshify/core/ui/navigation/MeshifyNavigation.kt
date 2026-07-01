package com.p2p.meshify.core.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute

/**
 * Core navigation host for Meshify.
 * Provides the base navigation structure with composable routes.
 * 
 * Note: This is a generic navigation container. 
 * Actual screen composables and ViewModels should be provided from the app module.
 */
@Composable
fun MeshifyNavHost(
    navController: NavHostController,
    startDestination: Screen = Screen.Home,
    onOnboardingRoute: @Composable () -> Unit = { MissingComposable() },
    onHomeRoute: @Composable () -> Unit = { MissingComposable() },
    onDiscoveryRoute: @Composable () -> Unit = { MissingComposable() },
    onChatRoute: @Composable (peerId: String, peerName: String?) -> Unit = { _, _ -> MissingComposable() },
    onSettingsRoute: @Composable () -> Unit = { MissingComposable() },
    onDeveloperRoute: @Composable () -> Unit = { MissingComposable() },
    onRealDeviceTestingRoute: @Composable () -> Unit = { MissingComposable() }
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable<Screen.Onboarding> {
            onOnboardingRoute()
        }

        composable<Screen.Home> {
            onHomeRoute()
        }

        composable<Screen.Discovery> {
            onDiscoveryRoute()
        }

        composable<Screen.Chat> { backStackEntry ->
            val route: Screen.Chat = backStackEntry.toRoute()
            onChatRoute(route.peerId, route.peerName)
        }

        composable<Screen.Settings> {
            onSettingsRoute()
        }

        composable<Screen.Developer> {
            onDeveloperRoute()
        }

        composable<Screen.RealDeviceTesting> {
            onRealDeviceTestingRoute()
        }
    }
}

@Composable
private fun MissingComposable() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Text("Missing route composable", color = Color.Red)
    }
}
