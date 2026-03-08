# Meshify - Staff Engineer Rules

## Project Vision
A modular, high-performance P2P mesh networking application. Core focus: Decentralization, Zero UI blocking, and Pluggable transports.

## Tech Stack & Architecture
- **Language:** Kotlin (Strictly)
- **UI:** Jetpack Compose (Material 3 Expressive)
- **Concurrency:** Kotlin Coroutines & Flows (StateFlow for UI, SharedFlow for events)
- **Architecture:** Clean Architecture (Domain-driven)
- **Dependency Injection:** Manual DI via `AppContainer` (Avoid Dagger/Hilt unless project scales)

## Critical Code Rules
- **Basics First:** Priorities are Stability, UX Flow, and Core Functionality. No "Additives" until the foundation is 100% reliable.
- **Non-blocking I/O:** ALL socket, network, and disk operations MUST be explicitly wrapped in `withContext(Dispatchers.IO)`.
- **Verification Protocol:** After EVERY `write_file` or `replace` operation, a `read_file` MUST be performed to verify code integrity and ensure no parts were accidentally deleted.
- **No Hardcoding (Constants):** Tech constants must live in `com.p2p.meshify.core.config.AppConfig`.
- **No Hardcoding (Strings):** ALL UI strings MUST live in `res/values/strings.xml`. Localization (Arabic/English) is mandatory.
- **UI Interaction:** The UI is passive and only observes `StateFlow` from ViewModels.
- **Expressive Motion:** Follow MD3E motion parameters via `LocalMeshifyMotion`.
- **Transport Abstraction:** Any network protocol must implement `IMeshTransport`.
- **Surgical Precision:** Do not rewrite whole files. Only modify the requested parts with surgical accuracy.
- **Zero AI Slop:** No placeholder comments or non-functional code.

## Directory Structure
Follow the established blueprint:
- `core/`: Config, Logger, Serializer, FileUtils.
- `data/`: Room DB (local), DAO, Entities, Repositories.
- `domain/`: Business logic, Repository interfaces, and Data Models.
- `network/`: Base transport interfaces and specific implementations (LAN, Bluetooth).
- `ui/`: Compose themes, components, and feature screens.

## Common Tasks (Commands)
- **Build:** `./gradlew assembleDebug`
- **Lint:** `./gradlew lint`
- **Test:** `./gradlew testDebugUnitTest`
- **Clean:** `./gradlew clean`

## AI Personality (Jules/Gemini)
- Act as an elite Staff Engineer.
- Be blunt about technical mistakes.
- If a requested feature is impossible or would cause UI lag, refuse and propose a non-blocking alternative.
- Always check `AppConfig` before proposing new constants.

## [سجل فهم المشروع]
### المرحلة الأولى: التأسيس (Foundation Phase) - 06/03/2026
- **تم الانتهاء من:** إنشاء الهيكلية الأساسية، تعريف الـ Payload والواجهات البرمجية للنقل.

### المرحلة الثانية: محرك الشبكة (Network Engine) - 06/03/2026
- **تم الانتهاء من:** `SocketManager` و `LanTransportImpl` (النسخة الأولية).

### المرحلة العاشرة: محرك الهوية والملف الشخصي - 06/03/2026
- **تم الانتهاء من:** `DataStore` ، منطق الـ Handshake ، والـ Routing المنطقي.

### المرحلة الثالثة عشر: واجهة الإعدادات والتحصين الأولي - 06/03/2026
- **تم الانتهاء من:** 7-Shape Morphing (النسخة الأولية)، حفظ المرفقات في الذاكرة الداخلية، وإضافة حالات الرسائل (SENT, FAILED).

### المرحلة الرابعة عشر: التطهير المعماري والتقني الشامل (Architectural Cleanup) - 06/03/2026
- **تم الانتهاء من:**
    - **Clean Architecture**: فصل طبقة الـ Domain تماماً عبر `IChatRepository`, `ISettingsRepository`, `IFileManager` و Use Cases.
    - **Robust SocketManager**: إصلاح تسريبات الذاكرة (Memory Leaks) وإضافة Connection Pooling و Timeouts و Thread Safety.
    - **Real Pagination**: تنفيذ نظام التجزئة الحقيقي (Limit/Offset) في كل الطبقات لضمان أداء مستقر.
    - **Secure Morphing Engine**: إصلاح محرك الأشكال السبعة باستخدام الـ Normalization والـ Secure Reflection لضمان العمل على جميع الأجهزة.
    - **Full Localization**: تعريب التطبيق بالكامل وإزالة جميع النصوص الصلبة (Hardcoded Strings).
    - **Navigation Component**: نقل التنقل لـ `NavHost` مركزي مع إدارة سليمة للـ Backstack.
    - **Advanced UI/UX**: إضافة مؤشر الكتابة (Typing Indicator)، تجميع الرسائل ذكياً، مسح الرسائل مع تأكيد، وعارض صور كامل الشاشة.
    - **Performance Optimization**: تهيئة Coil مع Memory & Disk Cache متقدم لمنع تهنيج الواجهة.
    - **Android 13+ Permissions**: إصلاح منطق الصلاحيات ليحترم معايير الخصوصية.

### المرحلة الخامسة عشر: ثورة الملاحة والنوع الآمن (Navigation Revolution) - 08/03/2026
- **تم الانتهاء من:**
    - **Stable Navigation**: التراجع عن `Navigation 3` لصالح `Navigation Compose 2.8.7` (النسخة المستقرة والأكثر كفاءة).
    - **Full Type-Safety**: تطبيق نظام الـ `Serializable Routes` بالكامل لضمان عدم حدوث أخطاء وقت التشغيل (Runtime Errors).
    - **Clean State Management**: التخلص من الإدارة اليدوية للـ `backStack` والاعتماد على `NavController` الأصلي.
    - **Coil 3 Migration**: تحديث جميع الـ `imports` والـ `dependencies` لتعمل مع Coil 3 بكفاءة.
    - **MD3 Expressive Ready**: تفعيل الـ `MaterialShapes` والـ `Expressive Motion` عبر الـ `Compiler Opt-ins` اللازمة.

### المرحلة السادسة عشر: تحسينات MD3E الشاملة (MD3E Comprehensive Refinement) - 08/03/2026
- **تم الانتهاء من:**
    - **LocalMeshifySettings**: إضافة `seedColor` و `customFontUri` إلى CompositionLocal
    - **DataStore Binding**: ربط MainActivity مباشرة بـ DataStore للتحديث اللحظي
    - **Settings Screen Redesign**: استخدام ListItem بدلاً من Cards، إضافة ColorPicker و FontPicker
    - **ChatScreen Optimization**: تصغير الـ Padding، Smart Avatar (يظهر مرة واحدة فقط في كل مجموعة)
    - **FAB Engine**: تنفيذ 3-Shape Morphing (Cookie9 -> Cookie6 -> Pentagon) مع Icon ثابت
    - **Radar Pulse**: استبدال الـ Spinner بـ 7-Shapes Morph Radar في Discovery Screen
    - **Noise Texture**: إضافة Noise Overlay (alpha 0.03) لجميع الشاشات
    - **Morphing Fix**: تثبيت الـ Path وال Rotation حول المركز الهندسي الدقيق
    - **Bottom Sheets**: توحيد Bottom Sheets بـ 28dp corners + drag handle
    - **Full Localization**: تعريب جميع النصوص الجديدة

## [الوضع الحالي - Status Quo]
التطبيق الآن يتمتع بـ:
- **Production-Grade Architecture**: Clean Architecture مع فصل كامل للـ Domain
- **MD3E Compliance**: Material 3 Expressive بالكامل (Shapes, Motion, Typography)
- **Real-time Settings**: تحديث لحظي لكل الإعدادات عبر CompositionLocal
- **Optimized Performance**: Noise Texture cached، Smart Avatar، أصغر Padding
- **Professional UI**: يضاهي LastChat في الجودة والتصميم

## [خريطة الطريق القادمة]
1.  **End-to-End Encryption**: تنفيذ الـ E2EE باستخدام Signal Protocol
2.  **Media Support**: إرسال الفيديوهات والمقاطع الصوتية
3.  **Unit & UI Testing**: إضافة اختبارات شاملة
