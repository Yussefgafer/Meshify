<h1 align="center"><font color="#FF0000">IMPORTANT</font></h1>

<p align="center">
🤖 AI-NATIVE PROJECT
This entire codebase, architecture, and UI logic were architected and implemented by LLM (Gemini 3 Flash & Jules[Gemini 3 Flash]) under the strategic direction of Me(Yussef Gafer). No human code was harmed in the making of this mesh.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/AI--GENERATED-100%25-red?style=for-the-badge&logo=openai&logoColor=white" alt="AI Generated">
</p>

<br><br>

## Thes Project is Master Shit Literally
- Ther is no **feature** In that app 
- The app is **unusable**

<br><br>

[![Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3_Expressive-4285F4?logo=android)](https://developer.android.com/jetpack/compose)


---

## 🌐 Meshify

**Meshify** is a modular, high-performance P2P mesh networking application built for decentralized communication. No central servers, no internet dependency—just pure device-to-device connectivity.

---

## 🚀 Features

- **Decentralized P2P:** Communicate directly with nearby devices using LAN (and upcoming Bluetooth) transports.
- **Material 3 Expressive (MD3E):** A cutting-edge UI/UX featuring fluid motion, shape morphing (7-Shape Engine), and dynamic layouts.
- **Reliable Messaging:** Real-time message status (Sent, Failed), typing indicators, and smart message grouping.
- **Media Support:** Robust handling of image attachments with advanced caching via Coil.
- **Privacy First:** Local-first data storage using Room and DataStore, with a roadmap toward End-to-End Encryption (E2EE).

## 🛠 Tech Stack

- **Language:** 100% Kotlin
- **UI:** Jetpack Compose with Material 3 Expressive
- **Concurrency:** Kotlin Coroutines & Flows
- **Local Database:** Room Persistence Library
- **Architecture:** Clean Architecture (Domain-Driven Design)
- **Networking:** Custom Socket Management with pluggable transport interfaces (`IMeshTransport`)
- **Dependency Injection:** Manual DI via `AppContainer` for maximum transparency and performance.

## 🏗 Architecture Overview

Meshify follows a strict **Clean Architecture** pattern to ensure modularity and testability:

- **`core/`**: Configuration, logging, and foundational utilities.
- **`data/`**: Implementation of repositories, local database (Room), and network transports.
- **`domain/`**: Business logic, repository interfaces, and use cases (the heart of the app).
- **`network/`**: Socket management and transport abstractions.
- **`ui/`**: Compose components, themes, and feature-specific ViewModels.

## 🏁 Getting Started

### Prerequisites
- Android Studio Ladybug (or newer)
- JDK 17
- Android Device/Emulator (API 26+)

### Installation
1. Clone the repository:
   ```bash
   git clone https://github.com/your-username/Meshify.git
   ```
2. Open the project in Android Studio.
3. Build and Run:
   ```bash
   ./gradlew assembleDebug
   ```

## 📈 Roadmap
- [ ] **End-to-End Encryption (E2EE):** Implementing a robust encryption layer for all payloads.
- [ ] **Bluetooth Transport:** Adding Bluetooth LE/Classic support for multi-protocol mesh.
- [ ] **Voice/Video Messages:** Support for rich media transfers.
- [ ] **Unit & UI Testing:** Expanding test coverage for core components.

## 🤝 Contributing
Contributions are welcome! Please feel free to submit a Pull Request.

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---
*Developed with ❤️ by **Jo** and **Kai (Void)**.*
