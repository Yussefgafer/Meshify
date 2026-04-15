# Meshify

> **Offline-first P2P messaging for Android — no servers, no internet, no compromises.**

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.10-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin">
  <img src="https://img.shields.io/badge/Min%20SDK-26%20(Android%208.0)-brightgreen.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/Target%20SDK-35%20(Android%2015)-blue.svg" alt="Target SDK">
  <img src="https://img.shields.io/github/repo-size/Yussefgafer/Meshify?color=orange" alt="Repo Size">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Status-Pre--Alpha-red.svg" alt="Status">
</p>

<p align="center">
  <strong>Decentralized • Offline-Ready • Lightweight • Open Source</strong>
</p>

---

## 📖 Overview

Meshify is a **decentralized peer-to-peer messaging application** that enables real-time communication between Android devices on the same local network — **without requiring internet connectivity or central servers**.

Built with **Clean Architecture**, **Jetpack Compose**, and **Material 3 Expressive**, Meshify delivers a modern, performant, and privacy-respecting messaging experience.

### ✨ Core Philosophy

- **Zero Infrastructure**: No servers, no cloud, no accounts. Just direct device-to-device communication.
- **Offline-First**: Works entirely on local networks (WiFi). Internet is optional.
- **Plaintext by Design**: No encryption overhead. Messages travel as plaintext over LAN for maximum simplicity and speed.
- **Privacy-Respecting**: No telemetry, no analytics, no data collection. Your conversations stay on your device.

---

## 🚀 Key Features

### 💬 Rich Messaging
- **1-on-1 messaging** with threaded replies
- **File attachments** — images, videos, documents
- **Message reactions**, delete, and forward
- **Offline storage** with Room database and pagination

### 🔌 Peer Discovery & Transport
- **mDNS/NSD** automatic peer discovery on local networks
- **Real-time presence** — instant online/offline status indicators
- **TCP-based transport** with connection pooling and keep-alive monitoring
- **UUID-based peer identification** (no phone numbers, no accounts)

### 🎨 Modern UI/UX
- **Material 3 Expressive** design system with dynamic colors
- **Light / Dark / System** theme support
- **Full Arabic & English localization** with RTL layout support
- **Tactile interactions** — spring-based animations, premium haptic feedback, morphing shapes

### 🏗️ Clean Architecture
- **Strict module boundaries** — domain, data, network, UI, and feature modules
- **Dependency Injection** with Hilt
- **Reactive state management** with Kotlin Flow and ViewModel
- **Testable & maintainable** codebase with clear separation of concerns

---

## 📐 Architecture

```
:app
  ├── :core:common        # Utilities (Logger, FileUtils, ImageCompressor)
  ├── :core:data          # Room DB, DataStore, Repository implementations
  ├── :core:domain        # Pure Kotlin: Interfaces, Models, Use Cases
  ├── :core:network       # mDNS, TCP Transport, Connection Pooling
  └── :core:ui            # Material 3 Components, Theme, Haptics
  ├── :feature:home       # Recent chats screen
  ├── :feature:chat       # Chat conversation screen
  ├── :feature:discovery  # Device discovery
  └── :feature:settings   # Settings & customization
```

### Module Dependency Rules

```
:app → :feature:* → :core:*
:feature:* → :core:* (NEVER other feature modules)
:core:* → :core:domain (domain has ZERO dependencies)
```

| Module | Responsibility |
|--------|---------------|
| `:core:domain` | Pure Kotlin — repository interfaces, domain models, use cases |
| `:core:data` | Room database (v7), DataStore preferences, repository implementations |
| `:core:network` | mDNS/NSD discovery, LAN TCP sockets, connection pooling, health monitoring |
| `:core:ui` | Material 3 components, theming, haptics, shared composables |
| `:feature:*` | Screen-level UI, ViewModels, navigation |

---

## 🛠️ Tech Stack

| Category | Technology | Version |
|----------|-----------|---------|
| **Language** | Kotlin | 2.3.10 |
| **UI** | Jetpack Compose | 2026.02.00 (BOM) |
| **Design** | Material 3 Expressive | 1.4.0-alpha10 |
| **Database** | Room | 2.8.4 |
| **Preferences** | DataStore | 1.1.1 |
| **Pagination** | Paging 3 | 3.3.5 |
| **Navigation** | Jetpack Navigation | 2.9.7 |
| **DI** | Hilt | 2.59 |
| **Media** | Media3 | 1.8.0 |
| **Images** | Coil 3 | 3.4.0 |
| **Testing** | JUnit 4, MockK, Turbine, Robolectric, Espresso | — |

### Build Configuration

| Setting | Value |
|---------|-------|
| **AGP** | 9.1.0 |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 35 (Android 15) |
| **JDK** | 21 |
| **Gradle JVM Args** | `-Xmx4096m` |

---

## 🚦 Getting Started

### Prerequisites

- **JDK 21** (recommended via SDKMAN)
- **Android SDK** (API 26+)
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

# Build release APK (requires signing config)
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
3. Peers appear automatically via mDNS discovery
4. Tap a peer to start messaging

> **Note:** Meshify operates on **local networks only**. No internet connection required.

---

## 🧪 Testing

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run instrumented tests
./gradlew connectedAndroidTest

# Lint check
./gradlew lint
```

---

## 📸 Screenshots

<p align="center">
  <em>Screenshots coming soon...</em>
</p>

---

## 🗺️ Roadmap

- [ ] **BLE Transport**: Bluetooth Low Energy for proximity-based messaging
- [ ] **Group Messaging**: Multi-peer conversations
- [ ] **Voice Messages**: Audio recording and playback
- [ ] **Custom Themes**: User-selectable color palettes beyond Material You
- [ ] **Message Search**: Full-text search across conversations
- [ ] **Export/Import Backup**: Local chat backup and restore

---

## 📄 License

Distributed under the **MIT License**. See [LICENSE](LICENSE) for details.

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
