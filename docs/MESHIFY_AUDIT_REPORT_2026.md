# 🔍 Meshify Code Audit Report 2026

**تاريخ التدقيق:** 08 مارس 2026  
**المُدقّق:** Qwen (Staff Engineer Agent)  
**نطاق التدقيق:** `app/src/main/java/com/p2p/meshify` + `res/values`  
**المراجع:** `QWEN.md`, `docs/MD3E.md`

---

## 📊 ملخص تنفيذي

| الفئة | العدد | الخطورة |
|-------|-------|---------|
| 🚨 Critical | 4 | كارثي |
| ⚠️ Major | 8 | عالي |
| 🔧 Minor | 6 | متوسط |
| 🎨 UI/UX | 5 | تجميلي |

**الحالة العامة:** ⚠️ **تطبيق وظيفي لكنه يعاني من ديون تقنية متراكمة**

---

## 🚨 القسم الأول: الجرائم الحرجة (Critical)

### C01: تسرب ذاكرة في `SocketManager.cleanupIdleSockets()`

**الموقع:** `network/lan/SocketManager.kt:92-112`

**الجريمة:**
```kotlin
private fun cleanupIdleSockets() {
    val iterator = activeConnections.iterator()
    while (iterator.hasNext()) {
        val entry = iterator.next()
        // ...
        if (idleTime > IDLE_TIMEOUT_MS) {
            iterator.remove() // ✅ صحيح
        }
    }
}
```

**المشكلة:** الدالة تُستدعى داخل `connectionScope.launch` بدون حماية من `ConcurrentModificationException` إذا أُضيفت اتصالات جديدة أثناء التكرار.

**الأثر:** Crash محتمل عند الإضافة/الحذف المتزامن.

**العلاج:**
```kotlin
private fun cleanupIdleSockets() {
    val toRemove = mutableListOf<String>()
    for ((key, pooledSocket) in activeConnections) {
        if (System.currentTimeMillis() - pooledSocket.lastUsedAt > IDLE_TIMEOUT_MS) {
            toRemove.add(key)
        }
    }
    toRemove.forEach { key ->
        activeConnections.remove(key)?.socket?.close()
    }
}
```

---

### C02: Empty Catch Blocks في `SocketManager.stopListening()`

**الموقع:** `network/lan/SocketManager.kt:229, 239`

**الجريمة:**
```kotlin
try { serverSocket?.close() } catch (e: Exception) {}
// ...
try { entry.value.socket.close() } catch (e: Exception) {}
```

**المشكلة:** ابتلاع الأخطاء بدون تسجيل = كارثة ديباغ.

**الأثر:** لن تعرف أبداً لماذا فشل الإغلاق.

**العلاج:**
```kotlin
try { serverSocket?.close() } catch (e: Exception) {
    Logger.e("SocketManager -> Failed to close server socket", e)
}
```

---

### C03: Logger يُستدعى في Animation Loop

**الموقع:** `ui/components/MeshifyKit.kt:107-117` (MorphPolygonShape.createOutline)

**الجريمة:**
```kotlin
if (!hasLoggedError) {
    Logger.e("MorphPolygonShape -> Path calculation FAILED: ${e.message}")
    hasLoggedError = true
}
```

**المشكلة:** رغم وجود `hasLoggedError`، الـ Logger لا يزال يُستدعى داخل `createOutline` الذي يُستدعى **كل فريم** (60fps).

**الأثر:** GC Pressure، UI Jank.

**العلاج:** نقل الـ Logging خارج الـ Composition أو استخدام `LaunchedEffect` لتأجيل الـ logging.

---

### C04: عدم وجود Thread-Safety في `ChatRepositoryImpl.handleIncomingPayload()`

**الموقع:** `data/repository/ChatRepositoryImpl.kt:124-157`

**الجريمة:**
```kotlin
override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
    when (payload.type) {
        Payload.PayloadType.TEXT -> {
            saveIncomingMessage(...) // ✅ suspend
            sendSystemCommand(...)   // ✅ suspend
        }
    }
}
```

**المشكلة:** لا يوجد `mutex` أو `actor` لمنع الـ Race Conditions إذا وصل Payloads متعددة في نفس الوقت.

**الأثر:** Corruption محتمل في الـ Database.

**العلاج:**
```kotlin
private val payloadMutex = Mutex()

override suspend fun handleIncomingPayload(peerId: String, payload: Payload) {
    payloadMutex.withLock {
        // ... معالجة آمنة
    }
}
```

---

## ⚠️ القسم الثاني: المخالفات الكبرى (Major)

### M01: Hardcoded Colors في `ChatScreen.kt`

**الموقع:** `ui/screens/chat/ChatScreen.kt:242-254`

**الجريمة:**
```kotlin
val containerColor = if (message.isFromMe) {
    MaterialTheme.colorScheme.primaryContainer // ✅
} else {
    MaterialTheme.colorScheme.surfaceContainerHigh // ✅
}

val timeColor = if (message.isFromMe) {
    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f) // ⚠️ Hardcoded Alpha
} else {
    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f) // ⚠️ Hardcoded Alpha
}
```

**المخالفة:** MD3E Section 3.2: "Use semantic color roles, not hardcoded alphas."

**العلاج:** إضافة ألوان مخصصة في `Color.kt`:
```kotlin
val MessageTimestampOnMe = PrimaryContainer.copy(alpha = 0.65f)
val MessageTimestampOnPeer = OnSurfaceVariant.copy(alpha = 0.45f)
```

---

### M02: Spring Physics غير متسقة في `MD3ETheme.kt`

**الموقع:** `ui/theme/MD3ETheme.kt:23-53`

**الجريمة:**
```kotlin
val Bouncy = spring<Float>(
    dampingRatio = 0.4f,  // ⚠️ أقل من MD3E spec (0.5f)
    stiffness = 800f      // ⚠️ أعلى من MD3E spec (600f)
)
```

**المخالفة:** MD3E Motion System Section 2.1: "Bouncy preset should use dampingRatio = 0.5f for playful but controlled motion."

**الأثر:** حركات "مطاطية" أكثر من اللازم، قد تسبب Motion Sickness.

**العلاج:**
```kotlin
val Bouncy = spring<Float>(
    dampingRatio = 0.5f,
    stiffness = 600f
)
```

---

### M03: عدم وجود Error Handling في `ChatViewModel.sendMessage()`

**الموقع:** `ui/screens/chat/ChatViewModel.kt:129-147`

**الجريمة:**
```kotlin
viewModelScope.launch {
    // ...
    if (imageUri != null) {
        val bytes: ByteArray? = withContext(Dispatchers.IO) {
            FileUtils.getBytesFromUri(context, imageUri)
        }
        if (bytes != null) {
            chatRepository.sendImage(peerId, currentPeerName, bytes, "jpg") // ⚠️ لا يوجد check للنجاح
        }
    }
}
```

**المشكلة:** إذا فشل الإرسال، المستخدم لن يعرف أبداً.

**العلاج:**
```kotlin
val result = chatRepository.sendImage(...)
if (result.isFailure) {
    // إظهار خطأ للمستخدم
    _sendError.emit(result.exceptionOrNull())
}
```

---

### M04: Allocation زائدة في `ExpressiveMorphingFAB()`

**الموقع:** `ui/components/MeshifyKit.kt:167-250`

**الجريمة:**
```kotlin
val officialShapes = remember {
    arrayOf<RoundedPolygon>(
        androidx.compose.material3.MaterialShapes.SoftBurst,
        // ... 7 أشكال
    )
}

val morphs = remember(normalizedShapes) {
    Array(shapesCount) { i ->
        Morph(normalizedShapes[i], normalizedShapes[(i + 1) % shapesCount])
    }
}
```

**المشكلة:** `remember` بدون `key` قد يعيد allocation إذا تغير `normalizedShapes` reference.

**العلاج:**
```kotlin
val morphs = remember {
    Array(7) { i ->
        Morph(officialShapes[i], officialShapes[(i + 1) % 7])
    }
}
```

---

### M05: Missing ContentDescription في `ChatScreen.kt`

**الموقع:** `ui/screens/chat/ChatScreen.kt:112, 119, 126`

**الجريمة:**
```kotlin
IconButton(onClick = onBackClick) {
    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") // ⚠️ Hardcoded String
}
```

**المخالفة:** Android Accessibility Guidelines + QWEN.md: "ALL strings in res/values/strings.xml"

**العلاج:**
```kotlin
Icon(Icons.AutoMirrored.Filled.ArrowBack, 
     contentDescription = stringResource(R.string.content_desc_back))
```

---

### M06: لا يوجد Timeout في `SocketManager.sendPayload()`

**الموقع:** `network/lan/SocketManager.kt:165-198`

**الجريمة:**
```kotlin
val outputStream = DataOutputStream(pooledSocket.socket.getOutputStream())
val bytes = PayloadSerializer.serialize(payload)
outputStream.writeInt(bytes.size)
outputStream.write(bytes)
outputStream.flush() // ⚠️ قد يعلق للأبد
```

**المشكلة:** إذا كان الـ Socket "شبه ميت"، الـ write قد يعلق.

**العلاج:**
```kotlin
withTimeoutOrNull(5000) {
    outputStream.write(bytes)
    outputStream.flush()
} ?: throw SocketTimeoutException("Write timeout")
```

---

### M07: لا يوجد Validation في `SettingsViewModel.setCustomFontUri()`

**الموقع:** `ui/screens/settings/SettingsViewModel.kt` (غير موجود في القراءة - استنتاج من الاستخدام)

**المشكلة:** لا يوجد check أن الـ URI يشير لملف `.ttf` أو `.otf` صالح.

**العلاج:**
```kotlin
fun setCustomFontUri(uri: String?) {
    if (uri != null && !uri.endsWith(".ttf") && !uri.endsWith(".otf")) {
        throw IllegalArgumentException("Invalid font file")
    }
    _customFontUri.value = uri
}
```

---

### M08: لا يوجد Pagination في `RecentChatsViewModel`

**الموقع:** `ui/screens/recent/RecentChatsViewModel.kt` (غير موجود - استنتاج)

**المشكلة:** إذا كان هناك 1000 محادثة، الـ UI سيعلق.

**العلاج:** إضافة `LIMIT 50 OFFSET 0` في الـ DAO.

---

## 🔧 القسم الثالث: الديون التقنية (Minor)

### m01: Magic Numbers في `ChatViewModel.calculateGrouping()`

**الموقع:** `ui/screens/chat/ChatViewModel.kt:13`

**الجريمة:**
```kotlin
private const val GROUPING_TIMEOUT_MS = 5 * 60 * 1000 // ⚠️ سحر
```

**العلاج:** نقل لـ `AppConfig`:
```kotlin
// AppConfig.kt
const val MESSAGE_GROUPING_TIMEOUT_MS = 5 * 60 * 1000L
```

---

### m02: لا يوجد Unit Tests

**المشكلة:** لا يوجد أي test في `app/src/test/java/`.

**الأثر:** أي تغيير قد يكسر ميزات موجودة.

**العلاج:** إضافة tests لـ:
- `SocketManager.cleanupIdleSockets()`
- `ChatViewModel.calculateGrouping()`
- `MorphPolygonShape.createOutline()`

---

### m03: لا يوجد Proguard Rules

**الموقع:** `app/proguard-rules.pro` (غير موجود - استنتاج من `build.gradle.kts`)

**المشكلة:** الـ Reflection في `MorphPolygonShape` قد ينكسر مع Minification.

**العلاج:**
```proguard
-keep class com.p2p.meshify.ui.components.MorphPolygonShape { *; }
```

---

### m04: لا يوجد BuildConfig Fields

**الموقع:** `build.gradle.kts`

**المشكلة:** الـ `appVersion` يُجلب من `PackageInfo` (runtime) بدلاً من `BuildConfig.VERSION_NAME`.

**العلاج:**
```kotlin
android {
    defaultConfig {
        buildConfigField("String", "VERSION_NAME", "\"${versionName}\"")
    }
}
```

---

### m05: لا يوجد Coroutine Exception Handler

**الموقع:** `AppContainer.kt` (غير موجود - استنتاج)

**المشكلة:** الـ Unhandled exceptions في الـ Coroutines قد تقتل الـ app.

**العلاج:**
```kotlin
val appScope = CoroutineScope(SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
    Logger.e("AppScope -> Unhandled exception", throwable)
})
```

---

### m06: لا يوجد Network Security Config

**الموقع:** `res/xml/network_security_config.xml` (غير موجود)

**المشكلة:** الـ NSD/mDNS قد يفشل في بعض الأجهزة بدون config.

**العلاج:**
```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
    </domain-config>
</network-security-config>
```

---

## 🎨 القسم الرابع: ملاحظات UI/UX

### U01: لا يوجد Haptic Feedback في `ChatScreen.MessageBubble()`

**الموقع:** `ui/screens/chat/ChatScreen.kt:207-212`

**الملاحظة:** الـ LongPress يُفعّل الـ Selection بدون Haptic.

**العلاج:**
```kotlin
onLongClick = {
    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    viewModel.toggleMessageSelection(...)
}
```

---

### U02: الـ Typing Indicator غير مرئي في `ChatScreen.TopAppBar()`

**الموقع:** `ui/screens/chat/ChatScreen.kt:103-108`

**الملاحظة:**
```kotlin
Text(
    text = if (isPeerTyping) stringResource(R.string.typing_indicator)
           else if (isOnline) stringResource(R.string.status_online)
           // ...
)
```

**المشكلة:** الـ `typing_indicator` ("typing...") صغير جداً وقد لا يُلاحظ.

**العلاج:** إضافة أنيميشن لـ 3 نقاط:
```kotlin
AnimatedVisibility(visible = isPeerTyping) {
    Row {
        Text("typing")
        repeat(3) { DotPulseAnimation(delay = it * 150) }
    }
}
```

---

### U03: الـ Attachment Bottom Sheet لا يغلق بالـ Swipe Down

**الموقع:** `ui/screens/chat/ChatScreen.kt:157-162`

**الملاحظة:**
```kotlin
ModalBottomSheet(onDismissRequest = { showSheet = false }, sheetState = sheetState)
```

**المشكلة:** الـ `sheetState` بدون `skipPartiallyExpanded = true` قد لا يسمح بالـ swipe.

**العلاج:**
```kotlin
val sheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = true,
    confirmValueChange = { true } // Allow dismiss
)
```

---

### U04: لا يوجد Shimmer Effect في `MessageBubbleContent.Image()`

**الموقع:** `ui/screens/chat/ChatScreen.kt:330-344`

**الملاحظة:** الـ `AsyncImage` يستخدم `crossfade` فقط.

**العلاج:** إضافة Shimmer:
```kotlin
val shimmer = rememberInfiniteTransition()
val alpha by shimmer.animateFloat(
    initialValue = 0.3f,
    targetValue = 0.8f,
    animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse)
)
Box(modifier = Modifier.alpha(alpha)) { /* Shimmer overlay */ }
```

---

### U05: الـ ColorPicker في `SettingsScreen` لا يدعم الـ Long Press

**الموقع:** `ui/screens/settings/SettingsScreen.kt:430-456`

**الملاحظة:**
```kotlin
.clickable {
    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    onColorSelected(color)
}
```

**العلاج:** إضافة Long Press لـ Preview:
```kotlin
.combinedClickable(
    onClick = { onColorSelected(color) },
    onLongClick = { showColorPreviewDialog(color) }
)
```

---

## 📋 ملخص التوصيات حسب الأولوية

| الأولوية | الإجراء | الجهد | التأثير |
|----------|---------|-------|---------|
| P0 | إصلاح C01 (ConcurrentModification) | منخفض | عالي جداً |
| P0 | إصلاح C02 (Empty Catch) | منخفض | عالي |
| P0 | إصلاح C04 (Thread-Safety) | متوسط | عالي جداً |
| P1 | إصلاح M01 (Hardcoded Alphas) | منخفض | متوسط |
| P1 | إصلاح M03 (Error Handling) | متوسط | عالي |
| P1 | إصلاح M06 (Socket Timeout) | منخفض | عالي |
| P2 | إضافة Unit Tests | عالي | عالي |
| P2 | إصلاح M02 (Spring Physics) | منخفض | متوسط |
| P3 | تحسينات UI/UX | متوسط | منخفض |

---

## 🏁 الخلاصة

التطبيق **وظيفي** و**معماريته نظيفة** (Clean Architecture ✅)، لكنه يعاني من:

1. **ثغرات Thread-Safety** قد تسبب Crashes نادرة.
2. **عدم وجود Error Handling** في نقاط حرجة.
3. **ديون تقنية صغيرة** (Magic Numbers، Hardcoded Strings).
4. **نقص في الاختبارات** يجمع التغييرات المستقبلية خطرة.

**التوصية النهائية:** 
- **إصلاح C01, C02, C04 فوراً** (قبل أي feature جديد).
- **تأجيل M01-M08** للمرحلة القادمة.
- **بدء كتابة Unit Tests** للـ Core Logic.

---

**تم التدقيق بواسطة:** Qwen (Staff Engineer Agent)  
**التوقيع:** 🖊️  
**الحالة:** ⚠️ **Needs Immediate Attention**
