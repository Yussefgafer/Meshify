# `:core:domain` — طبقة المجال النقية

**الغرض:** طبقة المجال (Domain Layer). **pure Kotlin/JVM** بلا أي اعتماد على Android. تحتوي النماذج (models)، واجهات المستودعات (repository interfaces)، وثوابت التطبيق. هذه أعمق طبقة في المشروع ولا يمكن لأي شيء آخر الاعتماد عليها.

**البناء (`build.gradle.kts`):** يستخدم `kotlin.jvm` + `kotlin.serialization`. التبعيات الوحيدة: `kotlinx.coroutines.core`، `kotlinx.serialization.json`، وأدوات الاختبار (JUnit، MockK، coroutines-test). لا توجد تبعيات Android.

## الملفات الرئيسية

جميع المسارات نسبة إلى `core/domain/src/main/java/`:

| الملف | المحتوى |
|---|---|
| `com/p2p/meshify/domain/model/Payload.kt` | نموذج `Payload` — حزمة البيانات المرسلة عبر الشبكة. يحتوي `PayloadType` enum (`TEXT, FILE, HANDSHAKE, SYSTEM_CONTROL, DELETE_REQUEST, REACTION, AVATAR_REQUEST, AVATAR_RESPONSE, VIDEO`). يتجاوز `equals/hashCode` يدوياً (بسبب `ByteArray`). كما يحوي `Handshake`، `DeleteRequest`، `ReactionUpdate` — كلها `@Serializable`. |
| `com/p2p/meshify/domain/model/PeerDevice.kt` | نموذج `PeerDevice` لجهاز نظير مُكتشف. يحوي `TransportType` enum (`LAN, BLE, BOTH`) ويحسب `SignalStrength` من RSSI. |
| `com/p2p/meshify/domain/model/MessageType.kt` | enum `MessageType` للرسائل/الملفات المدعومة (`TEXT, IMAGE, VIDEO, AUDIO, DOCUMENT, ARCHIVE, APK, FILE`) مع MIME type وقائمة امتدادات ودوال `fromExtension()` / `fromMimeType()`. |
| `com/p2p/meshify/domain/model/TransportMode.kt` | enum `TransportMode` (`MULTI_PATH, LAN_ONLY, BLE_ONLY, AUTO`) مع وصف نصي. |
| `com/p2p/meshify/domain/model/ThemeConfig.kt` | Enums خاصة بالتخصيص المرئي MD3E: `ShapeStyle`، `MotionPreset`، `FontFamilyPreset`، `BubbleStyle`. |
| `com/p2p/meshify/domain/model/SignalStrength.kt` | enum `SignalStrength` (`STRONG, MEDIUM, WEAK, OFFLINE`) مع `fromRssi()`. |
| `com/p2p/meshify/domain/model/AppConstants.kt` | ثوابت: `MAX_FILE_SIZE_BYTES = 100MB`، `DEFAULT_PEER_NAME_PREFIX = "Peer_"`. |
| `com/p2p/meshify/domain/security/model/MessageEnvelope.kt` | `MessageEnvelope` — مغلف الرسالة النصية العادية (plaintext) بعد إزالة التشفير في Phase 3. |
| `com/p2p/meshify/domain/security/model/OobVerificationMethod.kt` | enum `OobVerificationMethod` (`QR, SAS, NFC`). |
| `com/p2p/meshify/domain/security/model/SecurityEvent.kt` | `SecurityEvent` مع `EventType` (حالياً `MESSAGE_SEND_FAILED` فقط). |
| `com/p2p/meshify/core/domain/interfaces/WifiStateChecker.kt` | واجهة `WifiStateChecker` — تجريد لفحص حالة Wi-Fi (لتفادي اعتماد ViewModels على Android framework). |
| `com/p2p/meshify/domain/repository/IChatRepository.kt` | واجهة `IChatRepository` — الـ facade الرئيسي لعمليات الشات (إرسال، إدارة، تفاعلات، أنظمة). تضم `onlinePeers: Flow<Set<String>>`، `typingPeers: Flow<Set<String>>`، `securityEvents: SharedFlow<SecurityEvent>`. |
| `com/p2p/meshify/domain/repository/IFileManager.kt` | واجهة `IFileManager` — حفظ الملفات (`saveMedia()`). |
| `com/p2p/meshify/domain/repository/ISettingsRepository.kt` | واجهة `ISettingsRepository` الضخمة لكل إعدادات المستخدم (الاسم، الثيم، إعدادات MD3E، النقل، اللغة، الإشعارات، النسخ الاحتياطي...). تضم enum `ThemeMode` (`LIGHT, DARK, SYSTEM`). |

## قرارات تقنية ظاهرة

- **بادئتا تسمية:** `com.p2p.meshify.core.domain.*` (القديم، مثل `WifiStateChecker`) و `com.p2p.meshify.domain.*` (الأحدث للنماذج والواجهات).
- **تسلسل `Payload`:** يستخدم `ByteArray` ويتجاوز `equals/hashCode` يدوياً؛ التسلسل الفعلي يتم في `:core:common` عبر `PayloadSerializer` (لأنه يتعامل مع بيانات ثنائية خام).
- **أمان مُبسّط:** بعد Phase 3 كل الرسائل تُرسل بالنص العادي؛ `MessageEnvelope` لا يحوي تشفيراً.
- **نمط Repository:** `IChatRepository` هو facade يُفوَّض إلى 5 مستودعات متخصصة في `:core:data` (`MessageRepository`, `ChatManagementRepository`, `PendingMessageRepository`, `MessageAttachmentRepository`, `ReactionRepository`).
