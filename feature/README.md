# Feature Modules

This directory contains the feature modules for the Meshify application. Each module represents a distinct feature of the app and follows Clean Architecture principles.

## Module Structure

```
feature/
├── home/          # Recent chats screen (Home)
├── chat/          # Chat conversation screen
├── discovery/     # Peer discovery screen
└── settings/      # Application settings screen
```

## Modules Overview

### `:feature:home`
**Purpose**: Displays recent chats and online peers status.

**Components**:
- `RecentChatsScreen.kt` - UI composable for the home screen
- `RecentChatsViewModel.kt` - ViewModel managing chat list state

**Dependencies**:
- `:core:domain` - For `IChatRepository` interface
- `:core:data` - For `ChatEntity` data class
- `:core:ui` - For UI components (MeshifyListItem, MorphingAvatar, etc.)
- `:core:common` - For common utilities

---

### `:feature:chat`
**Purpose**: Handles one-on-one chat conversations with media support.

**Components**:
- `ChatScreen.kt` - UI composable for chat conversations
- `ChatViewModel.kt` - ViewModel managing messages and attachments
- `ChatUiState.kt` - Data class representing UI state (inline in ViewModel file)

**Features**:
- Text messaging
- Image/Video attachments (up to 10)
- Message reactions
- Reply functionality
- Delete for me/everyone
- Message status indicators (Queued, Sending, Sent, Delivered, Read, Failed)

**Dependencies**:
- `:core:domain` - For `IChatRepository`, `DeleteType`, `BubbleStyle`
- `:core:data` - For `MessageEntity`, `MessageType`, `MessageStatus`
- `:core:ui` - For UI components (MessageBubble, AlbumMediaGrid, etc.)
- `:core:common` - For common utilities

---

### `:feature:discovery`
**Purpose**: Discovers and displays nearby peer devices.

**Components**:
- `DiscoveryScreen.kt` - UI composable for peer discovery
- `DiscoveryViewModel.kt` - ViewModel observing mesh transport events
- `DiscoveryUiState.kt` - Data class for UI state (inline in ViewModel file)
- `PeerDevice.kt` - Data class representing a discovered peer (inline in ViewModel file)

**Features**:
- Real-time peer discovery via `IMeshTransport`
- Signal strength indicator (Strong, Medium, Weak, Offline)
- RSSI-based signal quality

**Dependencies**:
- `:core:domain` - For `SignalStrength` model
- `:core:network` - For `IMeshTransport` and `TransportEvent`
- `:core:ui` - For UI components (RadarPulseMorph, MorphingAvatar, etc.)
- `:core:common` - For common utilities

---

### `:feature:settings`
**Purpose**: Manages application settings and user preferences.

**Components**:
- `SettingsScreen.kt` - UI composable for settings
- `SettingsViewModel.kt` - ViewModel managing settings state

**Features**:
- Display name management
- Avatar upload (content-addressable storage)
- Theme mode selection (Light, Dark, System)
- Dynamic colors toggle
- Custom seed color picker
- Motion preset selection (Gentle, Standard, Snappy, Bouncy)
- Network visibility toggle
- Device ID display and copy

**Dependencies**:
- `:core:domain` - For `ISettingsRepository`, `ThemeMode`, `MotionPreset`, `BubbleStyle`
- `:core:data` - For settings data storage
- `:core:ui` - For UI components (MeshifySettingsItem, SeedColorPickerGrid, etc.)
- `:core:common` - For `FileUtils` and common utilities

---

## Architecture Principles

### Clean Architecture
Each feature module follows Clean Architecture:
- **Presentation Layer**: Screen composables and ViewModels
- **Domain Layer**: Accessed via `:core:domain` (repositories, models)
- **Data Layer**: Accessed via `:core:data` (entities, repositories implementation)

### Separation of Concerns
- UI logic is isolated in feature modules
- Business logic resides in `:core:domain`
- Data access is abstracted through repositories

### Dependency Rules
```
:app
  └── :feature:*
        └── :core:*
```

Feature modules **cannot** depend on each other. All inter-feature communication goes through the `:app` module or shared `:core:*` modules.

## Build Configuration

Each feature module has its own `build.gradle.kts` with:
- Android library plugin
- Kotlin Compose plugin
- Namespace: `com.p2p.meshify.feature.<name>`
- Compile SDK: 35
- Min SDK: 26

## Testing

Each module includes:
- JUnit for unit testing
- AndroidX Test for instrumented tests
- Compose UI testing utilities

## Migration Notes

### From `:app` Module
When migrating screens from `:app` to feature modules:
1. Update package from `com.p2p.meshify.ui.screens.*` to `com.p2p.meshify.feature.*`
2. Update imports to reference core modules
3. Ensure all dependencies are declared in `build.gradle.kts`
4. Remove circular dependencies

### Package Naming
- **Old**: `com.p2p.meshify.ui.screens.recent`
- **New**: `com.p2p.meshify.feature.home`

## Future Enhancements

- [ ] Add navigation graphs for each feature module
- [ ] Implement feature module-level DI (Hilt/Koin)
- [ ] Add screenshot tests for UI components
- [ ] Create shared UI test rules for common interactions
