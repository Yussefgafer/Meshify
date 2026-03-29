package com.p2p.meshify.core.ui.navigation

import androidx.compose.runtime.Composable
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
    onHomeRoute: @Composable () -> Unit = {},
    onDiscoveryRoute: @Composable () -> Unit = {},
    onChatRoute: @Composable (peerId: String, peerName: String?) -> Unit = { _, _ -> },
    onSettingsRoute: @Composable () -> Unit = {},
    onDeveloperRoute: @Composable () -> Unit = {}
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
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
    }
}
