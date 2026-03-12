# Project Map: Meshify

## Modules
- :app
- :core:common
- :core:data
- :core:domain
- :core:network
- :core:ui
- :feature:home
- :feature:chat
- :feature:discovery
- :feature:settings

## Dependency List (libs.versions.toml)
[versions]
agp = "9.1.0"
kotlin = "2.3.10"
ksp = "2.3.5"
coreKtx = "1.12.0"
junit = "4.13.2"
junitVersion = "1.1.5"
espressoCore = "3.5.1"
lifecycle = "2.10.0"
activityCompose = "1.10.1"
composeBom = "2025.02.00"
material3 = "1.4.0-alpha10"
material = "1.14.0-alpha09"
navigation = "2.8.7"
googleFonts = "1.7.8"
room = "2.8.0"
coil3 = "3.4.0"
datastore = "1.1.1"
media3 = "1.5.1"
accompanist = "0.36.0"

[libraries]
media3_exoplayer = { group = "androidx.media3", name = "media3-exoplayer", version.ref = "media3" }
media3_ui = { group = "androidx.media3", name = "media3-ui", version.ref = "media3" }
media3_session = { group = "androidx.media3", name = "media3-session", version.ref = "media3" }
androidx_core_ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
google_material = { group = "com.google.android.material", name = "material", version.ref = "material" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
androidx_junit = { group = "androidx.test.ext", name = "junit", version.ref = "junitVersion" }
androidx_espresso_core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espressoCore" }
androidx_lifecycle_runtime_ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle" }
androidx_lifecycle_runtime_compose = { group = "androidx.lifecycle", name = "lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx_lifecycle_service = { group = "androidx.lifecycle", name = "lifecycle-service", version.ref = "lifecycle" }
androidx_lifecycle_viewmodel_compose = { group = "androidx.lifecycle", name = "lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx_activity_compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx_compose_bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx_ui = { group = "androidx.compose.ui", name = "ui" }
androidx_ui_graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
androidx_ui_tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
androidx_ui_tooling_preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
androidx_ui_test_manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
androidx_ui_test_junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
androidx_material3 = { group = "androidx.compose.material3", name = "material3", version.ref = "material3" }
androidx_material_icons_extended = { group = "androidx.compose.material", name = "material-icons-extended" }
androidx_graphics_shapes = { group = "androidx.graphics", name = "graphics-shapes", version = "1.0.1" }
androidx_room_runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
androidx_room_ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
androidx_room_compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
androidx_datastore_preferences = { group = "androidx.datastore", name = "datastore-preferences", version.ref = "datastore" }
androidx_navigation_compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation" }
coil3_compose = { group = "io.coil-kt.coil3", name = "coil-compose", version.ref = "coil3" }
coil3_network = { group = "io.coil-kt.coil3", name = "coil-network-okhttp", version.ref = "coil3" }
androidx_ui_text_google_fonts = { group = "androidx.compose.ui", name = "ui-text-google-fonts", version.ref = "googleFonts" }
kotlinx_serialization_json = { group = "org.jetbrains.kotlinx", name = "kotlinx-serialization-json", version = "1.7.3" }
kotlinx_coroutines_core = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = "1.9.0" }
kotlinx_coroutines_test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version = "1.9.0" }
accompanist_permissions = { group = "com.google.accompanist", name = "accompanist-permissions", version.ref = "accompanist" }

[plugins]
android_application = { id = "com.android.application", version.ref = "agp" }
android_library = { id = "com.android.library", version.ref = "agp" }
kotlin_compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin_jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin_android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
androidx_room = { id = "androidx.room", version.ref = "room" }
kotlin_serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }

## Full Kotlin File List (Excluding build/ and hidden)
./feature/settings/src/main/java/com/p2p/meshify/feature/settings/SettingsViewModel.kt
./feature/settings/src/main/java/com/p2p/meshify/feature/settings/SettingsScreen.kt
./feature/home/src/main/java/com/p2p/meshify/feature/home/RecentChatsViewModel.kt
./feature/home/src/main/java/com/p2p/meshify/feature/home/RecentChatsScreen.kt
./feature/discovery/src/main/java/com/p2p/meshify/feature/discovery/DiscoveryViewModel.kt
./feature/discovery/src/main/java/com/p2p/meshify/feature/discovery/DiscoveryScreen.kt
./feature/chat/src/main/java/com/p2p/meshify/feature/chat/ChatScreen.kt
./feature/chat/src/main/java/com/p2p/meshify/feature/chat/ChatViewModel.kt
./app/src/test/java/com/p2p/meshify/ExampleUnitTest.kt
./app/src/main/java/com/p2p/meshify/MainActivity.kt
./app/src/main/java/com/p2p/meshify/AppContainer.kt
./app/src/main/java/com/p2p/meshify/service/MeshForegroundService.kt
./app/src/main/java/com/p2p/meshify/receivers/ReplyReceiver.kt
./app/src/main/java/com/p2p/meshify/MeshifyApp.kt
./app/src/androidTest/java/com/p2p/meshify/ExampleInstrumentedTest.kt
./core/common/src/test/java/com/p2p/meshify/core/util/PayloadSerializerTest.kt
./core/common/src/main/java/com/p2p/meshify/core/config/AppConfig.kt
./core/common/src/main/java/com/p2p/meshify/core/util/PayloadSerializer.kt
./core/common/src/main/java/com/p2p/meshify/core/util/FileUtils.kt
./core/common/src/main/java/com/p2p/meshify/core/util/Logger.kt
./core/network/src/main/java/com/p2p/meshify/core/network/lan/SocketManager.kt
./core/network/src/main/java/com/p2p/meshify/core/network/lan/LanTransportImpl.kt
./core/network/src/main/java/com/p2p/meshify/core/network/service/MessageQueueService.kt
./core/network/src/main/java/com/p2p/meshify/core/network/base/TransportEvent.kt
./core/network/src/main/java/com/p2p/meshify/core/network/base/IMeshTransport.kt
./core/domain/src/test/java/com/p2p/meshify/domain/model/SignalStrengthTest.kt
./core/domain/src/test/java/com/p2p/meshify/domain/model/PayloadTest.kt
./core/domain/src/main/java/com/p2p/meshify/domain/repository/IChatRepository.kt
./core/domain/src/main/java/com/p2p/meshify/domain/repository/IFileManager.kt
./core/domain/src/main/java/com/p2p/meshify/domain/repository/ISettingsRepository.kt
./core/domain/src/main/java/com/p2p/meshify/domain/usecase/ChatUseCases.kt
./core/domain/src/main/java/com/p2p/meshify/domain/model/ThemeConfig.kt
./core/domain/src/main/java/com/p2p/meshify/domain/model/SignalStrength.kt
./core/domain/src/main/java/com/p2p/meshify/domain/model/MessageType.kt
./core/domain/src/main/java/com/p2p/meshify/domain/model/Payload.kt
./core/data/src/main/java/com/p2p/meshify/core/data/repository/FileManagerImpl.kt
./core/data/src/main/java/com/p2p/meshify/core/data/repository/ChatRepositoryImpl.kt
./core/data/src/main/java/com/p2p/meshify/core/data/repository/SettingsRepository.kt
./core/data/src/main/java/com/p2p/meshify/core/data/local/entity/Entities.kt
./core/data/src/main/java/com/p2p/meshify/core/data/local/Migrations.kt
./core/data/src/main/java/com/p2p/meshify/core/data/local/MeshifyDatabase.kt
./core/data/src/main/java/com/p2p/meshify/core/data/local/dao/Daos.kt
./core/data/src/main/java/com/p2p/meshify/core/util/NotificationHelper.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/MeshifyKitDialogs.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/PhysicsSwipeToDelete.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/AlbumMediaGrid.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/MediaStagingChatInput.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/MeshifyKit.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/SettingsGroup.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/VideoPlayer.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/components/StagedMediaRow.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/navigation/MeshifyNavigation.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/navigation/Screen.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/hooks/PremiumHaptics.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/model/StagedAttachment.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/theme/MeshifyDesignSystem.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/theme/MD3ETheme.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/theme/Theme.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/theme/Type.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/theme/Color.kt
./core/ui/src/main/java/com/p2p/meshify/core/ui/theme/Font.kt
