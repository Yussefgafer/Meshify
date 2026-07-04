# Meshify

> **Offline-first P2P messaging for Android — no servers, no internet, no compromises.**

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.10-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Target%20SDK-36%20(Android%2016)-blue.svg" alt="Target SDK">
  <img src="https://img.shields.io/github/repo-size/Yussefgafer/Meshify?color=orange" alt="Repo Size">
  <img src="https://img.shields.io/badge/Version-1.1.2-purple.svg" alt="Version">
  <img src="https://img.shields.io/badge/Status-Active-brightgreen.svg" alt="Status">
</p>

<p align="center">
  <strong>Decentralized • Offline-Ready • Lightweight • Open Source</strong>
</p>

---

## 📖 Overview

Meshify is a **decentralized peer-to-peer messaging application** that enables real-time communication between Android devices on the same local network — **without requiring internet connectivity or central servers**.

Built with **Clean Architecture**, **Jetpack Compose**, and **Material 3**, Meshify delivers a modern, performant, and privacy-respecting messaging experience.

### ✨ Core Philosophy

- **Zero Infrastructure**: No servers, no cloud, no accounts. Just direct device-to-device communication.
- **Offline-First**: Works entirely on local networks (WiFi / LAN). Internet is optional.
- **Plaintext by Design**: No encryption overhead. Messages travel as plaintext over LAN for maximum simplicity and speed.
- **Privacy-Respecting**: No telemetry, no analytics, no data collection. Your conversations stay on your device.

---

## 🚀 Key Features

### 💬 Rich Messaging
- **1-on-1 messaging** with threaded replies
- **File attachments** — images, videos, documents
- **Message reactions**, delete, and forward
- **Message status tracking** — Queued, Sending, Sent, Delivered, Read, Failed
- **Offline storage** with Room database and pagination

### 🔌 Peer Discovery & Transport
- **mDNS/NSD** automatic peer discovery on local networks
- **BLE transport** (optional) — proximity-based messaging via Bluetooth Low Energy
- **Real-time presence** — instant online/offline status indicators
- **TCP-based transport** with connection pooling and keep-alive monitoring
- **UUID-based peer identification** (no phone numbers, no accounts)

### 🎨 Modern UI/UX
- **Material 3 Expressive** design system with dynamic colors
- **Light / Dark / System** theme support + custom seed color picker
- **Bubble style customization** — Rounded, Tailed, Squarcles, Organic
- **Motion presets** — Gentle, Standard, Snappy, Bouncy
- **Full Arabic & English localization** with RTL layout support
- **Onboarding flow** for first-time users
- **Tactile interactions** — spring-based animations, haptic feedback, morphing shapes

### 🏗️ Clean Architecture
- **Strict module boundaries** — domain, data, network, UI, and feature modules
- **Dependency Injection** with Hilt
- **Reactive state management** with Kotlin Flow and ViewModel
- **Type-safe navigation** with Kotlinx Serialization

---

## 📐 Architecture

```
:app
  ├── :core:common        # Utilities, FileUtils, ImageCompressor, constants
  ├── :core:data          # Room DB, DataStore, Repository implementations
  ├── :core:domain        # Pure Kotlin/JVM: Interfaces, Models, Use Cases
  ├── :core:network       # mDNS/NSD, TCP Transport, BLE, Connection Pooling
  └── :core:ui            # M3 Components, Theme, Haptics, Navigation
  ├── :feature:onboarding # Welcome / permission-granting flow
  ├── :feature:home       # Recent chats screen
  ├── :feature:chat       # Chat conversation screen
  ├── :feature:discovery  # Peer discovery with signal indicators
  ├── :feature:settings   # Settings & customization
  ├── :feature:help       # Help & About screen
  └── :feature:real-device-testing
```

### Module Dependency Rules

```
:app → :feature:* → :core:*
:feature:* → :core:* (NEVER other feature modules)
:core:* → :core:domain (domain has ZERO Android dependencies)
```

| Module | Responsibility |
|--------|---------------|
| `:core:domain` | Pure Kotlin/JVM — repository interfaces, domain models, use cases |
| `:core:data` | Room database, DataStore preferences, repository implementations |
| `:core:network` | mDNS/NSD discovery, LAN TCP sockets, BLE, connection pooling, health monitoring |
| `:core:ui` | Material 3 components, theming, haptics, navigation routes, shared composables |
| `:feature:onboarding` | First-launch welcome screens, permission requests |
| `:feature:home` | Recent chats list, online peers indicator |
| `:feature:chat` | Message conversation, file attachments, reactions |
| `:feature:discovery` | Peer device discovery with signal strength indicators |
| `:feature:settings` | Display name, avatar, theme, motion, bubble style, network visibility |
| `:feature:help` | Help documentation, About screen |
| `:feature:real-device-testing` | Hardware/network debugging tools |

---

## 🛠️ Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| **Language** | Kotlin | 2.3.10 |
| **UI** | Jetpack Compose | 2026.03.01 (BOM) |
| **Design** | Material 3 | 1.5.0-alpha17 |
| **Database** | Room | 2.8.4 |
| **Preferences** | DataStore | 1.1.1 |
| **Pagination** | Paging 3 | 3.4.0 |
| **Navigation** | Jetpack Navigation (type-safe) | 2.9.7 |
| **DI** | Hilt | 2.59.2 |
| **Media** | Media3 | 1.8.0 |
| **Images** | Coil 3 | 3.4.0 |
| **Serialization** | Kotlinx Serialization | 1.10.0 |
| **Testing** | JUnit 4, MockK, Turbine, Robolectric, Espresso | — |

### Build Configuration

| Setting | Value |
|---------|-------|
| **AGP** | 9.1.0 |
| **Compile SDK** | 36 (Android 16) |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 36 (Android 16) |
| **JDK** | 21 |
| **ABI** | `arm64-v8a` only |
| **Gradle** | `-Xmx4096m`, parallel builds, build cache |
| **R8** | Full mode for release builds |
| **Room Schema** | Exported per module |

---

## 🚦 Getting Started

### Prerequisites

- **JDK 21** (recommended via SDKMAN)
- **Android SDK** (API 36+)
- **Git**

### Build from Source

```bash
# Clone the repository
git clone https://github.com/Yussefgafer/Meshify.git
cd Meshify

# Clean build
./gradlew clean

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires KEYSTORE_PASSWORD env)
./gradlew assembleRelease
```

**Output APKs:**
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Install on Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Usage

1. Connect two or more Android devices to the **same WiFi network**
2. Launch Meshify on each device
3. Grant required permissions on first launch (onboarding flow)
4. Peers appear automatically via mDNS discovery
5. Tap a peer to start messaging

> **Note:** Meshify operates on **local networks only**. No internet connection required.

---

## 🧪 Testing

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run single test class
./gradlew :core:domain:test --tests "*PayloadTest*"

# Run single module tests
./gradlew :core:domain:test

# Lint check
./gradlew lint
```

---

## 🗺️ Roadmap

- [x] **BLE Transport**: Bluetooth Low Energy for proximity-based messaging
- [ ] **Group Messaging**: Multi-peer conversations
- [ ] **Voice Messages**: Audio recording and playback
- [ ] **Message Search**: Full-text search across conversations
- [ ] **Export/Import Backup**: Local chat backup and restore
- [ ] **End-to-End Encryption**: Optional encryption layer

---

## 🤝 Contributing

Meshify is a **personal project** built for learning and experimentation. While pull requests are welcome, please note that this repository is primarily maintained by a single developer.

### How to Contribute

1. **Fork** the repository
2. **Create a feature branch** (`git checkout -b feature/amazing-feature`)
3. **Commit your changes** with clear messages
4. **Push** to your branch (`git push origin feature/amazing-feature`)
5. **Open a Pull Request**

### Guidelines

- Follow **Clean Architecture** principles
- Maintain **module dependency boundaries**
- Write **KDoc comments** for public APIs
- Ensure **all tests pass** before submitting a PR

---

## 📬 Contact

**Yussef Gafer** — Project Maintainer

- GitHub: [@Yussefgafer](https://github.com/Yussefgafer)
- Project Link: [https://github.com/Yussefgafer/Meshify](https://github.com/Yussefgafer/Meshify)

---

## 🤖 AI-Assisted Development

> This codebase was designed and implemented using **LLM (Qwen)** under the strategic direction of **Yussef Gafer**. AI tools were used for code generation, architecture planning, and testing — with human oversight for every decision.

<p align="center">
  <img src="https://img.shields.io/badge/AI--ASSISTED-Qwen-7F52FF.svg?logo=openai&logoColor=white" alt="AI Assisted">
</p>

---

<p align="center">
  <strong>Built with ♥ for offline-first, decentralized communication.</strong>
</p>
