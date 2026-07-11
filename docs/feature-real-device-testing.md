# `:feature:real-device-testing` — اختبار الأجهزة الحقيقية

**الغرض:** وحدة اختبار شبكي شامل للأجهزة الحقيقية عبر LAN و BLE. تدير اختبارات: اكتشاف، ping، رسالة، ملف، زمن وصول، ورحلة ذهاب-إياب (round-trip)، مع آلة حالات من 8 مراحل.

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:data`, `:core:common`, `:core:network`, `:core:ui`. **لا Hilt**؛ الـ ViewModel يُنشأ عبر `factory` يدوي.

## الملفات (23 ملفاً، 5 حزم)

مسارات نسبة إلى `feature/real-device-testing/src/main/java/com/p2p/meshify/feature/realdevicetesting/`:

### `adapter/` (4)
| الملف | المحتوى |
|---|---|
| `adapter/TransportTestAdapter.kt` | واجهة تجريد آمنة فوق `IMeshTransport`: `initialize()`, `discoverPeers(timeout)`, `sendTestPayload(peerId, payloadType, testData)`, `shutdown()` + `data class TestSendResult`. |
| `adapter/LanTransportTestAdapter.kt` | يلف `LanTransportImpl` + `SocketManager`؛ يبني مكدس LAN مستقلاً (غير مشترك مع الإنتاج). |
| `adapter/BleTransportTestAdapter.kt` | يبني مكدس BLE مستقلاً (`BleAdvertiser`, `BleScanner`, `BleGattServer`, `BleGattClient`)؛ تجزئة للـ MTU عبر `sendLock`. |
| `adapter/TestRegistry.kt` | `object TestRegistry.INSTANCE` — يسجّل LAN (+ BLE إن توفرت)، يوفّر `getTransport()`, `getAvailableTransports()`, `shutdownAll()`. |

### `engine/` (5)
| الملف | المحتوى |
|---|---|
| `engine/TestEngine.kt` | المحرّك: ينفّذ 6 أنواع اختبار بالتتابع عبر `runners` map. يصدر النتائج عبر `OnTestResultUpdate`. ينظّف بيانات الاختبار بعد كل اختبار. |
| `engine/TestEngineConfig.kt` | كل المهلات/الأحجام: `DISCOVERY_TIMEOUT_MS=15000`, `PING_TIMEOUT_MS=10000`, `MESSAGE_TIMEOUT_MS=30000`, `FILE_TIMEOUT_MS=60000`, `LATENCY_*`، `ROUNDTRIP_TIMEOUT_MS=15000`, `TEST_FILE_SIZE_BYTES=1024`. |
| `engine/TestScenarioRunners.kt` | 6 runners: `DiscoveryTestRunner`, `PingTestRunner`, `MessageTestRunner`، `FileTestRunner`، `LatencyTestRunner` (10 pings)، `RoundTripTestRunner` (ECHO nonce). |
| `engine/TestResultLogger.kt` | يكتب `.log` مقروءاً في `test_logs/meshify_test_<timestamp>.log`. |
| `engine/TestDataCleaner.kt` | يحذف رسائل/محادثات الاختبار من Room ببادئة `test_target_<deviceId>`. |

### `model/` (4)
`DiscoveredPeer.kt` (مع `SignalLevel`, `SessionStatus`)، `TestResult.kt` (`TestStatus` = PENDING/RUNNING/PASSED/FAILED/TIMEOUT)، `TestScenario.kt`، `TestScenarioFactory.kt` (`createDefaults()` → 6 سيناريوهات).

### `preflight/` (2)
`PreFlightChecker.kt` (فحص أذونات + اتصال عبر `PermissionChecker`/`ConnectivityChecker`)، `PreFlightResult.kt` (`CheckStatus` = PASS/FAIL/SKIP).

### `ui/` (9)
`RealDeviceTestingUiState.kt` (آلة الحالات + 15 حدثاً)، `RealDeviceTestingViewModel.kt` (`factory` يدوي، `uiState` + `snackbarMessage`)، `RealDeviceTestScreen.kt` + 7 Composables خاصة (Initial, RunningPreflight, PreFlightDone, PreFlightFailed, NoPeersFound, RunningTests, TestsDone)، `TestTypeSelector.kt`, `TestProgressPanel.kt`, `TestResultsPanel.kt`, `DiscoveredPeerList.kt`, `PreFlightResultsCard.kt`.

## آلة الحالات (8 مراحل) — `RealDeviceTestingUiState`

| # | الحالة | المشغّل |
|---|---|---|
| 1 | `Initial` (data object) | — |
| 2 | `RunningPreflight(elapsedMs)` | `onEvent(RunPreflight)` |
| 3 | `PreFlightDone(...)` | اكتمال ما قبل الطيران بنجاح |
| 4 | `PreFlightFailed(...)` | فشل ما قبل الطيران (→ إعادة عبر `RunPreflight`) |
| 5 | `NoPeersFound(...)` | لا أقران مكتشفون |
| 6 | `RunningTests(...)` | مسح/بدء الاختبارات |
| 7 | `TestsDone(...)` | اكتمال الاختبارات (→ `RerunFailedTests` يعيد لـ 6) |

توفّر الخاصية المحسوبة `progressFraction` على الواجهة المختومة.

## تفاصيل LAN / BLE

- **LAN:** `LanTransportTestAdapter` يبني `SocketManager` + `LanTransportImpl` مستقلين؛ متاح دائماً (`isAvailable = true`).
- **BLE:** `BleTransportTestAdapter` يبني مكدساً مستقلاً؛ يتحقق `BluetoothAdapter.isEnabled`. إن لم يتوفر BLE يُسجّل LAN فقط.
- **الاكتشاف:** كلا النقلين يكتشفان الأقران بمهلة قابلة للضبط، وتُزال التكرارات بـ peer ID.

## مسار التنقل

`Screen.RealDeviceTesting` (data object) — مُوصولة في `MeshifyNavHost`. تُستدعى من `MainActivity` داخل `onDeveloperRoute`: Home → Settings → Developer → "Real Device Testing". **قابلة للوصول وقت التشغيل.**
