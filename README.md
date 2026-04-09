# Meshify

**Decentralized P2P messaging for Android — works without internet.**

Meshify is a peer-to-peer messaging application that enables secure, encrypted communication between Android devices on the same local network. No central server. No phone number. No internet connection required.

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.3.10-blue.svg" alt="Kotlin">
  <img src="https://img.shields.io/badge/Min_API-26-brightgreen.svg" alt="Min API">
  <img src="https://img.shields.io/badge/Target_API-35-blue.svg" alt="Target API">
  <img src="https://img.shields.io/badge/Status-Pre--Alpha-orange.svg" alt="Status">
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License"></a>
</p>

---

## Key Features

### 🔐 Encrypted Messaging
- **AES-256-GCM** encryption for all messages
- **ECDH + HKDF** key exchange with 256-bit session keys
- **ECDSA** signatures + Trust-On-First-Use (TOFU) authentication
- **Replay protection** via nonce cache + timestamp validation

### 📡 Peer Discovery
- **mDNS/NSD** automatic peer discovery on local network
- **Real-time presence** — online/offline status indicators
- **TCP-based transport** with connection pooling and keep-alive

### 💬 Rich Messaging
- Text messages with threaded replies
- Emoji reactions on messages
- Image, video, and file attachments
- Delete for me / Delete for everyone
- Forward to multiple peers

### 🎨 Modern UI
- Material 3 Expressive design system
- Light/Dark/System theme with dynamic colors
- Full Arabic and English localization with RTL support
- TalkBack accessibility support

---

## Architecture

```
┌─────────────────────────┐
│   UI (Jetpack Compose)  │   ← feature: modules
├─────────────────────────┤
│      ViewModel          │   ← State management
├─────────────────────────┤
│   Domain (Pure Kotlin)  │   ← Interfaces + Models
├─────────────────────────┤
│      Data Layer         │   ← Room + DataStore + Repos
├─────────────────────────┤
│    Network Layer        │   ← mDNS + Sockets + Crypto
└─────────────────────────┘
```

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

## Security

### Encryption Protocol

```
Device A                              Device B
    │                                    │
    │── HANDSHAKE (identity + ephemeral) ──▶│
    │◀── HANDSHAKE ACK (ephemeral + nonce) ─│
    │                                    │
    │  ECDH → HKDF → 256-bit session key │
    │                                    │
    │◀── AES-256-GCM (ECDSA signed) ──────▶│
    │   • Nonce cached (replay guard)     │
    │   • Timestamp validated (±5 min)    │
    └────────────────────────────────────┘
```

### Security Properties

- **Confidentiality** — AES-256-GCM with 12-byte random IV
- **Authentication** — ECDSA signatures on every message
- **Integrity** — GCM authentication tag (128-bit)
- **Replay Protection** — nonce cache + ±5 minute timestamp window
- **TOFU** — Trust-On-First-Use for peer identity keys
- **Fail-Fast** — signature verified BEFORE decryption attempt

---

## Performance

| Metric | Result |
|--------|--------|
| Text message send | ~50ms |
| 5MB image transfer | ~1.5s |
| 50MB video transfer | ~12s |
| Memory usage | ~85MB |
| Chat scroll | 60 FPS |
| Initial chat load | ~0.4s |

Key optimizations: buffered I/O, WebP compression, connection pooling, LRU cache for attachments, stable `LazyColumn` keys, `derivedStateOf` for recomposition reduction.

---

## Roadmap

### Completed
- [x] ECDH + AES-256-GCM encryption
- [x] TOFU peer authentication
- [x] Replay protection
- [x] 1-on-1 encrypted messaging
- [x] File attachments (images, video, documents)
- [x] Message replies, reactions, forward, delete
- [x] Material 3 Expressive UI
- [x] Room offline database with pagination
- [x] mDNS/NSD peer discovery
- [x] TCP transport with connection pooling
- [x] English + Arabic localization

### In Progress
- [ ] Unit tests for crypto modules
- [ ] UI tests for chat flows
- [ ] Bluetooth transport
- [ ] Wi-Fi Direct support
- [ ] Typing indicators, pull-to-refresh, search

### Future
- [ ] Group chats (2+ peers)
- [ ] Mesh routing (multi-hop)
- [ ] Post-quantum cryptography (ML-KEM / ML-DSA)
- [ ] Desktop clients

---

## Contributing

Contributions are welcome. Please follow these guidelines:

1. **Fork** the repository
2. **Branch** off `main` (`git checkout -b feature/your-feature`)
3. **Commit** with clear, descriptive messages
4. **Push** and open a Pull Request

### Code Standards

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Public APIs must have KDoc comments
- New features should include tests
- No hardcoded strings — use `strings.xml`
- No `runBlocking` on main thread
- No `!!` operators — use safe calls or error handling

---

## License

MIT License. See [LICENSE](LICENSE) for details.

---

## Links

- [GitHub Repository](https://github.com/Yussefgafer/Meshify)
- [Issues](https://github.com/Yussefgafer/Meshify/issues)
