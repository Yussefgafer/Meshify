# 🔥 Meshify Audit Report - Round 3: Reliability & Edge Cases

**تاريخ التدقيق:** 08 مارس 2026  
**المُدقّق:** Qwen (Staff Engineer Agent)  
**نطاق التدقيق:** Reliability, Edge Cases, Interruption Handling, Data Integrity  
**المراجع:** `QWEN.md`, `docs/MD3E.md`, `docs/MESHIFY_AUDIT_REPORT_2026.md`

---

## 📊 ملخص تنفيذي

| الفئة | العدد | الخطورة | الحالة |
|-------|-------|---------|--------|
| 🚨 Critical | 3 | كارثي | **غير مُصلح** |
| ⚠️ Major | 5 | عالي | **غير مُصلح** |
| 🔧 Minor | 4 | متوسط | **غير مُصلح** |
| ✅ Passing | 2 | - | **يجتاز الاختبار** |

**الحالة العامة:** ⚠️ **التطبيق صامد وظيفياً، لكن هناك ثغرات حرجة في التعامل مع الانقطاعات والبيانات المشوهة**

---

## 🧪 القسم الأول: اختبار الانقطاع (Interruption Handling)

### 1.1 انقطاع الواي فاي أثناء إرسال ملف كبير

**السيناريو:** المستخدم يرسل صورة 5MB، فجأة ينقطع الواي فاي.

**التحقيق:**

```kotlin
// SocketManager.kt:165-198
suspend fun sendPayload(targetAddress: String, payload: Payload): Result<Unit> {
    // ...
    val outputStream = DataOutputStream(pooledSocket.socket.getOutputStream())
    val bytes = PayloadSerializer.serialize(payload)
    outputStream.writeInt(bytes.size)
    outputStream.write(bytes)
    outputStream.flush() // ⚠️ لا يوجد Timeout
    // ...
}
```

**🚨 المشكلة الحرجة:**

1. **لا يوجد Socket Timeout** على عمليات الـ write. إذا انقطع الاتصال أثناء الإرسال، الـ coroutine قد يعلق للأبد.
2. **لا يوجد Partial Write Detection**. إذا أُرسِل 50% من الملف ثم انقطع، لن يعرف المرسل ذلك.
3. **لا يوجد Retry Logic**. الإرسال سيفشل للأبد حتى لو عاد الاتصال.

**الأثر:**
- UI قد يتجمد إذا كان الإرسال يُستدعى من Coroutine بدون `withTimeout`.
- المستخدم لن يعرف إذا فشل الإرسال أم نجح.
- الـ Socket قد يبقى في حالة "شبه ميتة" (Half-Open).

**العلاج المطلوب:**

```kotlin
suspend fun sendPayload(targetAddress: String, payload: Payload): Result<Unit> = withContext(ioDispatcher) {
    withTimeoutOrNull(10_000) { // 10s timeout for entire operation
        // ...
        withTimeoutOrNull(5_000) {
            outputStream.write(bytes)
            outputStream.flush()
        } ?: throw SocketTimeoutException("Write timeout")
    } ?: return@withContext Result.failure(SocketTimeoutException())
}
```

---

### 1.2 Timeout حقيقي لمنع تعليق Coroutine

**التحقيق:**

```kotlin
// SocketManager.kt:78
clientSocket.soTimeout = 30000 // ✅ 30s read timeout

// SocketManager.kt:184
socket.connect(InetSocketAddress(targetAddress, AppConfig.DEFAULT_PORT), 5000) // ✅ 5s connect timeout
socket.soTimeout = 30000 // ✅ 30s read timeout
```

**✅ النقطة الإيجابية:** الـ `soTimeout` موجود على الـ **Reading** side.

**🚨 الثغرة:**

```kotlin
// SocketManager.kt:190-195
outputStream.writeInt(bytes.size)
outputStream.write(bytes)
outputStream.flush() // ❌ لا يوجد timeout للكتابة
```

الـ `soTimeout` يؤثر **فقط** على الـ Reading operations. الـ Writing operations ليس لها timeout افتراضي.

**العلاج:** استخدام `withTimeoutOrNull` كما في القسم السابق.

---

### 1.3 التعامل مع Address Unreachable في LanTransportImpl

**التحقيق:**

```kotlin
// LanTransportImpl.kt:158-162
override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
    val ipAddress = peerMap[targetDeviceId] 
        ?: return Result.failure(Exception("Peer offline"))
    return socketManager.sendPayload(ipAddress, payload)
}
```

**🚨 المشكلة:**

1. **لا يوجد ICMP Check**. الـ `peerMap` قد يحتوي على IP قديم لم يعد متاحاً.
2. **لا يوجد SocketException Handling**. إذا كان الـ IP unreachable، الـ `sendPayload` سيرمي `ConnectException` أو `SocketTimeoutException`.
3. **لا يوجد Cleanup للـ Peer القريب**. إذا فشل الإرسال، يجب إزالة الـ peer من `peerMap` بعد عدد معين من المحاولات.

**الأثر:**
- المحاولات المتكررة لإرسال لـ peer ميت ستفشل كل مرة.
- لا يوجد "Dead Peer Detection" حقيقي.

**العلاج:**

```kotlin
override suspend fun sendPayload(targetDeviceId: String, payload: Payload): Result<Unit> {
    val ipAddress = peerMap[targetDeviceId] 
        ?: return Result.failure(Exception("Peer offline"))
    
    val result = socketManager.sendPayload(ipAddress, payload)
    
    if (result.isFailure) {
        val exception = result.exceptionOrNull()
        if (exception is SocketTimeoutException || exception is ConnectException) {
            // Mark peer as potentially dead
            failedSendCount.incrementAndGet()
            if (failedSendCount.get() > MAX_FAILURES) {
                peerMap.remove(targetDeviceId)
                _events.emit(TransportEvent.DeviceLost(targetDeviceId))
            }
        }
    }
    
    return result
}
```

---

## 🧪 القسم الثاني: نزاهة البيانات (Data Integrity)

### 2.1 PayloadSerializer مع ByteArray مشوه

**التحقيق:**

```kotlin
// PayloadSerializer.kt:80-115
fun deserializeSafe(bytes: ByteArray): DeserializeResult {
    // ✅ Minimum size check
    if (bytes.size < MIN_PAYLOAD_SIZE) {
        return DeserializeResult.Error("Payload too small: ${bytes.size} bytes", bytes)
    }

    val buffer = ByteBuffer.wrap(bytes)

    try {
        val totalLength = buffer.getInt()

        // ✅ Validate total length
        if (totalLength <= 0 || totalLength > bytes.size) {
            return DeserializeResult.Error("Invalid payload length: $totalLength (actual: ${bytes.size})", bytes)
        }

        // ✅ Bounds checking for type length
        if (typeLength < 0 || typeLength > MAX_TYPE_LENGTH || typeLength > buffer.remaining()) {
            return DeserializeResult.Error("Invalid V3 type length: $typeLength", bytes)
        }

        // ✅ Catch BufferUnderflowException
    } catch (e: BufferUnderflowException) {
        return DeserializeResult.Error("Buffer underflow: ${e.message}", bytes)
    } catch (e: Exception) {
        return DeserializeResult.Error("Deserialization failed: ${e.message}", bytes)
    }
}
```

**✅ النتيجة: التطبيق سيصمد!**

**نقاط القوة:**
1. **Minimum Size Check** يمنع `IndexOutOfBoundsException`.
2. **Total Length Validation** يمنع قراءة بيانات خارج الـ buffer.
3. **Type Length Bounds Check** يمنع تخصيص Arrays ضخمة أو سالبة.
4. **BufferUnderflowException Handling** يمسك أي قراءة زائدة.
5. **Fallback Payload** في `deserialize()` ينشئ `SYSTEM_CONTROL` payload آمن إذا فشل الـ deserialization.

**⚠️ ثغرة صغيرة:**

```kotlin
// PayloadSerializer.kt:67-77
fun deserialize(bytes: ByteArray): Payload {
    return when (val result = deserializeSafe(bytes)) {
        is DeserializeResult.Success -> result.payload
        is DeserializeResult.Error -> {
            // Fallback: create a SYSTEM_CONTROL payload with raw data
            Payload(
                id = UUID.randomUUID().toString(),
                senderId = "unknown",
                timestamp = System.currentTimeMillis(),
                type = Payload.PayloadType.SYSTEM_CONTROL,
                data = bytes // ⚠️ البيانات المشوهة تُحفَظ كما هي
            )
        }
    }
}
```

**المشكلة:** الـ fallback يحفظ الـ bytes المشوهة كما هي. هذا قد يسبب مشاكل لاحقاً إذا حاول كود آخر قراءتها.

**العلاج المقترح:**

```kotlin
is DeserializeResult.Error -> {
    Logger.w("PayloadSerializer -> Corrupted payload received: ${result.reason}")
    // Return a safe empty payload instead of preserving corrupted data
    Payload(
        id = UUID.randomUUID().toString(),
        senderId = "unknown",
        timestamp = System.currentTimeMillis(),
        type = Payload.PayloadType.SYSTEM_CONTROL,
        data = ByteArray(0) // Empty data instead of corrupted bytes
    )
}
```

---

### 2.2 ثغرات في Deserialize Logic

**التحقيق المتقدم:**

```kotlin
// PayloadSerializer.kt:120-130
val typeBytes = ByteArray(typeLength)
buffer.get(typeBytes)
val typeName = String(typeBytes) // ⚠️ لا يوجد charset specification
```

**🚨 المشكلة:**

1. **Default Charset Dependency**. الـ `String(byteArray)` يستخدم الـ default charset، والذي قد يختلف بين الأجهزة (UTF-8 vs ISO-8859-1).
2. **Invalid UTF-8 Handling**. إذا كانت الـ bytes تحتوي على UTF-8 غير صالح، قد نحصل على `MalformedInputException`.

**العلاج:**

```kotlin
val typeName = try {
    String(typeBytes, Charsets.UTF_8)
} catch (e: Exception) {
    Logger.w("PayloadSerializer -> Invalid UTF-8 type name, using default")
    String(typeBytes) // Fallback to default
}
```

---

**ثغرة أخرى:**

```kotlin
// PayloadSerializer.kt:140-145
val data = ByteArray(buffer.remaining())
buffer.get(data)

return DeserializeResult.Success(
    Payload(
        // ...
        data = data // ⚠️ لا يوجد حد أقصى لحجم البيانات
    )
)
```

**المشكلة:** لا يوجد check أن `data.size <= AppConfig.MAX_PAYLOAD_SIZE_BYTES`.

**العلاج:**

```kotlin
if (buffer.remaining() > AppConfig.MAX_PAYLOAD_SIZE_BYTES) {
    return DeserializeResult.Error(
        "Payload data too large: ${buffer.remaining()} bytes",
        bytes
    )
}
```

---

## 🧪 القسم الثالث: التحقق من التكامل (Integration Check)

### 3.1 LocalMeshifySettings في كل الشاشات

**التحقيق:**

```kotlin
// ✅ MainActivity.kt:79-116
val fontFamilyPreset by appContainer.settingsRepository.fontFamilyPreset.collectAsState(...)
val visualDensity by appContainer.settingsRepository.visualDensity.collectAsState(...)

MeshifyTheme(
    fontFamily = MD3EFontFamilies.getFontFamily(fontFamilyPreset),
    visualDensity = visualDensity,
    // ...
)
```

**✅ النتيجة: الإعدادات تُطبّق على مستوى التطبيق.**

**لكن:**

### 3.2 DiscoveryScreen و RecentChatsScreen يستخدمون visualDensity و fontFamily؟

**التحقيق:**

```kotlin
// DiscoveryScreen.kt:78
val motion = LocalMeshifyMotion.current
// ❌ لا يوجد استخدام لـ visualDensity أو fontFamily
```

```kotlin
// RecentChatsScreen.kt
// ❌ لا يوجد استخدام مباشر لـ visualDensity أو fontFamily
```

**🚨 المشكلة:**

الـ `visualDensity` و `fontFamily` يُطبّقان فقط عبر `MaterialTheme` في `MainActivity`. لكن:

1. **لا يوجد Dynamic Scaling** للـ components داخل الشاشات بناءً على `visualDensity`.
2. **لا يوجد احترام لـ fontFamily** في النصوص المخصصة.

**مثال على ما يجب فعله:**

```kotlin
// في DiscoveryScreen.kt
val themeConfig = LocalMeshifyThemeConfig.current
val densityScale = themeConfig.visualDensity

Text(
    text = stringResource(R.string.screen_discovery_title),
    style = MaterialTheme.typography.headlineMedium.copy(
        fontSize = MaterialTheme.typography.headlineMedium.fontSize * densityScale
    ),
    fontFamily = themeConfig.fontFamily
)
```

**الوضع الحالي:** الـ settings تُحفَظ وتُقرأ، لكن **لا تُطبّق ديناميكياً** في جميع الشاشات.

---

## 🧪 القسم الرابع: تحدي الـ Navigation

### 4.1 ضغط زر الرجوع أثناء Handshake

**السيناريو:** المستخدم في منتصف handshake مع peer، ضغط زر الرجوع بسرعة.

**التحقيق:**

```kotlin
// MeshifyNavigation.kt:69-83
composable<Screen.Chat> { backStackEntry ->
    val route: Screen.Chat = backStackEntry.toRoute()
    val chatViewModel: ChatViewModel = viewModel(
        key = route.peerId, // ✅ Keyed by peerId
        factory = object : ViewModelProvider.Factory { ... }
    )
    ChatScreen(
        viewModel = chatViewModel,
        onBackClick = { navController.popBackStack() }
    )
}
```

**✅ النقطة الإيجابية:** الـ ViewModel مُ keyed بـ `peerId`، مما يعني أنه سيعاد استخدامه إذا عدت لنفس المحادثة.

**🚨 المشكلة:**

```kotlin
// ChatViewModel.kt:157-172
private var typingJob: Job? = null
private var isTypingSignalSent = false

private fun handleTypingSignal(isTyping: Boolean) {
    typingJob?.cancel()
    typingJob = viewModelScope.launch {
        if (isTyping) {
            if (!isTypingSignalSent) {
                chatRepository.sendSystemCommand(peerId, "TYPING_ON")
                isTypingSignalSent = true
            }
            delay(3000)
            chatRepository.sendSystemCommand(peerId, "TYPING_OFF")
            isTypingSignalSent = false
        }
        // ...
    }
}
```

**عندما يضغط المستخدم "Back":**

1. الـ `ChatViewModel` سيُدمّر (إذا لم يكن keyed أو إذا خرجنا من الشاشة تماماً).
2. الـ `viewModelScope` سيُلغى، مما يُلغي الـ `typingJob`.
3. **لكن:** إذا كان الـ `sendSystemCommand` قيد التنفيذ، قد لا يُلغى بشكل نظيف.

**الأثر:**
- قد يُرسَل `TYPING_ON` بدون `TYPING_OFF` لاحقاً.
- الـ peer الآخر قد يظل يعتقد أنك تكتب للأبد.

**العلاج:**

```kotlin
override fun onCleared() {
    super.onCleared()
    // Ensure typing state is cleaned up
    viewModelScope.launch {
        if (isTypingSignalSent) {
            chatRepository.sendSystemCommand(peerId, "TYPING_OFF")
        }
    }
}
```

---

### 4.2 تنظيف State عند الرجوع

**التحقيق:**

```kotlin
// ChatViewModel.kt:139-144
fun clearSelection() {
    _selectedMessageIds.value = emptySet()
}

fun deleteSelectedMessages() {
    val ids = _selectedMessageIds.value.toList()
    if (ids.isEmpty()) return
    viewModelScope.launch {
        deleteMessagesUseCase(ids)
        clearSelection()
    }
}
```

**✅ النتيجة:** الـ `_selectedMessageIds` هو `MutableStateFlow`، وعند تدمير الـ ViewModel، سيُمسح تلقائياً.

**لكن:**

```kotlin
// ChatViewModel.kt:135-137
private val _pendingImageUri = MutableStateFlow<Uri?>(null)
val pendingImageUri: StateFlow<Uri?> = _pendingImageUri
```

**🚨 المشكلة:** إذا كان المستخدم اختار صورة للإرسال، ثم ضغط "Back" قبل الإرسال:

1. الـ `_pendingImageUri` سيُمسح (لأنه جزء من ViewModel).
2. **لكن:** لا يوجد إشعار للـ UI أن الصورة أُلغيت.
3. **الأهم:** لا يوجد cleanup للـ temporary file إذا كان الـ URI يشير لملف مؤقت.

**العلاج:**

```kotlin
override fun onCleared() {
    super.onCleared()
    _pendingImageUri.value?.let { uri ->
        // Clean up temporary file if needed
        FileUtils.deleteTemporaryFile(context, uri)
    }
}
```

---

## 📋 ملخص التوصيات حسب الأولوية

| الأولوية | الإجراء | الجهد | التأثير |
|----------|---------|-------|---------|
| P0 | إضافة Write Timeout في `SocketManager.sendPayload()` | منخفض | عالي جداً |
| P0 | إضافة Dead Peer Detection في `LanTransportImpl` | متوسط | عالي |
| P0 | إصلاح Fallback Payload في `PayloadSerializer.deserialize()` | منخفض | عالي |
| P1 | إضافة UTF-8 Charset صريح في deserialization | منخفض | متوسط |
| P1 | إضافة Max Payload Size Check في deserialization | منخفض | متوسط |
| P1 | إضافة `onCleared()` cleanup في `ChatViewModel` | منخفض | متوسط |
| P2 | تطبيق `visualDensity` ديناميكياً في جميع الشاشات | متوسط | منخفض |
| P2 | إضافة Temporary File Cleanup عند ViewModel destruction | منخفض | منخفض |

---

## ✅ النقاط الإيجابية

1. **PayloadSerializer آمن جداً** مع Bounds Checking و Exception Handling شامل.
2. **SocketManager لديه Read Timeouts** صحيحة.
3. **Navigation نظامه نظيف** مع Keyed ViewModels.
4. **State Management يستخدم StateFlow** بشكل صحيح.

---

## 🏁 الخلاصة النهائية

**التطبيق صامد وظيفياً**، لكن هناك **ثغرات حرجة في التعامل مع الانقطاعات**:

1. **لا يوجد Write Timeout** = خطر تعليق Coroutines.
2. **لا يوجد Dead Peer Detection** = خطر محاولات إرسال لا نهائية لـ peers أموات.
3. **Fallback Payload يحفظ البيانات المشوهة** = خطر تلوث البيانات.

**التوصية النهائية:**
- **إصلاح P0 فوراً** قبل أي feature جديد.
- **تأجيل P1-P2** للمرحلة القادمة.
- **إضافة Integration Tests** للسيناريوهات الحرجة (انقطاع، بيانات مشوهة، navigation سريع).

---

**تم التدقيق بواسطة:** Qwen (Staff Engineer Agent)  
**التوقيع:** 🖊️  
**الحالة:** ⚠️ **Needs Immediate Attention**  
**الجولة القادمة المقترحة:** Encryption & Security Audit
