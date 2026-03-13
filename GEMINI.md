# 🌐 Meshify: AI-Native P2P Mesh Network

Meshify is a decentralized, high-performance communication engine built on a modular P2P architecture. It enables resilient, device-to-device messaging without central servers or internet dependency.

---
# اهم شيء يجب ان تتذكره و هو ان لا لل Mocking و يجب استغلال ال Skills المناسبه
---

## 🏗 Architecture & Core Design
Meshify follows a strict **Clean Architecture** pattern with **Domain-Driven Design (DDD)** and **Manual Dependency Injection**.

### 📦 Key Modules
- **`app/`**: Application entry point, DI wiring (`AppContainer`), and `MeshForegroundService`.
- **`core/network/`**: The transport layer. Defines `IMeshTransport` and implements `LanTransportImpl` (NSD/Sockets).
- **`core/data/`**: Persistence layer using Room (`MeshifyDatabase`) and DataStore.
- **`core/domain/`**: Pure business logic, repository interfaces (`IChatRepository`, `ISettingsRepository`), and domain models.
- **`core/ui/`**: **Material 3 Expressive (MD3E)** implementation. Contains the shape morphing engine, custom spring physics, and `MeshifyKit` components.
- **`feature/`**: Module-per-feature (Chat, Discovery, Settings, Home).

### 💉 Dependency Injection
- **`AppContainer`**: The central DI hub. Manages singletons like `ChatRepository`, `LanTransport`, and `Database`. 
- **Manual Injection**: ViewModels and Services receive dependencies directly from the `AppContainer` via the custom `Application` class (`MeshifyApp`).

---

## 📡 Networking Stack
- **Discovery**: Uses Android **NsdManager** (mDNS). Service type: `_Meshify._tcp`.
- **Transport**: `IMeshTransport` abstraction allows for LAN (Sockets), Bluetooth, or Wi-Fi Direct implementations.
- **Reliability**: `MessageQueueService` and `PendingMessageDao` handle retries and offline message queuing.
- **Payload Handling**: Centralized in `ChatRepositoryImpl.handleIncomingPayload`.

---

## 🎨 UI & Motion System (MD3E)
Meshify leverages the **Material 3 Expressive** design language:
- **Shape Engine**: 7 base shapes (`Sunny`, `Breezy`, `Pentagon`, `Blob`, `Burst`, `Clover`, `Circle`) normalized for fluid morphing via `androidx.graphics.shapes`.
- **Motion System**: 4 signature spring presets defined in `MotionSpecs`:
  - `Gentle`: (0.9 damping, 300 stiffness)
  - `Standard`: (0.8 damping, 600 stiffness)
  - `Snappy`: (0.6 damping, 1000 stiffness)
  - `Bouncy`: (0.4 damping, 800 stiffness) - Playful "rubber-band" feel.
- **Components**: `MorphingAvatar`, custom FABs, and `PremiumNoiseTexture` for tactile depth.

---

## 🛠 Tech Stack
- **Language**: Kotlin 2.3.x (JVM 21)
- **Build System**: AGP 9.1.0, Gradle 8.9+
- **Database**: Room 2.8.0 (with Destructive Migration enabled for development)
- **UI**: Jetpack Compose (Experimental MD3E APIs)
- **Media**: Coil 3 (Network & File loading)

---

## 🚀 Key Commands
| Task | Command |
|------|---------|
| **Build Debug APK** | `./gradlew assembleDebug` |
| **Run Unit Tests** | `./gradlew test` |
| **Clean Build** | `./gradlew clean` |
| **Lint Check** | `./gradlew lint` |

---

## ⚠️ Development Context (Critical)
1. **Surgical Edits**: Follow the Clean Architecture boundaries. Don't leak Network/Data logic into the UI or Domain layers.
2. **Experimental APIs**: The project uses `@ExperimentalMaterial3ExpressiveApi`. Do not "downgrade" to stable M3 unless explicitly asked.
3. **P2P Lifecycle**: `MeshForegroundService` manages the network lifecycle. Ensure `transport.stop()` is called deterministically to release socket locks.
4. **God Object Warning**: `ChatRepositoryImpl` is currently over-burdened (500+ lines). Future tasks should focus on decomposing it into specialized managers (e.g., `MessageSender`, `HandshakeManager`).
5. **Data Migrations**: Room is currently using `fallbackToDestructiveMigration()`. Be aware that schema changes will wipe local database data.

*Last Updated: March 13, 2026*
