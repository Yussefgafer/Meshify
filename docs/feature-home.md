# `:feature:home` — قائمة المحادثات الأخيرة

**الغرض:** الشاشة الرئيسية (بعد الإعداد الأولي). تعرض كل المحادثات مع بحث، حذف بالسحب، حالة الاتصال (Online/Offline)، وعدد الرسائل غير المقروءة. تنتقل إلى شاشة الاكتشاف (إضافة محادثة) والإعدادات.

**الاعتماديات (`build.gradle.kts`):** `:core:domain`, `:core:data`, `:core:ui`, `:core:common` + Compose BOM، material3، icons-extended، Coil 3، lifecycle، Hilt + navigation-compose.

## الملفات

جميع المسارات نسبة إلى `feature/home/src/main/java/com/p2p/meshify/feature/home/`:

| الملف | المحتوى |
|---|---|
| `RecentChatsScreen.kt` | الشاشة الرئيسية: `Scaffold` + `CenterAlignedTopAppBar` (عنوان + زر Settings)، `FloatingActionButton` → Discovery، `OutlinedTextField` بحث، `LazyColumn`، حوار حذف، وحالات Loading/Error/Empty. تستخدم `imePadding()`. |
| `RecentChatsUiState.kt` | `data class RecentChatsUiState` (`chats`, `onlinePeers: Set<String>`, حالة التحميل، الخطأ). |
| `RecentChatsViewModel.kt` | `@HiltViewModel`. يحمّل المحادثات مع بحث (debounce 300ms عبر `flatMapLatest`)، يراقب `onlinePeers`، يوفّر `deleteChat()`, `markChatAsRead()`, `retryLoad()`, `updateSearchQuery()`. |

## الشاشات

- **`RecentChatsScreen`** (المسار `Screen.Home`، route فارغ `""`): المكونات الرئيسية:
  - `MagneticChatItem` + `PhysicsSwipeToDelete` (سحب للحذف)، `MeshifyListItem` (avatar + حالة اتصال + اسم + آخر رسالة + وقت + `UnreadBadge`)، `DeleteConfirmationDialog`.
  - ثوابت: `SEARCH_BAR_BORDER_ALPHA = 0.5f`، `EMPTY_STATE_TEXT_ALPHA = 0.7f`، `MAX_UNREAD_DISPLAY = 99`.

## قرارات تقنية

- يستخدم `ChatRepositoryImpl` مباشرة (وليس `IChatRepository`) لأن `getAllChats()`/`searchChats()`/`onlinePeers` ليست في واجهة domain.
- تدفق Room Flow مستمر (ليس `take(1)`) — أي تغيير بالـ DB ينعكس فوراً.
- جمع `onlinePeers` في Coroutine منفصل لتحديث حالة الاتصال لحظياً.
- لا توجد ملفات اختبارية (حُذفت حسب قرار المستخدم — راجع `QWEN.md`).
