# `:app` — المُجمّع (Application)

**الغرض:** وحدة التطبيق التي تربط كل `:feature:*` عبر التنقل وتُهيّئ البيئة (Hilt، الشبكة، الخدمات، الاستقبال).

**البناء (`build.gradle.kts`):** `android.application` + `kotlin.compose` + `ksp` + `androidx.room` + `kotlin.serialization` + `hilt`.
- **compileSdk = 37، targetSdk = 36**، **minSdk = 26**، `applicationId = "com.p2p.meshify"`.
- **versionCode = 13**، **versionName = `1.1.3`**.
- **abiFilters = `arm64-v8a` فقط**.
- **resConfigs = `["en", "ar"]`**.
- **التوقيع:** Release عبر `meshify.jks` + متغيرات البيئة `KEYSTORE_PASSWORD`/`KEY_PASSWORD`.
- **Lint:** `abortOnError = false`، `checkReleaseBuilds = false`، `disable += "MissingTranslation"`.
- **Room schema:** `$projectDir/schemas`.
- **opt-ins:** `ExperimentalMaterial3Api`، `ExperimentalMaterial3ExpressiveApi`.
- **Release:** minify + shrink resources مفعّلة.

**الاعتماديات:** كل `:core:*` (common, domain, data, network, ui) + كل `:feature:*`.

## الملفات (7)

| الملف | المحتوى |
|---|---|
| `MainActivity.kt` | `@AndroidEntryPoint class MainActivity : ComponentActivity()` — نقطة الدخول. تربط `MeshifyNavHost` بكل مسارات الشاشات، تُدير سير الأذونات (onboarding + runtime)، تطبّق locale من DataStore، وتُنشئ ViewModels **يدوياً** (`viewModel(factory = ...)`) لكل مسار لأن Hilt لا يستطيع حقن معاملات معقدة (context/chatRepository/transportManager). |
| `MeshifyApp.kt` | `@HiltAndroidApp class MeshifyApp : Application(), SingletonImageLoader.Factory` — تهيئة: crash handler، `transportManager.startAllTransports()` + `startDiscoveryOnAll()`، جامع أحداث النقل العام (`handleIncomingPayload`)، مراقب BLE (تشغيل/إيقاف ديناميكي)، مراقب `transportMode`، تسجيل BLE ديناميكي عبر `Provider<BleTransportImpl>`، وضبط Coil 3 ImageLoader (25% RAM + 2% disk + OkHttp + crossfade). |
| `di/AppModule.kt` | `@Module @InstallIn(SingletonComponent)` — توفّر `MeshifyDatabase` (مع migrations 5→6 + 6→7)، `ISettingsRepository`، `IFileManager`، `NotificationHelper`، `SimplePeerIdProvider`، `StringResourceProvider`، `WifiStateChecker`، `TransportManager.createDefault()`. |
| `di/NetworkModule.kt` | توفّر `BleTransportImpl` (يقرأ `displayName` من الإعدادات عبر `runBlocking`). |
| `di/RepositoryModule.kt` | توفّر DAOs (`ChatDao`, `MessageDao`, `PendingMessageDao`)، `ChatRepositoryImpl`، وربط واجهة `IChatRepository`. |
| `receivers/ReplyReceiver.kt` | `BroadcastReceiver` للرد المضمّن من الإشعارات (`REPLY_ACTION`). أمان: توقيع HMAC، تحقق زمني (15 دقيقة)، فحص وجود المحادثة، rate limiting (10/دقيقة)، تعقيم الرسالة، exponential backoff (3 محاولات). |
| `service/MeshForegroundService.kt` | `Service` للحفاظ على الشبكة حية: multicast lock، جمع أحداث النقل، إغلاق حتمي بمهلة 3 ثوانٍ. قناة `mesh_service_channel`. |

## `MainActivity` بتفصيل

1. **`onCreate`:** `enableEdgeToEdge()` + تحميل locale من DataStore + فحص إكمال onboarding (إن أُكمِل يُستدعى `checkAndRequestPermissions()`) + Compose content يقرأ `themeMode`/`dynamicColor`، يحدد `startDestination` (Home أو Onboarding)، ويلفّ بـ `MeshifyTheme` + `CompositionLocalProvider(LocalPremiumHaptics)`.
2. **ربط المسارات:** Home (`RecentChatsViewModel` يدويًا بـ `app.chatRepository`)، Discovery (`DiscoveryViewModel` بـ `app.transportManager`+`app.wifiStateChecker`)، Chat (`ChatViewModel` بـ `SavedStateHandle`+`app.chatRepository`)، Settings (`SettingsViewModel` بـ `app.settingsRepository`، → Developer)، Developer (`DeveloperViewModel` بـ DAOs، → RealDeviceTesting / reset onboarding)، RealDeviceTesting (`RealDeviceTestingViewModel.factory`)، Onboarding (`OnboardingRoute` الخاصة بـ 3 صفحات).
3. **سير الأذونات:** ACCESS_WIFI_STATE/CHANGE_WIFI_STATE/CHANGE_WIFI_MULTICAST_STATE/ACCESS_NETWORK_STATE + POST_NOTIFICATIONS + NEARBY_WIFI_DEVICES (API 33+) + BLUETOOTH_SCAN/CONNECT/ADVERTISE (API 31+) + ACCESS_FINE_LOCATION (pre-S). عند الرفض الجزئي يُستدعى `startAppService()` (يعمل بوضع LAN-only).
4. **تبديل اللغة:** `applyLocale(lang)` → `resources.updateConfiguration(...)` ثم `activity.recreate()`.

## `MeshifyApp` بتفصيل

- `@Inject`: `chatRepository`, `transportManager`, `settingsRepository`, `wifiStateChecker`, `database`, `bleTransportProvider`.
- **`onCreate`:** تهيئة `Logger` + uncaught exception handler + تشغيل كل النقلات والاكتشاف + جامع أحداث `PayloadReceived` → `chatRepository.handleIncomingPayload()` + مراقبا `bleEnabled` و `transportMode`.
- **`onTerminate`:** إيقاف BLE وكل النقلات، إغلاق repository، إلغاء scope.
- **Coil:** `newImageLoader()` بـ OkHttp + 25% RAM cache + 2% disk cache + crossfade.
