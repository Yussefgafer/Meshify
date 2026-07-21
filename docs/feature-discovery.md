# `:feature:discovery` — اكتشاف الأجهزة النظيرة

**الغرض:** اكتشاف الأجهزة النظيرة (peers) على الشبكة المحلية وعبر BLE. تعرض قائمة بالأجهزة المكتشفة مع معلومات الاتصال وجودة الإشارة، وتنتقل إلى شاشة المحادثة لكل جهاز.

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:data`, `:core:network`, `:core:ui`, `:core:common` + Compose، material3، Coil 3، lifecycle، Hilt.

## الملفات

جميع المسارات نسبة إلى `feature/discovery/src/main/java/com/p2p/meshify/feature/discovery/`:

| الملف | المحتوى |
|---|---|
| `DiscoveryScreen.kt` | الشاشة + كل الـ Composables المساعدة (PeerList, PeerListItem, TransportBadge, SignalStrengthIndicator, EmptyDiscoveryState, WifiDisabledState, ErrorState). |
| `DiscoveryViewModel.kt` | ViewModel مع `DiscoveryUiState` data class — يُدير الحالة عبر `TransportManager`. |

## الشاشات والمكونات

- **`DiscoveryScreen`** (المسار `Screen.Discovery`): `Scaffold` + `TopAppBar` (رجوع + تحديث) + `SnackbarHost` + `LinearProgressIndicator` + `PeerList` (LazyColumn). `onPeerClick` يأخذ `PeerDevice` وينتقل إلى `Screen.Chat(peer.id, peer.name)`.
- **`TransportBadge`** — شارة نوع النقل (LAN/BLE/BOTH) عبر أيقونة Wifi/Bluetooth.
- **`SignalStrengthIndicator`** — 3 أشرطة RSSI (قوي/متوسط/ضعيف/غير متصل).
- **`WifiDisabledState`** — شاشة خاصة عند تعطيل Wi-Fi (زر "Open Wi-Fi Settings") بدل القائمة الفارغة.

## `DiscoveryViewModel`

- `uiState: StateFlow<DiscoveryUiState>` يحوي: `discoveredPeers`, `isSearching`, `isRefreshing`, `errorMessage`, `isWifiEnabled`, `canDiscover`.
- أفعال: `refresh()` (إيقاف/إعادة تشغيل الاكتشاف)، `checkWifiState()` (عبر `WifiStateChecker`)، `observeTransportEvents()` (جمع `TransportEvent`).
- يُنشأ **يدوياً** في `MainActivity` (وليس عبر Hilt) لأنه يحتاج `TransportManager` و `WifiStateChecker`.

## قرارات تقنية

- `MutableMap<String, PeerDevice>` لـ O(1) lookup بدل `indexOfFirst`.
- دوال دمج: `mergeTransportType()` / `mergeRssi()` / `mergeName()` لدمج بيانات الجهاز من LAN + BLE.
- ثوابت: `DISCOVERY_CLEANUP_DELAY_MS = 200L`، `DISCOVERY_SCAN_DELAY_MS = 2000L`.
