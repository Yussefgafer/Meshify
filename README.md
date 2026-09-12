Meshify

Offline-first P2P messaging for Android - no servers, no internet, no compromises.

￼ ￼ ￼ ￼ ￼ ￼ 

Decentralized • Offline-Ready • Lightweight • Open Source 

🤖 Full Slop Code

This codebase was written and implemented using LLM.

Built for offline-first, decentralized communication. 

Overview

Meshify is a decentralized peer-to-peer messaging application that enables real-time communication between Android devices on the same local network - without requiring internet connectivity or central servers.

Built with Clean Architecture, Jetpack Compose, and Material 3, Meshify delivers a modern, performant, and privacy -respecting messaging experience.

Core Philosophy

Zero Infrastructure: No servers, no cloud, no accounts. Just direct device-to-device communication.

Offline-First: Works entirely on local networks (WiFi / LAN). Internet is optional.

No privacy: No encryption overhead. Messages travel as plaintext over LAN for maximum simplicity and speed but No telemetry, no analytics, no data collection.

Key Features

Rich Messaging

1-on-1 messaging with threaded replies

File attachments - images, videos, documents

Message reactions, delete, and forward

Message status tracking - Queued, Sending, Sent, Delivered, Read, Failed

Offline storage with Room database and pagination

Peer Discovery & Transport

mDNS/NSD automatic peer discovery on local networks

BLE transport (Beta) (optional) - proximity-based messaging via Bluetooth Low Energy

Real-time presence - instant online/offline status indicators

TCP-based transport with connection pooling and keep-alive monitoring

UUID-based peer identification (no phone numbers, no accounts)

Modern UI/UX

Material 3 Expressive (trying) design system with dynamic colors

Light / Dark / System theme support + custom seed color picker

Full Arabic & English localization with RTL layout support

Usage

Connect two or more Android devices to the same WiFi network

Launch Meshify on each device

Grant required permissions on first launch

Peers appear automatically via mDNS discovery

Tap a peer to start messaging

Note: Meshify operates on local networks only. No internet connection required - At least for now.