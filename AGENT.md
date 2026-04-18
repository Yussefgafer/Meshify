# MESHIFY PROJECT CONTEXT

> **Project**: Meshify - Offline P2P Messaging
 - This entire project is built using LLMs.
 - This app is for personal use only, so performance and speed should be prioritized over security, but clean code is essential.
 - The current project is a Git repo, so you can look at the latest commits to understand what changes have occurred.
> **Type**: Android Application (Kotlin + Jetpack Compose + Hilt)
> **Architecture**: Clean Architecture + MVVM + Multi-Module

---

## PROJECT OVERVIEW

Meshify is a decentralized, offline-first P2P messaging application that enables communication between Android devices on the same local network without requiring internet connectivity or central servers.

### Core Value Proposition
- **Simple & Lightweight**: Plaintext messages over LAN with zero encryption overhead
- **Offline-Ready**: Works on local networks (WiFi) without internet access
- **Clean Architecture**: Modular, testable, and maintainable codebase
- **Localization**: Full English and Arabic (RTL) support

### Key Features
- 1-on-1 plaintext text messaging
- File attachments (images, videos, documents)
- Message replies, reactions, delete, forward
- mDNS/NSD peer discovery
- LAN TCP transport with connection pooling
- Material 3 UI
- Room offline database with pagination
- UUID-based peer identification (SimplePeerIdProvider)

---

## ARCHITECTURE

### Module Dependency Graph

```
:app
  ├── :core:common        # Utilities (Logger, FileUtils, ImageCompressor)
  ├── :core:data          # Room DB, DataStore, Repository implementations
  ├── :core:domain        # Pure Kotlin: Interfaces, Models, Use Cases
  ├── :core:network       # mDNS, Sockets, LAN/BLE Transport
  └── :core:ui            # Material 3 Components, Theme, Shared UI
  ├── :feature:home       # Recent chats screen
  ├── :feature:chat       # Chat conversation screen
  ├── :feature:discovery  # Device discovery
  └── :feature:settings   # Settings & customization
```

### Dependency Rules
```
:app → :feature:* → :core:*
:feature:* → :core:* (NEVER other feature modules)
:core:* → :core:domain (domain has ZERO dependencies)
```

### Architectural Layers

**Presentation Layer** (`:feature:*`, `:core:ui`)
- Jetpack Compose UI with Material 3
- ViewModels for state management
- Hilt for dependency injection

**Domain Layer** (`:core:domain`)
- Pure Kotlin (no Android/framework dependencies)
- Repository interfaces (`IChatRepository`, `ISettingsRepository`)
- Domain models and use cases
- Security models (`SecurityEvent`, `MessageEnvelope`, `OobVerificationMethod`)

**Data Layer** (`:core:data`)
- Room database (plain SQLite, no encryption)
- DataStore for preferences
- Repository implementations
- LRU caching and pagination

**Network Layer** (`:core:network`)
- LAN Transport: TCP sockets with connection pooling
- BLE Transport: Bluetooth Low Energy (in progress)
- mDNS/NSD service discovery
- Keep-alive and health monitoring

---

## TECH STACK

### Core Technologies
| Technology | Version | Purpose |
|------------|---------|---------|
| **Kotlin** | 2.3.10 | Primary language |
| **AGP** | 9.1.0 | Android Gradle Plugin |
| **Jetpack Compose** | 2026.02.00 (BOM) | UI toolkit |
| **Material 3** | 1.4.0-alpha10 | Design system |

### Data & State
| Library | Version | Purpose |
|---------|---------|---------|
| **Room** | 2.8.4 | Local database |
| **DataStore** | 1.1.1 | Preferences storage |
| **Paging 3** | 3.3.5 | Data pagination |
| **Navigation** | 2.9.7 | In-app navigation |
| **Hilt** | 2.59 | Dependency injection |

### Networking & Media
| Library | Version | Purpose |
|---------|---------|---------|
| **Media3** | 1.8.0 | Media playback |
| **Coil 3** | 3.4.0 | Image loading |

### Build & Testing
| Tool | Purpose |
|------|---------|
| **KSP** | Annotation processing (Room, Hilt) |
| **JUnit 4** | Unit testing |
| **MockK** | Mocking framework |
| **Turbine** | Flow testing |
| **Robolectric** | Android unit testing |
| **Espresso** | UI testing |

---

## BUILD & RUN COMMANDS

### Prerequisites
- **JDK**: 21 (configured via SDKMAN: `/home/youusef/.sdkman/candidates/java/21-librca`)
- **Android SDK**: API 26+ (Min), API 35 (Target)
- **Gradle JVM Args**: `-Xmx4096m` (configured in `gradle.properties`)

### Build Commands

```bash
# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run all tests
./gradlew test

# Run unit tests only
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint

# Format Kotlin code
./gradlew ktlintFormat
```

### Development Commands

```bash
# Run app on connected device/emulator
./gradlew installDebug

# View build scan
./gradlew build --scan

# Check dependency updates
./gradlew dependencyUpdates
```

---

## KEY DIRECTORIES

```
Meshify/
├── app/                          # Main application module
│   ├── src/main/java/com/p2p/meshify/
│   │   ├── MeshifyApp.kt        # Application class (Hilt setup)
│   │   ├── MainActivity.kt      # Main activity (Compose host)
│   │   ├── service/             # Foreground services
│   │   └── receivers/           # Broadcast receivers
│   ├── schemas/                 # Room database schemas
│   └── build.gradle.kts         # App-level build config
├── core/
│   ├── common/                  # Shared utilities
│   ├── data/                    # Data layer (Room, DataStore, Repos)
│   ├── domain/                  # Domain layer (Interfaces, Models)
│   │   ├── model/               # Domain models (PeerDevice, Payload, etc.)
│   │   ├── repository/          # Repository interfaces
│   │   ├── security/            # Crypto interfaces & models
│   │   └── usecase/             # Use cases
│   ├── network/                 # Network layer
│   │   ├── base/                # Transport interfaces
│   │   ├── lan/                 # LAN TCP implementation
│   │   └── ble/                 # BLE implementation
│   └── ui/                      # Shared UI components & theme
├── feature/
│   ├── home/                    # Recent chats screen
│   ├── chat/                    # Chat conversation screen
│   ├── discovery/               # Peer discovery screen
│   ├── settings/                # Settings screen
│   ├── onboarding/              # Welcome flow
│   └── help/                    # FAQ & About screens
├── gradle/
│   └── libs.versions.toml       # Version catalog
├── docs/                        # Documentation local only (this file in the .gitignore)
└── QWEN.md                      # This file (AI assistant context) (this file in the .gitignore)
```

---

## SECURITY ARCHITECTURE

### Message Format — Plaintext
- **MessageEnvelope**: Simple data class with `senderId`, `recipientId`, `text`, `timestamp`, `messageType`
- **Serialization**: Binary ByteBuffer with length-prefixed fields (efficient, compact)
- **No encryption**: Messages travel as plaintext over LAN
- **No authentication**: No ECDSA signatures, no TOFU, no key exchange

### Database
- **Plain SQLite**: No SQLCipher, no encryption
- **Room**: Version 7 (migrated from v6 by dropping `trusted_peers` table)
- **Key Management**: None required

### Network
- **No Central Server**: Pure P2P architecture
- **Local Network Only**: No internet dependency
- **Connection Pooling**: Pre-warmed, monitored connections
- **Handshake V3**: Exchanges peer name only (no crypto fields)

### Peer Identity
- **SimplePeerIdProvider**: UUID generated once, stored in SharedPreferences
- **No Keystore**: No EC keys, no certificates, no Android Keystore
- **No Biometric**: No fingerprint/face authentication

### Removed Security Infrastructure (5 phases, ~8,300 lines)
All of the following have been permanently removed:
- ~~SQLCipher~~ (Phase 1)
- ~~Android Keystore PeerIdentity~~ (Phase 2)
- ~~ECDH key exchange (EcdhSessionManager)~~ (Phase 3)
- ~~AES-256-GCM encryption (MessageEnvelopeCrypto)~~ (Phase 3)
- ~~HKDF key derivation (HkdfKeyDerivation)~~ (Phase 3)
- ~~Nonce cache replay protection~~ (Phase 3)
- ~~EncryptedSessionKeyStore~~ (Phase 3)
- ~~TOFU trust model (PeerTrustStore, TrustedPeer, TrustLevel)~~ (Phase 4)
- ~~Security events (DecryptionFailed, TofuViolation, SessionExpired)~~ (Phase 4)
- ~~Tink, BouncyCastle, Security Crypto, Biometric dependencies~~ (Phase 5)

---

## UI/UX CONVENTIONS

### Material 3
- Uses `androidx.compose.material3:material3:1.4.0-alpha10`
- Opt-in: `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`
- Motion presets: Gentle, Standard, Snappy, Bouncy

### Theme System
- **Modes**: Light, Dark, System
- **Dynamic Colors**: Material You support
- **Custom Seed Colors**: User-selectable accent colors
- **Persistence**: Stored via DataStore

### Localization
- **Languages**: English, Arabic
- **RTL Support**: Full right-to-left layout support
- **String Resources**: All UI strings in `strings.xml`
- **Google Fonts**: For typography

## UI/UX CONVENTIONS (FIDGET TOY PHILOSOPHY)

### Core Philosophy
- **The "Fidget Toy" Feel:** Every user interaction MUST have an immediate, satisfying, and physically plausible reaction. The app should feel alive, tactile, and playful.
- **Zero Latency Illusion:** Since the app is offline-first, UI updates must be instant. Never block UI animations waiting for background tasks.

### Design Language & Shapes
- **Standard:** Material You 3 Expressive (M3E) / Android 16 design language.
- **Strict Shape Rules:**
  - **NEVER** use perfect `CircleShape` or hard sharp corners.
  - **ALWAYS** use **Squircles** or highly rounded squares (e.g., `RoundedCornerShape(24.dp)` or `28.dp` for large elements, `16.dp` for smaller ones).
  - **Shape Morphing:** Interactive elements MUST animate their corner radius or border thickness on press/hover (e.g., a button morphs from `24.dp` to `32.dp` when pressed down).

---

### Key Optimizations
- BufferedOutputStream for file transfer
- WebP image compression
- Parallel file transfer
- Connection pooling
- ArrayDeque for messages (O(1) prepend)
- LRU cache for attachments
- `derivedStateOf` in Compose
- Stable LazyColumn keys
- Flow `.distinctUntilChanged`

---

## TESTING STRATEGY

### Current Status
- **Unit tests**: Minimal (needs coverage)
- **UI tests**: Not implemented
- **Test infrastructure**: JUnit, MockK, Turbine, Robolectric configured

### Testing Conventions
- **Unit Tests**: `/src/test/java/` (JUnit 4 + MockK)
- **Instrumented Tests**: `/src/androidTest/java/` (AndroidX Test + Espresso)
- **Flow Testing**: Use Turbine for Kotlin Flow streams
- **Coroutines**: Use `kotlinx-coroutines-test` for dispatcher control

### Test Targets
- Repository implementations
- ViewModel state management
- UI component rendering
- Integration tests (network + DB)
- Message serialization/deserialization

---


## DEVELOPMENT CONVENTIONS

### Kotlin Coding Style
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Prefer `val` over `var` (immutability first)
- Use data classes for models
- Seal classes for state modeling

### Compose Best Practices
- Use `remember` and `derivedStateOf` for performance
- Stable keys for LazyColumn items
- Hoist state to ViewModels
- Use `LaunchedEffect` for side effects
- Avoid recomposition loops

---

## CONFIGURATION FILES

| File | Purpose |
|------|---------|
| `build.gradle.kts` | Root build configuration |
| `settings.gradle.kts` | Module includes and repository config |
| `gradle.properties` | Gradle JVM args, Kotlin settings |
| `gradle/libs.versions.toml` | Version catalog for dependencies |
| `app/build.gradle.kts` | App-level build config (signing, features) |
| `app/proguard-rules.pro` | R8/ProGuard rules |
| `app/src/main/AndroidManifest.xml` | Android manifest (permissions, components) |


