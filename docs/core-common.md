# `:core:common` — الطبقة المشتركة (Android Library)

**الغرض:** مكتبة أدوات ومرافق مشتركة متاحة لكل الوحدات الأخرى. تحوي اللوجر، تسلسل الحمولات، أدوات الملفات والصور، فحص الأذونات والاتصال، مزوّد الهوية، وموارد النصوص المترجمة.

**البناء (`build.gradle.kts`):** `namespace = "com.p2p.meshify.core.common"`. تعتمد على `:core:domain`. تستخدم `kotlinx.serialization.json`، `kotlinx.coroutines.core`، `androidx.core.ktx`، `androidx.exifinterface`. تملك `res/` بملفات `strings.xml` (إنجليزي في `values/`، عربي في `values-ar/` — 612 و639 سطراً على التوالي).

## الملفات الرئيسية

جميع المسارات نسبة إلى `core/common/src/main/java/com/p2p/meshify/core/`:

| الملف | المحتوى |
|---|---|
| `util/Logger.kt` | `Logger` — أداة تسجيل مركزية (TAG + `LoggerWrapper`). تُعطّل في إصدارات الإنتاج وتستخدم `android.util.Log` مباشرة. |
| `util/FileUtils.kt` | `FileUtils` — قراءة البايت من URI، حساب SHA-256، حفظ البايت في التخزين الداخلي، التحقق من وجود ملف. |
| `util/PayloadSerializer.kt` | `PayloadSerializer` — تسلسل/إلغاء تسلسل `Payload`. Wire Format الإصدار 3 (V3) مع تحقق من الحدود (أقصى 10MB للبيانات). `DeserializeResult` sealed class (`Success | Error`). توافق عكسي مع V2. |
| `config/AppConfig.kt` | `AppConfig` — ثوابت البروتوكول: `DEFAULT_PORT = 8888`، UUIDs الخاصة بـ BLE، المهلات، حدود الحمولة (10MB)، حجم المخزن المؤقت `DEFAULT_BUFFER_SIZE = 32KB`. |
| `common/preflight/ConnectivityChecker.kt` | `ConnectivityChecker` — فحص شامل (Wi-Fi مفعّل، متصل، IPv4 صالح، منفذ محلي)؛ يرجع `ConnectivityResult.allPassed`. |
| `common/preflight/PermissionChecker.kt` | `PermissionChecker` — فحص أذونات Android (ACCESS_WIFI_STATE, NEARBY_WIFI_DEVICES, ACCESS_FINE_LOCATION)؛ يرجع `PermissionResult`. |
| `common/security/SimplePeerIdProvider.kt` | `SimplePeerIdProvider` — مزوّد هوية بسيط: UUID عشوائي في SharedPreferences (يستبدل `PeerIdentityManager` المعتمد على Keystore). |
| `common/util/HexUtil.kt` | `HexUtil` — تحويل سداسي عشري: `toHex()`, `toFingerprint()`, `toFingerprintSpaced()`، و `String.hexToByteArray()`. |
| `common/util/ImageCompressor.kt` | `ImageCompressor` — ضغط الصور (تغيير الحجم لأقصى 1920px، حفظ اتجاه EXIF) عبر `androidx.exifinterface` و `BitmapFactory`. |
| `common/util/MimeTypeDetector.kt` | `MimeTypeDetector` — كشف MIME من الامتداد/المسار بخرائط يدوية. |
| `common/util/ParallelFileTransfer.kt` | `ParallelFileTransfer` — نقل ملفات متوازٍ عبر TCP/IP: تقسيم (1–8 أجزاء)، تتبع تقدم، إعادة محاولة للأجزاء الفاشلة، عبر `Dispatchers.IO`. |
| `common/util/PeerNameParser.kt` | `PeerNameParser` — استخراج الاسم النظيف من تنسيقات النقل (يزيل `(device_id)` من النهاية). |
| `common/util/RateLimiter.kt` | `RateLimiter` — محدّد معدل بنافذة منزلقة، آمن للخيوط (`ConcurrentHashMap`)، حد أقصى 10000 معرّف. |
| `common/util/StringResourceProvider.kt` | واجهة `StringResourceProvider` + `AndroidStringResourceProvider` — فصل Android Context عن المستودعات. |
| `common/util/TimeUtils.kt` | `formatMessageTime(Long)` — تنسيق الطابع الزمني إلى `hh:mm a`. |

## قرارات تقنية ظاهرة

- **تسلسل `Payload` هنا وليس في `:core:domain`** لأن `PayloadSerializer` يحتاج التعامل مع بيانات ثنائية خام.
- **فصل موارد النصوص** عبر `StringResourceProvider` interface لفصل Context عن مستودعات `:core:data`.
- **أدوات عالية المستوى** (`ParallelFileTransfer`, `ImageCompressor`, `RateLimiter`) موضوعة هنا لتكون متاحة لكل الوحدات.
- **الأمان المُبسّط:** `SimplePeerIdProvider` (UUID في SharedPreferences) يحل محل حل Keystore الأقدم.
