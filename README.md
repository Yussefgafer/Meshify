# Meshify

**Decentralized P2P messaging for Android — works without internet.**

Meshify is a peer-to-peer messaging application that enables secure, encrypted communication between Android devices on the same local network. No central server. No phone number. No internet connection required.

<p align="center">
  <img src="https://img.shields.io/github/repo-size/Yussefgafer/Meshify" alt="Repo size">
  <img src="https://img.shields.io/badge/Kotlin-2.3.10-blue.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/Min_API-26-brightgreen.svg" alt="Min API">
  <img src="https://img.shields.io/badge/Target_API-35-blue.svg" alt="Target API">
  <img src="https://img.shields.io/badge/Status-Pre--Alpha-orange.svg" alt="Status">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
</p>

<p align="center">
AI-NATIVE PROJECT
This entire codebase, architecture, and UI logic were architected and implemented by LLM(Qwen-3.6) under the strategic direction of Me(Yussef Gafer). No human code was harmed in the making of this app.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/AI--GENERATED-100%25-red?style=for-the-badge&logo=openai&logoColor=white" alt="AI Generated">
</p>

---

## Key Features

### Encrypted Messaging
- **AES-256-GCM** encryption for all messages
- **ECDH + HKDF** key exchange with 256-bit session keys
- **ECDSA** signatures + Trust-On-First-Use (TOFU) authentication
- **Replay protection** via nonce cache + timestamp validation

### Peer Discovery
- **mDNS/NSD** automatic peer discovery on local network
- **Real-time presence** — online/offline status indicators
- **TCP-based transport** with connection pooling and keep-alive

### Rich Messaging
- Text messages with threaded replies
- Image, video, and file attachments
- Delete messages
- Forward to multiple peers

### Modern UI
- Material 3 design system
- Light/Dark/System theme with dynamic colors
- Full Arabic and English localization with RTL support

---

## Architecture

Built on **Clean Architecture** with strict module boundaries:

| Module | Responsibility |
|--------|---------------|
| `:core:domain` | Pure Kotlin — interfaces and models only |
| `:core:data` | Room database, DataStore, repositories |
| `:core:network` | mDNS discovery, TCP transport, encryption |
| `:core:ui` | Material 3 components, theming, haptics |
| `:feature:*` | Screen-level UI and ViewModels |

---

## Tech Stack

- **Kotlin 2.3.10** — primary language
- **Jetpack Compose** — modern declarative UI
- **Room 2.8.4** — offline database with paging
- **Material 3 Expressive** — design system
- **Tink 1.16.0** — cryptographic primitives
- **Bouncy Castle 1.82** — crypto provider
- **Media3 1.8.0** — media playback
- **Coil 3** — image loading

---

## Getting Started

### Prerequisites

| Requirement | Version |
|-------------|---------|
| JDK | 21 |
| Android SDK | API 26+ |
| Target SDK | API 35 |

### Build

```bash
git clone https://github.com/Yussefgafer/Meshify.git
cd Meshify

# Debug APK
./gradlew assembleDebug

# Release APK (requires signing config)
./gradlew assembleRelease
```

The built APK will be at:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

### Install

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Usage

1. Connect two or more Android devices to the **same WiFi network**
2. Launch Meshify on each device
3. Peers appear automatically via mDNS
4. Tap a peer to start encrypted messaging

> **Note:** Meshify operates on local networks only. Internet connectivity is not required.

---

## License

MIT License. See [LICENSE](LICENSE) for details.

---

## Links

- [GitHub Repository](https://github.com/Yussefgafer/Meshify)
