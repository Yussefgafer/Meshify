# `:core:network` — طبقة النقل الشبكي (Android Library)

**الغرض:** طبقة النقل الشبكي للتطبيق — اكتشاف الأقران ونقل البيانات دون اتصال بالإنترنت. تُعرّف واجهة `IMeshTransport` المجرّدة وتوفّر تطبيقين: `LanTransportImpl` (TCP/IP + mDNS/NSD) و `BleTransportImpl` (BLE GATT).

**البناء (`build.gradle.kts`):** `namespace = "com.p2p.meshify.core.network"`. تعتمد على `:core:common`, `:core:domain`. تستخدم `kotlinx.coroutines.core`, `kotlinx.serialization.json`, `androidx.core.ktx`.

## الملفات الرئيسية

جميع المسارات نسبة إلى `core/network/src/main/java/com/p2p/meshify/core/network/`:

| الملف | المحتوى |
|---|---|
| `base/IMeshTransport.kt` | واجهة النقل المجرّدة: `start()`, `stop()`, `sendPayload()`, `events: Flow<TransportEvent>`, `onlinePeers: StateFlow<Set<String>>`. |
| `base/TransportCapability.kt` | enum `TransportCapability` (FILE_TRANSFER, LOW_LATENCY, HIGH_BANDWIDTH, OFFLINE, MESH_NETWORKING...). |
| `base/TransportEvent.kt` | sealed class `TransportEvent` (DeviceDiscovered, DeviceLost, ConnectionEstablished, ConnectionLost, PayloadReceived, Error). |
| `lan/LanTransportImpl.kt` | نقل LAN كامل عبر mDNS/NSD لاكتشاف الأقران و TCP/IP عبر `SocketManager`. |
| `lan/SocketManager.kt` | مدير مآخذ TCP مع تجمع اتصالات، `KeepAliveManager`، معالجة الاتصالات الواردة بالتوازي. |
| `lan/ConnectionPool.kt` | تجمع اتصالات بـ `Semaphore(100)` وأقفال `Mutex` لكل اتصال؛ تنظيف خامل بعد 5 دقائق. |
| `lan/PooledSocket.kt` | `data class PooledSocket` — غلاف المأخذ مع بيانات وصفية. |
| `lan/SocketFactory.kt` | إنشاء وتكوين ServerSocket/Socket بمهلات (اتصال 5ث، قراءة 30ث). |
| `lan/KeepAliveManager.kt` | نبضات PING كل 60 ثانية، كشف الاتصالات الميتة عبر مهلة 2 ثانية. |
| `ble/BleAdvertiser.kt` | نشر هذا الجهاز عبر BLE (`BluetoothLeAdvertiser` + `AppConfig.BLE_SERVICE_UUID`) مع تشفير `peerId`. |
| `ble/BleScanner.kt` | مسح أجهزة Meshify عبر `BluetoothLeScanner` (تصفية حسب UUID)، يصدر `BleDiscoveredDevice` عبر `Flow`. |
| `ble/BleGattServer.kt` | خادم GATT للاستقبال مع خصائص RX/TX وإشعارات. |
| `ble/BleGattClient.kt` | عميل GATT للاتصال بالأقران مع `BleGattConnection` لإدارة كل اتصال وتفاوض MTU. |
| `ble/BleConnectionPool.kt` | إدارة اتصالات BLE بحد أقصى (`AppConfig.BLE_MAX_CONNECTIONS`) وتنظيف خامل. |
| `ble/BlePayloadSerializer.kt` | تجزئة `Payload` لقطع متوافقة مع BLE MTU وإعادة تجميعها (رأس 12 بايت: totalSize, chunkIndex, totalChunks). |
| `ble/BleTransportImpl.kt` | نقل BLE كامل ينسّق المكوّنات السابقة مع `sendLock` (Mutex). |
| `TransportManager.kt` | المدير المركزي: يسجّل النقلات (`registerTransport`)، يختار الأفضل حسب `TransportMode`، يمزج أحداث `Flow`. `createDefault()` يسجّل `LanTransportImpl`. |
| `ProgressFileReader.kt` | قراءة ملف مع إصدار تقدم (0–100) عبر `StateFlow<Int>`. |
| `WifiStateCheckerImpl.kt` | تنفيذ `WifiStateChecker` عبر `WifiManager.isWifiEnabled`. |

## قرارات تقنية ظاهرة

- **تجريد النقل:** كل النقلات تنفّذ `IMeshTransport`، ما يسمح بإضافة نقلات جديدة (Wi-Fi Direct, DHT, UWB) بسهولة.
- **اكتشاف LAN:** عبر Android `NsdManager` (وليس JmDNS). نوع الخدمة `AppConfig.SERVICE_TYPE`؛ اسم الخدمة `Meshify_` + UUID (peerId).
- **بروتوكول TCP:** حمولة [4 بايت طول] + [N بايت بيانات] تُسلَّسل عبر `PayloadSerializer`. مهلات: اتصال 5ث، قراءة 30ث، كتابة 5ث.
- **تفاوض BLE MTU:** `gatt.requestMtu(AppConfig.BLE_MTU_SIZE)`، تجزئة يدوية عبر `BlePayloadSerializer` (حد الحمولة `BLE_MTU_SIZE - 12`).
- **Multi-path:** `TransportManager.selectBestTransport()` يدعم `MULTI_PATH` (يرسل عبر LAN + BLE معاً).
- **كشف الأقران الميتة:** `LanTransportImpl` يتتبع الإخفاقات المتتالية (حد 5) ثم يصدر `DeviceLost`؛ و `KeepAliveManager` عبر PING/PONG بمهلة 2 ثانية.
- **السلامة في التزامن:** `Mutex` في `BleTransportImpl.sendLock`، و `peerMapMutex`/`failedCountsMutex` في `LanTransportImpl`، و `connectionLocks` في `ConnectionPool`.
- **حجم المخزن:** `AppConfig.DEFAULT_BUFFER_SIZE = 32KB` في `SocketManager` و `ProgressFileReader`.
