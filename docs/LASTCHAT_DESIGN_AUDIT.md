# 🕵️‍♂️ المخطط الهندسي الشامل: تحويل Meshify إلى "تحفة" تقنية (Legacy of LastChat)
> **إلى النموذج المنفذ:** هذا الملف هو "مواصفة فنية" (Technical Specification). اتبع التعليمات الجراحية أدناه لإصلاح الـ UI، تحسين الأداء، وتطبيق فلسفة MD3E.

---

## 🛑 1. لغز الأشكال المختفية (The Morphing Shapes Mystery)
**المشكلة:** الأشكال في `ExpressiveMorphingFAB` و `MorphingAvatar` لا تظهر أو تظهر بشكل مشوه/شفاف.

### أ. السبب القاتل (The Logic Error):
في ملف `app/src/main/java/com/p2p/meshify/ui/components/MD3EComponents.kt`:
*   **القسمة الخاطئة:** يتم حساب `sizeValue` بقسمة الـ `size` على `2.2f`. هذا الرقم اعتباطي ويسبب خروج الـ Path عن حدود الـ Canvas.
*   **غياب التسنتر (Normalization):** في `MD3ETheme.kt` وظيفة `normalize()` هي `no-op`. الأشكال إحداثياتها سارحة، والـ `scale` يطبق من نقطة (0,0) أعلى اليسار، مما يطرد الشكل خارج الـ `Box`.
*   **تغطية الـ Surface:** الـ `Surface` له لون خلفية صلب يغطي على ما يرسمه الـ `drawBehind`.

### ب. الحل الجراحي (The Implementation Spec):
1.  **Normalization:** يجب استخدام `Matrix` لتحويل إحداثيات الـ `RoundedPolygon` لتقع تماماً في المربع `(0,0)` إلى `(1,1)`.
2.  **Custom Shape:** بدلاً من `drawBehind` اليدوي، يجب إنشاء كلاس `MorphShape` يرث من `androidx.compose.ui.graphics.Shape`:
    ```kotlin
    class MorphPolygonShape(private val morph: Morph, private val progress: Float) : Shape {
        override fun createOutline(...) {
            val path = AndroidPath()
            morph.toPath(progress, path)
            // يجب تطبيق Matrix Scale لتناسب الـ size المتاح
            return Outline.Generic(path.asComposePath())
        }
    }
    ```
3.  **Usage:** طبق الـ `Shape` مباشرة في `Modifier.clip()` أو `Surface(shape = ...)`.

---

## 🖼️ 2. نظام الوسائط والصور (Media & Image Pipeline)
**المصدر:** `docs/Examples/LastChat/app/src/main/java/me/rerere/rikkahub/ui/components/richtext/ZoomableAsyncImage.kt`

### أ. العرض والتحميل:
*   **Coil 3:** يجب الترقية لـ `coil3` لدعم أفضل للـ Coroutines والـ Caching.
*   **Shimmer Effect:** استخدم `Modifier.shimmer()` (موجود في كود LastChat) كـ `placeholder` حتى اكتمال التحميل.
*   **Crossfade:** يجب تفعيل `crossfade(true)` بمدة `400ms` لنعومة الظهور.

### ب. عارض الصور الكامل (Full Image Viewer):
*   **State:** استخدم `mutableStateOf(false)` للتحكم في ظهور الـ `ImagePreviewDialog`.
*   **Zoom Logic:** يجب أن يدعم الـ `FullImageViewer` تقنيات الـ `Double Tap to Zoom` و `Pinch to Zoom`.
*   **Blur Background:** عند فتح الصورة، يجب عمل `RenderEffect.createBlurEffect` للخلفية (Android 12+) أو طبقة سوداء بـ `alpha = 0.9f`.

---

## ⚡ 3. انسيابية الحركة (Motion & Navigation)
**المصدر:** `docs/Examples/LastChat/app/src/main/java/me/rerere/rikkahub/ui/hooks/HeroAnimation.kt`

### أ. العناصر المشتركة (Shared Elements):
*   **التحدي:** انتقال صورة البروفايل من `RecentChatsScreen` إلى `ChatScreen` كأنها "تطير".
*   **الحل:** استخدم `SharedTransitionScope` (Experimental في Compose).
*   **الربط:** أعطِ كل أفاتار `sharedElement(rememberSharedContentState(key = "avatar_${chat.id}"))`.

### ب. قوانين الفيزياء (Animation Specs):
*   **Morphing:** استخدم `spring(dampingRatio = 0.4f, stiffness = 500f)` لحركة "مطاطية" ممتعة.
*   **Rotation:** الدوران المستمر للأيقونات يجب أن يكون `LinearEasing` بمدة `3000ms`.
*   **Scroll:** استخدم `NestedScrollConnection` لإخفاء/إظهار الـ FAB بنعومة مع التمرير.

---

## 🛠️ 4. البنية التحتية (Architecture & Logic)

### أ. الشبكة (Network):
*   **NSD Watchdog:** تم تحسينه في `LanTransportImpl`. يجب التأكد من أن `stopDiscovery()` تنادي على `resolvingPeers.clear()` لمنع الـ Memory Leaks.
*   **Thread Safety:** أي تعديل في الـ `onlinePeers` (StateFlow) يجب أن يكون عبر `update { ... }` لضمان الـ Atomicity.

### ب. قاعدة البيانات (Database):
*   **Migrations:** تم إنشاء `MIGRATION_1_2`. يجب التأكد من إضافة `db.execSQL("PRAGMA foreign_keys=ON")` في الـ `onOpen` للـ Database.
*   **Indexing:** الـ `chatId` في جدول `messages` يجب أن يكون عليه `Index` لتفادي البطء عند وصول الرسائل لـ 1000+.

---

## 📝 5. ملاحظات نهائية للمنفذ:
1.  **No AI Slop:** لا تضع `// TODO` أو `MockData`. انقل المنطق الحقيقي من `LastChat`.
2.  **Surgical Edits:** لا تعيد كتابة الملفات بالكامل. استبدل المكونات البصرية فقط.
3.  **Localize:** تأكد من أن كل النصوص تُسحب من `strings.xml`.
4.  **Haptics:** أضف `haptic.performHapticFeedback(HapticFeedbackType.LongPress)` مع كل `onClick` في الـ FAB والـ Cards.

---
**إعداد وتدقيق:** الوكيل Kai (Gemini CLI) - الشريك التقني لـ Jo.
**الحالة:** جاهز للتنفيذ.
