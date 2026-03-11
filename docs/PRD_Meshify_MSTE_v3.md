# 📡 Meshify — وثيقة متطلبات المنتج (PRD) الشاملة
## Meshify Standardized Transport Engine (MSTE) — الإصدار 3.0

**الحالة:** جاهز للتنفيذ  
**الإصدار:** 3.0 — Full Deep-Dive Edition  
**تاريخ آخر تحديث:** 10 مارس 2026  
**المشروع:** `com.p2p.meshify`  
**المستودع:** `github.com/Yussefgafer/Meshify`  
**الحد الأدنى لـ API:** 26 (Android 8.0)  
**Target SDK:** 35  

---

## جدول المحتويات

1. [الرؤية والهدف](#1-الرؤية-والهدف)
2. [الحالة الراهنة للـ Codebase](#2-الحالة-الراهنة-للـ-codebase)
3. [المبادئ المعمارية الحاكمة](#3-المبادئ-المعمارية-الحاكمة)
4. [طبقة النقل — Transport Stack](#4-طبقة-النقل--transport-stack)
5. [نموذج الـ Payload والـ Serialization](#5-نموذج-الـ-payload-والـ-serialization)
6. [محرك نقل الملفات — PayloadOrchestrator](#6-محرك-نقل-الملفات--payloadorchestrator)
7. [طبقة الأمان والتشفير](#7-طبقة-الأمان-والتشفير)
8. [نظام اكتشاف الأجهزة والـ Failover](#8-نظام-اكتشاف-الأجهزة-والـ-failover)
9. [طبقة البيانات — Room Database](#9-طبقة-البيانات--room-database)
10. [طبقة الـ Domain — Use Cases & Repositories](#10-طبقة-الـ-domain--use-cases--repositories)
11. [ميزات التطبيق المطلوبة](#11-ميزات-التطبيق-المطلوبة)
12. [واجهة المستخدم — UI/UX + MD3E](#12-واجهة-المستخدم--uiux--md3e)
13. [الإعدادات والتخصيص](#13-الإعدادات-والتخصيص)
14. [الأذونات — Permissions](#14-الأذونات--permissions)
15. [خارطة الطريق والمراحل](#15-خارطة-الطريق-والمراحل)
16. [قائمة مهام الذكاء الاصطناعي — AI ToDo Checklist](#16-قائمة-مهام-الذكاء-الاصطناعي--ai-todo-checklist)

---

## 1. الرؤية والهدف

### 1.1 الرسالة الأساسية

Meshify هو تطبيق Android مفتوح المصدر لاتصالات P2P لامركزية كاملة (Decentralized Peer-to-Peer). الهدف تحويل كل موبايل من "مستقبل للإنترنت" إلى "عقدة (Node) مستقلة في شبكة محلية". التطبيق يعمل دون أي خادم مركزي، دون إنترنت، دون خدمات سحابية — فقط اتصال مباشر device-to-device بين الأجهزة القريبة.

### 1.2 ما يميّز Meshify

- **لامركزية حقيقية:** لا يوجد سيرفر إطلاقاً. كل جهاز هو node مستقل.
- **طبقة نقل شفافة (Transparent Transport Layer):** التطبيق الفوقاني لا يعرف ولا يهتم بأي وسيط اتصال يُستخدم — LAN أو WiFi Direct أو Bluetooth — بل يهتم فقط بوصول البيانات بأمان وسرعة.
- **تدهور رشيق (Graceful Degradation):** إذا فشل LAN → يتحول تلقائياً لـ WiFi Direct → ثم Bluetooth.
- **خصوصية أولاً:** تشفير AES-256-GCM، تبادل مفاتيح ECDH، تخزين محلي فقط بـ Room + DataStore.
- **تجربة مستخدم عالمية المستوى:** Material 3 Expressive بـ 7 أشكال ديناميكية، physics-based animations، haptic feedback متقدم.

### 1.3 المستخدم المستهدف

أي شخص يريد التواصل مع أشخاص قريبين منه فيزيائياً دون الاعتماد على الإنترنت: في أماكن العمل، الحفلات، المخيمات، الشبكات المحلية، المناطق التي لا يتوفر فيها إنترنت، أو أي بيئة تتطلب خصوصية كاملة.

---

## 2. الحالة الراهنة للـ Codebase

### 2.1 ما تم بناؤه بالفعل ✅

| المكوّن | الملف | الحالة |
|---------|-------|--------|
| `IMeshTransport` interface | `network/base/IMeshTransport.kt` | ✅ مكتمل — يحتاج إضافة `transferProgress` |
| `TransportEvent` sealed class | `network/base/TransportEvent.kt` | ✅ مكتمل |
| `LanTransportImpl` (NSD + TCP) | `network/lan/LanTransportImpl.kt` | ✅ يعمل — يحتاج تحسينات |
| `SocketManager` | `network/lan/SocketManager.kt` | ✅ موجود |
| `Payload` data class + `PayloadType` | `domain/model/Payload.kt` | ✅ موجود — يحتاج توسعة |
| `IChatRepository` interface | `domain/repository/IChatRepository.kt` | ✅ مكتمل |
| `ChatRepositoryImpl` | `data/repository/ChatRepositoryImpl.kt` | ✅ موجود — يحتاج إعادة هيكلة |
| `ISettingsRepository` + impl | `data/repository/SettingsRepository.kt` | ✅ مكتمل |
| `MeshifyDatabase` Room (schema v3) | `data/local/` | ✅ موجود — يحتاج migration v4 |
| `AppContainer` (Manual DI) | `AppContainer.kt` | ✅ موجود — يحتاج تحديث |
| MD3E Design System (7 Shapes) | `ui/theme/` | ✅ مكتمل |
| `SignalStrength` + morph logic | `domain/model/SignalStrength.kt` | ✅ مكتمل |
| MD3E Settings (Shape, Motion, Font, Bubble) | `ISettingsRepository` | ✅ مكتمل |
| `NotificationHelper` | `core/util/NotificationHelper.kt` | ✅ موجود |
| Message reactions, delete, reply, forward | `IChatRepository` + `MessageEntity` | ✅ موجود |
| `pending_messages` Queue | Room table | ✅ موجود |
| `MessageQueueService` | `network/service/MessageQueueService.kt` | ✅ موجود |
| `MeshForegroundService` | `network/service/MeshForegroundService.kt` | ✅ موجود |
| `PhysicsSwipeToDelete` | `ui/components/PhysicsSwipeToDelete.kt` | ✅ مكتمل |
| `MorphingAvatar` + `SignalMorphAvatar` | `ui/components/` | ✅ موجود |
| `ChatScreen` + `ChatViewModel` | `ui/screens/chat/` | ✅ موجود — يحتاج تطوير |
| `SettingsScreen` + `SettingsViewModel` | `ui/screens/settings/` | ✅ مكتمل |
| `RecentChatsScreen` | `ui/screens/home/` | ✅ موجود |
| `DiscoveryScreen` | `ui/screens/discovery/` | ✅ موجود |
| Navigation Graph | `ui/navigation/MeshifyNavDisplay.kt` | ✅ موجود |

### 2.2 ما لم يُبنَ بعد ❌

| المكوّن | الأولوية |
|---------|----------|
| `CompositeTransport` / `TransportOrchestrator` | 🔴 حرجة |
| `WifiDirectTransportImpl` | 🔴 حرجة |
| `BluetoothTransportImpl` | 🟠 عالية |
| `PayloadOrchestrator` (Chunked Transfer Engine) | 🔴 حرجة |
| Sliding Window Protocol | 🔴 حرجة |
| `file_transfers` Room table (Migration v4) | 🔴 حرجة |
| `peer_identities` Room table (Migration v4) | 🟠 عالية |
| توسعة `Payload` model (destinationId, ttl, routePath) | 🔴 حرجة |
| أنواع Payload الجديدة (FILE_REQ, FILE_ACC, FILE_META, CHUNK_DATA, TRANSFER_ACK, VOICE) | 🔴 حرجة |
| AES-256-GCM Encryption Layer | 🔴 حرجة |
| ECDH Key Exchange في الـ Handshake | 🔴 حرجة |
| `EncryptedTransportDecorator` | 🔴 حرجة |
| `PeerStore` (Multi-Identity Mapping) | 🟠 عالية |
| Heartbeat System (3s interval) | 🟠 عالية |
| Binary Serialization (`PayloadSerializer`) | 🟠 عالية |
| Request-to-Send (RTS) UI Card في الـ ChatScreen | 🔴 حرجة |
| File Transfer Progress UI (شريط التقدم في الـ bubble) | 🔴 حرجة |
| Voice Messaging (تسجيل + Waveform + Playback) | 🟠 عالية |
| Onboarding Flow (Welcome + Profile Setup + Permissions) | 🟠 عالية |
| Global Search (Chats + In-Message) | 🟡 متوسطة |
| Media Gallery Screen | 🟡 متوسطة |
| Auto-Download Rules Settings | 🟡 متوسطة |
| Parallel Transfer Count Settings | 🟡 متوسطة |
| Room DB Proper Migrations (بدلاً من `fallbackToDestructiveMigration`) | 🟠 عالية |
| Mesh Relay (TTL-based multi-hop routing) | 🟢 مستقبلي |
| Multi-Source Swarm Download | 🟢 مستقبلي |
| Protobuf Full Migration | 🟢 مستقبلي |

### 2.3 المشاكل الحرجة في الـ Codebase الحالي

**🔴 مشكلة 1 — إرسال الملفات كـ ByteArray كامل:**
`ChatRepositoryImpl` يقرأ الصورة أو الفيديو بـ `readBytes()` ويضعه في `payload.data` مباشرة. ملف 50MB = 50MB في RAM دفعة واحدة. هذا سيُسبب `OutOfMemoryError` وـ TCP timeout. **يجب استبداله بالـ Chunking System بالكامل.**

**🔴 مشكلة 2 — لا يوجد تشفير من أي نوع:**
البيانات تُرسل كـ plain bytes عبر TCP. كل من يشم الشبكة (Wireshark) يرى المحتوى كاملاً.

**🔴 مشكلة 3 — لا يوجد إلا LAN Transport:**
`AppContainer` يُحقن `lanTransport` مباشرة. لا يوجد Composite/Orchestrator Layer. إذا انقطع الـ Wi-Fi، لا يوجد fallback.

**🟠 مشكلة 4 — `fallbackToDestructiveMigration(dropAllTables = true)`:**
أي تغيير في database schema سيمسح كل بيانات المستخدم. **يجب كتابة migrations صحيحة.**

**🟠 مشكلة 5 — Serialization بـ JSON:**
`kotlinx.serialization` (JSON) تُستخدم للـ Payloads. هذا overhead غير ضروري خصوصاً للـ ByteArrays التي تُحوَّل لـ Base64.

**🟡 مشكلة 6 — RSSI مزيّف:**
`estimateRssiFromAddress()` في `LanTransportImpl` تُعيد قيمة ثابتة `-55`. `SignalStrength` كامل مبني على بيانات حقيقية لكن الـ data المُدخلة مزيفة.

---

## 3. المبادئ المعمارية الحاكمة

### 3.1 Agnostic Transport (حيادية وسيط النقل)
طبقة الـ Domain (Use Cases + IChatRepository) **لا تعرف ولا تهتم** بأي وسيط اتصال يُستخدم حالياً. تتعامل دائماً مع `IMeshTransport` فقط. الـ Transport الفعلي يُحدَّد من `AppContainer`.

### 3.2 Payload-Centric Design
كل شيء هو `Payload` — رسالة نصية، صورة، صوت، أو chunk من ملف كبير. هذا يُبسّط الطبقات العليا ويُعطي مرونة كاملة للتوسع مستقبلاً.

### 3.3 Surgical Integrity
كل ملف يُنقل يُتحقق منه بـ SHA-256 قبل الإرسال وبعد الاستقبال الكامل. أي chunk تالف يُعاد طلبه تلقائياً.

### 3.4 Graceful Degradation
الأولوية: **LAN (أعلى) → WiFi Direct → Bluetooth (أدنى)**. فشل أي طبقة يُحرّك النظام للطبقة التالية دون تدخل المستخدم.

### 3.5 User Autonomy
لا يُحمَّل أي ملف يتجاوز الحد المحدد (قابل للتخصيص) دون موافقة صريحة من المستخدم. نظام **Request-to-Send (RTS)** إلزامي لكل الملفات.

### 3.6 Zero-Latency Orchestration
التبديل بين الـ Transports يتم في أقل من **500ms** دون انقطاع الجلسة مع الـ Peer.

### 3.7 Binary Sovereignty
الهدف التدريجي هو التخلص من JSON serialization والانتقال لـ Binary format لتقليل الـ Overhead بنسبة 40-60%.

### 3.8 Local-First Data
كل البيانات تُخزَّن محلياً فقط باستخدام Room (Structured Data) + DataStore (Preferences) + Internal Storage (Media Files). لا يُرسَل أي شيء لأي سيرفر خارجي.

---

## 4. طبقة النقل — Transport Stack

### 4.1 `IMeshTransport` Interface — التحديث المطلوب

```kotlin
// الحالي في network/base/IMeshTransport.kt
interface IMeshTransport {
    val events: Flow<TransportEvent>
    val onlinePeers: StateFlow<Set<String>>
    val typingPeers: StateFlow<Set<String>>
    
    // ❌ غير موجود — يجب إضافته:
    val transferProgress: SharedFlow<TransferProgress>

    suspend fun start()
    suspend fun stop()
    suspend fun startDiscovery()
    suspend fun stopDiscovery()
    suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit>
}

// نموذج جديد يُضاف في domain/model/
data class TransferProgress(
    val sessionId: String,
    val peerId: String,
    val direction: TransferDirection,   // SENDING أو RECEIVING
    val totalBytes: Long,
    val transferredBytes: Long,
    val speedBytesPerSec: Long,
    val estimatedSecondsRemaining: Long,
    val isComplete: Boolean = false,
    val isFailed: Boolean = false,
    val failureReason: String? = null
)
```

### 4.2 `CompositeTransport` — العقل المدبّر (يجب بناؤه من الصفر)

`CompositeTransport` هو wrapper يحمل قائمة مرتّبة بالأولوية من الـ transports ويتصرف كـ `IMeshTransport` واحد أمام باقي النظام. **هذا هو أهم مكوّن مفقود في المشروع.**

**الملف:** `app/src/main/java/com/p2p/meshify/network/composite/CompositeTransport.kt`

```kotlin
class CompositeTransport(
    private val transports: List<IMeshTransport>  // مرتبة: [LAN, WiFi Direct, BT]
) : IMeshTransport {

    // يجمع events من كل الـ transports في flow واحد
    override val events: Flow<TransportEvent> = 
        merge(*transports.map { it.events }.toTypedArray())

    // يجمع onlinePeers من كل الـ transports (union)
    override val onlinePeers: StateFlow<Set<String>> = combine(
        transports.map { it.onlinePeers }
    ) { setsArray -> setsArray.fold(emptySet()) { acc, set -> acc + set } }
        .stateIn(scope, SharingStarted.WhileSubscribed(), emptySet())

    // يجمع transferProgress من كل الـ transports
    override val transferProgress: SharedFlow<TransferProgress> = 
        merge(*transports.map { it.transferProgress }.toTypedArray())
            .shareIn(scope, SharingStarted.WhileSubscribed(), 0)

    // يرسل عبر أعلى-أولوية transport يعرف هذا الـ peerId
    override suspend fun sendPayload(
        targetDeviceId: String,
        payload: Payload
    ): Result<Unit> {
        for (transport in transports) {
            if (transport.onlinePeers.value.contains(targetDeviceId)) {
                val result = transport.sendPayload(targetDeviceId, payload)
                if (result.isSuccess) return result
                // فشل على هذا الـ transport → جرّب التالي
            }
        }
        return Result.failure(Exception("No transport available for peer: $targetDeviceId"))
    }

    override suspend fun start() = transports.forEach { it.start() }
    override suspend fun stop() = transports.forEach { it.stop() }
    override suspend fun startDiscovery() = transports.forEach { it.startDiscovery() }
    override suspend fun stopDiscovery() = transports.forEach { it.stopDiscovery() }
}
```

**قواعد الـ Failover في `CompositeTransport`:**
- إذا فشل LAN في إرسال payload → يُجرَّب WiFi Direct فوراً.
- إذا فشل كلاهما → يُجرَّب Bluetooth (للرسائل النصية الصغيرة فقط).
- إذا فشل الكل → يُحفَظ الـ Payload في `pending_messages` ويُعاد إرساله عند عودة أي transport.

### 4.3 `LanTransportImpl` — التحسينات المطلوبة

**الحالة الراهنة:** يعمل بشكل صحيح بـ NSD (mDNS) لاكتشاف الأجهزة وـ TCP Sockets للإرسال والاستقبال. `Dead Peer Detection` موجود (3 failures threshold). `Reactive Visibility` موجود.

**التحسينات المطلوبة:**

1. **إضافة `transferProgress` SharedFlow** — يُصدر أحداث التقدم أثناء إرسال الـ CHUNK_DATA payloads.
2. **إضافة دعم Chunked Sending** — `SocketManager.sendPayload()` يجب أن تدعم إرسال payload كبير (CHUNK_DATA) بكفاءة عبر TCP stream دون تحميله كاملاً في RAM.
3. **إصلاح RSSI الحقيقي** — استبدال القيمة الثابتة `-55` في `estimateRssiFromAddress()` بـ `WifiManager.calculateSignalLevel()` الحقيقي.
4. **تحسين NSD Watchdog** — الـ watchdog الحالي يعيد تشغيل الـ discovery كل `DISCOVERY_SCAN_INTERVAL_MS`. يجب التأكد من عدم استهلاك battery زائد.

**الإعدادات الحالية في `AppConfig` التي تحكم LAN:**
- `SERVICE_TYPE = "_meshify._tcp."` — نوع الخدمة لـ mDNS
- `DEFAULT_PORT` — الـ port الذي يستمع عليه الـ TCP server
- `DISCOVERY_SCAN_INTERVAL_MS` — فترة إعادة تشغيل الـ NSD watchdog
- `MAX_FAILURES_BEFORE_REMOVAL = 3` — عدد فشل الإرسال قبل اعتبار الـ peer ميتاً

### 4.4 `WifiDirectTransportImpl` — يجب بناؤه من الصفر

**الملف:** `app/src/main/java/com/p2p/meshify/network/wifidirect/WifiDirectTransportImpl.kt`

**المكتبة:** Android's `WifiP2pManager` API (موجودة في Android SDK — لا تحتاج dependency خارجي)

**تدفق العمل الكامل:**

```
┌─────────────────────────────────────────────────────────────────────┐
│                    WiFi Direct Transport Lifecycle                    │
├──────────┬──────────────────────────────────────────────────────────┤
│ Step 1   │ تسجيل WifiP2pBroadcastReceiver لاستقبال أحداث الشبكة     │
│ Step 2   │ wifiP2pManager.discoverPeers()                            │
│          │   → WIFI_P2P_PEERS_CHANGED_ACTION                         │
│          │   → requestPeers() → قائمة WifiP2pDevice                  │
│ Step 3   │ اختيار peer → wifiP2pManager.connect(WifiP2pConfig)       │
│ Step 4   │   → WIFI_P2P_CONNECTION_CHANGED_ACTION                    │
│          │   → requestConnectionInfo() → WifiP2pInfo                 │
│          │   → isGroupOwner? → GroupOwner يفتح ServerSocket           │
│          │                   → Client يتصل بـ groupOwnerAddress       │
│ Step 5   │ تبادل الـ Handshake payload                               │
│ Step 6   │ إرسال/استقبال Payloads عبر TCP مباشرة (نفس SocketManager) │
└──────────┴──────────────────────────────────────────────────────────┘
```

**خصائص WiFi Direct Transport:**
- سرعة نقل تصل لـ 250 Mbps نظرياً (عملياً 40-100 Mbps)
- لا تحتاج Access Point (Router)
- المدى: حتى 200 متر في الهواء الطلق
- تكوين الـ Group يستغرق 2-5 ثوانٍ → يجب عرض حالة "Connecting..." في الـ UI
- يدعم اتصال Multiple Clients (Group) — مفيد للـ Mesh مستقبلاً

**الأذونات المطلوبة (موجودة بالفعل في AndroidManifest.xml):**
```xml
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation"
    tools:targetApi="s" />
```

### 4.5 `BluetoothTransportImpl` — يجب بناؤه

**الملف:** `app/src/main/java/com/p2p/meshify/network/bluetooth/BluetoothTransportImpl.kt`

**الاستخدام:** وسيط احتياطي نهائي (Last Resort Fallback). يُستخدم للرسائل النصية القصيرة والـ Handshake فقط حين تفشل LAN وـ WiFi Direct.

**القرار المعماري:** استخدام **Bluetooth Classic (RFCOMM)** في المرحلة الأولى لبساطة التنفيذ.

**تدفق العمل:**
```
BluetoothAdapter.startDiscovery()
    → ACTION_FOUND broadcast → قائمة BluetoothDevice
    → BluetoothSocket.connect(MY_UUID)
    → تبادل Handshake عبر InputStream/OutputStream
    → إرسال Payloads (نصية فقط، < 16KB كل payload)
```

**MTU Limitation:** Bluetooth Classic يدعم حتى 64KB per frame نظرياً، لكن عملياً 16KB هو حد آمن. لهذا chunk size=16KB تم اختياره ليكون متوافقاً مع BT.

**الأذونات الجديدة التي تُضاف للـ Manifest:**
```xml
<!-- Android < 12 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />
<!-- Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />
```

### 4.6 ترتيب الأولوية في `CompositeTransport`

```
Priority 1: LAN (NSD + TCP)      → أعلى bandwidth، أقل latency
Priority 2: WiFi Direct (P2P)    → bandwidth عالٍ، لا يحتاج Router
Priority 3: Bluetooth Classic    → bandwidth منخفض، availability عالية
```

### 4.7 تحديث `AppContainer`

```kotlin
// AppContainer.kt — بعد الإضافات
class AppContainer(private val context: Context) {

    val lanTransport: IMeshTransport by lazy {
        LanTransportImpl(context, socketManager, settingsRepository)
    }
    
    // إضافات جديدة:
    val wifiDirectTransport: IMeshTransport by lazy {
        WifiDirectTransportImpl(context, settingsRepository)
    }
    
    val bluetoothTransport: IMeshTransport by lazy {
        BluetoothTransportImpl(context, settingsRepository)
    }
    
    val compositeTransport: IMeshTransport by lazy {
        EncryptedTransportDecorator(
            inner = CompositeTransport(listOf(
                lanTransport,
                wifiDirectTransport,
                bluetoothTransport
            )),
            keyStore = peerKeyStore
        )
    }
    
    val peerKeyStore: PeerKeyStore by lazy { PeerKeyStore() }
    
    val heartbeatService: HeartbeatService by lazy {
        HeartbeatService(compositeTransport, scope)
    }

    val chatRepository: IChatRepository by lazy {
        ChatRepositoryImpl(
            database.chatDao(),
            database.messageDao(),
            database.pendingMessageDao(),
            database.fileTransferDao(),   // جديد
            compositeTransport,           // بدلاً من lanTransport فقط
            payloadOrchestrator,
            fileManager,
            notificationHelper,
            settingsRepository
        )
    }
    
    val payloadOrchestrator: PayloadOrchestrator by lazy {
        PayloadOrchestrator(
            transport = compositeTransport,
            fileTransferDao = database.fileTransferDao(),
            settingsRepository = settingsRepository
        )
    }
}
```

---

## 5. نموذج الـ Payload والـ Serialization

### 5.1 الـ Payload Model الحالي

```kotlin
// domain/model/Payload.kt — الحالي
data class Payload(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val timestamp: Long = System.currentTimeMillis(),
    val type: PayloadType,
    val data: ByteArray
) {
    enum class PayloadType {
        TEXT, FILE, HANDSHAKE, SYSTEM_CONTROL,
        DELETE_REQUEST, REACTION, DELIVERY_ACK,
        AVATAR_REQUEST, AVATAR_RESPONSE, VIDEO
    }
}
```

### 5.2 الـ Payload Model المحدَّث المطلوب

```kotlin
// domain/model/Payload.kt — بعد التحديث
data class Payload(
    val id: String = UUID.randomUUID().toString(),
    val senderId: String,
    val destinationId: String = "",           // ✅ جديد — المستقبل النهائي
    val timestamp: Long = System.currentTimeMillis(),
    val type: PayloadType,
    val data: ByteArray,
    val ttl: Int = 3,                         // ✅ جديد — Time To Live (hops)
    val routePath: List<String> = emptyList() // ✅ جديد — مسار الرسالة للـ Mesh Relay
) {
    enum class PayloadType {
        // موجودة حالياً — تبقى كما هي:
        TEXT,
        FILE,               // للملفات والصور الصغيرة (< 1MB) — يبقى للتوافق
        HANDSHAKE,
        SYSTEM_CONTROL,
        DELETE_REQUEST,
        REACTION,
        DELIVERY_ACK,
        AVATAR_REQUEST,
        AVATAR_RESPONSE,
        VIDEO,              // للفيديوهات الصغيرة — يبقى للتوافق

        // جديدة — تُضاف:
        VOICE,              // رسالة صوتية مشفرة (Opus/AAC)
        FILE_REQ,           // طلب إرسال ملف كبير — يحمل FileRequest serialized
        FILE_ACC,           // قبول طلب الإرسال — يحمل sessionId
        FILE_REJ,           // رفض طلب الإرسال — يحمل سبب الرفض
        FILE_META,          // تفاصيل النقل — يحمل FileMeta serialized
        CHUNK_DATA,         // قطعة بيانات — يحمل header + chunk bytes
        TRANSFER_ACK,       // تأكيد استلام chunks — يحمل TransferAck serialized
        TRANSFER_CANCEL     // إلغاء عملية نقل جارية
    }
}
```

### 5.3 هياكل البيانات المصاحبة

```kotlin
// 1. للـ HANDSHAKE — تحديث الموجود
@Serializable
data class Handshake(
    val name: String,
    val avatarHash: String? = null,
    val publicKey: String? = null  // ✅ جديد — ECDH Public Key (Base64 encoded)
)

// 2. للـ FILE_REQ — جديد
@Serializable
data class FileRequest(
    val sessionId: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileSizeBytes: Long,
    val sha256Hash: String,
    val mimeType: String,
    val blurThumbnailBase64: String? = null  // < 2KB JPEG مبكسل (للمعاينة)
)

// 3. للـ FILE_META — جديد
@Serializable
data class FileMeta(
    val sessionId: String,
    val totalChunks: Int,
    val chunkSizeBytes: Int = 16 * 1024,
    val fileName: String,
    val sha256Hash: String
)

// 4. للـ TRANSFER_ACK — جديد
@Serializable
data class TransferAck(
    val sessionId: String,
    val receivedChunkIndices: List<Int>  // الـ chunks التي وصلت بنجاح
)

// 5. هيكل CHUNK_DATA payload.data:
// Byte Layout:
// [0..N-1]    : sessionId (UTF-8, length prefixed: 2 bytes for length + N bytes for string)
// [N..N+3]    : chunkIndex (Int, 4 bytes, Big-Endian)
// [N+4..]     : chunkData (remaining bytes)
// إجمالي max: 2 + 36 + 4 + 16384 = ~16426 bytes per chunk payload
```

### 5.4 `PayloadSerializer` — Binary Serialization

**الملف:** `app/src/main/java/com/p2p/meshify/network/serializer/PayloadSerializer.kt`

```kotlin
object PayloadSerializer {
    
    fun encode(payload: Payload): ByteArray {
        val baos = ByteArrayOutputStream()
        DataOutputStream(baos).use { dos ->
            dos.writeByte(payload.type.ordinal)      // 1 byte
            dos.writeUTF(payload.id)                 // 2 + N bytes
            dos.writeUTF(payload.senderId)           // 2 + N bytes
            dos.writeUTF(payload.destinationId)      // 2 + N bytes
            dos.writeByte(payload.ttl)               // 1 byte
            dos.writeLong(payload.timestamp)         // 8 bytes
            dos.writeInt(payload.routePath.size)     // 4 bytes
            payload.routePath.forEach { dos.writeUTF(it) }
            dos.writeInt(payload.data.size)          // 4 bytes
            dos.write(payload.data)                  // N bytes
        }
        return baos.toByteArray()
    }

    fun decode(bytes: ByteArray): Payload {
        DataInputStream(ByteArrayInputStream(bytes)).use { dis ->
            val type = Payload.PayloadType.values()[dis.readByte().toInt() and 0xFF]
            val id = dis.readUTF()
            val senderId = dis.readUTF()
            val destinationId = dis.readUTF()
            val ttl = dis.readByte().toInt()
            val timestamp = dis.readLong()
            val routeSize = dis.readInt()
            val routePath = (0 until routeSize).map { dis.readUTF() }
            val dataLength = dis.readInt()
            val data = ByteArray(dataLength).also { dis.readFully(it) }
            return Payload(id, senderId, destinationId, timestamp, type, data, ttl, routePath)
        }
    }
}
```

**ملاحظة:** هذا يحل محل `Json.encodeToString(payload).toByteArray()` الذي يُنتج JSON overhead.

---

## 6. محرك نقل الملفات — PayloadOrchestrator

### 6.1 المشكلة الحالية بالتفصيل

في `ChatRepositoryImpl.sendImage()` الحالي:
```kotlin
// ❌ الكود الحالي المشكل
override suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, ...): Result<Unit> {
    // imageBytes يمكن أن تكون 50MB في RAM
    val payload = Payload(senderId = myId, type = Payload.PayloadType.FILE, data = imageBytes)
    meshTransport.sendPayload(peerId, payload) // إرسال 50MB دفعة واحدة!
}
```

**النتيجة:** `OutOfMemoryError` للملفات الكبيرة + لا يوجد progress + لا يوجد resume.

### 6.2 `PayloadOrchestrator` — المكوّن الجديد

**الملف:** `app/src/main/java/com/p2p/meshify/network/orchestrator/PayloadOrchestrator.kt`

**مسؤوليات `PayloadOrchestrator`:**
1. استقبال طلب إرسال ملف (file URI/path + target peerId).
2. حساب SHA-256 للملف كاملاً من الـ disk قبل أي إرسال.
3. إنشاء `FileRequest` وإرسالها كـ `FILE_REQ` payload.
4. انتظار `FILE_ACC` من الـ Peer (timeout: 30 ثانية).
5. قراءة الملف من الـ disk chunk بـ chunk (لا يُحمَّل كله في RAM).
6. إرسال `FILE_META` ثم الـ chunks بنظام Sliding Window.
7. استقبال `TRANSFER_ACK` وتحريك النافذة.
8. إعادة إرسال الـ chunks الفاقدة.
9. عند الاستلام: إعادة تجميع الـ chunks من الـ disk.
10. التحقق من SHA-256 للملف المُجمَّع مقارنةً بالـ hash في `FILE_META`.
11. تحديث `file_transfers` table في Room بعد كل ACK.
12. إصدار `TransferProgress` events عبر `SharedFlow`.

### 6.3 استراتيجية الـ Chunking

**حجم الـ Chunk الثابت:** `CHUNK_SIZE = 16 * 1024 = 16,384 bytes` (16KB)

**سبب الاختيار:**
- متوافق مع Bluetooth MTU (BT Classic يدعم ~64KB لكن 16KB آمن دائماً)
- معقول لـ LAN وـ WiFi Direct
- يضمن أن نفس الكود يعمل عبر كل الـ transports بدون منطق خاص لكل transport

**حساب عدد الـ Chunks:**
```kotlin
val totalChunks = ceil(fileSizeBytes.toDouble() / CHUNK_SIZE).toInt()
// مثال: ملف 10MB = 10,485,760 / 16,384 = 640 chunk
```

**قراءة الـ Chunks من الـ Disk:**
```kotlin
// قراءة chunk بـ chunk من الـ disk دون تحميل الملف كله
File(filePath).inputStream().use { stream ->
    val buffer = ByteArray(CHUNK_SIZE)
    var chunkIndex = 0
    var bytesRead: Int
    while (stream.read(buffer).also { bytesRead = it } != -1) {
        val chunkData = buffer.copyOf(bytesRead)
        // إرسال chunkData كـ CHUNK_DATA payload
        chunkIndex++
    }
}
```

### 6.4 Sliding Window Protocol — التفاصيل الكاملة

**Window Size الافتراضي:** `WINDOW_SIZE = 10` chunks (يعني 160KB per batch)

```
مثال لملف 640 chunk مع window size = 10:

Iteration 1:
  المُرسل يرسل chunks[0..9]
  المستقبل يرسل ACK: receivedChunkIndices = [0,1,2,3,5,6,7,8,9] (chunk 4 ضاع!)
  المُرسل يعيد إرسال chunk[4]
  المستقبل يرسل ACK: receivedChunkIndices = [4] (تأكيد الـ chunk المُعاد)
  النافذة تتحرك: chunks[10..19]

Iteration 2:
  المُرسل يرسل chunks[10..19]
  ...
```

**منطق تحريك النافذة:**
- النافذة لا تتحرك للأمام إلا بعد تأكيد استلام **جميع** chunks في النافذة الحالية.
- إذا ظل chunk لم يُؤكَّد بعد 3 محاولات إعادة إرسال → يُعلَّق النقل ويُنتظر حتى يُستأنف.

**ACK Timeout:** 5 ثوانٍ. إذا لم يصل أي ACK خلال 5 ثوانٍ → تُعاد إرسال كل chunks النافذة الحالية.

**Window Size الديناميكي (اختياري - تحسين مستقبلي):**
- LAN: Window = 20 chunks (320KB per batch)
- WiFi Direct: Window = 10 chunks (160KB per batch)
- Bluetooth: Window = 2 chunks (32KB per batch)

### 6.5 استئناف النقل المنقطع — Resume Logic

**الشرط:** يعتمد على `file_transfers` Room table (انظر §9.3).

**تدفق الاستئناف:**
```
عند إعادة الاتصال مع Peer:
1. المستقبل يُرسل FILE_REQ بنفس sha256Hash للملف الذي لم يكتمل
2. المُرسل يبحث في file_transfers عن session بنفس الـ hash
3. إذا وجد → يستأنف من آخر chunk مؤكَّد
   يحسب firstMissingChunk من receivedChunksBitmap
4. يُرسل FILE_META مع startChunkIndex = firstMissingChunk
5. المستقبل يبدأ الكتابة في نفس الـ temp file من نفس الـ offset
```

**`receivedChunksBitmap` Implementation:**
```kotlin
// BitSet بحجم totalChunks
// ملف 640 chunk = 640 bits = 80 bytes فقط كـ bitmap

fun BitSet.toByteArray(): ByteArray { ... }  // تحويل للتخزين في Room
fun ByteArray.toBitSet(): BitSet { ... }      // استرجاع من Room

// تسجيل chunk مستقبَل:
bitmap.set(chunkIndex)

// البحث عن أول chunk ناقص:
val firstMissing = bitmap.nextClearBit(0)  // -1 إذا اكتمل الملف
```

### 6.6 التوازي والتحكم في الموارد

- **افتراضي:** ملفان (2) يُنقلان بالتوازي في نفس الوقت (sending أو receiving).
- **قابل للضبط:** إعداد في Settings يسمح للمستخدم بتغيير العدد من 1 إلى 5.
- **Dynamic Buffer:**
  - LAN: Buffer حتى 512KB لكل connection
  - WiFi Direct: Buffer حتى 256KB
  - Bluetooth: Buffer = حجم chunk فقط (16KB)
- **Memory Guard:** إجمالي الذاكرة المستخدمة للـ buffers لا تتجاوز 25% من الـ available heap.

### 6.7 Multi-Source Swarm Download (مرحلة مستقبلية — Phase 3)

إذا كان الملف موجوداً عند أكثر من peer:
1. المستقبل يُرسل `FILE_REQ` لـ Peer A وـ Peer B في نفس الوقت.
2. Peer A يُرسل الـ manifest (قائمة SHA-256 hashes لكل chunk).
3. Peer B يُرسل نفس الـ manifest (تأكيد التطابق بين النسختين).
4. المستقبل يُقسّم الـ chunks: أرقام زوجية من A، أرقام فردية من B.
5. كل chunk يُتحقق منه بـ hash فريد من الـ manifest — أي peer يُرسل chunk فاسد يُعرَّف تلقائياً.

---

## 7. طبقة الأمان والتشفير

### 7.1 المشكلة الحالية

لا يوجد أي تشفير في الـ codebase. البيانات تُرسل كـ plain bytes عبر TCP على الشبكة المحلية. أي شخص على نفس الشبكة يستطيع قراءة كل الرسائل والملفات.

### 7.2 الخوارزميات المختارة

| الغرض | الخوارزمية | السبب |
|--------|-----------|-------|
| تشفير البيانات | AES-256-GCM | AEAD — يوفر سرية + سلامة + مصادقة |
| تبادل المفاتيح | ECDH (X25519 أو secp256r1) | مبني في Android KeyPairGenerator |
| اشتقاق المفاتيح | HKDF-SHA256 | لاشتقاق AES key من ECDH shared secret |
| التحقق من الملفات | SHA-256 | لضمان سلامة الملفات المنقولة |

**كل هذه الخوارزميات موجودة في `javax.crypto` و`java.security` — لا تحتاج أي dependency خارجي.**

### 7.3 تدفق الـ Handshake المشفَّر

```
Device A (المُبادر)                    Device B (المستقبِل)
─────────────────                       ────────────────
1. يُولّد ECDH KeyPair (pubA, privA)
   باستخدام KeyPairGenerator.getInstance("EC")
2. يُرسل HANDSHAKE {name, avatarHash, publicKey=pubA_Base64}
────────────────────────────────────────►
                                    3. يُولّد ECDH KeyPair (pubB, privB)
                                    4. يحسب sharedSecret = ECDH(privB, pubA)
                                    5. يشتق AES Key = HKDF(sharedSecret, salt, 32)
                                    6. يُرسل HANDSHAKE {name, avatarHash, publicKey=pubB_Base64}
◄────────────────────────────────────────
7. يحسب sharedSecret = ECDH(privA, pubB)
8. يشتق AES Key = HKDF(sharedSecret, salt, 32)

✅ كلاهما لديهم الآن نفس AES-256 Key بدون أن يُرسَل عبر الشبكة!

9. كل Payload بعد ذلك:
   payload.data = AES_GCM_Encrypt(key, plaintext)
   → [IV: 12 bytes] [Ciphertext] [GCM Auth Tag: 16 bytes]
```

### 7.4 `EncryptedTransportDecorator` — يجب بناؤه

**الملف:** `app/src/main/java/com/p2p/meshify/network/security/EncryptedTransportDecorator.kt`

```kotlin
class EncryptedTransportDecorator(
    private val inner: IMeshTransport,
    private val keyStore: PeerKeyStore
) : IMeshTransport by inner {

    override suspend fun sendPayload(
        targetDeviceId: String,
        payload: Payload
    ): Result<Unit> {
        // لا نُشفَّر الـ HANDSHAKE (نحتاجه لتبادل المفاتيح أولاً)
        if (payload.type == Payload.PayloadType.HANDSHAKE) {
            return inner.sendPayload(targetDeviceId, payload)
        }
        
        val key = keyStore.getAesKey(targetDeviceId)
            ?: return Result.failure(Exception("No encryption key for $targetDeviceId. Handshake required."))
        
        val encryptedData = AesGcm.encrypt(key, payload.data)
        return inner.sendPayload(targetDeviceId, payload.copy(data = encryptedData))
    }
    
    // events تُعالَج لفك تشفير incoming payloads:
    override val events: Flow<TransportEvent> = inner.events.map { event ->
        if (event is TransportEvent.PayloadReceived && event.payload.type != Payload.PayloadType.HANDSHAKE) {
            val key = keyStore.getAesKey(event.deviceId)
            if (key != null) {
                val decryptedData = AesGcm.decrypt(key, event.payload.data)
                event.copy(payload = event.payload.copy(data = decryptedData))
            } else event  // لم يحدث Handshake بعد
        } else event
    }
}
```

**`PeerKeyStore`:** `ConcurrentHashMap<String, ByteArray>` (peerId → AES-256 key). تُحدَّث عند كل Handshake ناجح. تُخزَّن في الذاكرة فقط (volatile — تُعاد عند كل تشغيل).

**`AesGcm` helper:**
```kotlin
object AesGcm {
    private const val IV_SIZE = 12      // 96 bits
    private const val TAG_SIZE = 128    // 128 bits auth tag
    
    fun encrypt(key: ByteArray, plaintext: ByteArray): ByteArray {
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE, iv))
        val ciphertext = cipher.doFinal(plaintext)
        return iv + ciphertext  // prepend IV to ciphertext
    }
    
    fun decrypt(key: ByteArray, ciphertext: ByteArray): ByteArray {
        val iv = ciphertext.copyOfRange(0, IV_SIZE)
        val data = ciphertext.copyOfRange(IV_SIZE, ciphertext.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_SIZE, iv))
        return cipher.doFinal(data)
    }
}
```

### 7.5 خزينة المفاتيح — ECDH Key Management

```kotlin
class PeerKeyStore {
    // Map: peerId → AES-256 derived key
    private val keys = ConcurrentHashMap<String, ByteArray>()
    
    fun storeKeyFromHandshake(
        myPrivateKey: PrivateKey,
        peerPublicKeyBase64: String,
        peerId: String
    ) {
        val peerPubKey = base64ToPublicKey(peerPublicKeyBase64)
        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(myPrivateKey)
        keyAgreement.doPhase(peerPubKey, true)
        val sharedSecret = keyAgreement.generateSecret()
        
        // اشتقاق AES key من الـ shared secret
        val aesKey = HKDF.deriveKey(sharedSecret, salt = peerId.toByteArray(), length = 32)
        keys[peerId] = aesKey
    }
    
    fun getAesKey(peerId: String): ByteArray? = keys[peerId]
    
    fun removeKey(peerId: String) = keys.remove(peerId)
}
```

**Private Key Storage:** يُخزَّن الـ private key في Android `KeyStore` (hardware-backed إن أمكن) تحت alias `"meshify_ecdh_key"`. لا يُخرج من الـ KeyStore أبداً.

---

## 8. نظام اكتشاف الأجهزة والـ Failover

### 8.1 `PeerStore` — تعريف الهوية المتعددة

```kotlin
// domain/model/PeerIdentity.kt — جديد
data class PeerIdentity(
    val peerId: String,
    val displayName: String,
    val avatarHash: String? = null,
    val lanIp: String? = null,          // آخر IP معروف على LAN
    val wfdAddress: String? = null,     // WiFi Direct MAC/IP
    val btMac: String? = null,          // Bluetooth MAC Address
    val publicKey: String? = null,      // ECDH Public Key (Base64)
    val lastSeenTimestamp: Long = 0L,
    val signalStrength: SignalStrength = SignalStrength.OFFLINE,
    val preferredTransport: TransportType = TransportType.LAN
)

enum class TransportType { LAN, WIFI_DIRECT, BLUETOOTH, UNKNOWN }
```

`PeerStore` هو `ConcurrentHashMap<String, PeerIdentity>` داخل `CompositeTransport`. يُحدَّث عند كل `DeviceDiscovered` event أو `HANDSHAKE` payload.

**استخدام `PeerStore` في الـ UI:** `DiscoveryScreen` يعرض الـ peers من `onlinePeers` StateFlow مع بيانات الـ Signal Strength من الـ PeerStore → يُحرّك `SignalMorphAvatar` بشكل صحيح.

### 8.2 نظام الـ Heartbeat

**الملف:** `app/src/main/java/com/p2p/meshify/network/service/HeartbeatService.kt`

```kotlin
class HeartbeatService(
    private val transport: IMeshTransport,
    private val scope: CoroutineScope
) {
    private val HEARTBEAT_INTERVAL_MS = 3000L  // 3 ثوانٍ
    private val HEARTBEAT_TIMEOUT_MS = 5000L   // 5 ثوانٍ
    private val MAX_MISSED_PINGS = 3
    
    // missedPings: peerId → عدد المرات التي لم يصل فيها PONG
    private val missedPings = ConcurrentHashMap<String, Int>()
    
    fun start() {
        scope.launch {
            while (isActive) {
                transport.onlinePeers.value.forEach { peerId ->
                    sendPing(peerId)
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    fun onPongReceived(peerId: String) {
        missedPings[peerId] = 0
    }
    
    private suspend fun sendPing(peerId: String) {
        val result = transport.sendPayload(peerId, Payload(
            senderId = myDeviceId,
            destinationId = peerId,
            type = Payload.PayloadType.SYSTEM_CONTROL,
            data = "PING".toByteArray()
        ))
        if (result.isFailure) {
            val count = (missedPings[peerId] ?: 0) + 1
            missedPings[peerId] = count
            if (count >= MAX_MISSED_PINGS) {
                // الـ peer ميت — يُزال من كل شيء
                missedPings.remove(peerId)
            }
        }
    }
}
```

**ملاحظة:** الـ Heartbeat يعمل من `HeartbeatService` المستقل ويُشغَّل من `AppContainer.init()`. الـ `LanTransportImpl` يحتفظ بـ Dead Peer Detection بالـ send failures كطبقة إضافية.

### 8.3 منطق الـ Failover الكامل

```
لكل عملية إرسال لـ peer:
│
├── LAN available for this peer?
│   ├── YES → sendPayload via LAN
│   │   ├── Success → ✅ done
│   │   └── Failure → fallthrough to WiFi Direct
│   └── NO → fallthrough to WiFi Direct
│
├── WiFi Direct available for this peer?
│   ├── YES → sendPayload via WiFi Direct
│   │   ├── Success → ✅ done
│   │   └── Failure → fallthrough to Bluetooth
│   └── NO → fallthrough to Bluetooth
│
├── Bluetooth available for this peer?
│   ├── YES (small payload only: TEXT, SYSTEM_CONTROL, HANDSHAKE)
│   │   ├── Success → ✅ done
│   │   └── Failure → save to pending_messages
│   └── NO → save to pending_messages
│
└── pending_messages → يُعاد الإرسال عند عودة أي transport
```

**Transport Switch Duration Target:** أقل من 500ms.

**Session Continuity لنقل الملفات:** إذا كانت هناك عملية `PayloadOrchestrator` جارية على LAN وانقطع → يُوقف النقل مؤقتاً → يُحاوَل الاستئناف عبر WiFi Direct بنفس `sessionId` → إذا تعذّر → يُحفَظ في `file_transfers` table → يُستأنف تلقائياً عند عودة أي transport.

---

## 9. طبقة البيانات — Room Database

### 9.1 الـ Schema الحالي (v3) — جداول موجودة

**جدول `chats`:** peerId (PK), peerName, lastMessage, lastTimestamp

**جدول `messages`:** id (PK), chatId, senderId, text, mediaPath, type, timestamp, isFromMe, status, isDeletedForMe, isDeletedForEveryone, deletedAt, deletedBy, reaction, replyToId, groupId

**جدول `pending_messages`:** id (PK), recipientId, recipientName, content, type, timestamp, status, retryCount, maxRetries

### 9.2 `MessageType` enum الحالي

```kotlin
enum class MessageType {
    TEXT, IMAGE, VIDEO, FILE,
    // يجب إضافة:
    VOICE,          // رسالة صوتية
    FILE_TRANSFER   // ملف كبير يتم نقله عبر PayloadOrchestrator
}
```

### 9.3 جدول `file_transfers` جديد — Migration v4

**الملف:** `app/src/main/java/com/p2p/meshify/data/local/entity/FileTransferEntity.kt`

```kotlin
@Entity(tableName = "file_transfers")
data class FileTransferEntity(
    @PrimaryKey val sessionId: String,
    val peerId: String,                    // الـ peer المعني
    val fileName: String,                  // اسم الملف
    val fileSizeBytes: Long,               // حجم الملف الكامل
    val sha256Hash: String,                // SHA-256 للتحقق
    val mimeType: String,                  // "image/jpeg", "video/mp4", إلخ
    val totalChunks: Int,                  // إجمالي عدد الـ chunks
    val chunkSizeBytes: Int = 16 * 1024,   // 16KB افتراضياً
    val receivedChunksBitmap: ByteArray,   // BitSet → ByteArray
    val localFilePath: String? = null,     // مسار الملف الناقص على الجهاز
    val direction: String,                 // "SENDING" أو "RECEIVING"
    val status: String,                    // "PENDING"/"IN_PROGRESS"/"COMPLETED"/"FAILED"/"CANCELLED"
    val startedAt: Long = System.currentTimeMillis(),
    val lastUpdatedAt: Long = System.currentTimeMillis(),
    val relatedMessageId: String? = null   // FK → messages.id
)
```

**`FileTransferDao`:**
```kotlin
@Dao
interface FileTransferDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transfer: FileTransferEntity)
    
    @Query("SELECT * FROM file_transfers WHERE sessionId = :sessionId")
    suspend fun getBySessionId(sessionId: String): FileTransferEntity?
    
    @Query("SELECT * FROM file_transfers WHERE peerId = :peerId AND status = 'IN_PROGRESS'")
    fun getActiveTransfers(peerId: String): Flow<List<FileTransferEntity>>
    
    @Query("UPDATE file_transfers SET receivedChunksBitmap = :bitmap, lastUpdatedAt = :timestamp WHERE sessionId = :sessionId")
    suspend fun updateBitmap(sessionId: String, bitmap: ByteArray, timestamp: Long)
    
    @Query("UPDATE file_transfers SET status = :status, localFilePath = :path WHERE sessionId = :sessionId")
    suspend fun updateStatus(sessionId: String, status: String, path: String? = null)
    
    @Query("SELECT * FROM file_transfers WHERE status IN ('PENDING', 'IN_PROGRESS') AND direction = 'RECEIVING'")
    fun getIncompleteReceivingTransfers(): Flow<List<FileTransferEntity>>
}
```

### 9.4 جدول `peer_identities` جديد — Migration v4

```kotlin
@Entity(tableName = "peer_identities")
data class PeerIdentityEntity(
    @PrimaryKey val peerId: String,
    val displayName: String,
    val avatarHash: String? = null,
    val lanIp: String? = null,
    val wfdAddress: String? = null,
    val btMac: String? = null,
    val publicKey: String? = null,
    val lastSeenTimestamp: Long = 0L,
    val isFavorite: Boolean = false
)
```

**ملاحظة:** `PeerKeyStore` (AES keys) لا تُخزَّن في Room — تبقى في الذاكرة فقط. يُعاد الـ Handshake عند كل تشغيل جديد للتطبيق.

### 9.5 Room Migration v3 → v4

**الملف:** `app/src/main/java/com/p2p/meshify/data/local/MeshifyDatabase.kt`

```kotlin
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // إنشاء جدول file_transfers
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS file_transfers (
                sessionId TEXT NOT NULL PRIMARY KEY,
                peerId TEXT NOT NULL,
                fileName TEXT NOT NULL,
                fileSizeBytes INTEGER NOT NULL,
                sha256Hash TEXT NOT NULL,
                mimeType TEXT NOT NULL,
                totalChunks INTEGER NOT NULL,
                chunkSizeBytes INTEGER NOT NULL DEFAULT 16384,
                receivedChunksBitmap BLOB NOT NULL,
                localFilePath TEXT,
                direction TEXT NOT NULL,
                status TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                lastUpdatedAt INTEGER NOT NULL,
                relatedMessageId TEXT
            )
        """)
        
        // إنشاء جدول peer_identities
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS peer_identities (
                peerId TEXT NOT NULL PRIMARY KEY,
                displayName TEXT NOT NULL,
                avatarHash TEXT,
                lanIp TEXT,
                wfdAddress TEXT,
                btMac TEXT,
                publicKey TEXT,
                lastSeenTimestamp INTEGER NOT NULL DEFAULT 0,
                isFavorite INTEGER NOT NULL DEFAULT 0
            )
        """)
        
        // إضافة عمود VOICE لـ messages type (لا تحتاج SQL — enum handled in Kotlin)
    }
}

// في MeshifyDatabase:
@Database(
    entities = [ChatEntity::class, MessageEntity::class, PendingMessageEntity::class,
                FileTransferEntity::class, PeerIdentityEntity::class],
    version = 4
)
// استبدال fallbackToDestructiveMigration بـ:
Room.databaseBuilder(context, MeshifyDatabase::class.java, "meshify.db")
    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
    .build()
```

---

## 10. طبقة الـ Domain — Use Cases & Repositories

### 10.1 `IChatRepository` — الإضافات المطلوبة

```kotlin
interface IChatRepository {
    // موجود حالياً — يبقى كما هو
    fun getAllChats(): Flow<List<ChatEntity>>
    fun getMessages(chatId: String): Flow<List<MessageEntity>>
    fun getMessagesPaged(chatId: String, limit: Int, offset: Int): Flow<List<MessageEntity>>
    val onlinePeers: Flow<Set<String>>
    val typingPeers: Flow<Set<String>>
    suspend fun sendMessage(peerId: String, peerName: String, text: String, replyToId: String?): Result<Unit>
    suspend fun sendImage(peerId: String, peerName: String, imageBytes: ByteArray, extension: String, replyToId: String?): Result<Unit>
    suspend fun sendVideo(peerId: String, peerName: String, videoBytes: ByteArray, extension: String, replyToId: String?): Result<Unit>
    suspend fun deleteMessage(messageId: String, deleteType: DeleteType): Result<Unit>
    suspend fun deleteChat(peerId: String)
    suspend fun addReaction(messageId: String, reaction: String?): Result<Unit>
    suspend fun forwardMessage(messageId: String, targetPeerIds: List<String>): Result<Unit>
    suspend fun sendSystemCommand(peerId: String, command: String)
    suspend fun handleIncomingPayload(peerId: String, payload: Payload)
    suspend fun retryPendingMessages(peerId: String)
    
    // إضافات جديدة:
    suspend fun sendFile(peerId: String, peerName: String, fileUri: String, mimeType: String): Result<String>  // يعيد sessionId
    suspend fun sendVoiceMessage(peerId: String, peerName: String, audioBytes: ByteArray, durationMs: Long): Result<Unit>
    fun getActiveTransfers(peerId: String): Flow<List<FileTransferEntity>>
    val transferProgress: Flow<TransferProgress>
    suspend fun cancelTransfer(sessionId: String)
    suspend fun acceptFileRequest(sessionId: String, peerId: String): Result<Unit>
    suspend fun rejectFileRequest(sessionId: String, peerId: String): Result<Unit>
    fun searchMessages(query: String): Flow<List<MessageEntity>>
    fun searchChats(query: String): Flow<List<ChatEntity>>
}
```

### 10.2 Use Cases الجديدة

**الملف:** `app/src/main/java/com/p2p/meshify/domain/usecase/FileTransferUseCases.kt`

```kotlin
class SendFileUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(
        peerId: String,
        peerName: String,
        fileUri: String,
        mimeType: String
    ): Result<String> = repository.sendFile(peerId, peerName, fileUri, mimeType)
}

class AcceptFileRequestUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(sessionId: String, peerId: String): Result<Unit> =
        repository.acceptFileRequest(sessionId, peerId)
}

class CancelTransferUseCase(private val repository: IChatRepository) {
    suspend operator fun invoke(sessionId: String) =
        repository.cancelTransfer(sessionId)
}

class GetTransferProgressUseCase(private val repository: IChatRepository) {
    operator fun invoke(): Flow<TransferProgress> = repository.transferProgress
}
```

**الملف:** `app/src/main/java/com/p2p/meshify/domain/usecase/SearchUseCases.kt`

```kotlin
class SearchMessagesUseCase(private val repository: IChatRepository) {
    operator fun invoke(query: String): Flow<List<MessageEntity>> =
        repository.searchMessages(query)
}

class SearchChatsUseCase(private val repository: IChatRepository) {
    operator fun invoke(query: String): Flow<List<ChatEntity>> =
        repository.searchChats(query)
}
```

### 10.3 `ChatRepositoryImpl` — إعادة هيكلة

الـ `handleIncomingPayload()` يجب أن يُعالج أنواع الـ Payload الجديدة:

```kotlin
override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
    payloadMutex.withLock {
        when (payload.type) {
            // موجود حالياً:
            Payload.PayloadType.SYSTEM_CONTROL -> handleSystemControl(peerId, payload)
            Payload.PayloadType.DELETE_REQUEST -> handleDeleteRequest(payload)
            Payload.PayloadType.REACTION -> handleReaction(payload)
            Payload.PayloadType.TEXT -> handleTextMessage(peerId, payload)
            Payload.PayloadType.FILE -> handleLegacyFile(peerId, payload)
            Payload.PayloadType.VIDEO -> handleLegacyVideo(peerId, payload)
            Payload.PayloadType.HANDSHAKE -> handleHandshakeAtRepository(peerId, payload)
            
            // جديدة — تُضاف:
            Payload.PayloadType.FILE_REQ -> payloadOrchestrator.handleIncomingFileRequest(peerId, payload)
            Payload.PayloadType.FILE_ACC -> payloadOrchestrator.handleFileAccepted(peerId, payload)
            Payload.PayloadType.FILE_REJ -> payloadOrchestrator.handleFileRejected(peerId, payload)
            Payload.PayloadType.FILE_META -> payloadOrchestrator.handleFileMeta(peerId, payload)
            Payload.PayloadType.CHUNK_DATA -> payloadOrchestrator.handleChunkData(peerId, payload)
            Payload.PayloadType.TRANSFER_ACK -> payloadOrchestrator.handleTransferAck(peerId, payload)
            Payload.PayloadType.TRANSFER_CANCEL -> payloadOrchestrator.handleTransferCancel(peerId, payload)
            Payload.PayloadType.VOICE -> handleVoiceMessage(peerId, payload)
            
            else -> Logger.w("ChatRepository → Unknown payload type: ${payload.type}")
        }
    }
}
```

---

## 11. ميزات التطبيق المطلوبة

### 11.1 نظام Request-to-Send (RTS) — الأكثر أهمية

**الهدف:** لا يُحمَّل أي ملف دون موافقة صريحة من المستخدم.

**تدفق الإرسال (Sender Side):**
1. المستخدم يختار ملفاً من المعرض أو مدير الملفات.
2. `PayloadOrchestrator` يحسب SHA-256 + يُنشئ blur thumbnail.
3. يُرسل `FILE_REQ` payload يحمل: `{fileName, fileSizeBytes, sha256Hash, mimeType, blurThumbnailBase64}`.
4. في `ChatScreen`، تظهر رسالة من النوع `FILE_TRANSFER` بحالة `PENDING_ACCEPTANCE`.
5. إذا قَبِل المستقبل → يبدأ النقل + يتغير الـ bubble لشريط تقدم.
6. إذا رفض → يتغير الـ bubble لـ "File rejected".

**تدفق الاستقبال (Receiver Side):**
1. يصل `FILE_REQ` payload.
2. يُحفَظ في قاعدة البيانات كـ `MessageEntity` من نوع `FILE_TRANSFER` بحالة `AWAITING_USER_ACCEPT`.
3. في `ChatScreen`، يظهر **RTS Card** (انظر §12.2).
4. المستخدم يضغط "Download" → يُرسَل `FILE_ACC` → يبدأ النقل.
5. المستخدم يتجاهله → يبقى الـ Card إلى أجل غير مسمى (لا يبدأ تحميل تلقائي).

**Auto-Download Rules (قابل للتخصيص في Settings):**
- تحميل تلقائي للصور إذا كانت < 5MB على LAN فقط.
- تحميل تلقائي للملفات إذا كانت < 1MB على أي transport.
- إيقاف التحميل التلقائي الكامل.

### 11.2 Voice Messaging System

**تسجيل الصوت:**
- Long-press على زر الميكروفون في Input Bar لبدء التسجيل.
- سحب يساراً أثناء التسجيل لإلغائي (swipe-to-cancel).
- Haptic feedback: نبضة عند البدء، اهتزاز عند الإلغاء.
- الحد الأقصى للتسجيل: 5 دقائق.
- الفورمات: **Opus** (مشفَّر بـ `.ogg`) أو **AAC** (`.m4a`) — حجم ضغط عالٍ.

**الإرسال:**
- يُضغط الصوت بـ Opus/AAC → يُشفَّر بـ AES-256-GCM → يُرسَل كـ `VOICE` payload.
- إذا كان الصوت صغير (< 1MB) → يُرسَل مباشرةً كـ `VOICE` payload (لا يحتاج chunking).
- إذا كان الصوت كبير (> 1MB) → يُرسَل عبر `PayloadOrchestrator` كـ chunked transfer.

**Playback:**
- في `ChatScreen`: waveform بسيط أو progress bar.
- زر play/pause مع عداد الوقت.
- استخدام **Media3 ExoPlayer** (موجود بالفعل في الـ dependencies).
- يُشار إلى حالة القراءة: لم يُسمع / سُمع.

### 11.3 Onboarding Flow

**Screens:**

**Screen 1 — Welcome:**
- خلفية حيّة بـ MD3E shapes تتحرك.
- عنوان: "Meshify — Connect Beyond the Cloud"
- وصف: "Chat securely with nearby devices. No internet. No servers."
- زر: "Get Started"

**Screen 2 — Profile Setup:**
- حقل اسم المستخدم (إلزامي — `minLength = 2`, `maxLength = 30`).
- Default value: "Meshify User" إذا تُرك فارغاً.
- اختياري: تحديد صورة الـ Avatar من المعرض.
- زر: "Continue"

**Screen 3 — Permissions:**
- شرح سبب كل إذن بلغة بشرية:
  - **Location / Nearby WiFi Devices:** "To discover nearby devices on your network"
  - **Notifications:** "To alert you when new messages arrive"
  - **Bluetooth:** "To connect with devices without WiFi"
- زر: "Grant Permissions" يُفتح Request لكل إذن.
- إذا رُفض إذن → يُشرح للمستخدم كيف يُفعّله يدوياً.

**Onboarding Logic:**
- يظهر فقط عند أول تشغيل (flag في DataStore: `isOnboardingCompleted: Boolean`).
- لا يمكن تخطيه إلا بعد إدخال الاسم (Screen 2).

### 11.4 Global Search

**Chat-Level Search (`RecentChatsScreen`):**
- Search bar مخفي يظهر عند Scroll Up أو النقر على أيقونة البحث.
- يُفلتر المحادثات بـ: اسم الـ Peer.
- النتائج تظهر real-time أثناء الكتابة.

**In-Chat Search (`ChatScreen`):**
- أيقونة Search في الـ TopAppBar.
- يبحث عن نص داخل رسائل المحادثة الحالية.
- يُبرز (highlight) النص المطابق في الـ MessageBubble.
- أزرار "Previous" و "Next" للتنقل بين النتائج.

**DAO Query المطلوبة:**
```kotlin
// في MessageDao:
@Query("SELECT * FROM messages WHERE chatId = :chatId AND text LIKE '%' || :query || '%' ORDER BY timestamp ASC")
fun searchMessages(chatId: String, query: String): Flow<List<MessageEntity>>

// في ChatDao:
@Query("SELECT * FROM chats WHERE peerName LIKE '%' || :query || '%' ORDER BY lastTimestamp DESC")
fun searchChats(query: String): Flow<List<ChatEntity>>
```

### 11.5 Media Gallery

شاشة مستقلة (`MediaGalleryScreen`) تعرض كل الوسائط المُستلمة والمُرسَلة.

**التبويبات:**
- **Images:** شبكة (Grid) من الصور المحفوظة.
- **Videos:** قائمة الفيديوهات مع preview thumbnail.
- **Files:** قائمة الملفات مع الاسم والحجم وتاريخ الاستلام.
- **Voice:** قائمة رسائل الصوت مع مدتها.

**الـ DAO Query:**
```kotlin
@Query("SELECT * FROM messages WHERE type IN ('IMAGE', 'VIDEO', 'FILE', 'VOICE') AND isDeletedForMe = 0 ORDER BY timestamp DESC")
fun getAllMedia(): Flow<List<MessageEntity>>
```

---

## 12. واجهة المستخدم — UI/UX + MD3E

### 12.1 Design System الحالي (مكتمل ✅)

**7 أشكال MD3E (موجودة في `ui/theme/MD3EShapes.kt`):**
- `Sunny` — نجمة 10 رؤوس
- `Breezy` — نجمة 9 رؤوس
- `Pentagon` — خماسي
- `Blob` — شكل عضوي
- `Burst` — انفجار 8 رؤوس
- `Clover` — برسيم 4 أوراق
- `Circle` — دائرة كاملة

**Motion Presets (موجودة):**
- `GENTLE` — stiffness منخفض، damping عالٍ (هادئ)
- `STANDARD` — MD3E المتوازن الافتراضي
- `SNAPPY` — stiffness عالٍ، damping منخفض (سريع)
- `BOUNCY` — مرن وحيوي

**Font Presets (موجودة + Google Fonts):**
Roboto, Poppins, Lora, Montserrat, Playfair, Inter

**Bubble Styles (موجودة):**
ROUNDED, TAILED, SQUARCLES, ORGANIC

### 12.2 RTS Card — مكوّن UI جديد

**الملف:** `app/src/main/java/com/p2p/meshify/ui/components/FileRequestCard.kt`

```
┌────────────────────────────────────────────┐
│  [blur thumbnail أو icon الملف]            │
│                                            │
│  📎 filename.mp4                            │
│  245.7 MB • Video • MP4                    │
│                                            │
│  ┌──────────────┐   ┌──────────────┐       │
│  │   Decline    │   │  Download ▼  │       │
│  └──────────────┘   └──────────────┘       │
└────────────────────────────────────────────┘
```

**States:**
- `AWAITING_ACCEPTANCE` → يُعرض الكارد الكامل
- `IN_PROGRESS` → يُعرض شريط تقدم مع سرعة النقل: `245.7 MB • 3.2 MB/s • ~76s left`
- `COMPLETED` → يُعرض كصورة/فيديو/ملف عادي
- `FAILED` → يُعرض رسالة خطأ مع زر "Retry"
- `CANCELLED` → "Transfer cancelled"
- `REJECTED` → "File declined"

**شريط التقدم:**
```kotlin
// LinearProgressIndicator من MD3
LinearProgressIndicator(
    progress = { transferredBytes.toFloat() / totalBytes },
    modifier = Modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.primary
)
```

### 12.3 `ChatScreen` — التحسينات المطلوبة

1. **دعم `FILE_TRANSFER` MessageType:** `MessageBubble` يجب أن يتعامل مع الـ state الجديدة ويُعرض `FileRequestCard` بدلاً من الـ text/image bubble.
2. **Typing Indicator:** مؤشر `isPeerTyping` موجود في `ChatUiState` — يجب عرضه كـ animated dots.
3. **Voice Message Recorder:** شريط التسجيل يظهر عند Long-Press على زر الميكروفون.
4. **Connection Status Banner:** شريط صغير يظهر عند فقدان الاتصال بالـ peer.
5. **scrollToBottom عند استقبال رسالة جديدة:** موجود جزئياً — يجب تحسينه ليعمل فقط إذا كان المستخدم في أسفل القائمة.

### 12.4 `DiscoveryScreen` — التحسينات المطلوبة

1. **`SignalMorphAvatar` يعمل بـ RSSI حقيقي** بدلاً من القيمة الثابتة `-55`. يتطلب إصلاح `estimateRssiFromAddress()` في `LanTransportImpl`.
2. **عرض الـ Transport Type** لكل peer: أيقونة صغيرة تُشير لـ WiFi/BT/etc.
3. **حالة الاتصال:** زر "Connect" → "Connecting..." → "Connected" بـ animation.
4. **WiFi Direct Peers:** عند إضافة `WifiDirectTransportImpl`، يجب عرض الـ WFD peers في نفس الشاشة.

### 12.5 Onboarding Screens — جديدة

يجب إنشاء 3 Composables جديدة في `ui/screens/onboarding/`:
- `OnboardingWelcomeScreen.kt`
- `OnboardingProfileScreen.kt`
- `OnboardingPermissionsScreen.kt`

وإضافة routes جديدة في `MeshifyNavDisplay.kt`:
```kotlin
// في NavGraph — يُضاف في البداية قبل كل الـ routes:
val startDestination = if (isOnboardingCompleted) "home" else "onboarding_welcome"

composable("onboarding_welcome") { OnboardingWelcomeScreen(onNext = { navController.navigate("onboarding_profile") }) }
composable("onboarding_profile") { OnboardingProfileScreen(onNext = { navController.navigate("onboarding_permissions") }) }
composable("onboarding_permissions") { OnboardingPermissionsScreen(onDone = { navController.navigate("home") { popUpTo(0) } }) }
```

---

## 13. الإعدادات والتخصيص

### 13.1 الإعدادات الموجودة حالياً ✅

| الإعداد | الـ Key في DataStore | النوع | القيمة الافتراضية |
|--------|---------------------|------|----------------|
| Display Name | `display_name` | String | "Meshify User" |
| Theme Mode | `theme_mode` | String (Enum) | SYSTEM |
| Dynamic Color | `dynamic_color` | Boolean | true |
| Haptic Feedback | `haptic_feedback` | Boolean | true |
| Network Visibility | `network_visible` | Boolean | true |
| Avatar Hash | `avatar_hash` | String? | null |
| Shape Style | `shape_style` | String (Enum) | CIRCLE |
| Motion Preset | `motion_preset` | String (Enum) | STANDARD |
| Motion Scale | `motion_scale` | Float | 1.0f |
| Font Family | `font_family` | String (Enum) | ROBOTO |
| Custom Font URI | `custom_font_uri` | String? | null |
| Bubble Style | `bubble_style` | String (Enum) | ROUNDED |
| Visual Density | `visual_density` | Float | 1.0f |
| Seed Color | `seed_color` | Int | 0xFF006D68 |

### 13.2 الإعدادات الجديدة المطلوبة

```kotlin
// يُضاف في ISettingsRepository وـ SettingsRepository:

// إعدادات نقل الملفات:
val autoDownloadOnLan: Flow<Boolean>          // تحميل تلقائي على LAN فقط
val autoDownloadMaxSizeMb: Flow<Int>          // الحد الأقصى للتحميل التلقائي (MB)
val autoDownloadImages: Flow<Boolean>         // تحميل الصور تلقائياً
val autoDownloadVideos: Flow<Boolean>         // تحميل الفيديوهات تلقائياً
val parallelTransferCount: Flow<Int>          // عدد النقل المتوازي (1-5)
val dynamicBufferEnabled: Flow<Boolean>       // تحسين حجم الـ Buffer تلقائياً

// إعدادات الشبكة:
val preferredTransport: Flow<String>          // "AUTO" / "LAN" / "WIFI_DIRECT" / "BLUETOOTH"
val heartbeatIntervalSeconds: Flow<Int>       // فترة الـ Heartbeat (1-10 ثوانٍ)

// إعدادات الأمان:
val requireEncryption: Flow<Boolean>          // إلزامية التشفير (default: true)

// إعدادات التطبيق:
val isOnboardingCompleted: Flow<Boolean>      // هل اكتمل الـ Onboarding؟
val notificationsEnabled: Flow<Boolean>       // تفعيل الإشعارات
val messageNotificationSound: Flow<Boolean>   // صوت الإشعار
```

---

## 14. الأذونات — Permissions

### 14.1 الأذونات الموجودة في AndroidManifest.xml ✅

```xml
<!-- شبكة -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Discovery Android 13+ -->
<uses-permission android:name="android.permission.NEARBY_WIFI_DEVICES"
    android:usesPermissionFlags="neverForLocation" tools:targetApi="s"/>

<!-- Discovery Android < 13 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Foreground Service -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />

<!-- Notifications + Haptics -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.VIBRATE" />
```

### 14.2 أذونات جديدة تُضاف للـ Manifest

```xml
<!-- Bluetooth Android < 12 -->
<uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" android:maxSdkVersion="30" />

<!-- Bluetooth Android 12+ -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN"
    android:usesPermissionFlags="neverForLocation" tools:targetApi="s"/>
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_ADVERTISE" />

<!-- WiFi Direct (إضافي) -->
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

<!-- Audio Recording (للـ Voice Messages) -->
<uses-permission android:name="android.permission.RECORD_AUDIO" />
```

### 14.3 Runtime Permissions (يُطلب في الـ Onboarding)

| الإذن | الوقت | السبب |
|------|-------|------|
| `ACCESS_FINE_LOCATION` أو `NEARBY_WIFI_DEVICES` | عند أول تشغيل | اكتشاف الأجهزة على الشبكة |
| `POST_NOTIFICATIONS` | عند أول تشغيل | إشعارات الرسائل الجديدة |
| `RECORD_AUDIO` | عند أول استخدام Voice | تسجيل الرسائل الصوتية |
| `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` | عند تفعيل BT Transport | اكتشاف الأجهزة عبر البلوتوث |

---

## 15. خارطة الطريق والمراحل

### المرحلة 1 — القلب النابض (The Foundation) 🔴

**المدة المقدّرة:** 2-3 أسابيع  
**الهدف:** الـ codebase قابل للعمل بشكل آمن ومستقر عبر LAN.

1. توسعة `Payload` model (إضافة destinationId, ttl, routePath).
2. إضافة أنواع الـ `PayloadType` الجديدة (FILE_REQ, FILE_ACC, FILE_META, CHUNK_DATA, TRANSFER_ACK, VOICE, FILE_REJ, TRANSFER_CANCEL).
3. كتابة `PayloadSerializer` (Binary Encoding).
4. بناء `PayloadOrchestrator` مع Sliding Window Protocol.
5. إضافة `file_transfers` Room table + `FileTransferDao` + Migration v4.
6. إضافة `peer_identities` Room table.
7. استبدال `fallbackToDestructiveMigration` بـ proper migrations.
8. تحديث `ChatRepositoryImpl` لمعالجة الـ Payload types الجديدة.
9. بناء `AesGcm` helper.
10. بناء `PeerKeyStore`.
11. تحديث `Handshake` data class لتضمين `publicKey`.
12. بناء `EncryptedTransportDecorator`.
13. تحديث `LanTransportImpl.handleHandshake()` لاستخراج وتبادل الـ ECDH public keys.

### المرحلة 2 — واجهة النقل (Transfer UI) 🟠

**المدة المقدّرة:** 1-2 أسابيع  
**الهدف:** المستخدم يرى تقدم نقل الملفات ويتحكم فيها.

1. بناء `FileRequestCard` Composable (RTS Card).
2. تحديث `MessageBubble` لدعم `FILE_TRANSFER` MessageType بكل states.
3. ربط `TransferProgress` SharedFlow بالـ ViewModel.
4. إضافة شريط التقدم في الـ bubble.
5. إضافة أزرار Accept/Decline/Cancel.
6. تحديث `ChatViewModel` للتعامل مع file transfers.
7. تحديث `IChatRepository` + `ChatRepositoryImpl` بالـ functions الجديدة.

### المرحلة 3 — Transports الإضافية 🟠

**المدة المقدّرة:** 2-3 أسابيع  
**الهدف:** الـ app يعمل عبر WiFi Direct وـ Bluetooth.

1. بناء `WifiDirectTransportImpl` بالكامل.
2. بناء `BluetoothTransportImpl` بالكامل.
3. بناء `CompositeTransport`.
4. بناء `HeartbeatService`.
5. بناء `PeerStore`.
6. تحديث `AppContainer` لاستخدام `compositeTransport`.
7. إصلاح RSSI الحقيقي في `LanTransportImpl`.

### المرحلة 4 — ميزات المستخدم (User Features) 🟡

**المدة المقدّرة:** 2 أسابيع  
**الهدف:** تجربة مستخدم كاملة وسلسة.

1. بناء Onboarding Flow (3 screens).
2. إضافة `isOnboardingCompleted` DataStore key.
3. تحديث Navigation Graph.
4. بناء Voice Messaging (recorder + player).
5. إضافة `RECORD_AUDIO` permission request.
6. بناء Global Search (Chats + In-Message).
7. إضافة DAO queries للـ search.
8. بناء `MediaGalleryScreen`.
9. إضافة إعدادات Auto-Download + Parallel Transfer.

### المرحلة 5 — الصقل والتحسين (Polish) 🟢

**المدة المقدّرة:** 1-2 أسابيع  
**الهدف:** الـ app يبدو ويتصرف كـ production app.

1. تحسين `DiscoveryScreen` (RSSI حقيقي + Transport type badge).
2. إضافة Typing Indicator في `ChatScreen`.
3. إضافة Connection Status Banner.
4. تحسين Scroll behavior في `ChatScreen`.
5. Notification improvements (reply from notification).
6. Battery optimization (تحسين الـ Heartbeat interval).
7. تحسين الـ error handling وـ user-facing error messages.

### المرحلة 6 — المستقبل (Future) 🟢

1. Full Protobuf Migration.
2. Mesh Relay (TTL-based multi-hop routing).
3. Multi-Source Swarm Download.
4. Group Chats.
5. Forward Secrecy (Key Rotation).

---

## 16. قائمة مهام الذكاء الاصطناعي — AI ToDo Checklist

> **تعليمات للذكاء الاصطناعي المنفِّذ:** عند إتمام أي مهمة، ضع `[x]` بجانبها مع شرح موجز في السطر التالي لما قُمتَ به بالضبط. لا تضع `[x]` إلا بعد التحقق الفعلي من أن الكود يُترجم بدون أخطاء ويحقق الهدف المكتوب.

---

### 🔴 المرحلة 1 — القلب النابض

#### 1.1 توسعة Payload Model
- [ ] **تحديث `domain/model/Payload.kt`** — إضافة `destinationId: String = ""` وـ `ttl: Int = 3` وـ `routePath: List<String> = emptyList()` للـ `Payload` data class.
  > _ما تم: ..._
- [ ] **إضافة PayloadTypes الجديدة** — إضافة `VOICE, FILE_REQ, FILE_ACC, FILE_REJ, FILE_META, CHUNK_DATA, TRANSFER_ACK, TRANSFER_CANCEL` لـ `Payload.PayloadType` enum.
  > _ما تم: ..._
- [ ] **إضافة `FileRequest` data class** — إنشاء `@Serializable data class FileRequest(sessionId, fileName, fileSizeBytes, sha256Hash, mimeType, blurThumbnailBase64?)` في `domain/model/`.
  > _ما تم: ..._
- [ ] **إضافة `FileMeta` data class** — إنشاء `@Serializable data class FileMeta(sessionId, totalChunks, chunkSizeBytes, fileName, sha256Hash)` في `domain/model/`.
  > _ما تم: ..._
- [ ] **إضافة `TransferAck` data class** — إنشاء `@Serializable data class TransferAck(sessionId, receivedChunkIndices: List<Int>)` في `domain/model/`.
  > _ما تم: ..._
- [ ] **إضافة `TransferProgress` data class** — إنشاء data class في `domain/model/` مع: sessionId, peerId, direction, totalBytes, transferredBytes, speedBytesPerSec, estimatedSecondsRemaining, isComplete, isFailed, failureReason?.
  > _ما تم: ..._
- [ ] **إضافة `TransferDirection` و `TransferStatus` enums** في `domain/model/`.
  > _ما تم: ..._
- [ ] **تحديث `Handshake` data class** — إضافة `publicKey: String? = null` لـ `Handshake` في `domain/model/Payload.kt`.
  > _ما تم: ..._

#### 1.2 Binary Serialization
- [ ] **إنشاء `PayloadSerializer` object** — ملف جديد `network/serializer/PayloadSerializer.kt` يُنفّذ `encode(Payload): ByteArray` وـ `decode(ByteArray): Payload` كما هو موضح في §5.4.
  > _ما تم: ..._
- [ ] **تحديث `SocketManager`** — استبدال أي JSON serialization بـ `PayloadSerializer.encode/decode`.
  > _ما تم: ..._

#### 1.3 Database Migration v4
- [ ] **إنشاء `FileTransferEntity`** — إنشاء `data/local/entity/FileTransferEntity.kt` بالـ schema الكامل من §9.3.
  > _ما تم: ..._
- [ ] **إنشاء `FileTransferDao`** — إنشاء `data/local/dao/FileTransferDao.kt` بكل الـ queries المطلوبة.
  > _ما تم: ..._
- [ ] **إنشاء `PeerIdentityEntity`** — إنشاء `data/local/entity/PeerIdentityEntity.kt`.
  > _ما تم: ..._
- [ ] **إنشاء `PeerIdentityDao`** — إنشاء `data/local/dao/PeerIdentityDao.kt`.
  > _ما تم: ..._
- [ ] **كتابة `MIGRATION_3_4`** — كتابة `MIGRATION_3_4` object في `MeshifyDatabase.kt` يُنشئ جدولَي `file_transfers` وـ `peer_identities`.
  > _ما تم: ..._
- [ ] **استبدال `fallbackToDestructiveMigration`** في `AppContainer.kt` بـ `.addMigrations(MIGRATION_3_4)`.
  > _ما تم: ..._
- [ ] **إضافة الجداول الجديدة** لقائمة `entities` في `@Database` annotation في `MeshifyDatabase.kt` ورفع الـ `version` لـ 4.
  > _ما تم: ..._
- [ ] **إضافة `fileTransferDao()` و `peerIdentityDao()`** كـ abstract functions في `MeshifyDatabase`.
  > _ما تم: ..._

#### 1.4 طبقة التشفير
- [ ] **إنشاء `AesGcm` object** — إنشاء `network/security/AesGcm.kt` مع `encrypt(key, plaintext): ByteArray` وـ `decrypt(key, ciphertext): ByteArray` بـ AES-256-GCM.
  > _ما تم: ..._
- [ ] **إنشاء `PeerKeyStore`** — إنشاء `network/security/PeerKeyStore.kt` كـ `ConcurrentHashMap<String, ByteArray>` مع `storeKeyFromHandshake()` وـ `getAesKey()` وـ `removeKey()` (انظر §7.5).
  > _ما تم: ..._
- [ ] **إنشاء `EncryptedTransportDecorator`** — إنشاء `network/security/EncryptedTransportDecorator.kt` الذي يُشفَّر/يفك تشفير `payload.data` كما هو موضح في §7.4.
  > _ما تم: ..._
- [ ] **تحديث `LanTransportImpl.handleHandshake()`** — استخراج `publicKey` من الـ `Handshake` payload → إرسال ECDH public key الخاص بالجهاز → تخزين الـ shared AES key في `PeerKeyStore` عبر `storeKeyFromHandshake()`.
  > _ما تم: ..._

#### 1.5 PayloadOrchestrator
- [ ] **إنشاء `PayloadOrchestrator` class** — إنشاء `network/orchestrator/PayloadOrchestrator.kt` بكل الـ functions المطلوبة من §6.2.
  > _ما تم: ..._
- [ ] **تنفيذ `sendFile()`** — قراءة الملف chunk بـ chunk + SHA-256 + إرسال FILE_REQ + انتظار FILE_ACC + إرسال FILE_META + Sliding Window.
  > _ما تم: ..._
- [ ] **تنفيذ Sliding Window Protocol** — Window Size=10، ACK timeout=5s، إعادة الإرسال التلقائي للـ chunks الفاقدة.
  > _ما تم: ..._
- [ ] **تنفيذ `handleIncomingFileRequest()`** — استقبال FILE_REQ + حفظ في `file_transfers` table + إصدار event للـ UI.
  > _ما تم: ..._
- [ ] **تنفيذ `handleChunkData()`** — كتابة الـ chunk في temp file على الـ disk + تحديث `receivedChunksBitmap` + إرسال `TRANSFER_ACK` كل 10 chunks.
  > _ما تم: ..._
- [ ] **تنفيذ التحقق من الملف** — بعد استقبال آخر chunk: إعادة حساب SHA-256 ومقارنته بـ `FILE_META.sha256Hash`. إذا طابق → نقل للمسار النهائي. إذا لم يطابق → حذف وطلب إعادة الإرسال.
  > _ما تم: ..._
- [ ] **تنفيذ Resume Logic** — عند استقبال FILE_REQ لملف موجود في `file_transfers` بنفس hash → الاستئناف من آخر chunk مؤكَّد.
  > _ما تم: ..._
- [ ] **إصدار `TransferProgress` events** — `SharedFlow<TransferProgress>` يُحدَّث بعد كل chunk تحويل.
  > _ما تم: ..._

#### 1.6 تحديث AppContainer وـ ChatRepository
- [ ] **تحديث `ChatRepositoryImpl`** — إضافة معالجة الـ PayloadTypes الجديدة في `handleIncomingPayload()` ودلغتها لـ `PayloadOrchestrator`.
  > _ما تم: ..._
- [ ] **تحديث `IChatRepository`** — إضافة الـ functions الجديدة من §10.1.
  > _ما تم: ..._
- [ ] **إضافة `sendFile()` في `ChatRepositoryImpl`** — تستدعي `PayloadOrchestrator.sendFile()`.
  > _ما تم: ..._
- [ ] **إضافة `acceptFileRequest()`** في `ChatRepositoryImpl`.
  > _ما تم: ..._
- [ ] **تحديث `AppContainer`** — ربط `PayloadOrchestrator`، `PeerKeyStore`، `EncryptedTransportDecorator`، وتمريرهم للـ `ChatRepositoryImpl`.
  > _ما تم: ..._

---

### 🟠 المرحلة 2 — واجهة النقل (Transfer UI)

- [ ] **إنشاء `FileRequestCard` Composable** — ملف `ui/components/FileRequestCard.kt` يعرض الـ states كلها: AWAITING, IN_PROGRESS (مع شريط تقدم + سرعة)، COMPLETED, FAILED, CANCELLED, REJECTED.
  > _ما تم: ..._
- [ ] **تحديث `MessageBubble`** — إضافة case لـ `MessageType.FILE_TRANSFER` يُعرض `FileRequestCard` مع الـ state الصحيح.
  > _ما تم: ..._
- [ ] **إضافة `MessageType.VOICE` و `FILE_TRANSFER`** لـ `MessageType` enum.
  > _ما تم: ..._
- [ ] **تحديث `ChatViewModel`** — إضافة `transferProgress: StateFlow<TransferProgress?>` وـ functions لـ acceptFile/rejectFile/cancelTransfer.
  > _ما تم: ..._
- [ ] **ربط `TransferProgress` بالـ UI** في `ChatScreen` — عرض شريط التقدم داخل الـ message bubble.
  > _ما تم: ..._
- [ ] **إضافة File picker** في `ChatScreen` — زر لاختيار ملف من المعرض أو من الـ storage → استدعاء `sendFile()`.
  > _ما تم: ..._

---

### 🟠 المرحلة 3 — Transports الإضافية

- [ ] **بناء `WifiDirectTransportImpl`** — `network/wifidirect/WifiDirectTransportImpl.kt` بالتدفق الكامل من §4.4.
  > _ما تم: ..._
- [ ] **بناء `BluetoothTransportImpl`** — `network/bluetooth/BluetoothTransportImpl.kt` بـ RFCOMM Classic Bluetooth.
  > _ما تم: ..._
- [ ] **بناء `CompositeTransport`** — `network/composite/CompositeTransport.kt` بالمنطق من §4.2.
  > _ما تم: ..._
- [ ] **بناء `HeartbeatService`** — `network/service/HeartbeatService.kt` بـ Ping/Pong كل 3 ثوانٍ.
  > _ما تم: ..._
- [ ] **تحديث `AppContainer`** — استخدام `CompositeTransport` بدلاً من `lanTransport` مباشرة في `ChatRepositoryImpl`.
  > _ما تم: ..._
- [ ] **إضافة Bluetooth permissions** في `AndroidManifest.xml`.
  > _ما تم: ..._
- [ ] **إصلاح `estimateRssiFromAddress()`** في `LanTransportImpl` لاستخدام `WifiManager.calculateSignalLevel()` الحقيقي.
  > _ما تم: ..._
- [ ] **إضافة `transferProgress` SharedFlow** لـ `IMeshTransport` interface وتنفيذه في كل الـ transports.
  > _ما تم: ..._

---

### 🟡 المرحلة 4 — ميزات المستخدم

- [ ] **إنشاء `OnboardingWelcomeScreen`** — شاشة ترحيب بـ MD3E shapes animations وزر "Get Started".
  > _ما تم: ..._
- [ ] **إنشاء `OnboardingProfileScreen`** — حقل الاسم الإلزامي + اختيار Avatar اختياري.
  > _ما تم: ..._
- [ ] **إنشاء `OnboardingPermissionsScreen`** — عرض وطلب الـ permissions الضرورية.
  > _ما تم: ..._
- [ ] **إضافة `isOnboardingCompleted`** لـ `ISettingsRepository` وـ `SettingsRepository`.
  > _ما تم: ..._
- [ ] **تحديث Navigation Graph** في `MeshifyNavDisplay.kt` لدعم Onboarding flow.
  > _ما تم: ..._
- [ ] **بناء Voice Recorder** في `ChatScreen` — Long-press للتسجيل، swipe-to-cancel، haptic feedback.
  > _ما تم: ..._
- [ ] **بناء Voice Player Composable** — waveform أو progress bar مع play/pause وعداد الوقت.
  > _ما تم: ..._
- [ ] **إضافة `RECORD_AUDIO` permission** في `AndroidManifest.xml` + runtime request في Onboarding.
  > _ما تم: ..._
- [ ] **إضافة Global Search في `RecentChatsScreen`** — search bar قابل للإخفاء يُفلتر المحادثات.
  > _ما تم: ..._
- [ ] **إضافة In-Chat Search في `ChatScreen`** — search icon في TopAppBar + highlight للنتائج.
  > _ما تم: ..._
- [ ] **إضافة search DAO queries** في `MessageDao` وـ `ChatDao`.
  > _ما تم: ..._
- [ ] **إنشاء `MediaGalleryScreen`** — شاشة بـ 4 tabs: Images, Videos, Files, Voice.
  > _ما تم: ..._
- [ ] **إضافة Auto-Download Settings** — switches في `SettingsScreen` لـ autoDownloadOnLan، autoDownloadMaxSizeMb، autoDownloadImages، autoDownloadVideos.
  > _ما تم: ..._
- [ ] **إضافة Parallel Transfer Setting** — slider من 1 إلى 5 في `SettingsScreen`.
  > _ما تم: ..._
- [ ] **تحديث `ISettingsRepository` + `SettingsRepository`** بكل الإعدادات الجديدة من §13.2.
  > _ما تم: ..._
- [ ] **تطبيق Auto-Download Logic** في `PayloadOrchestrator` — فحص الـ settings قبل قبول FILE_REQ تلقائياً.
  > _ما تم: ..._

---

### 🟡 المرحلة 5 — الصقل

- [ ] **إضافة Typing Indicator** في `ChatScreen` — dots animation تظهر عند `uiState.isPeerTyping == true`.
  > _ما تم: ..._
- [ ] **إضافة Connection Status Banner** في `ChatScreen` — شريط أحمر/أصفر يظهر عند انقطاع الاتصال.
  > _ما تم: ..._
- [ ] **تحسين Scroll behavior** في `ChatScreen` — لا يُطاع `scrollToBottom` إلا إذا كان المستخدم في آخر 3 رسائل.
  > _ما تم: ..._
- [ ] **تحديث `DiscoveryScreen`** — إضافة Transport Type badge (WiFi/BT/etc) لكل peer.
  > _ما تم: ..._
- [ ] **إضافة DiscoveryScreen WiFi Direct peers** — عند اكتمال `WifiDirectTransportImpl`.
  > _ما تم: ..._
- [ ] **تحسين Battery usage** — تقليل discovery interval بعد أول 2 دقيقة بدون peers جديدة.
  > _ما تم: ..._

---

### 🟢 المرحلة 6 — المستقبل (Future — لا تُنفَّذ الآن)

- [ ] Full Protobuf Migration (إضافة `com.google.protobuf` plugin + `.proto` schema)
- [ ] Mesh Relay — إضافة TTL decrement + packet forwarding logic في `CompositeTransport`
- [ ] Multi-Source Swarm Download — Manifest System + Parallel Download من أكثر من peer
- [ ] Group Chats — schema جديد + `GroupEntity` + منطق broadcast
- [ ] Forward Secrecy — Key Rotation كل N رسالة

---

*نهاية الوثيقة — PRD Meshify MSTE v3.0*
*آخر تحديث: 10 مارس 2026*
