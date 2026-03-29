# Meshify TODO — Comprehensive Task Tracker

> **Last Updated:** 2026-03-29  
> **Project Health:** 4.2/10 (FAIL)  
> **Total Issues:** 85+ (20 Code + 18 Slop + 47 UX)  
> **Total Features:** 22 proposed  

---

## 📋 How to Use This File

This file serves as the **single source of truth** for all pending work on Meshify.

### For You (Jo):
1. Open this file to see what needs to be done
2. Pick 1-2 tasks from the top of the list
3. In a new conversation, say: "Fix [TASK_ID] and [TASK_ID]"
4. I will implement them and update this file with what was done

### For Me (Qwen):
1. At the start of each session, read this file
2. Know exactly what's pending, in-progress, and completed
3. After completing tasks, update this file immediately
4. Move completed tasks to the "Done" section with details

---

## 🎯 Priority System

| Priority | Meaning | Examples |
|----------|---------|----------|
| **P0** | Critical — Must fix before production | Security vulnerabilities, data loss bugs |
| **P1** | High — Fix within 1-2 weeks | Missing error states, race conditions |
| **P2** | Medium — Fix within 1 month | UX polish, hardcoded values |
| **P3** | Low — Nice to have | Animations, tooltips, visual refinements |

---

## 🔥 P0 — CRITICAL (Do These First)

### Security Issues

#### [SEC-01] No Message Encryption in Transit
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL (CVSS 9.8)
- **Files:** `MessageRepository.kt:97-117`, `ChatRepositoryImpl.kt:450-480`
- **Problem:** Messages sent as plaintext via Payload without encryption. `MessageEnvelopeCrypto` exists but is never used.
- **Impact:** Any device on same network can intercept and read all messages
- **Fix Required:**
  1. Wire `MessageEnvelopeCrypto` into `MessageRepository.send*()` methods
  2. Encrypt payload before sending: `val envelope = messageCrypto.encrypt(plaintext, peerId)`
  3. Add new PayloadType: `ENCRYPTED_MESSAGE`
  4. Decrypt on receive in `ChatRepositoryImpl.processPayload()`
- **Estimated:** 8-10 hours
- **Dependencies:** None
- **Test Criteria:**
  - Wireshark capture shows encrypted data only
  - Messages still deliver correctly
  - Performance impact <10%

#### [SEC-02] No Peer Authentication / TOFU Store Not Integrated
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL (CVSS 9.1)
- **Files:** `AppContainer.kt:54-57`, `PeerTrustStore.kt:1-108`, `LanTransportImpl.kt:handshake()`
- **Problem:** `PeerTrustStore` exists but never used. Any device can impersonate another peer.
- **Impact:** Man-in-the-middle attacks possible
- **Fix Required:**
  1. In `LanTransportImpl.handleHandshake()`: verify peer public key
  2. First contact: store public key in TOFU store
  3. Subsequent contacts: verify key matches stored key
  4. Mismatch: reject connection + alert user
- **Estimated:** 6-8 hours
- **Dependencies:** SEC-01 (can be done in parallel)
- **Test Criteria:**
  - MITM attack detected and blocked
  - First-time peer connection works smoothly
  - Key change triggers warning

#### [SEC-03] Sensitive Data Logged
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `Logger.kt:1-66`, all files using `Logger.i()` and `Logger.d()`
- **Problem:** Logger outputs message content, peer IDs, payload details to Logcat
- **Impact:** Any app with log access can read messages
- **Fix Required:**
  ```kotlin
  // In Logger.kt:
  private val isDebug = BuildConfig.DEBUG
  
  fun d(message: String, tag: String = DEFAULT_TAG) {
      if (isDebug) Log.d(tag, message)
  }
  
  fun i(message: String, tag: String = DEFAULT_TAG) {
      if (isDebug) Log.i(tag, message)
  }
  
  // Always log errors (needed for debugging)
  fun e(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
      Log.e(tag, message, throwable)
  }
  ```
- **Estimated:** 1-2 hours
- **Dependencies:** None
- **Test Criteria:**
  - Release build shows no debug logs
  - Debug build shows all logs
  - Error logs still appear in release

#### [SEC-04] Nonce Cache Not Enforced
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH (CVSS 8.6)
- **Files:** `AppContainer.kt:58-61`, `ChatRepositoryImpl.kt:400-480`, `InMemoryNonceCache.kt`
- **Problem:** `InMemoryNonceCache` exists but not used in message flow. Replay attacks possible.
- **Impact:** Attacker can capture and replay messages infinitely
- **Fix Required:**
  ```kotlin
  // In ChatRepositoryImpl.processPayload():
  private suspend fun processPayload(peerId: String, payload: Payload) {
      // Check for replay attacks FIRST
      if (!nonceCache.addIfAbsent(payload.id.toByteArray())) {
          Logger.w("ChatRepository -> Duplicate payload: ${payload.id}")
          return // Reject replay
      }
      // ... rest of processing
  }
  ```
- **Estimated:** 2-3 hours
- **Dependencies:** SEC-01 (encryption integration)
- **Test Criteria:**
  - Replay attack detected and blocked
  - Valid messages still processed
  - Cache doesn't grow unbounded (30s window)

#### [SEC-05] DataStore Stores Device ID Unencrypted
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH (CVSS 8.2)
- **Files:** `SettingsRepository.kt:1-322`
- **Problem:** Device ID and settings stored in DataStore without encryption
- **Impact:** Root access exposes peer identity
- **Fix Required:**
  1. Add dependency: `implementation("androidx.security:security-crypto:1.1.0-alpha06")`
  2. Replace DataStore with EncryptedSharedPreferences
  3. Migrate existing data on first launch
- **Estimated:** 4-6 hours
- **Dependencies:** None
- **Test Criteria:**
  - Device ID encrypted in storage
  - Migration from old data works
  - Performance impact negligible

---

### Testing Issues

#### [TEST-01] Zero Unit Tests
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL
- **Files:** `app/src/test/` (only `ExampleUnitTest.kt` with `assertEquals(4, 2+2)`)
- **Problem:** No tests for repositories, ViewModels, or use cases
- **Impact:** No way to verify changes don't break functionality
- **Fix Required:** Create comprehensive test suite:
  - `MessageRepositoryTest` (send, queue, retry)
  - `ChatRepositoryImplTest` (payload handling, forward, delete)
  - `ChatManagementRepositoryTest` (CRUD operations)
  - `PendingMessageRepositoryTest` (exponential backoff)
  - `MessageAttachmentRepositoryTest` (attachment CRUD)
  - `ReactionRepositoryTest` (reaction sync)
  - `WelcomeViewModelTest` (onboarding state)
  - `ChatViewModelTest` (message list, send, delete)
  - `SettingsRepositoryTest` (theme, name, avatar)
- **Estimated:** 40-60 hours
- **Dependencies:** None (can start immediately)
- **Test Criteria:**
  - 80%+ code coverage
  - All tests pass on CI
  - Tests catch intentional bugs

#### [TEST-02] Zero UI Tests
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL
- **Files:** `app/src/androidTest/` (only `ExampleInstrumentedTest.kt`)
- **Problem:** No Compose UI tests for any screen
- **Impact:** Manual testing required for every change
- **Fix Required:** Create UI test suite:
  - `ChatScreenTest` (send message, delete, react, forward)
  - `RecentChatsScreenTest` (navigate, delete chat)
  - `DiscoveryScreenTest` (discover, connect)
  - `SettingsScreenTest` (change theme, name)
  - `WelcomeScreenTest` (swipe, complete onboarding)
  - `ForwardMessageDialogTest` (select, forward)
- **Estimated:** 20-30 hours
- **Dependencies:** None
- **Test Criteria:**
  - All critical paths covered
  - Tests run on CI
  - Tests catch UI regressions

---

### Logic Bugs

#### [LOGIC-01] Race Condition in forwardMessage()
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `ChatRepositoryImpl.kt:225-310`
- **Problem:** `scope.launch` in `forEach` without waiting. `delay(500)` unreliable. Returns before completion.
- **Impact:** Forward may report success when some failed
- **Fix Required:**
  ```kotlin
  override suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit> {
      val message = messageDao.getMessageById(messageId)
          ?: return Result.failure(Exception("Message not found"))
      
      return try {
          val results = targetPeerIds.map { peerId ->
              async { forwardToPeer(message, peerId) }
          }.awaitAll()
          
          val allSuccess = results.all { it }
          if (allSuccess) Result.success(Unit)
          else Result.failure(Exception("Some forwards failed"))
      } catch (e: Exception) {
          Logger.e("ChatRepository -> Failed to forward: $messageId", e)
          Result.failure(e)
      }
  }
  ```
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - All forwards complete before return
  - Partial failures reported correctly
  - No 500ms delay needed

---

## 🔴 P1 — HIGH PRIORITY

### Code Quality

#### [CODE-01] Empty Catch Blocks in SocketManager
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `SocketManager.kt:278-279`
- **Problem:** Empty catch blocks silently swallow exceptions during cleanup
- **Impact:** Resource leaks undetected
- **Fix Required:**
  ```kotlin
  try { inputStream.close() } catch (e: Exception) {
      Logger.w("SocketManager -> Failed to close input stream for $address", e)
  }
  try { client.close() } catch (e: Exception) {
      Logger.w("SocketManager -> Failed to close socket for $address", e)
  }
  ```
- **Estimated:** 30 minutes
- **Dependencies:** None
- **Test Criteria:**
  - Exceptions logged
  - No silent failures

#### [CODE-02] Empty Catch Block in PremiumHaptics
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `PremiumHaptics.kt:55`
- **Problem:** Fallback haptic failure silently swallowed
- **Impact:** Users lose tactile confirmation
- **Fix Required:**
  ```kotlin
  } catch (e2: Exception) {
      Log.w("PremiumHaptics", "Fallback haptic also failed", e2)
  }
  ```
- **Estimated:** 15 minutes
- **Dependencies:** None
- **Test Criteria:**
  - Failures logged
  - No crashes

#### [CODE-03] Blocking .first() Without Error Handling
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `SettingsRepository.kt:272`
- **Problem:** `context.dataStore.data.first()` blocks without error handling
- **Impact:** App freeze if DataStore corrupted
- **Fix Required:**
  ```kotlin
  val prefs = try {
      context.dataStore.data.first()
  } catch (e: Exception) {
      Logger.e("SettingsRepository -> Failed to read preferences", e)
      return Result.failure(e)
  }
  ```
- **Estimated:** 1 hour
- **Dependencies:** None
- **Test Criteria:**
  - Corrupted DataStore handled gracefully
  - Error returned, not thrown

#### [CODE-04] TODOs in Production Code (8 instances)
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** Multiple (grep: `TODO|FIXME`)
- **Problem:** TODOs indicate incomplete work
- **Impact:** Technical debt accumulation
- **Fix Required:** Either implement or remove each TODO
- **Estimated:** 4-6 hours
- **Dependencies:** None
- **Test Criteria:**
  - Zero TODOs in production code

#### [CODE-05] God Function (SocketManager)
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `SocketManager.kt` (716 lines)
- **Problem:** Single class handles pooling, keep-alive, cleanup, send, receive, pre-warming
- **Impact:** Hard to maintain, test, extend
- **Fix Required:** Extract responsibilities:
  - `ConnectionPool` (manages pooled sockets)
  - `KeepAliveManager` (handles ping/pong)
  - `ConnectionCleaner` (removes idle sockets)
  - `SocketManager` (orchestrates above)
- **Estimated:** 6-8 hours
- **Dependencies:** None
- **Test Criteria:**
  - Each class <200 lines
  - All tests pass after refactor

---

### UX Issues

#### [UX-01] No "Scroll to Bottom" FAB in ChatScreen
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL
- **Files:** `ChatScreen.kt`
- **Problem:** When scrolled up, new messages appear off-screen with no way to jump back
- **Impact:** Users miss new messages
- **Fix Required:**
  - Add FloatingActionButton when not at bottom
  - Tap scrolls to latest message
  - Hide FAB when at bottom
- **Estimated:** 3-4 hours
- **Dependencies:** None
- **Test Criteria:**
  - FAB appears when scrolled up
  - Tap scrolls smoothly to bottom
  - FAB hides when at bottom

#### [UX-02] No Character Limit or Warning
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL
- **Files:** `ChatViewModel.kt`, `MediaStagingChatInput.kt`
- **Problem:** No limit on message length
- **Impact:** Can crash app with huge messages
- **Fix Required:**
  - Soft limit: 1000 chars (show warning)
  - Hard limit: 4096 chars (disable send)
  - Counter in input field
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - Warning at 1000 chars
  - Send disabled at 4096
  - No crashes with max length

#### [UX-03] No Typing Indicator
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `ChatViewModel.kt`, `ChatScreen.kt`, `Payload.kt`
- **Problem:** No visual feedback when peer is typing
- **Impact:** Users don't know if peer is responding
- **Fix Required:**
  - Add `TYPING` payload type
  - Send typing event when user starts typing
  - Show "[Peer] is typing..." in ChatScreen
  - Clear after 3s of inactivity
- **Estimated:** 4-6 hours
- **Dependencies:** None
- **Test Criteria:**
  - Indicator appears when peer types
  - Disappears after typing stops
  - No spam (debounce)

#### [UX-04] No Loading State in Discovery Screen
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL
- **Files:** `DiscoveryScreen.kt`
- **Problem:** Empty state shown with no indication of scanning
- **Impact:** Users don't know if app is searching or broken
- **Fix Required:**
  - Show CircularProgressIndicator during scan
  - Text: "Scanning for devices..."
  - Timeout after 30s with retry option
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - Loading state visible immediately
  - Times out gracefully
  - Retry works

#### [UX-05] No Pull to Refresh in Discovery
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL
- **Files:** `DiscoveryScreen.kt`
- **Problem:** No refresh gesture to rescan for peers
- **Impact:** Cannot manually refresh peer list
- **Fix Required:**
  - Add `pullToRefresh {}` modifier
  - On refresh: restart discovery
  - Show loading indicator during refresh
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - Pull gesture works
  - Triggers rescan
  - Loading state shown

#### [UX-06] No Confirmation Before Forwarding
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `ForwardMessageDialog.kt`
- **Problem:** Forward button sends immediately without confirmation
- **Impact:** Accidental mass-forwards
- **Fix Required:**
  - Show confirmation dialog: "Forward to 5 peers?"
  - Show peer names in confirmation
  - Cancel/Confirm buttons
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - Confirmation shown before send
  - User can cancel
  - Forward proceeds on confirm

#### [UX-07] No Search in Home Screen
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `RecentChatsScreen.kt`
- **Problem:** No search bar to find specific conversations
- **Impact:** Users with 50+ chats cannot find conversations
- **Fix Required:**
  - Add search bar in TopAppBar (expandable)
  - Filter chats by peer name
  - Clear button
- **Estimated:** 4-6 hours
- **Dependencies:** None
- **Test Criteria:**
  - Search filters in real-time
  - Empty state for no matches
  - Clear resets filter

#### [UX-08] No Dynamic Font Size Support
- **Status:** ❌ NOT STARTED
- **Severity:** CRITICAL (Accessibility)
- **Files:** All Compose files using fixed `sp` values
- **Problem:** Fixed font sizes don't respect system font scale
- **Impact:** Visually impaired users cannot enlarge text
- **Fix Required:**
  - Use `LocalDensity.current.fontScale` multiplier
  - Or use `MaterialTheme.typography` exclusively
- **Estimated:** 6-8 hours
- **Dependencies:** None
- **Test Criteria:**
  - Font scale 1.5x: text enlarges
  - Font scale 2.0x: layout doesn't break
  - All screens tested

---

### Performance

#### [PERF-01] MAX_MESSAGES_IN_MEMORY Too High
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ChatViewModel.kt:52`
- **Problem:** `MAX_MESSAGES_IN_MEMORY = 500` consumes 5-8MB
- **Impact:** Memory pressure on low-RAM devices
- **Fix Required:**
  ```kotlin
  companion object {
      private const val MAX_MESSAGES_IN_MEMORY = 200 // Reduced from 500
  }
  ```
- **Estimated:** 30 minutes
- **Dependencies:** None
- **Test Criteria:**
  - Memory usage reduced
  - Pagination still works smoothly

---

## 🟡 P2 — MEDIUM PRIORITY

### Design System

#### [DS-01] Hardcoded Dimensions (84 instances)
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ForwardMessageDialog.kt:137`, `MediaStagingChatInput.kt`, `ChatScreen.kt`
- **Problem:** 84 hardcoded `.dp` values instead of `MeshifyDesignSystem.Spacing`
- **Impact:** Inconsistent UI
- **Fix Required:** Replace all hardcoded dimensions with Design System tokens
- **Estimated:** 6-8 hours
- **Dependencies:** None
- **Test Criteria:**
  - Zero hardcoded `.dp` values
  - UI looks identical

#### [DS-02] Hardcoded Font Sizes (32 remaining)
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ChatScreen.kt:656`, etc.
- **Problem:** `fontSize = 10.sp` hardcoded instead of Typography
- **Impact:** Breaks dynamic font scaling
- **Fix Required:** Use `MaterialTheme.typography.*` exclusively
- **Estimated:** 4-6 hours
- **Dependencies:** UX-08 (do together)
- **Test Criteria:**
  - Zero hardcoded `sp` values
  - Font scaling works

#### [DS-03] Inconsistent Icon Sizes (39 instances)
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** Multiple files
- **Problem:** Icons use 18dp, 20dp, 22dp, 24dp interchangeably
- **Impact:** Visual inconsistency
- **Fix Required:**
  ```kotlin
  object MeshifyDesignSystem {
      object IconSizes {
          val Small = 18.dp
          val Medium = 24.dp
          val Large = 32.dp
          val XL = 40.dp
      }
  }
  ```
- **Estimated:** 3-4 hours
- **Dependencies:** None
- **Test Criteria:**
  - All icons use standard sizes
  - Visual consistency

#### [DS-04] Dialog Corner Radius Inconsistent
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** Multiple dialog files
- **Problem:** 16dp, 20dp, 28dp used interchangeably
- **Impact:** Visual inconsistency
- **Fix Required:**
  ```kotlin
  object MeshifyDesignSystem {
      object DialogShapes {
          val Default = RoundedCornerShape(28.dp)
          val Small = RoundedCornerShape(16.dp)
      }
  }
  ```
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - All dialogs use consistent radius

---

### UX Polish

#### [UX-09] Missing Error States in Forward Dialog
- **Status:** ❌ NOT STARTED
- **Severity:** HIGH
- **Files:** `ForwardMessageDialog.kt`
- **Problem:** No error UI when forwarding fails
- **Impact:** User doesn't know if message sent
- **Fix Required:**
  - Show error message on failure
  - Retry button
  - Partial failure reporting
- **Estimated:** 3-4 hours
- **Dependencies:** None
- **Test Criteria:**
  - Error shown on failure
  - Retry works
  - Partial failures reported

#### [UX-10] Missing Loading States in File Transfer
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ChatScreen.kt`, `ChatViewModel.kt`
- **Problem:** No progress indicator for large files (>5MB)
- **Impact:** User doesn't know if transfer stuck
- **Fix Required:**
  - Add progress StateFlow
  - Show LinearProgressIndicator
  - Update progress during transfer
- **Estimated:** 4-6 hours
- **Dependencies:** None
- **Test Criteria:**
  - Progress shown during transfer
  - Percentage accurate
  - Completion indicated

#### [UX-11] No Delete Animation
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** `ChatScreen.kt`
- **Problem:** Messages deleted instantly without animation
- **Impact:** Jarring UX
- **Fix Required:**
  ```kotlin
  AnimatedVisibility(
      visible = message.id !in deletedMessageIds,
      exit = fadeOut() + shrinkVertically()
  ) {
      MessageBubble(message, ...)
  }
  ```
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - Smooth exit animation
  - No layout jumps

#### [UX-12] Missing Tooltips for Buttons
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** `ChatScreen.kt:700-798`, all IconButtons
- **Problem:** IconButtons lack tooltips
- **Impact:** New users don't understand buttons
- **Fix Required:**
  ```kotlin
  TooltipBox(
      positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
      tooltip = { Text("Forward message") }
  ) {
      IconButton(onClick = onForwardClick) {
          Icon(Icons.Default.Forward, contentDescription = "Forward")
      }
  }
  ```
- **Estimated:** 3-4 hours
- **Dependencies:** None
- **Test Criteria:**
  - All IconButtons have tooltips
  - Tooltips appear on long press

#### [UX-13] No "Select All" in Multi-Select
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ChatScreen.kt`
- **Problem:** Must tap each message individually
- **Impact:** Tedious to select many messages
- **Fix Required:**
  - Add "Select All" button in SelectionModeTopBar
  - Select all visible messages
- **Estimated:** 2-3 hours
- **Dependencies:** None
- **Test Criteria:**
  - Select All works
  - Deselect All works

#### [UX-14] No Undo After Delete
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ChatScreen.kt`
- **Problem:** Delete is permanent immediately
- **Impact:** Accidental deletes cannot be recovered
- **Fix Required:**
  - Snackbar with "Undo" for 5 seconds
  - Queue actual deletion after timeout
- **Estimated:** 4-6 hours
- **Dependencies:** None
- **Test Criteria:**
  - Undo restores message
  - Auto-delete after 5s

---

## 🟢 P3 — LOW PRIORITY

### Features

#### [FEAT-01] Signal Strength Indicator
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** `DiscoveryScreen.kt`, `LanTransportImpl.kt`
- **Problem:** Users don't know signal quality
- **Fix Required:** Show RSSI as bars or color (green/yellow/red)
- **Estimated:** 4 hours
- **Dependencies:** None
- **Test Criteria:** Signal strength visible

#### [FEAT-02] Last Seen Timestamp
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** `DiscoveryScreen.kt`
- **Problem:** Users don't know if peer still around
- **Fix Required:** Show "Last seen: X min ago"
- **Estimated:** 3 hours
- **Dependencies:** None
- **Test Criteria:** Timestamp updates

#### [FEAT-03] Message Delivery Confirmation (Visual)
- **Status:** ❌ NOT STARTED
- **Severity:** MEDIUM
- **Files:** `ChatScreen.kt`
- **Problem:** Status icons not shown
- **Fix Required:** Show ⏳📤✓✓✓ in message bubbles
- **Estimated:** 5 hours
- **Dependencies:** None
- **Test Criteria:** Status visible

#### [FEAT-04] Quick Settings Toggle
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** New `TileService` class
- **Problem:** Must open app to enable discovery
- **Fix Required:** Android Quick Settings Tile
- **Estimated:** 4 hours
- **Dependencies:** None
- **Test Criteria:** Tile toggles discovery

#### [FEAT-05] Haptic "Ping" on New Peer
- **Status:** ❌ NOT STARTED
- **Severity:** LOW
- **Files:** `DiscoveryService.kt`
- **Problem:** Users don't notice new peers
- **Fix Required:** Haptic feedback on discovery
- **Estimated:** 2 hours
- **Dependencies:** None
- **Test Criteria:** Subtle tick on new peer

---

### Emergency Features (High Impact)

#### [FEAT-06] Emergency Beacon / SOS Broadcast
- **Status:** ❌ NOT STARTED
- **Severity:** P0 (Life-saving)
- **Files:** New `EmergencyBeaconService.kt`, `ChatRepositoryImpl.kt`
- **Problem:** No way to broadcast emergency alert
- **Fix Required:**
  - Long-press SOS button
  - Broadcast beacon with GPS coordinates
  - Recipients get loud alert
  - Re-broadcast through mesh
- **Estimated:** 12 hours
- **Dependencies:** None
- **Test Criteria:** SOS reaches all peers within 3 hops

#### [FEAT-07] Broadcast Channels
- **Status:** ❌ NOT STARTED
- **Severity:** P1
- **Files:** New `ChannelRepository.kt`, navigation changes
- **Problem:** Only 1:1 chats, no mass communication
- **Fix Required:**
  - Create public channels by name
  - Messages broadcast to all subscribers
  - Mesh propagation (TTL, hop limit)
- **Estimated:** 20 hours
- **Dependencies:** None
- **Test Criteria:** Message reaches 500+ users

#### [FEAT-08] Offline Location Sharing
- **Status:** ❌ NOT STARTED
- **Severity:** P1
- **Files:** New `LocationService.kt`
- **Problem:** Can't find friends in crowd
- **Fix Required:**
  - Share GPS coordinates over mesh
  - Map or compass view
  - One-time or continuous
- **Estimated:** 15 hours
- **Dependencies:** None
- **Test Criteria:** Location visible on map

#### [FEAT-09] Voice Messages (Push-to-Talk)
- **Status:** ❌ NOT STARTED
- **Severity:** P1
- **Files:** `ChatScreen.kt`, new `VoiceMessageRepository.kt`
- **Problem:** Typing is slow
- **Fix Required:**
  - Hold button to record
  - Compress (Opus 16kbps)
  - Send as VOICE_MESSAGE payload
- **Estimated:** 18 hours
- **Dependencies:** None
- **Test Criteria:** 10s message sends in <2s

#### [FEAT-10] File Sharing (Any File Type)
- **Status:** ❌ NOT STARTED
- **Severity:** P1
- **Files:** `ChatScreen.kt`, extend existing file transfer
- **Problem:** Only images/videos supported
- **Fix Required:**
  - Generic file picker
  - Support any MIME type
  - Recipient accept/reject
- **Estimated:** 16 hours
- **Dependencies:** None
- **Test Criteria:** 10MB PDF sends in ~20s

---

## 📝 COMPLETED TASKS

### How to Document Completed Work

After completing a task, add an entry like this:

```markdown
#### [TASK-ID] Task Name
- **Status:** ✅ COMPLETED (YYYY-MM-DD)
- **What Was Done:**
  - File 1: Changed X to Y
  - File 2: Added Z
  - File 3: Refactored A to B
- **Code Changes:**
  ```kotlin
  // Before:
  val x = oldCode()
  
  // After:
  val x = newCode()
  ```
- **Testing:**
  - Test 1: Description
  - Test 2: Description
- **Impact:** What improved (metrics if available)
- **Follow-up:** Any remaining work or related tasks
```

---

### Example Entry

#### [CODE-01] Empty Catch Blocks in SocketManager
- **Status:** ✅ COMPLETED (2026-03-29)
- **What Was Done:**
  - `SocketManager.kt:278-279`: Added logging to empty catch blocks
  - Both inputStream.close() and client.close() now log failures
- **Code Changes:**
  ```kotlin
  // Before:
  try { inputStream.close() } catch (e: Exception) {}
  try { client.close() } catch (e: Exception) {}
  
  // After:
  try { inputStream.close() } catch (e: Exception) {
      Logger.w("SocketManager -> Failed to close input stream for $address", e)
  }
  try { client.close() } catch (e: Exception) {
      Logger.w("SocketManager -> Failed to close socket for $address", e)
  }
  ```
- **Testing:**
  - Manually tested socket cleanup
  - Verified exceptions logged in Logcat
- **Impact:** Resource leaks now visible in logs
- **Follow-up:** None

---

## 📊 Progress Tracking

### Summary Statistics

| Category | Total | Not Started | In Progress | Completed |
|----------|-------|-------------|-------------|-----------|
| **P0 — Critical** | 7 | 7 | 0 | 0 |
| **P1 — High** | 14 | 14 | 0 | 0 |
| **P2 — Medium** | 10 | 10 | 0 | 0 |
| **P3 — Low** | 14 | 14 | 0 | 0 |
| **TOTAL** | **45** | **45** | **0** | **0** |

### Burndown Chart

```
Week 1: [██████████░░░░░░░░░░] 0/45 tasks (0%)
Week 2: [████████████████████] 0/45 tasks (0%)
Week 3: [████████████████████] 0/45 tasks (0%)
Week 4: [████████████████████] 0/45 tasks (0%)
Week 5: [████████████████████] 0/45 tasks (0%)
```

### Velocity

- **Week 1 Target:** 5-7 tasks (P0 security + testing foundation)
- **Week 2 Target:** 7-10 tasks (P1 UX fixes + more tests)
- **Week 3 Target:** 8-10 tasks (P2 polish + features)
- **Week 4 Target:** 5-7 tasks (P3 + bug fixes)
- **Week 5 Target:** Remaining tasks + final testing

---

## 🎯 Session Workflow

### At Session Start:
1. Read this TODO.md file
2. Check "Not Started" section for next priority tasks
3. Ask Jo which tasks to work on, or suggest top priority
4. Move selected tasks to "In Progress"

### During Session:
1. Implement the tasks
2. Write tests for each task
3. Verify all test criteria met

### At Session End:
1. Move completed tasks to "Completed" section
2. Add detailed entry for each task (see example above)
3. Update progress statistics
4. Note any blockers or dependencies for next session

---

## 🚧 Current Session Template

### Session: [DATE]

**Tasks Worked On:**
- [TASK-ID]
- [TASK-ID]

**Status:**
- Task 1: ✅ COMPLETED / ⏸ IN PROGRESS / ❌ BLOCKED
- Task 2: ✅ COMPLETED / ⏸ IN PROGRESS / ❌ BLOCKED

**Summary:**
[Brief description of what was accomplished]

**Blockers:**
[Any issues preventing completion]

**Next Session:**
[Suggested tasks for next time]

---

## 📚 Additional Resources

### Related Files:
- `QWEN.md` — Project memory and architecture
- `README.md` — User-facing documentation
- `docs/` — Technical documentation

### Skills Used:
- `kotlin-compose-v2` — For Compose-related tasks
- `android-security` — For security implementations
- `architecture-guardian` — For architectural decisions
- `jetpack-compose` — For UI/UX patterns

### Testing Commands:
```bash
# Run unit tests
./gradlew test

# Run UI tests
./gradlew connectedAndroidTest

# Run specific test
./gradlew :app:testDebugUnitTest --tests "MessageRepositoryTest"

# Check code coverage
./gradlew koverHtmlReport
```

### Build Commands:
```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Install on device
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Clean build
./gradlew clean && ./gradlew assembleDebug
```

---

## 🎯 Quick Start for Next Session

**To get started immediately in a new session:**

1. **Say:** "Read TODO.md and tell me what's next"
2. **I will:** Check the highest priority "Not Started" tasks
3. **You choose:** Pick 1-2 tasks to work on
4. **I implement:** Complete the tasks with tests
5. **I update:** Document everything in this file

**Recommended first tasks:**
1. [SEC-03] Sensitive Data Logged (1-2h, easy win)
2. [CODE-01] Empty Catch Blocks (30min, easy win)
3. [PERF-01] MAX_MESSAGES Too High (30min, easy win)
4. [TEST-01] Write first unit test (4-6h, foundational)
5. [UX-01] Scroll to Bottom FAB (3-4h, high impact)

---

**Good luck! Let's make Meshify production-ready! 🚀**
