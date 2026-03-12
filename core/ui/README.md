# :core:ui Module

Material 3 Expressive UI components and theme for Meshify.

## Structure

```
core/ui/src/main/java/com/p2p/meshify/core/ui/
├── theme/
│   ├── Color.kt              # Color palette and presets
│   ├── Type.kt               # Typography scale
│   ├── Font.kt               # Google Fonts families
│   ├── MD3ETheme.kt          # Motion specs and shapes
│   ├── MeshifyDesignSystem.kt # Spacing, shapes, elevation
│   └── Theme.kt              # Main theme composable
├── components/
│   ├── MeshifyKit.kt         # Core components (Avatar, Card, FAB, etc.)
│   ├── MeshifyKitDialogs.kt  # Dialog components
│   ├── AlbumMediaGrid.kt     # Media grid for attachments
│   ├── PhysicsSwipeToDelete.kt # Swipe-to-delete with physics
│   ├── SettingsGroup.kt      # Settings UI components
│   ├── StagedMediaRow.kt     # Staged media preview row
│   ├── MediaStagingChatInput.kt # Chat input with media staging
│   ├── StandardChatInput.kt  # Standard chat input (optional)
│   └── VideoPlayer.kt        # ExoPlayer video composable
├── navigation/
│   ├── Screen.kt             # Type-safe navigation routes
│   └── MeshifyNavigation.kt  # Navigation host
├── hooks/
│   └── PremiumHaptics.kt     # Haptic feedback patterns
└── model/
    └── StagedAttachment.kt   # UI model for staged media
```

## Dependencies

- `:core:common` - Utility classes (FileUtils)
- `:core:domain` - Domain models and repositories
- `:core:data` - Data entities (MessageAttachmentEntity, MessageType)

## Features

### Theme System
- **MD3E (Material 3 Expressive)**: Dynamic motion, shapes, and colors
- **Google Fonts Integration**: Poppins, Lora, Montserrat, Inter, Playfair Display
- **Dynamic Color**: Support for Android 12+ Material You
- **Custom Shape Styles**: Star, Circle, Blob, Burst, Clover, etc.

### Components
- **MorphingAvatar**: Shape-morphing avatar with MD3E shapes
- **AnimatedMorphingFAB**: Expressive floating action button
- **PhysicsSwipeToDelete**: Swipe-to-delete with haptic feedback
- **MeshifySettingsGroup**: Settings UI with scale animations
- **AlbumMediaGrid**: Telegram-style media grid

### Navigation
- Type-safe navigation with Kotlinx Serialization
- Generic navigation host for app module integration

## Usage

### Applying the Theme

```kotlin
MeshifyTheme(
    themeMode = "SYSTEM", // "LIGHT", "DARK", "SYSTEM"
    dynamicColor = true,
    motionPreset = MotionPreset.STANDARD,
    shapeStyle = ShapeStyle.CIRCLE,
    bubbleStyle = BubbleStyle.ROUNDED
) {
    // Your app content
}
```

### Using Components

```kotlin
// Morphing Avatar
MorphingAvatar(
    initials = "John",
    avatarHash = "abc123",
    isOnline = true,
    size = 56.dp
)

// Physics Swipe to Delete
PhysicsSwipeToDelete(
    onDelete = { /* delete action */ },
    position = ItemPosition.ONLY
) {
    // Your content
}

// Settings Item
MeshifySettingsItem(
    title = "Notifications",
    subtitle = "Manage notification settings",
    icon = Icons.Default.Notifications,
    onClick = { /* navigate */ }
)
```

## Migration Notes

This module was migrated from `:app` module to provide reusable UI components across the app.

### Package Changes
- Old: `com.p2p.meshify.ui.*`
- New: `com.p2p.meshify.core.ui.*`

### Key Changes
1. `FileUtils` moved to `:core:common`
2. `StagedAttachment` moved to `:core:ui:model`
3. `PremiumHaptics` moved to `:core:ui:hooks`
4. Navigation is now generic and provided by app module
