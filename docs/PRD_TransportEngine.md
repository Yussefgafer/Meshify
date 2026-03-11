# 🌐 Meshify Standardized Transport Engine (MSTE) - PRD

**Status:** Planning / Draft  
**Version:** 1.1  
**Last Updated:** Tuesday, March 10, 2026  

---

## 1. Executive Summary
Meshify aims to become a resilient, multi-transport P2P engine. This document outlines the technical requirements for a standardized system that handles file transfers, encryption, and peer discovery across different physical layers (LAN, Bluetooth, WiFi Direct) seamlessly.

---

## 2. Core Architectural Principles
- **Agnostic Transport:** The application logic should not know which physical medium is being used.
- **Surgical Integrity:** Every file transfer must be verified via SHA-256 to prevent corruption in unstable P2P environments.
- **Graceful Degradation:** If LAN fails, the system should automatically attempt to failover to WiFi Direct or Bluetooth.
- **User Autonomy:** No large files should be downloaded without explicit user consent (Request-to-Send pattern).

---

## 3. The Transport Stack

### 3.1 IMeshTransport Interface (Updated)
The base interface for all transport implementations.
- `events: Flow<TransportEvent>`: Emits connection status, incoming payloads, and transport-specific errors.
- `onlinePeers: StateFlow<Set<String>>`: Real-time set of reachable device IDs.
- `typingPeers: StateFlow<Set<String>>`: Real-time set of peers currently typing.
- `transferProgress: SharedFlow<TransferProgress>`: Progress updates (bytes sent/received, speed).

### 3.2 CompositeTransport (The Router)
A wrapper that holds a prioritized list of transports:
1. **LAN (Local Area Network):** Highest bandwidth, lowest latency.
2. **WiFi Direct:** High bandwidth, requires group negotiation.
3. **Bluetooth (Classic/LE):** Low bandwidth, high availability, energy efficient.

---

## 4. Payload & Message Protocol

### 4.1 Enhanced Payload Structure
To support future Mesh/Relay capabilities:
```kotlin
data class Payload(
    val id: String,              // Unique packet ID
    val senderId: String,        // Original creator
    val destinationId: String,   // Ultimate recipient
    val ttl: Int = 3,            // Time To Live (hops)
    val routePath: List<String>, // Breadcrumbs of the path taken
    val type: PayloadType,       // Packet category
    val data: ByteArray          // Encrypted payload content
)
```

### 4.2 Payload Types
- `HANDSHAKE`: Identity exchange + ECDH Public Key.
- `TEXT`: Standard text messaging.
- `VOICE`: Audio messages (m4a/opus).
- `FILE_REQ`: Metadata of a file being offered (Name, Size, Hash, Blur Thumbnail).
- `FILE_ACC`: Acceptance of a file request.
- `FILE_META`: Detailed transfer info (Total chunks, session ID).
- `CHUNK_DATA`: A 16KB segment of a file.
- `MESSAGE_ACK`: Application-level confirmation of delivery (Sent/Received).

---

## 5. File Transfer Engine (PayloadOrchestrator)

### 5.1 Chunking Strategy
- Maximum chunk size fixed at **16KB** to ensure compatibility with Bluetooth MTU limitations.
- Chunks are sent sequentially over TCP (LAN/WD) or via managed streams (BT).

### 5.2 Integrity & Verification
- **Pre-send:** Calculate SHA-256 of the entire file.
- **Post-receive:** Re-calculate and compare. Reject file if hashes mismatch.

### 5.3 Resuming & Persistence
- Uses `file_transfers` Room table to track:
  - `sessionId`: Matches `FILE_META`.
  - `receivedChunksBitmap`: Tracks exactly which segments are on disk.
  - Allows resuming from the last successful chunk even after a reboot or transport change.

---

## 6. Security (Hop-to-Hop)
- **Encryption Middleware:** A decorator that wraps the `CompositeTransport`.
- **Algorithm:** AES-256-GCM for authenticated encryption.
- **Key Exchange:** ECDH (Elliptic-curve Diffie–Hellman) during the `HANDSHAKE` phase.

---

## 7. Application Polish & Features

### 7.1 Onboarding Flow
- **Welcome Screen:** High-quality intro to Meshify.
- **Profile Setup:** User MUST choose a Display Name (defaulting to "Meshify User" if skipped) and optionally an Avatar.
- **Permission Requests:** Requesting Notification, Location (for NSD), and Storage permissions upfront.

### 7.2 Voice Messaging System
- **Recorder:** Long-press to record, swipe to cancel (inspired by ChatApp-Compose).
- **Format:** High-efficiency Opus or AAC.
- **Playback:** In-chat wave-form or simple progress bar player.

### 7.3 Global Search
- **Chat Search:** Search bar in `RecentChatsScreen` to filter conversations.
- **Message Search:** Search inside a specific `ChatScreen` for keywords.

### 7.4 Media & Gallery
- **Media Viewer:** High-performance image/video viewer with swipe-to-dismiss.
- **Persistence:** Saved media gallery within the app.

---

## 8. User Experience (UX)

### 8.1 Request-to-Send (RTS) Flow
1. Sender sends `FILE_REQ`.
2. Receiver sees a "Media Placeholder" with a blurred thumbnail and "Download (X MB)" button.
3. Receiver clicks "Download" -> Sends `FILE_ACC`.
4. Transfer begins.

### 8.2 Automation Rules
Users can configure auto-download behavior in Settings:
- Auto-download only on LAN.
- Auto-download only for files < 5MB.
- Specific rules for Images vs. Videos.

---

## 9. Development Roadmap
1. **Phase 1:** Payload Model & Serializer Update.
2. **Phase 2:** PayloadOrchestrator & Room Persistence (Resuming Logic).
3. **Phase 3:** Enhanced UI (Onboarding & Global Search).
4. **Phase 4:** Voice Messaging Implementation.
5. **Phase 5:** Bluetooth & WiFi Direct Transports.
6. **Phase 6:** Mesh Relaying & E2EE Exploration.
