# Meshify

> **Offline-first P2P messaging for Android — no servers, no internet, no compromises.**

<p align="center">
  <img src="https://img.shields.io/badge/Kotlin-2.4.0-7F52FF.svg?logo=kotlin&logoColor=white" alt="Kotlin">
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

## 🤖 Slop Code

> This codebase was designed and implemented using **LLM (Qwen Code)**.

<p align="center">
  <img src="https://img.shields.io/badge/AI--ASSISTED-Qwen-7F52FF.svg?logo=openai&logoColor=white" alt="AI Assisted">
</p>

---

<p align="center">
  <strong>Built with ♥ for offline-first, decentralized communication.</strong>
</p>

## 📖 Overview

Meshify is a **decentralized peer-to-peer messaging application** that enables real-time communication between Android devices on the same local network — **without requiring internet connectivity or central servers**.

Built with **Clean Architecture**, **Jetpack Compose**, and **Material 3**, Meshify delivers a modern, performant, and privacy-respecting messaging experience.

### ✨ Core Philosophy

- **Zero Infrastructure**: No servers, no cloud, no accounts. Just direct device-to-device communication.
- **Offline-First**: Works entirely on local networks (WiFi / LAN). Internet is optional.
- **No privacy**: No encryption overhead. Messages travel as plaintext over LAN for maximum simplicity and speed.
- **Privacy Respecting**: No telemetry, no analytics, no data collection. Your conversations stay on your device.

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
- **BLE transport (Beta)** (optional) — proximity-based messaging via Bluetooth Low Energy
- **Real-time presence** — instant online/offline status indicators
- **TCP-based transport** with connection pooling and keep-alive monitoring
- **UUID-based peer identification** (no phone numbers, no accounts)

### 🎨 Modern UI/UX
- **Material 3 Expressive (trying)** design system with dynamic colors
- **Light / Dark / System** theme support + custom seed color picker
- **Full Arabic & English localization** with RTL layout support

## 📖 Documentation

Full, source-accurate documentation lives in [`docs/`](docs/README.md) — covering the architecture, every `core:*` module, each `feature:*` module, and the `:app` aggregator.

---

### Usage

1. Connect two or more Android devices to the **same WiFi network**
2. Launch Meshify on each device
3. Grant required permissions on first launch (onboarding flow)
4. Peers appear automatically via mDNS discovery
5. Tap a peer to start messaging

> **Note:** Meshify operates on **local networks only**. No internet connection required.
