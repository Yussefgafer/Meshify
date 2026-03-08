package com.p2p.meshify.ui.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
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
fun MeshifyNavDisplay(
    context: Context,
    navController: NavHostController,
    appContainer: AppContainer
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home
    ) {
        composable<Screen.Home> {
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
                onChatClick = { chat -> navController.navigate(Screen.Chat(chat.peerId)) },
                onDiscoverClick = { navController.navigate(Screen.Discovery) },
                onSettingsClick = { navController.navigate(Screen.Settings) }
            )
        }

        composable<Screen.Discovery> {
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
                onPeerClick = { peer -> navController.navigate(Screen.Chat(peer.id)) },
                onSettingsClick = { navController.navigate(Screen.Settings) }
            )
        }

        composable<Screen.Chat> { backStackEntry ->
            val route: Screen.Chat = backStackEntry.toRoute()
            val chatViewModel: ChatViewModel = viewModel(
                key = route.peerId,
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return ChatViewModel(
                            context = context,
                            peerId = route.peerId,
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

        composable<Screen.Settings> {
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
