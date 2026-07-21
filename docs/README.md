# توثيق Meshify (`docs/`)

توثيق حقيقي مستخرج من الكود الفعلي لمشروع Meshify (تطبيق مراسلة P2P غير متصل بالإنترنت لنظام Android). كل ملف يصف الغرض، الملفات الرئيسية، والقرارات التصميمية/التقنية الظاهرة في الكود — بلا أي محتوى نظري أو وهمي.

## كيفية التنظيم

التنظيم يتبع البنية الفعلية للمشروع (حجم كل وحدة وتعقيدها)، لا قالباً ثابتاً:

- **موديولات أساسية (`core:*`) وصغيرة:** كل منها **ملف مستقل** في جذر `docs/`.
- **موديولات ميزات غنية (chat, settings, real-device-testing):** الأولى والثانية في **مجلدات فرعية** لكثرة الشاشات/المكونات. `real-device-testing` ملف مستقل لكونها تدفق اختبار واحد متماسك.

## الفهرس

### نظرة عامة
- [architecture.md](architecture.md) — طبقات المشروع، قواعد الاعتماد، بادئات الحزم، القرارات البنيوية.

### الموديولات الأساسية (`core:*`)
- [core-domain.md](core-domain.md) — طبقة المجال النقية (Kotlin/JVM): النماذج، واجهات المستودعات، الثوابت.
- [core-common.md](core-common.md) — المكتبة المشتركة: اللوجر، تسلسل الحمولات، أدوات الملفات/الصور، فحص الأذونات والاتصال.
- [core-data.md](core-data.md) — طبقة البيانات: Room (v7)، DAOs، المستودعات، DataStore، NotificationHelper.
- [core-network.md](core-network.md) — طبقة النقل: LAN (TCP/mDNS) و BLE (GATT) عبر `IMeshTransport` + `TransportManager`.
- [core-ui.md](core-ui.md) — نظام التصميم M3، المكونات، التنقل (`Screen`/`MeshifyNavHost`)، نماذج UI، الاهتزاز.

### موديولات الميزات (`feature:*`)
- [feature-home.md](feature-home.md) — قائمة المحادثات الأخيرة (شاشة واحدة).
- [feature-chat/](feature-chat/) — شاشة المحادثة: الرسائل، الوسائط، الرد، إعادة التوجيه، التحديد، البحث.
- [feature-discovery.md](feature-discovery.md) — اكتشاف الأجهزة النظيرة (LAN/BLE).
- [feature-settings/](feature-settings/) — الإعدادات + شاشة المطورين المخفية.
- [feature-onboarding.md](feature-onboarding.md) — شاشة الترحيب الثلاثية + سير الأذونات.
- [feature-real-device-testing.md](feature-real-device-testing.md) — اختبار الأجهزة الحقيقية (آلة حالات 8 مراحل، LAN/BLE).

### التطبيق
- [app.md](app.md) — `:app` المُجمّع: `MainActivity`، `MeshifyApp`، وحدات Hilt، المستقبِل، الخدمة الأمامية.
