package com.p2p.meshify.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.p2p.meshify.AppContainer
import com.p2p.meshify.ui.screens.chat.ChatScreen
import com.p2p.meshify.ui.screens.chat.ChatViewModel
import com.p2p.meshify.ui.screens.discovery.DiscoveryScreen
import com.p2p.meshify.ui.screens.discovery.DiscoveryViewModel
import com.p2p.meshify.ui.screens.recent.RecentChatsScreen
import com.p2p.meshify.ui.screens.recent.RecentChatsViewModel
import com.p2p.meshify.ui.screens.settings.SettingsScreen
import com.p2p.meshify.ui.screens.settings.SettingsViewModel

@Composable
fun MeshifyNavHost(
    context: Context,
    navController: NavHostController,
    appContainer: AppContainer
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        // HOME SCREEN
        composable(Screen.Home.route) {
            val homeViewModel: RecentChatsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return RecentChatsViewModel(appContainer.chatRepository) as T
                    }
                }
            )
            RecentChatsScreen(
                viewModel = homeViewModel,
                onChatClick = { chat ->
                    navController.navigate(Screen.Chat.createRoute(chat.peerId))
                },
                onDiscoverClick = {
                    navController.navigate(Screen.Discovery.route)
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // DISCOVERY SCREEN
        composable(Screen.Discovery.route) {
            val discoveryViewModel: DiscoveryViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return DiscoveryViewModel(appContainer.lanTransport) as T
                    }
                }
            )
            DiscoveryScreen(
                viewModel = discoveryViewModel,
                onPeerClick = { peer ->
                    navController.navigate(Screen.Chat.createRoute(peer.id))
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // CHAT SCREEN (Now with PeerId only)
        composable(
            route = Screen.Chat.route,
            arguments = listOf(
                navArgument("peerId") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val peerId = backStackEntry.arguments?.getString("peerId") ?: ""
            
            val chatViewModel: ChatViewModel = viewModel(
                key = peerId,
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ChatViewModel(
                            context = context,
                            peerId = peerId,
                            chatRepository = appContainer.chatRepository,
                            getMessagesUseCase = appContainer.getMessagesUseCase,
                            sendMessageUseCase = appContainer.sendMessageUseCase,
                            deleteMessagesUseCase = appContainer.deleteMessagesUseCase
                        ) as T
                    }
                }
            )
            ChatScreen(
                viewModel = chatViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }

        // SETTINGS SCREEN
        composable(Screen.Settings.route) {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return SettingsViewModel(appContainer.settingsRepository) as T
                    }
                }
            )
            SettingsScreen(
                viewModel = settingsViewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
