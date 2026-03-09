# 🌐 Meshify: The Sovereign P2P Mesh Network
> **Last Updated:** Monday, March 9, 2026
> **Status:** Active Development (AI-Native)

## 🎯 Project Identity
Meshify is a high-performance, decentralized communication engine built on a modular P2P architecture. It bypasses central servers entirely, using local network discovery (NSD) and custom socket transports to create a resilient, device-to-device mesh.

---

## 🏗 Architecture (Clean & Modular)
The project follows a strict **Clean Architecture** pattern with **Manual Dependency Injection** via `AppContainer`.

- **`core/`**: Utilities, `Logger`, and `AppConfig`.
- **`data/`**: 
    - **Local**: Room DB (`MeshifyDatabase`) with specialized entities for `chats`, `messages`, and `pending_messages`.
    - **Repository**: Implementations for Chat, Settings, and File Management.
- **`domain/`**: The business logic layer. Contains repository interfaces and `ChatUseCases`.
- **`network/`**: 
    - **LAN**: `LanTransportImpl` using Android NsdManager for discovery (`_Meshify._tcp`).
    - **Service**: `MeshForegroundService` for persistent background mesh connectivity.
- **`ui/`**: 
    - **Theme**: Advanced **Material 3 Expressive (MD3E)** implementation.
    - **Components**: Custom `MeshifyKit`, `PremiumNoiseTexture`, and `PhysicsSwipeToDelete`.

---

## 📡 Networking Stack (The Mesh Engine)
The transport layer is pluggable via `IMeshTransport`.

- **Discovery:** Uses **mDNS/NSD**. Peers register as `Meshify_{UUID}`.
- **Handshake:** Automated exchange of Display Name and **Avatar Hashes** upon resolution.
- **Dead Peer Detection:** Peers are automatically marked as "Dead" and removed from the online list after **3 consecutive socket failures**.
- **Message Reliability:** A dedicated `MessageQueueService` monitors `pending_messages` and retries delivery when peers come back online.

---

## 🎨 Design System: MD3E + Spring Physics
Meshify uses a custom "Expressive" design system that goes beyond standard Material 3:

- **Shape Engine:** 7 base shapes (`Sunny`, `Clover`, `Burst`, `Blob`, etc.) normalized for fluid morphing.
- **Motion System:** 4 custom spring presets:
    - `Gentle`: (0.9 damping, 300 stiffness)
    - `Standard`: (0.8 damping, 600 stiffness)
    - `Snappy`: (0.6 damping, 1000 stiffness)
    - `Bouncy`: (0.4 damping, 800 stiffness) - The "Playful" signature.
- **Visual Texture:** `PremiumNoiseTexture` applied globally at 3% alpha for a high-end tactile feel.

---

## 🛠 Tech Stack Summary
- **Language:** Kotlin 2.1.x (JVM 21)
- **UI:** Jetpack Compose (Experimental MD3E)
- **Persistence:** Room (with Destructive Migration for dev), DataStore (Preferences)
- **Networking:** Java Sockets + NsdManager
- **Media:** Coil 3 (Image loading), Media3/ExoPlayer (Video)
- **DI:** Manual (`AppContainer`)

---

## ⚠️ Dev Guidelines & Rules
1. **Never Revert UI Logic:** The UI depends on specific `ExperimentalMaterial3ExpressiveApi` features. Don't "fix" them by reverting to stable M3.
2. **Handle Pending Messages:** When adding new message types, ensure they are integrated into `PendingMessageEntity` for reliability.
3. **P2P Visibility:** Always respect `ISettingsRepository.isNetworkVisible` before registering the NSD service.
4. **Surgical Edits Only:** The code is highly modular. Edit the specific layer (Data/Domain/UI) without bleeding logic into others.

---

## 🚀 Key Commands
- **Build & Run:** `./gradlew installDebug`
- **Check Lint:** `./gradlew lint`
- **Clear DB:** (Dev shortcut) The project uses `fallbackToDestructiveMigration(dropAllTables = true)`. Simply increment the version in `MeshifyDatabase` to wipe local state.
