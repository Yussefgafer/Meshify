# Feature: Onboarding (Welcome Screen)

## Overview
Complete onboarding flow for Meshify with 4 screens, swipe support, and Material 3 Expressive design.

## Features
- ✅ 4 onboarding screens with animated illustrations
- ✅ Swipe support with HorizontalPager
- ✅ Page indicator dots with haptic feedback
- ✅ Double-tap protection (500ms debounce)
- ✅ RTL support (Arabic)
- ✅ TalkBack accessibility (content descriptions)
- ✅ Reduced motion support
- ✅ Spring animations (dampingRatio = 0.8f, stiffness = 350f)
- ✅ Button press animation (scale 1.0 → 0.92 in 50ms)
- ✅ Illustration loop animations (2000ms)

## Architecture

```
feature/onboarding/
├── WelcomeScreen.kt          → Main composable with pager & navigation
├── WelcomeViewModel.kt        → State management & navigation logic
├── WelcomeUiState.kt          → Data classes for UI state
├── OnboardingPage.kt          → Reusable page composable + illustrations
├── build.gradle.kts           → Module dependencies
├── proguard-rules.pro         → ProGuard configuration
├── consumer-rules.pro         → Consumer ProGuard rules
└── src/main/res/values/
    └── strings.xml            → All onboarding strings
```

## Usage

```kotlin
// In your navigation or activity:
val viewModel: WelcomeViewModel = viewModel()

WelcomeScreen(
    viewModel = viewModel,
    onGetStartedClick = {
        // Navigate to main app
        // Request permissions
        // Mark onboarding as completed
    },
    onPrivacyPolicyClick = {
        // Open privacy policy URL
    },
    onTermsClick = {
        // Open terms of service URL
    }
)
```

## Onboarding Screens

### Screen 1: Welcome
- **Title:** "Welcome to Meshify"
- **Subtitle:** "Experience the future of private communication"
- **Illustration:** Mesh network with connected nodes
- **Links:** Privacy Policy, Terms of Service

### Screen 2: Privacy
- **Title:** "Private & Secure"
- **Subtitle:** "Your data stays yours"
- **Illustration:** Shield with lock icon
- **Links:** Privacy Policy, Terms of Service

### Screen 3: P2P
- **Title:** "Direct P2P Messaging"
- **Subtitle:** "No internet required"
- **Illustration:** Three connected devices
- **Links:** None

### Screen 4: Get Started
- **Title:** "Let's Get Started"
- **Subtitle:** "Ready to join the mesh?"
- **Illustration:** Permission icons (WiFi, Bluetooth, Location)
- **Button:** "Get Started"

## Design System Integration

All UI elements use `MeshifyDesignSystem`:
- **Spacing:** `MeshifyDesignSystem.Spacing`
- **Shapes:** `MeshifyDesignSystem.Shapes.Button` (RoundedCornerShape(20.dp))
- **Colors:** `MeshifyPrimary`, `MeshifyOnPrimary`, `StatusOnline`
- **Typography:** `MaterialTheme.typography.displaySmall`, `titleLarge`, `bodyLarge`

## Animations

### Page Transitions
```kotlin
spring(
    dampingRatio = 0.8f,
    stiffness = 350f
)
```

### Button Press
- Scale: 1.0 → 0.92 (50ms)
- Haptic feedback: `HapticPattern.Pop`

### Illustration Loops
- Rotation: 0° → 360° (3000ms, linear)
- Scale: 1.0 → 1.05 (2000ms, reverse)
- Oscillation: -10° → 10° (2000ms, reverse)

## Accessibility

### TalkBack Support
- All icons have `contentDescription`
- Page dots announce current page number
- Buttons have descriptive labels

### Reduced Motion
- Animations use spring physics (respect system settings)
- No forced animations that ignore user preferences

### RTL Support
- Layout direction automatically adapts
- Text alignment is center-based
- Illustrations are direction-agnostic

## Haptic Feedback

Uses `LocalPremiumHaptics` from `core:ui`:
- **Tick:** Page navigation, dot taps
- **Pop:** Button press
- **Light tick:** Dot selection

## State Management

```kotlin
data class WelcomeUiState(
    val currentPage: Int = 0,
    val totalPages: Int = 4,
    val isLastPage: Boolean = false,
    val isAnimating: Boolean = false
)
```

## ViewModel Actions

- `nextPage()` — Navigate to next page
- `previousPage()` — Navigate to previous page
- `goToPage(pageIndex: Int)` — Jump to specific page
- `skipOnboarding()` — Skip to last page
- `getCurrentPageInfo()` — Get current page data

## Testing

```kotlin
// Unit tests (to be implemented)
@Test
fun welcomeViewModel_nextPage_updatesState() {
    val viewModel = WelcomeViewModel()
    viewModel.nextPage()
    assertEquals(1, viewModel.uiState.value.currentPage)
}

// UI tests (to be implemented)
@Test
fun welcomeScreen_swipeLeft_showsNextPage() {
    // Compose UI test
}
```

## Future Enhancements

- [ ] Add video illustrations
- [ ] Add sound effects (optional)
- [ ] Add more onboarding pages (features, permissions)
- [ ] Add A/B testing for onboarding flow
- [ ] Add analytics tracking
- [ ] Add skip confirmation dialog
- [ ] Add progress indicator (step X of Y)

## Dependencies

```kotlin
implementation(project(":core:common"))
implementation(project(":core:domain"))
implementation(project(":core:ui"))
implementation(platform(libs.androidx.compose.bom))
implementation(libs.androidx.ui)
implementation(libs.androidx.material3)
implementation(libs.androidx.lifecycle.runtime.compose)
implementation(libs.androidx.lifecycle.viewmodel.compose)
```

## Build Commands

```bash
# Compile onboarding module
./gradlew :feature:onboarding:compileDebugKotlin

# Build onboarding AAR
./gradlew :feature:onboarding:assembleDebug

# Run tests (when implemented)
./gradlew :feature:onboarding:test
```

## Known Issues

None at this time.

## Changelog

### 2026-03-21 — Initial Implementation
- Created `feature:onboarding` module
- Implemented 4 onboarding screens with animated illustrations
- Added swipe support with HorizontalPager
- Added page indicator dots with haptic feedback
- Implemented double-tap protection
- Added RTL and TalkBack support
- Integrated with MeshifyDesignSystem
- Build passes successfully
