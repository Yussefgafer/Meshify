# `:core:data` — طبقة البيانات (Android Library)

**الغرض:** طبقة البيانات. تحوي قاعدة Room، واجهات DAO، الكيانات، وتنفيذات المستودعات. تدير التخزين المحلي (DataStore للإعدادات، Room للرسائل والمحادثات) ونقل البيانات عبر الشبكة والاتصالات P2P.

**البناء (`build.gradle.kts`):** `namespace = "com.p2p.meshify.core.data"`. تعتمد على `:core:common`, `:core:domain`, `:core:network`. تستخدم **Room** (runtime, ktx, compiler عبر ksp)، **DataStore Preferences**، **Hilt** (android + compiler عبر ksp)، `kotlinx-serialization-json`, `kotlinx-coroutines-core`, `androidx.core.ktx`. مخطط Room: `schemaDirectory("$projectDir/schemas")`.

## الملفات الرئيسية

جميع المسارات نسبة إلى `core/data/src/main/java/com/p2p/meshify/core/data/`:

| الملف | المحتوى |
|---|---|
| `local/entity/Entities.kt` | كيانات Room: `ChatEntity`, `MessageEntity`, `MessageAttachmentEntity`, `PendingMessageEntity`. و enum `MessageStatus` (`QUEUED, SENDING, SENT, DELIVERED, READ, FAILED, RECEIVED`). |
| `local/dao/Daos.kt` | DAOs: `ChatDao` (getAllChats, insertChat, searchChats, resetUnreadCount)، `MessageDao` (getMessagesPaged, insertMessage, updateMessageStatus, searchMessagesInChat)، `PendingMessageDao` (getByRecipient, insert, update, delete, getAll). |
| `local/MeshifyDatabase.kt` | `MeshifyDatabase` — قاعدة البيانات (الإصدار 7). الكيانات: Chat/Message/MessageAttachment/PendingMessage (حُذف جدول `trusted_peers` في v7). تضم `MIGRATION_6_7`. |
| `repository/ChatRepositoryImpl.kt` | `ChatRepositoryImpl` — تنفيذ `IChatRepository`. Facade يجمع 5 مستودعات متخصصة. يعالج إرسال الرسائل (نص عادي)، الحمولات الواردة (كل الأنواع)، التوقيع (ACK)، والتسلسل اليدوي لـ `MessageEnvelope` عبر `ByteBuffer`. |
| `repository/MessageRepository.kt` | إرسال الرسائل (نص وملفات). `saveAndSend()` تحفظ بالـ DB أولاً ثم ترسل وتحدّث الحالة. `sendFileWithProgress()` بقراءة الملف مع تتبع التقدم. `selectBestTransport()`. تستخدم `withTimeout(30s)`. |
| `repository/ChatManagementRepository.kt` | CRUD للمحادثات: `getAllChats()`, `searchChats()`, `deleteChat()`, `markChatAsRead()`, `deleteMessage()`, `forwardMessage()`. |
| `repository/SettingsRepository.kt` | تنفيذ `ISettingsRepository` عبر DataStore Preferences. +30 مفتاحاً (الاسم، الثيم، الألوان، MD3E، النقل، اللغة، الإشعارات، النسخ الاحتياطي). `safeEdit()` لمعالجة الأخطاء. |
| `repository/FileManagerImpl.kt` | تنفيذ `IFileManager` عبر Android Context. يحفظ الملفات في `filesDir/media/`. |
| `repository/MessageAttachmentRepository.kt` | مرفقات رسائل الألبومات: `saveAttachments()`, `getAttachmentsForMessage()`. |
| `repository/PendingMessageRepository.kt` | الرسائل المعلقة: `retryPendingMessages()` (exponential backoff + jitter)، `sendMessageWithBackoff()` (حتى 5 محاولات)، `pendingCount` StateFlow. |
| `repository/ReactionRepository.kt` | التفاعلات: `addReaction()` (تحديث DB + إرسال للطرف النظير). |
| `util/NotificationHelper.kt` | الإشعارات: قنوات، رد مضمّن عبر RemoteInput، توقيع HMAC عبر Android KeyStore مع تدوير مفتاح كل 30 يوماً. |

## مخطط قاعدة البيانات (v7)

```
chats (peerId PK, peerName, lastMessage, lastTimestamp, unreadCount)
 └─ messages (id PK, chatId FK, senderId, text, mediaPath, type, timestamp,
              isFromMe, status, reaction, replyToId, groupId)
       └─ message_attachments (id PK, type, messageId FK, filePath)
pending_messages (id PK, recipientId, recipientName, content, type, timestamp, status, retryCount, maxRetries)
```

**الفهارس:** `chats.lastTimestamp`؛ `messages.chatId`، `messages.chatId+timestamp`، `messages.senderId`، `messages.status`، `messages.groupId`.

**تاريخ الإصدارات:** v1 أساسي → v2 فهارس → v3/v4 فهرس `groupId` → v5 جدول `trusted_peers` (TOFU) → v6 عمود `unreadCount` → v7 حذف `trusted_peers`.

## قرارات تقنية ظاهرة

- **نمط Facade:** `ChatRepositoryImpl` يُفوَّض إلى 5 مستودعات متخصصة.
- **تسلسل رسالة مخصص:** `ChatRepositoryImpl` يُسلسل/يفك `MessageEnvelope` يدوياً عبر `java.nio.ByteBuffer` (بدلاً من kotlinx-serialization).
- **دورة حياة Coroutine:** `SupervisorJob + CoroutineScope(Dispatchers.IO)` مع `Closeable` للتنظيف.
- **النظير غير المتصل:** الرسائل توضع في `PendingMessageEntity` وتُعاد محاولتها مع exponential backoff (`BASE_DELAY=1s`, `MAX_DELAY=30s`, `MAX_ATTEMPTS=5`).
- **DataStore:** `SettingsRepository` بنمط `safeEdit()` لمعالجة أخطاء الكتابة.
- **حدود الحجم:** 10MB لـ `Payload` عبر الشبكة، 100MB للملفات في `sendFileWithProgress()`.
