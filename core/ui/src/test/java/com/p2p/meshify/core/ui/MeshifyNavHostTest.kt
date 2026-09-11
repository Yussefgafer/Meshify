package com.p2p.meshify.core.ui

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.navigation.compose.ComposeNavigator
import androidx.navigation.testing.TestNavHostController
import com.p2p.meshify.core.ui.navigation.MeshifyNavHost
import com.p2p.meshify.core.ui.navigation.Screen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MeshifyNavHostTest {
    @get:Rule val rule = createComposeRule()

    @Test fun `startDestination Home renders onHomeRoute`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Home,
                    onHomeRoute = { Text("HomeScreen") },
                    onDiscoveryRoute = { Text("DiscoveryScreen") },
                    onSettingsRoute = { Text("SettingsScreen") }
                )
            }
        }
        rule.onNodeWithText("HomeScreen").assertIsDisplayed()
        rule.onNodeWithText("DiscoveryScreen").assertDoesNotExist()
    }

    @Test fun `startDestination Discovery renders onDiscoveryRoute`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Discovery,
                    onDiscoveryRoute = { Text("DiscoveryScreen") }
                )
            }
        }
        rule.onNodeWithText("DiscoveryScreen").assertIsDisplayed()
    }

    @Test fun `startDestination Settings renders onSettingsRoute`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Settings,
                    onSettingsRoute = { Text("SettingsScreen") }
                )
            }
        }
        rule.onNodeWithText("SettingsScreen").assertIsDisplayed()
    }

    @Test fun `startDestination Onboarding renders onOnboardingRoute`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Onboarding,
                    onOnboardingRoute = { Text("OnboardingScreen") }
                )
            }
        }
        rule.onNodeWithText("OnboardingScreen").assertIsDisplayed()
    }

    @Test fun `startDestination Developer renders onDeveloperRoute`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Developer,
                    onDeveloperRoute = { Text("DeveloperScreen") }
                )
            }
        }
        rule.onNodeWithText("DeveloperScreen").assertIsDisplayed()
    }

    @Test fun `startDestination RealDeviceTesting renders route`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.RealDeviceTesting,
                    onRealDeviceTestingRoute = { Text("RealDeviceTestingScreen") }
                )
            }
        }
        rule.onNodeWithText("RealDeviceTestingScreen").assertIsDisplayed()
    }

    @Test fun `Chat route extracts peerId and peerName`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Chat(peerId = "peer-42", peerName = "Alice"),
                    onChatRoute = { peerId, peerName ->
                        Text("Chat:$peerId:${peerName ?: "null"}")
                    }
                )
            }
        }
        rule.onNodeWithText("Chat:peer-42:Alice").assertIsDisplayed()
    }

    @Test fun `Chat route with null peerName`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Chat(peerId = "p1", peerName = null),
                    onChatRoute = { peerId, peerName ->
                        Text("Chat:$peerId:${peerName ?: "null"}")
                    }
                )
            }
        }
        rule.onNodeWithText("Chat:p1:null").assertIsDisplayed()
    }

    @Test fun `MissingComposable fallback shows red placeholder when no lambda provided`() {
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Home
                    // defaults -> MissingComposable with "Missing route composable" text
                )
            }
        }
        rule.onNodeWithText("Missing route composable").assertIsDisplayed()
    }

    @Test fun `navigation between routes`() {
        lateinit var navController: TestNavHostController
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Home,
                    onHomeRoute = { Text("HomeScreen") },
                    onDiscoveryRoute = { Text("DiscoveryScreen") }
                )
            }
        }
        rule.onNodeWithText("HomeScreen").assertIsDisplayed()
        rule.runOnIdle {
            navController.navigate(Screen.Discovery)
        }
        rule.onNodeWithText("DiscoveryScreen").assertIsDisplayed()
    }

    @Test fun `Chat special chars peerId round-trip`() {
        val peerId = "peer with spaces & symbols/123"
        rule.setContent {
            DxTestTheme(dynamicColor = false) {
                val navController = TestNavHostController(androidx.compose.ui.platform.LocalContext.current).apply {
                    navigatorProvider.addNavigator(ComposeNavigator())
                }
                MeshifyNavHost(
                    navController = navController,
                    startDestination = Screen.Chat(peerId = peerId, peerName = "Test Name"),
                    onChatRoute = { id, name -> Text("Chat:$id|$name") }
                )
            }
        }
        rule.onNodeWithText("Chat:$peerId|Test Name").assertIsDisplayed()
    }
}
